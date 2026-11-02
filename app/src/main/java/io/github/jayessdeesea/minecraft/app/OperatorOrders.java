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
 * Which chat lines are orders from a human operator: {@code !mission <text>} from a player named
 * in {@code minecraft.operators}. Everything else in chat - the squad's own radio net, other
 * players talking - is ignored; a {@code !mission} line from anyone not on the list is logged and
 * ignored.
 *
 * <p>The sender is matched by UUID: in offline mode the server derives each player's UUID from
 * its name ({@code "OfflinePlayer:" + name}, a version-3 UUID), so the list of names is the list
 * of UUIDs without decoding player info. Offline mode authenticates nobody, so this keeps stray
 * players from steering the experiment by accident; it doesn't stop an impostor.
 */
@Component
public class OperatorOrders {

    private static final Logger LOG = LogManager.getLogger(OperatorOrders.class);

    static final String PREFIX = "!mission ";

    public record Order(String operator, String mission) {
    }

    private final Map<UUID, String> operators;

    public OperatorOrders(@Value("${minecraft.operators}") String operators) {
        this.operators = Arrays.stream(operators.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableMap(OperatorOrders::offlineUuid, Function.identity()));
    }

    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Order> missionFrom(ChatMessage chat) {
        if (!chat.message().startsWith(PREFIX)) {
            return Optional.empty();
        }
        String mission = chat.message().substring(PREFIX.length()).trim();
        String operator = operators.get(chat.sender());
        if (operator == null) {
            LOG.warn("Ignoring a mission from {}, who isn't in minecraft.operators: {}", chat.sender(), mission);
            return Optional.empty();
        }
        if (mission.isEmpty()) {
            LOG.warn("Ignoring an empty mission from {}", operator);
            return Optional.empty();
        }
        return Optional.of(new Order(operator, mission));
    }
}
