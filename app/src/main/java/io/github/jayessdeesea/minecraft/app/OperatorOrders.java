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
 *   <li>{@code !mission <text>} - a new mission for the general (and the end of any escort);</li>
 *   <li>{@code !escort} - the squad escorts the operator; {@code !escort <player>} - that player.</li>
 * </ul>
 * Everything else in chat - the squad's own radio net, other players talking - is ignored; an
 * order from anyone not on the list is logged and ignored.
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

    /** An order from the operator. */
    public sealed interface Order {
        String operator();

        record Mission(String operator, String mission) implements Order {
        }

        record Escort(String operator, String principal) implements Order {
        }

        /** Anything else an operator says: not an order, but worth the general's hearing. */
        record Remark(String operator, String remark) implements Order {
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
        if (!command.equals(MISSION) && !command.equals(ESCORT)) {
            // An operator talking, not ordering - passed up; anyone else's chat is just chat.
            return operator == null || command.startsWith("!") || chat.message().isBlank() ? Optional.empty()
                    : Optional.of(new Order.Remark(operator, chat.message().strip()));
        }
        if (operator == null) {
            LOG.warn("Ignoring {} from {}, who isn't in minecraft.operators: {}", command, chat.sender(), argument);
            return Optional.empty();
        }
        if (command.equals(ESCORT)) {
            return Optional.of(new Order.Escort(operator, argument.isEmpty() ? operator : argument));
        }
        if (argument.isEmpty()) {
            LOG.warn("Ignoring an empty mission from {}", operator);
            return Optional.empty();
        }
        return Optional.of(new Order.Mission(operator, argument));
    }
}
