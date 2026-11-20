package io.github.jayessdeesea.minecraft.app;

/** The squad simulation's terrain from a world kept live ({@link WorldRecords}): its states as kept, air where unknown or outside it. */
final class SimWorlds {

    private SimWorlds() {
    }

    static SimServer.Terrain replay(WorldRecords.Snapshot world) {
        return (x, y, z) -> {
            int state = world.stateAt(x, y, z);
            return state == WorldRecords.UNKNOWN ? SimServer.AIR : state;
        };
    }
}
