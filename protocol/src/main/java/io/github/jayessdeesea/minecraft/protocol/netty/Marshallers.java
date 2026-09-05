package io.github.jayessdeesea.minecraft.protocol.netty;

/**
 * Maps each {@link ProtocolState} to its {@link PacketMarshaller}, shared by every session-manager
 * handler. Stateless static helper, not a base class - none of the marshaller implementations
 * hold any mutable state, so singleton instances are safe to hand out and reuse across states.
 */
final class Marshallers {

    private static final PacketMarshaller HANDSHAKE = new HandshakeMarshaller();
    private static final PacketMarshaller STATUS = new StatusMarshaller();
    private static final PacketMarshaller LOGIN = new LoginMarshaller();
    private static final PacketMarshaller CONFIGURATION = new ConfigurationMarshaller();
    private static final PacketMarshaller PLAY = new PlayMarshaller();

    private Marshallers() {
    }

    static PacketMarshaller forState(ProtocolState state) {
        return switch (state) {
            case HANDSHAKE -> HANDSHAKE;
            case STATUS -> STATUS;
            case LOGIN -> LOGIN;
            case CONFIGURATION -> CONFIGURATION;
            case PLAY -> PLAY;
        };
    }
}
