package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A {@link SquadLeader} whose tactical decisions come from an LLM call, per AGENTS.md's "Behavior
 * modeling" section: {@link #decide} splits into {@link #buildContext} (pure) -&gt;
 * {@link #callModel} (impure - the one line touching Spring AI's {@code ChatClient}) -&gt;
 * {@link #parseOrders} (pure), so only {@code callModel} needs faking in a test.
 *
 * <p>Not a {@code @Component} - one instance exists per squad (AGENTS.md's Objective section), so
 * future {@code app} wiring constructs one per squad manually, the same way {@code SoldierRunner}
 * constructs each {@code DeterministicSoldier} manually rather than as a shared singleton bean.
 */
public final class SpringAiSquadLeader implements SquadLeader {

    private final String name;
    private final ChatClient chatClient;

    // volatile, not locked: receiveObjective may be called from a different thread than decide (a
    // future General's own invocation thread) but never concurrently with itself, and decide is
    // only ever called by this squad leader's own single invocation loop - the same "sole writer"
    // guarantee DeterministicSoldier's volatile order field relies on.
    private volatile Objective objective;

    public SpringAiSquadLeader(String name, ChatClient chatClient) {
        this.name = name;
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void receiveObjective(Objective objective) {
        this.objective = objective;
    }

    /**
     * No objective received yet is not a failure to route around - it's the literal analog of
     * {@link Order.Idle}, a soldier's own "no goal, take no action" default. Short-circuits before
     * touching the model, both to avoid a pointless call and so this path is testable without one.
     */
    @Override
    public List<Order> decide(List<SoldierStatus> soldierStatuses) {
        Objective currentObjective = this.objective;
        if (currentObjective == null) {
            return soldierStatuses.stream().<Order>map(status -> new Order.Idle()).toList();
        }

        PromptContext context = buildContext(currentObjective, soldierStatuses);
        LlmResponse response = callModel(context);
        return parseOrders(response, soldierStatuses);
    }

    static PromptContext buildContext(Objective objective, List<SoldierStatus> soldierStatuses) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a squad leader commanding a small group of Minecraft player bots.\n");
        prompt.append("Your current objective: ").append(objective.description()).append("\n\n");
        prompt.append("Current squad status:\n");
        for (SoldierStatus status : soldierStatuses) {
            prompt.append("- ").append(status.name()).append(" at ").append(describe(status.position()))
                    .append(", current order: ").append(describe(status.order())).append('\n');
        }
        prompt.append("\nRespond with exactly one line per soldier, in this exact order, and no other\n");
        prompt.append("text or commentary. Each line must be exactly one of:\n");
        prompt.append("<name>: IDLE\n");
        prompt.append("<name>: TREND <x> <z>\n");
        prompt.append("where <x> and <z> are the world coordinates to trend toward.\n");
        return new PromptContext(prompt.toString());
    }

    LlmResponse callModel(PromptContext context) {
        return new LlmResponse(chatClient.prompt(context.prompt()).call().content());
    }

    static List<Order> parseOrders(LlmResponse response, List<SoldierStatus> soldierStatuses) {
        List<String> lines = response.text().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (lines.size() != soldierStatuses.size()) {
            throw new IllegalStateException("expected " + soldierStatuses.size() + " order line(s), got "
                    + lines.size() + " - raw response: " + response.text());
        }

        List<Order> orders = new ArrayList<>(soldierStatuses.size());
        for (int i = 0; i < soldierStatuses.size(); i++) {
            String expectedName = soldierStatuses.get(i).name();
            String line = lines.get(i);
            String prefix = expectedName + ":";
            if (!line.startsWith(prefix)) {
                throw new IllegalStateException("expected line " + i + " to start with \"" + prefix
                        + "\", got \"" + line + "\" - raw response: " + response.text());
            }
            orders.add(parseOrder(line.substring(prefix.length()).trim(), line, response));
        }
        return orders;
    }

    private static Order parseOrder(String body, String line, LlmResponse response) {
        if (body.equals("IDLE")) {
            return new Order.Idle();
        }
        String[] parts = body.split("\\s+");
        if (parts.length == 3 && parts[0].equals("TREND")) {
            try {
                return new Order.TrendToward(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("unparseable TREND coordinates in line \"" + line
                        + "\" - raw response: " + response.text(), e);
            }
        }
        throw new IllegalStateException("unrecognized order in line \"" + line
                + "\" - raw response: " + response.text());
    }

    private static String describe(Position position) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x(), position.y(), position.z());
    }

    private static String describe(Order order) {
        return switch (order) {
            case Order.Idle() -> "Idle";
            case Order.MoveTo move -> String.format(Locale.ROOT, "MoveTo(%.1f, %.1f, %.1f within %.1f/%.1f)",
                    move.x(), move.y(), move.z(), move.horizontalTolerance(), move.verticalTolerance());
            case Order.TrendToward trend ->
                    String.format(Locale.ROOT, "TrendToward(%.1f, %.1f)", trend.x(), trend.z());
        };
    }
}
