package io.github.jayessdeesea.minecraft.protocol;

import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;

/**
 * The subset of Play-state session info decoded so far, seeded from {@link PlayLoginPacket} - the
 * first packet Play sends. This is a starting shape, not a finished one: it grows one confirmed
 * field at a time as more of Play's packet catalog gets decoded (docs/protocol-spec.md § 3.5),
 * the same way Configuration's catalog was built. Not yet wired into a persistent connection or
 * the {@code bot} module - a {@code Soldier} reading live position/health/inventory out of this is
 * the eventual destination, not what exists today.
 */
public record PlayState(int entityId, String dimensionName, int gameMode) {

    public static PlayState fromLogin(PlayLoginPacket login) {
        return new PlayState(login.entityId(), login.dimensionName(), login.gameMode());
    }
}
