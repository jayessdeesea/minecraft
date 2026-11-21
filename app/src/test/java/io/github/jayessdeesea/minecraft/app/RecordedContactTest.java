package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A contact gone wrong, replayed on the ground it happened on (backlog/engagement-doctrine.md): live (field602) Mia
 * stood a minute at (45.6, -7, 35.8) "fighting hand to hand" a zombie 1.9 off and 5 up, a skeleton 5.8 off and 5 below,
 * and was shot dead there - her ground kept 4 s before. Correct, a minute on: out of the skeleton's sight, or at it -
 * within reach, fighting it - not stood where she was.
 */
class RecordedContactTest {

    @Test
    void miaGetsOutOfTheSkeletonsSight() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(Path.of(new ClassPathResource("worlds/contact-mia-field602.json").getURI()));
        EntityTracker entities = new EntityTracker();
        Position zombie = new Position(47.5, -2.0, 35.8), skeleton = new Position(45.7, -12.0, 30.0);
        entities.spawn(1, UUID.randomUUID(), HostileMobRegistry.typeIdOf("zombie"), zombie.x(), zombie.y(), zombie.z(), "eval");
        entities.spawn(2, UUID.randomUUID(), HostileMobRegistry.typeIdOf("skeleton"), skeleton.x(), skeleton.y(), skeleton.z(), "eval");
        WorldView world = RecordedGroundEfficiencyTest.world(ground.minX() - 16, ground.minZ() - 16, ground.sizeX() + 32, ground.sizeZ() + 32,
                (x, z) -> 0, (x, yz) -> {
                    int y = yz >> 16, z = (short) (yz & 0xFFFF);
                    int s = ground.stateAt(x, y, z);
                    return s == WorldRecords.UNKNOWN ? (y < ground.minY() ? 1 : 0) : s;
                }, entities);
        DeterministicSoldier mia = new DeterministicSoldier("Mia", new Position(45.6, -7.0, 35.8));
        mia.reportKit(new Kit(Optional.of("stone_sword"), 3, 4));
        mia.reportHealth(11, 17);
        mia.receiveOrder(new Order.MoveTo(13.5, 12.0, 11.5, 1.5, 3.0, Risk.LOW));
        for (int tick = 0; tick < 1200; tick++) {
            mia.tick(world);
        }
        Position at = mia.status().position();
        Position eye = new Position(skeleton.x(), skeleton.y() + DeterministicSoldier.EYE_HEIGHT, skeleton.z());
        System.out.printf("[Eval] contact/mia-field602: Mia at %s, %s%n", at, mia.status().report());
        boolean hidden = !DeterministicSoldier.lineOfSight(world, eye, new Position(at.x(), at.y() + DeterministicSoldier.TARGET_HEIGHT, at.z()));
        boolean atIt = at.distanceTo(skeleton) <= DeterministicSoldier.ATTACK_REACH;
        assertTrue(hidden || atIt, "Mia out of the skeleton's sight, or at it, a minute on: at " + at + ", " + mia.status().report());
    }

    /**
     * A drowned's trident, from water 9 below: live (field604) Mia held her post at (-1.2, 62.2, 175.8), "carries on: no way
     * between them on foot" - a drowned counted as one fighting hand to hand - while its throws took her from 15 health
     * to none. Here its throw is 4 health every 1.5 s it can see her. Correct, a minute on: alive. A known failure: she
     * holds her post and dies; counted as one that shoots (a drowned may hold a trident), she backs 3 blocks into the
     * ground behind her, still in its sight, and dies - getting out of a thrower's sight across open ground is what's
     * left (backlog/engagement-doctrine.md). Once she lives, take the known mark off so the case holds the fix.
     */
    @Test
    void miaGetsOutOfTheDrownedsReach() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(Path.of(new ClassPathResource("worlds/contact-mia-field604.json").getURI()));
        EntityTracker entities = new EntityTracker();
        Position drowned = new Position(4.5, 53.2, 166.6);
        entities.spawn(1, UUID.randomUUID(), HostileMobRegistry.typeIdOf("drowned"), drowned.x(), drowned.y(), drowned.z(), "eval");
        WorldView world = RecordedGroundEfficiencyTest.world(ground.minX() - 16, ground.minZ() - 16, ground.sizeX() + 32, ground.sizeZ() + 32,
                (x, z) -> 0, (x, yz) -> {
                    int y = yz >> 16, z = (short) (yz & 0xFFFF);
                    int s = ground.stateAt(x, y, z);
                    return s == WorldRecords.UNKNOWN ? (y < ground.minY() ? 1 : 0) : s;
                }, entities);
        DeterministicSoldier mia = new DeterministicSoldier("Mia", new Position(-1.2, 62.2, 175.8));
        mia.reportKit(new Kit(Optional.of("wooden_sword"), 2, 0));
        double health = 15;
        mia.reportHealth(health, 17);
        mia.receiveOrder(new Order.MoveTo(-1.2, 62.2, 175.8, 1.5, 3.0, Risk.LOW)); // her post
        Position eye = new Position(drowned.x(), drowned.y() + DeterministicSoldier.EYE_HEIGHT, drowned.z());
        for (int tick = 0; tick < 1200 && health > 0; tick++) {
            mia.tick(world);
            Position at = mia.status().position();
            if (tick % 30 == 0 && at.distanceTo(drowned) <= 24
                    && DeterministicSoldier.lineOfSight(world, eye, new Position(at.x(), at.y() + DeterministicSoldier.TARGET_HEIGHT, at.z()))) {
                health -= 4;
                mia.reportHealth(Math.max(0, health), 17);
            }
        }
        System.out.printf("[Eval] contact/mia-field604: Mia at %s, %.0f health, %s%n", mia.status().position(), health, mia.status().report());
        assertFalse(health > 0, "contact/mia-field604 passes now: make it assert she's alive, so it holds the fix");
    }

    /**
     * A creeper's blast run from over a drop: live (field609) Eli, 5 health and no armour, "gets clear of the creeper's
     * blast" from one 6 off by the mine, on the edge of its shaft at (-24.1, 20, 91.8) - and fell 16 blocks to
     * (-23.8, 4, 91.9), dead: her walk's first step was a diagonal, made for straight off the edge. Correct, a minute on:
     * no fall that its health doesn't outlast - a fall's damage its blocks less 3.
     */
    @Test
    void eliGetsClearOfTheCreeperWithoutAFallThatKillsHer() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(Path.of(new ClassPathResource("worlds/contact-eli-field609.json").getURI()));
        EntityTracker entities = new EntityTracker();
        Position creeper = new Position(-20.5, 21.5, 96.6);
        entities.spawn(1, UUID.randomUUID(), HostileMobRegistry.typeIdOf("creeper"), creeper.x(), creeper.y(), creeper.z(), "eval");
        WorldView world = RecordedGroundEfficiencyTest.world(ground.minX() - 16, ground.minZ() - 16, ground.sizeX() + 32, ground.sizeZ() + 32,
                (x, z) -> 0, (x, yz) -> {
                    int y = yz >> 16, z = (short) (yz & 0xFFFF);
                    int s = ground.stateAt(x, y, z);
                    return s == WorldRecords.UNKNOWN ? (y < ground.minY() ? 1 : 0) : s;
                }, entities);
        DeterministicSoldier eli = new DeterministicSoldier("Eli", new Position(-24.1, 20.0, 91.8));
        eli.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        double health = 5;
        eli.reportHealth(health, 15);
        eli.receiveOrder(new Order.MoveTo(-27.0, 24.0, -23.0, 1.5, 3.0, Risk.LOW));
        double last = eli.status().position().y(), falling = 0, worst = 0;
        Position worstAt = null;
        for (int tick = 0; tick < 1200; tick++) {
            eli.tick(world);
            double y = eli.status().position().y();
            // A fall: ticks in a row down faster than a walk down stairs goes.
            falling = last - y > 0.3 ? falling + last - y : 0;
            if (falling > worst) {
                worst = falling;
                worstAt = eli.status().position();
            }
            last = y;
        }
        System.out.printf("[Eval] contact/eli-field609: Eli at %s, worst fall %.1f to %s, %s%n", eli.status().position(), worst, worstAt, eli.status().report());
        assertTrue(worst - 3 < health, "contact/eli-field609: a fall of " + worst + " at " + health + " health");
    }

    /**
     * An archer met at one health: live (field611) Eli, at 0.6 health with no rations, was taken off cutting the mine and
     * "fights hand to hand" a skeleton 6.4 off on level ground at (-22.1, 20, 91.5) - closed on it across the open and
     * was shot dead at (-18.2, 20, 90.6). Here its shot is 4 health every 1.5 s it can see her. Correct, a minute on:
     * alive.
     */
    @Test
    void eliTakesCoverFromTheSkeletonAtOneHealth() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(Path.of(new ClassPathResource("worlds/contact-eli-field611.json").getURI()));
        EntityTracker entities = new EntityTracker();
        Position skeleton = new Position(-16.5, 20.0, 90.5); // in her sight: the live one moved, and from (-15.8, 20, 90.5) a wall hides her
        entities.spawn(1, UUID.randomUUID(), HostileMobRegistry.typeIdOf("skeleton"), skeleton.x(), skeleton.y(), skeleton.z(), "eval");
        WorldView world = RecordedGroundEfficiencyTest.world(ground.minX() - 16, ground.minZ() - 16, ground.sizeX() + 32, ground.sizeZ() + 32,
                (x, z) -> 0, (x, yz) -> {
                    int y = yz >> 16, z = (short) (yz & 0xFFFF);
                    int s = ground.stateAt(x, y, z);
                    return s == WorldRecords.UNKNOWN ? (y < ground.minY() ? 1 : 0) : s;
                }, entities);
        DeterministicSoldier eli = new DeterministicSoldier("Eli", new Position(-22.1, 20.0, 91.5));
        eli.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        double health = 1;
        eli.reportHealth(health, 15);
        eli.receiveOrder(new Order.MoveTo(-22.1, 20.0, 91.5, 1.5, 3.0, Risk.LOW));
        Position eye = new Position(skeleton.x(), skeleton.y() + DeterministicSoldier.EYE_HEIGHT, skeleton.z());
        for (int tick = 0; tick < 1200 && health > 0; tick++) {
            eli.tick(world);
            Position at = eli.status().position();
            if (tick % 30 == 29 && at.distanceTo(skeleton) <= 16
                    && DeterministicSoldier.lineOfSight(world, eye, new Position(at.x(), at.y() + DeterministicSoldier.TARGET_HEIGHT, at.z()))) {
                health -= 4;
                eli.reportHealth(Math.max(0, health), 15);
            }
        }
        System.out.printf("[Eval] contact/eli-field611: Eli at %s, %.0f health, %s%n", eli.status().position(), health, eli.status().report());
        assertTrue(health > 0, "contact/eli-field611: shot dead at " + eli.status().position());
    }
}
