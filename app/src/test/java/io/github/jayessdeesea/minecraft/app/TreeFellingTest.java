package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which log a cutter goes to, which logs are one tree, and the box dug round it. */
class TreeFellingTest {

    /** A trunk of three logs at (10, 64..66, 10) and one of two at (13, 64..65, 12); no ground. */
    private static final Set<BlockPos> LOGS = Set.of(
            new BlockPos(10, 64, 10), new BlockPos(10, 65, 10), new BlockPos(10, 66, 10),
            new BlockPos(13, 64, 12), new BlockPos(13, 65, 12));

    /** A world of nothing but {@code logs}. */
    private static TreeFelling over(Set<BlockPos> logs) {
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        return new TreeFelling(world, () -> 0L);
    }

    private final TreeFelling felling = over(LOGS);

    @Test
    void theNearestLogIsTheOneAcrossLeast() {
        // (10, 64, 10) is 2 across; (13, 64, 12) is 3.
        assertEquals(new BlockPos(10, 64, 10), felling.nearestLog(new Position(10.5, 64, 12.5), Set.of()).orElseThrow());
        // A log of a tree held by another cutter is passed over.
        assertEquals(new BlockPos(13, 64, 12),
                felling.nearestLog(new Position(10.5, 64, 12.5), felling.treeAt(new BlockPos(10, 64, 10))).orElseThrow());
    }

    @Test
    void upOrDownCountsHalfAsMuchAsAcross() {
        Position at = new Position(10.5, 64, 10.5);
        BlockPos level = new BlockPos(12, 64, 11); // 2.24 across
        // 1 across and 4 up is 3: farther than the level one, so height counts for something.
        BlockPos fourUp = new BlockPos(11, 68, 10);
        assertEquals(level, over(Set.of(level, fourUp)).nearestLog(at, Set.of()).orElseThrow());
        // 1 across and 2 up is 2: nearer than the level one, so height counts for less than across.
        BlockPos twoUp = new BlockPos(11, 66, 10);
        assertEquals(twoUp, over(Set.of(level, twoUp)).nearestLog(at, Set.of()).orElseThrow());
    }

    @Test
    void aLogIsLookedForAsFarAsTheLookReachAndNoFarther() {
        Position at = new Position(10.5, 64, 10.5);
        BlockPos atTheEdge = new BlockPos(10 + TreeFelling.TREE_LOOK, 64, 10);
        BlockPos pastIt = new BlockPos(10 + TreeFelling.TREE_LOOK + 1, 64, 10);
        assertEquals(Optional.of(atTheEdge), over(Set.of(atTheEdge)).nearestLog(at, Set.of()));
        assertTrue(over(Set.of(pastIt)).nearestLog(at, Set.of()).isEmpty());
    }

    @Test
    void aTreeIsTheLogsJoinedToTheFirst() {
        assertEquals(Set.of(new BlockPos(10, 64, 10), new BlockPos(10, 65, 10), new BlockPos(10, 66, 10)),
                felling.treeAt(new BlockPos(10, 65, 10)));
        assertEquals(Set.of(new BlockPos(13, 64, 12), new BlockPos(13, 65, 12)), felling.treeAt(new BlockPos(13, 64, 12)));
    }

    @Test
    void aJoinedLogFartherAcrossThanATreeSpreadsIsNotTheTree() {
        Set<BlockPos> branch = new HashSet<>();
        Set<BlockPos> tree = new HashSet<>();
        for (int x = 10; x <= 10 + TreeFelling.TREE_ACROSS + 1; x++) {
            branch.add(new BlockPos(x, 64, 10));
            if (x <= 10 + TreeFelling.TREE_ACROSS) {
                tree.add(new BlockPos(x, 64, 10));
            }
        }
        assertEquals(tree, over(branch).treeAt(new BlockPos(10, 64, 10)));
    }

    @Test
    void aTreeIsTakenInAsFarAsItsMostLogs() {
        Set<BlockPos> column = new HashSet<>();
        for (int y = 0; y < TreeFelling.TREE_LOGS + 20; y++) {
            column.add(new BlockPos(0, y, 0));
        }
        assertEquals(TreeFelling.TREE_LOGS, over(column).treeAt(new BlockPos(0, 0, 0)).size());
    }

    @Test
    void theBoxIsTwoRoundTheLogsAndTheCrownOverThem() {
        Box box = TreeFelling.treeBox(felling.treeAt(new BlockPos(10, 64, 10)));
        assertEquals(new Box(8, 64, 8, 12, 66 + TreeFelling.TREE_CROWN, 12), box);
    }
}
