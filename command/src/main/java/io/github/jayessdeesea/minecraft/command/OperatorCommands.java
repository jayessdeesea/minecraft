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
     * The supply point mission on the chunk block ({@code x}, {@code z}) is in, at {@code level} - sea level if none: the
     * chunk flattened to the level, paved in cobblestone there, then the supply point's template on it.
     */
    String giveSupplyPointMission(int x, int z, OptionalInt level);

    /**
     * The mineshaft mission on the chunk block ({@code x}, {@code z}) is in, its head at {@code level} - sea level if
     * none: the head fenced, lit and its chests put up, then its shaft mined till dry.
     */
    String giveMineshaftMission(int x, int z, OptionalInt level);

    /**
     * The mission for an animal farm - {@code animal} one of sheep, cow, pig, chicken - on the chunk a block is in. Not
     * every implementation gives it: one that doesn't refuses loudly.
     */
    default String giveAnimalFarmMission(String animal, int x, int z, OptionalInt level) {
        throw new UnsupportedOperationException("no animal farm missions here");
    }

    /**
     * The mission for a template named in words - {@code template} "placeholder": the chunk paved, fenced, gated and lit,
     * held for what's put there later - on the chunk a block is in. Not every implementation gives it.
     */
    default String giveTemplateMission(String template, int x, int z, OptionalInt level) {
        throw new UnsupportedOperationException("no template missions here");
    }

    /**
     * A squad sent to look round block ({@code x}, {@code z}) for {@code animal} - sheep, cow or chicken - and lead what
     * it finds to block ({@code toX}, {@code toZ}). Not every implementation gives it.
     */
    default String giveScoutMission(String animal, int x, int z, int toX, int toZ) {
        throw new UnsupportedOperationException("no scout missions here");
    }

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
