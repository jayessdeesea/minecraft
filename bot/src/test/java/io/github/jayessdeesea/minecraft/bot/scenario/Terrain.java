package io.github.jayessdeesea.minecraft.bot.scenario;

import java.util.Random;

import static io.github.jayessdeesea.minecraft.bot.scenario.SyntheticWorld.MAX;
import static io.github.jayessdeesea.minecraft.bot.scenario.SyntheticWorld.MIN;

/**
 * Terrain families. Each builds a randomised instance of one kind of ground between the start
 * (south, {@code startZ}) and the objective (north, {@code goalZ}). Some instances are
 * deliberately impassable - a lake bank to bank, a ravine with no bridge, a walled compound with no
 * gate - because "says CANTCO instead of trying forever" is as much a property as "arrives".
 */
public enum Terrain {

    /**
     * A plateau 2 to 8 blocks higher, across the whole map on the objective's side, behind a sheer
     * wall: out of reach on foot, a climb for a soldier carrying blocks.
     */
    CLIFF {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int wallZ = goalZ + 12 + random.nextInt(Math.max(1, startZ - goalZ - 24));
            int height = 2 + random.nextInt(7);
            for (int x = MIN; x <= MAX; x++) {
                for (int z = MIN; z <= wallZ; z++) {
                    world.setGround(x, z, Scenario.BASE_GROUND + height);
                }
            }
        }
    },

    /** Open ground with scattered one-block rocks. */
    FLAT {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int rocks = random.nextInt(60);
            for (int i = 0; i < rocks; i++) {
                int x = coord(random);
                int z = coord(random);
                world.solid(x, world.ground(x, z) + 1, z);
            }
        }
    },

    /** Rolling hills from a few random sine waves; steep spots can make parts unreachable. */
    HILLS {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            double amplitude = 2 + random.nextDouble() * 12;
            double[][] waves = new double[3][4];
            for (double[] w : waves) {
                w[0] = 0.03 + random.nextDouble() * 0.12; // x frequency
                w[1] = 0.03 + random.nextDouble() * 0.12; // z frequency
                w[2] = random.nextDouble() * Math.PI * 2;  // phase
                w[3] = random.nextDouble();                // weight
            }
            for (int x = MIN; x <= MAX; x++) {
                for (int z = MIN; z <= MAX; z++) {
                    double h = 0;
                    for (double[] w : waves) {
                        h += w[3] * Math.sin(x * w[0] + z * w[1] + w[2]);
                    }
                    world.setGround(x, z, Scenario.BASE_GROUND + (int) Math.round(amplitude * h / 1.5));
                }
            }
        }
    },

    /** Trees: 1x1 trunks with a leaf canopy - solid at head height, so soldiers weave through. */
    FOREST {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int trees = 40 + random.nextInt(260);
            for (int i = 0; i < trees; i++) {
                int x = coord(random);
                int z = coord(random);
                int g = world.ground(x, z);
                int trunk = 4 + random.nextInt(3);
                for (int y = g + 1; y <= g + trunk; y++) {
                    world.solid(x, y, z);
                }
                int r = random.nextBoolean() ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        world.solid(x + dx, g + trunk, z + dz);
                        world.solid(x + dx, g + trunk + 1, z + dz);
                    }
                }
            }
        }
    },

    /** An elliptical lake between start and objective; wide ones span the map bank to bank. */
    LAKE {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int cx = coord(random) / 2;
            int cz = (startZ + goalZ) / 2 + random.nextInt(11) - 5;
            double rx = 6 + random.nextInt(60);
            double rz = 4 + random.nextInt(12);
            for (int x = MIN; x <= MAX; x++) {
                for (int z = MIN; z <= MAX; z++) {
                    double dx = (x - cx) / rx;
                    double dz = (z - cz) / rz;
                    if (dx * dx + dz * dz <= 1) {
                        int g = world.ground(x, z);
                        world.setGround(x, z, g - 2);
                        world.hazard(x, g - 1, z);
                        world.hazard(x, g, z);
                    }
                }
            }
        }
    },

    /** A trench across the whole map, sometimes shallow enough to cross, sometimes bridged. */
    RAVINE {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int z0 = (startZ + goalZ) / 2 + random.nextInt(11) - 5;
            int width = 1 + random.nextInt(5);
            int depth = 1 + random.nextInt(10);
            boolean bridged = random.nextInt(3) == 0;
            int bridgeX = coord(random) / 2;
            for (int x = MIN; x <= MAX; x++) {
                if (bridged && x == bridgeX) {
                    continue;
                }
                for (int z = z0; z < z0 + width; z++) {
                    world.setGround(x, z, world.ground(x, z) - depth);
                }
            }
        }
    },

    /** A 3-high square wall around the objective, with a one-block gate in a random side - or none. */
    COMPOUND {
        @Override
        void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ) {
            int half = 6 + random.nextInt(6);
            int cz = goalZ;
            int gateSide = random.nextInt(5); // 4 = no gate
            int gateOffset = random.nextInt(2 * half - 1) - half + 1;
            for (int i = -half; i <= half; i++) {
                wall(world, goalX + i, cz - half, gateSide == 0 && i == gateOffset);
                wall(world, goalX + i, cz + half, gateSide == 1 && i == gateOffset);
                wall(world, goalX - half, cz + i, gateSide == 2 && i == gateOffset);
                wall(world, goalX + half, cz + i, gateSide == 3 && i == gateOffset);
            }
        }

        private void wall(SyntheticWorld world, int x, int z, boolean gate) {
            if (gate) {
                return;
            }
            int g = world.ground(x, z);
            for (int y = g + 1; y <= g + 3; y++) {
                world.solid(x, y, z);
            }
        }
    };

    abstract void build(SyntheticWorld world, Random random, int startZ, int goalX, int goalZ);

    private static int coord(Random random) {
        return MIN + random.nextInt(MAX - MIN + 1);
    }
}
