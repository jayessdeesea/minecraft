package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.supply.WaterCarrier;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The squad simulation's bucket against a carrier's look: filled at the pond it looks at, poured where it looks down. */
class SimBucketsTest {

    /** Flat, with a pond of three sources at x 10..12, z 0, and a hole dug at the origin. */
    private static int pondAndHole(int x, int y, int z) {
        return y == GROUND && z == 0 && x >= 10 && x <= 12 ? SimServer.WATER
                : y == GROUND && x == 0 && z == 0 ? SimServer.AIR : SquadSimulationTest.flat(x, y, z);
    }

    private static float[] look(double x, double z, Position point) {
        return WaterCarrier.look(new Position(x, GROUND + 1, z), point);
    }

    @Test
    void aBucketFillsAtThePondItLooksAtAndPoursIntoTheHoleItLooksDownInto() {
        SimServer server = new SimServer(new SimClock(), SimBucketsTest::pondAndHole, 1000);
        List<String> log = new ArrayList<>();

        float[] atPond = look(11.5, 1.5, new Position(11.5, GROUND + 0.5, 0.5));
        OptionalInt filled = SimBuckets.use(server, new PlayerPosition(11.5, GROUND + 1, 1.5), atPond[0], atPond[1], SimBuckets.BUCKET, log::add);
        assertEquals(OptionalInt.of(SimBuckets.WATER_BUCKET), filled, log.toString());
        assertEquals(SimServer.WATER, server.block(11, GROUND, 0), "a pond's source fills again");

        float[] intoHole = look(0.5, 1.5, new Position(0.5, GROUND - 0.1, 0.5));
        OptionalInt poured = SimBuckets.use(server, new PlayerPosition(0.5, GROUND + 1, 1.5), intoHole[0], intoHole[1], SimBuckets.WATER_BUCKET, log::add);
        assertEquals(OptionalInt.of(SimBuckets.BUCKET), poured, log.toString());
        assertEquals(SimServer.WATER, server.block(0, GROUND, 0), "the hole a source: " + log);
    }
}
