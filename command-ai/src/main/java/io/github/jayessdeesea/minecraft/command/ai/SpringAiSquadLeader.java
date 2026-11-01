package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@link SquadLeader} whose decisions come from an LLM, per AGENTS.md's "Behavior modeling"
 * section: {@link #decide} is {@link #buildContext} (pure) -&gt; {@link #callModel} (impure - the
 * one call into Spring AI) -&gt; {@link #toSquadOrder} (pure validation), so only {@code callModel}
 * needs faking in a test.
 *
 * <p>The role, doctrine and coordinate conventions are the client's system prompt
 * ({@code prompts/squad-leader-system.md}, wired in {@link CommandAiConfiguration}); each call's
 * user prompt is just the situation, in XML-tagged sections. The reply is structured output
 * ({@link SquadOrderReply}): provider-native schema mode where the model supports it, schema
 * validation with retries either way. A reply that still fails {@link #toSquadOrder}'s checks
 * throws - AGENTS.md's no-fallbacks tenet - and the caller keeps the squad on its last order.
 *
 * <p>Not a {@code @Component}: one instance per squad, constructed by the composition root.
 */
public final class SpringAiSquadLeader implements SquadLeader {

    private final String name;
    private final ChatClient chatClient;

    // volatile, not locked: receiveObjective comes from the general's thread, decide from this
    // squad's command thread - each field has one writer.
    private volatile Objective objective;
    private volatile SquadOrder lastOrder;
    private volatile Report report = new Report.Idle();

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

    @Override
    public Report report() {
        return report;
    }

    /**
     * No objective yet is not a failure - the squad-level analog of {@code Order.Idle}. Returns
     * empty without calling the model, both to avoid a pointless call and so it's testable
     * without one.
     */
    @Override
    public Optional<SquadOrder> decide(List<SoldierStatus> soldierStatuses, TerrainSurvey terrain) {
        Objective current = this.objective;
        if (current == null) {
            return Optional.empty();
        }
        SquadOrderReply reply = callModel(buildContext(name, current, lastOrder, soldierStatuses, terrain));
        SquadOrder order = toSquadOrder(reply);
        lastOrder = order;
        report = reply.cantco() == null || reply.cantco().isBlank() ? new Report.Moving() : new Report.Cantco(reply.cantco());
        return Optional.of(order);
    }

    static PromptContext buildContext(String squadName, Objective objective, SquadOrder lastOrder,
                                      List<SoldierStatus> soldierStatuses, TerrainSurvey terrain) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<objective>\n")
                .append("Mission: ").append(objective.mission()).append('\n')
                .append("Location: ").append(describe(objective.location())).append('\n')
                .append("Intent: ").append(objective.intent()).append('\n')
                .append("</objective>\n\n");
        prompt.append("<current_order>\n")
                .append(lastOrder == null ? "None yet." : describe(lastOrder)).append('\n')
                .append("</current_order>\n\n");
        prompt.append("<squad name=\"").append(squadName).append("\">\n");
        for (int i = 0; i < soldierStatuses.size(); i++) {
            SoldierStatus soldier = soldierStatuses.get(i);
            prompt.append("- ").append(soldier.name()).append(i == 0 ? " (point man)" : "")
                    .append(" at ").append(describe(soldier.position()))
                    .append(": ").append(describe(soldier.report())).append('\n');
        }
        prompt.append("</squad>\n\n");
        prompt.append(describe(terrain)).append('\n');
        prompt.append("Decide the squad's order now.");
        return new PromptContext(prompt.toString());
    }

    SquadOrderReply callModel(PromptContext context) {
        return chatClient.prompt()
                .user(context.prompt())
                .call()
                .entity(SquadOrderReply.class, spec -> spec.useProviderStructuredOutput().validateSchema());
    }

    static SquadOrder toSquadOrder(SquadOrderReply reply) {
        if (reply == null) {
            throw new IllegalStateException("squad leader model returned no order");
        }
        if (!Double.isFinite(reply.x()) || !Double.isFinite(reply.y()) || !Double.isFinite(reply.z())) {
            throw new IllegalStateException("non-finite objective coordinates: " + reply);
        }
        if (reply.y() < -64 || reply.y() > 320) {
            throw new IllegalStateException("objective height outside the world: " + reply);
        }
        if (reply.formation() == null || reply.interval() == null) {
            throw new IllegalStateException("missing formation or interval: " + reply);
        }
        if (!(reply.verticalTolerance() > 0)) {
            throw new IllegalStateException("vertical tolerance must be positive: " + reply);
        }
        if (reply.intent() == null || reply.intent().isBlank()) {
            throw new IllegalStateException("an order without intent: " + reply);
        }
        return new SquadOrder(new Position(reply.x(), reply.y(), reply.z()), reply.formation(), reply.interval(),
                reply.verticalTolerance(), reply.intent());
    }

    /**
     * The survey as a compact table: one row per z (north at the top), one column per x, each cell
     * the ground height - suffixed {@code x} if the point man can't reach it - or {@code ~~} for
     * water and {@code ??} for unknown. The system prompt explains the notation.
     */
    static String describe(TerrainSurvey terrain) {
        StringBuilder table = new StringBuilder("<terrain>\n      ");
        int columns = 2 * terrain.radius() / terrain.spacing() + 1;
        for (int c = 0; c < columns; c++) {
            table.append(String.format(Locale.ROOT, "%6s", "x=" + terrain.samples().get(c).x()));
        }
        table.append('\n');
        for (int r = 0; r * columns < terrain.samples().size(); r++) {
            table.append(String.format(Locale.ROOT, "%-6s", "z=" + terrain.samples().get(r * columns).z()));
            for (int c = 0; c < columns; c++) {
                TerrainSurvey.Sample sample = terrain.samples().get(r * columns + c);
                String cell = switch (sample.ground()) {
                    case LAND -> sample.height() + (sample.reachable() ? "" : "x");
                    case WATER -> "~~";
                    case UNKNOWN -> "??";
                };
                table.append(String.format(Locale.ROOT, "%6s", cell));
            }
            table.append('\n');
        }
        return table.append("</terrain>\n").toString();
    }

    static String describe(Position position) {
        return String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", position.x(), position.y(), position.z());
    }

    private static String describe(SquadOrder order) {
        return String.format(Locale.ROOT, "Move to %s in %s at %s interval, within %.0f blocks of that height. Intent: %s",
                describe(order.objective()), order.formation(), order.interval(), order.verticalTolerance(), order.intent());
    }

    static String describe(Report report) {
        return switch (report) {
            case Report.Idle() -> "Idle";
            case Report.Moving() -> "Moving";
            case Report.InPosition() -> "InPosition";
            case Report.InContact(int hostiles) -> "InContact with " + hostiles + " hostile(s)";
            case Report.Cantco(String reason) -> "Cantco: " + reason;
        };
    }
}
