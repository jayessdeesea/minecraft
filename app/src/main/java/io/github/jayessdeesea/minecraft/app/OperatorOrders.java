package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.protocol.ChatMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which chat lines are orders from a human operator - from a player named in
 * {@code minecraft.operators}:
 * <ul>
 *   <li>{@code !mission <text>} - a new mission for the platoon leader (and the end of any escort);</li>
 *   <li>{@code !escort} - the squad escorts the operator; {@code !escort <player>} - that player;</li>
 *   <li>{@code !flatten <x1> <z1> <x2> <z2> <y>} - the ground worked by day: that rectangle taken down
 *   to y, everything over it dug out (operator, 2026-11-15; command.Flatten);</li>
 *   <li>{@code !farm <x> <z> [y]} - a farm on the chunk block (x, z) is in, at y - sea level unless given: the
 *   platoon leader's farm mission, its chunk flattened a block below, floored in cobblestone, the template on it
 *   (operator, 2026-11-21; command.FarmMission).</li>
 * </ul>
 * Anything else an operator says is a remark for the platoon leader, but a word starting with {@code !}
 * that isn't one of these is an order not understood - answered SAY AGAIN on the radio, never
 * dropped. Everything else in chat - the squad's own radio net, other players talking - is
 * ignored; an order from anyone not on the list is logged and ignored.
 *
 * <p>The sender is matched by UUID: in offline mode the server derives each player's UUID from
 * its name ({@code "OfflinePlayer:" + name}, a version-3 UUID), so the list of names is the list
 * of UUIDs without decoding player info. Offline mode authenticates nobody, so this keeps stray
 * players from steering the experiment by accident; it doesn't stop an impostor.
 */
@Component
public class OperatorOrders {

    private static final Logger LOG = LogManager.getLogger(OperatorOrders.class);

    static final String MISSION = "!mission";
    static final String ESCORT = "!escort";
    static final String FLATTEN = "!flatten";
    static final String FARM = "!farm";

    /** An order from the operator. */
    public sealed interface Order {
        String operator();

        record Mission(String operator, String mission) implements Order {
        }

        record Escort(String operator, String principal) implements Order {
        }

        /** The ground to flatten by day: {@code site}. */
        record Flatten(String operator, io.github.jayessdeesea.minecraft.command.Flatten site) implements Order {
        }

        /** A farm at a chunk: {@code farm}. */
        record Farm(String operator, io.github.jayessdeesea.minecraft.command.FarmMission farm) implements Order {
        }

        /** Anything else an operator says: not an order, but worth the platoon leader's hearing. */
        record Remark(String operator, String remark) implements Order {
        }

        /** A {@code !} order that isn't one: {@code command} as the operator sent it. */
        record Unknown(String operator, String command) implements Order {
        }
    }

    private final Map<UUID, String> operators;

    public OperatorOrders(@Value("${minecraft.operators}") String operators) {
        this.operators = Arrays.stream(operators.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableMap(OperatorOrders::offlineUuid, Function.identity()));
    }

    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Order> orderFrom(ChatMessage chat) {
        String[] words = chat.message().strip().split("\\s+", 2);
        String command = words[0];
        String argument = words.length > 1 ? words[1].strip() : "";
        String operator = operators.get(chat.sender());
        if (!command.equals(MISSION) && !command.equals(ESCORT) && !command.equals(FLATTEN) && !command.equals(FARM)) {
            // An operator talking, not ordering - passed up; anyone else's chat is just chat.
            if (operator == null || chat.message().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(command.startsWith("!") ? new Order.Unknown(operator, command)
                    : new Order.Remark(operator, chat.message().strip()));
        }
        if (operator == null) {
            LOG.warn("Ignoring {} from {}, who isn't in minecraft.operators: {}", command, chat.sender(), argument);
            return Optional.empty();
        }
        if (command.equals(ESCORT)) {
            return Optional.of(new Order.Escort(operator, argument.isEmpty() ? operator : argument));
        }
        if (command.equals(FLATTEN)) {
            // A site it can't read is an order not understood: SAY AGAIN, with how to give it.
            return Optional.of(io.github.jayessdeesea.minecraft.command.Flatten.parse(argument)
                    .<Order>map(site -> new Order.Flatten(operator, site))
                    .orElseGet(() -> new Order.Unknown(operator, FLATTEN + " " + argument)));
        }
        if (command.equals(FARM)) {
            return Optional.of(io.github.jayessdeesea.minecraft.command.FarmMission.parse(argument)
                    .<Order>map(farm -> new Order.Farm(operator, farm))
                    .orElseGet(() -> new Order.Unknown(operator, FARM + " " + argument)));
        }
        if (argument.isEmpty()) {
            LOG.warn("Ignoring an empty mission from {}", operator);
            return Optional.empty();
        }
        return Optional.of(new Order.Mission(operator, argument));
    }
}
