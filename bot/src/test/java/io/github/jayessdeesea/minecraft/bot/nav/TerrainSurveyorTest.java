package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey.Ground;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flat ground at y=64, with: a lake (surface y=63) everywhere x &lt; -20, and a walled pen around
 * x &gt; 20 (a 3-high wall at x = 20) whose floor is ordinary ground - visible, but unreachable.
 */
class TerrainSurveyorTest {

    private static final int GROUND = 64;

    private static final WorldView WORLD = new WorldView() {
        private boolean water(int x, int y) {
            return x < -20 && y == GROUND - 1;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return (y < GROUND && !water(x, y)) || (x == 20 && y < GROUND + 3);
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return water(x, y);
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private static TerrainSurvey.Sample at(TerrainSurvey survey, int x, int z) {
        return survey.samples().stream().filter(s -> s.x() == x && s.z() == z).findFirst().orElseThrow();
    }

    @Test
    void reportsHeightWaterAndReachability() {
        TerrainSurvey survey = TerrainSurveyor.survey(WORLD, new Position(0.5, GROUND, 0.5));

        assertEquals(81, survey.samples().size(), "9x9 grid at 8-block spacing out to 32 blocks");

        TerrainSurvey.Sample open = at(survey, 8, 0);
        assertEquals(Ground.LAND, open.ground());
        assertEquals(GROUND, open.height());
        assertTrue(open.reachable());

        assertEquals(Ground.WATER, at(survey, -24, 0).ground());

        TerrainSurvey.Sample penned = at(survey, 24, 0);
        assertEquals(Ground.LAND, penned.ground());
        assertFalse(penned.reachable(), "behind a 3-high wall");
    }

    @Test
    void aPointManCarryingBlocksCanClimbTheWall() {
        TerrainSurvey survey = TerrainSurveyor.survey(WORLD, new Position(0.5, GROUND, 0.5), 64);

        assertTrue(at(survey, 24, 0).reachable(), "pillar up beside the wall, over, and down the other side");
        assertEquals(2, at(survey, 24, 0).climb(), "two steps up beside the 3-high wall, then a jump onto it");
        assertEquals(0, at(survey, 8, 0).climb(), "a walk spends nothing");
        assertEquals(Ground.WATER, at(survey, -24, 0).ground(), "climbing doesn't make water land");
    }

    @Test
    void aClimbNeedsTheBlocksForIt() {
        assertFalse(at(TerrainSurveyor.survey(WORLD, new Position(0.5, GROUND, 0.5), 1), 24, 0).reachable(),
                "one block doesn't climb a 3-high wall");
    }

    @Test
    void aSoilTerraceTooHighToJumpIsReachedByCuttingSteps() {
        WorldView terrace = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || x >= 10 && y < GROUND + 3;
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return x >= 10 && y >= GROUND && y < GROUND + 3;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public java.util.List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(Position center, double radius) {
                return java.util.List.of();
            }
        };
        TerrainSurvey survey = TerrainSurveyor.survey(terrace, new Position(0.5, GROUND, 0.5));

        assertEquals(GROUND + 3, at(survey, 16, 0).height());
        assertTrue(at(survey, 16, 0).reachable(), "a step cut into the face, then onto the top");
    }

    @Test
    void aPointManInMidJumpStillSeesWhatsReachable() {
        TerrainSurvey survey = TerrainSurveyor.survey(WORLD, new Position(0.5, GROUND + 1.2, 0.5));

        assertTrue(at(survey, 8, 0).reachable());
    }
}
