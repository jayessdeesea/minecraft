package io.github.jayessdeesea.minecraft.command;

import java.util.OptionalInt;

/**
 * What the operator's chat may have the platoon leader do: domain commands, each through the same path and checks as the
 * operator's own orders from game chat (WILCO on the net, the order traced) - never a register's row written, edited or
 * deleted. Each returns what was done, as said; one that finds nothing to act on says so instead and does nothing.
 *
 * @throws IllegalArgumentException from any of them, for a command it can't carry out as given - a level outside the world
 */
public interface OperatorCommands {

    /** The farm mission on the chunk block ({@code x}, {@code z}) is in, at {@code level} - sea level if none: {@code !farm}. */
    String giveFarmMission(int x, int z, OptionalInt level);

    /**
     * The tree farm mission on the chunk block ({@code x}, {@code z}) is in, at {@code level} - sea level if none: the
     * chunk flattened to the level, then the tree chunk's template on it.
     */
    String giveTreeFarmMission(int x, int z, OptionalInt level);

    /**
     * The farm mission open on chunk ({@code chunkX}, {@code chunkZ}) cancelled: its phases not done closed SUPERSEDED,
     * {@code reason} kept in each row's why, none deleted - and not taken up again after a restart.
     */
    String cancelMission(int chunkX, int chunkZ, String reason);

    /** The platoon's mission replaced by {@code mission}: {@code !mission}. */
    String replacePlatoonMission(String mission);

    /** The first squad attached to {@code player} as a protective detail: {@code !escort}. */
    String escort(String player);

    /** The ground the squads flatten by day: {@code !flatten}. */
    String setFlattenGround(Flatten ground);
}
