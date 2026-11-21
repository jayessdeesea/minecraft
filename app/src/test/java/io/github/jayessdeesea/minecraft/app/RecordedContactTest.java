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
}
