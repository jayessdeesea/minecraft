package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmDutiesTest {

    /** Four on the farm: three harvest a share each, the fourth bakes. */
    @Test
    void threeHarvestAndTheFourthBakes() {
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob", "Carl", "Dana"));
        assertEquals(FarmDuties.Kind.BAKER, duties.get("Dana").kind());
        assertEquals(new FarmDuties.Duty("Bob", FarmDuties.Kind.HARVESTER, 1, 3), duties.get("Bob"));
    }

    /** Two on the farm both harvest: a baker would leave half the plot to one. */
    @Test
    void twoBothHarvest() {
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob"));
        assertEquals(FarmDuties.Kind.HARVESTER, duties.get("Bob").kind());
        assertEquals(2, duties.get("Bob").shares());
    }

    /** The shares cover the plot, each cell in one of them. */
    @Test
    void theSharesCoverThePlotOnce() {
        List<Integer> plot = IntStream.range(0, 80).boxed().toList();
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob", "Carl", "Dana"));
        List<Integer> covered = new ArrayList<>();
        for (String h : List.of("Alice", "Bob", "Carl")) {
            covered.addAll(FarmDuties.share(plot, duties.get(h)));
        }
        assertEquals(plot, covered);
    }

    /** Each harvester's ground is a run of whole columns of the plot - no cell in two - and the baker's is the dump. */
    @Test
    void harvestersAreOrderedOntoColumnsAndTheBakerToTheDump() {
        List<Position> plot = new ArrayList<>();
        for (int x = 6; x <= 14; x++) {
            for (int z = -4; z <= 4; z++) {
                plot.add(new Position(x, 63, z));
            }
        }
        Optional<Position> dump = Optional.of(new Position(3, 64, 2));
        Map<String, DutyOrder> orders = FarmDuties.orders(List.of("Alice", "Bob", "Carl", "Dana"), plot, dump);

        assertEquals("harvester over x 6..8, z -4..4, the dump at (3, 64, 2)", orders.get("Alice").line());
        assertEquals("harvester over x 9..11, z -4..4, the dump at (3, 64, 2)", orders.get("Bob").line());
        assertEquals("harvester over x 12..14, z -4..4, the dump at (3, 64, 2)", orders.get("Carl").line());
        assertEquals("baker, the dump at (3, 64, 2)", orders.get("Dana").line());
        for (Position cell : plot) {
            assertEquals(1, orders.values().stream().filter(o -> o.ground().filter(g -> g.contains((int) cell.x(), (int) cell.y(), (int) cell.z()))
                    .isPresent()).count(), "cell " + cell + " in one harvester's ground");
        }
    }

    /** A plot of x 0..39 at y 63, z 0..9: the first 16 columns a chunk farm's, the rest round a pond. */
    private static List<Position> plotWithAChunkFarm() {
        List<Position> plot = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 10; z++) {
                plot.add(new Position(x, 63, z));
            }
        }
        return plot;
    }

    private static final FarmDuties.Tended CHUNK = new FarmDuties.Tended(new Box(0, 62, 0, 16, 65, 16), new Position(13, 64, 13));

    /** Four on a farm with a chunk farm on it: the last harvester tends the chunk, the other two share the rest, the fourth bakes. */
    @Test
    void aChunkFarmHasOneTenderItsYieldToItsChest() {
        Map<String, DutyOrder> orders = FarmDuties.orders(List.of("Alice", "Bob", "Carl", "Dana"), plotWithAChunkFarm(),
                Optional.of(new Position(3, 64, 2)), List.of(CHUNK));

        assertEquals(new DutyOrder("Carl", DutyOrder.Duty.HARVESTER, Optional.of(CHUNK.ground()), Optional.of(CHUNK.chest())), orders.get("Carl"));
        assertEquals(DutyOrder.Duty.BAKER, orders.get("Dana").duty(), "the baker kept");
        assertEquals(17, orders.get("Alice").ground().orElseThrow().minX(), "the rest of the plot, outside the chunk");
        assertEquals(39, orders.get("Bob").ground().orElseThrow().maxX());
        assertEquals(Optional.of(new Position(3, 64, 2)), orders.get("Bob").dump(), "the rest's yield to the farm's dump");
    }

    /** One harvester keeps the whole plot, the chunk farm with it: a tender would leave the rest untended. */
    @Test
    void aLoneHarvesterKeepsTheWholePlot() {
        Map<String, DutyOrder> orders = FarmDuties.orders(List.of("Alice"), plotWithAChunkFarm(), Optional.empty(), List.of(CHUNK));

        assertEquals(new Box(0, 62, 0, 39, 64, 9), orders.get("Alice").ground().orElseThrow());
        assertEquals(Optional.empty(), orders.get("Alice").dump());
    }

    /** A farm that's all chunk farm: each harvester tends one, as many as there are. */
    @Test
    void aFarmThatsAllChunkFarmIsAllTended() {
        List<Position> chunk = plotWithAChunkFarm().stream().filter(p -> p.x() <= 15).toList();
        Map<String, DutyOrder> orders = FarmDuties.orders(List.of("Alice"), chunk, Optional.empty(), List.of(CHUNK));

        assertEquals(Optional.of(CHUNK.chest()), orders.get("Alice").dump());
    }
}
