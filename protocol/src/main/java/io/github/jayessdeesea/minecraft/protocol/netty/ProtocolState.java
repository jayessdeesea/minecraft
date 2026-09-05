package io.github.jayessdeesea.minecraft.protocol.netty;

/**
 * The five connection states (docs/protocol-spec.md § 3) and the transitions the protocol
 * actually allows. Only the session-manager handler (e.g. {@link StatusClientHandler},
 * {@link JoinClientHandler}) ever reads this - everything else in the pipeline just processes
 * whatever the currently-active {@link PacketMarshaller} produces, without knowing what state
 * that implies.
 */
public enum ProtocolState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY;

    public boolean canTransitionTo(ProtocolState next) {
        return switch (this) {
            case HANDSHAKE -> next == STATUS || next == LOGIN;
            case LOGIN -> next == CONFIGURATION;
            case CONFIGURATION -> next == PLAY;
            case STATUS, PLAY -> false;
        };
    }
}
