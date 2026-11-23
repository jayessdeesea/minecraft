package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A soldier's habit with gates (operator, 2026-11-22: the soldiers should have a habit of keeping them shut): a gate is
 * a way through, never a wall and never left open. Walking up to a shut one it opens it; clear of an open one, with
 * nobody in it, it shuts it - its own or one it found open. Whatever its task: this is the walk's, ticked every tick.
 *
 * <p>To the world model a gate, open or shut, is not solid ({@code ChunkCache.isSolid}), so a route goes through it;
 * this is what makes that true by the time the soldier gets there. Each use is the server's to answer with a block
 * update, and a use swings the gate whichever way it stands: so a gate used by anybody isn't used again by anybody
 * for {@link #AGAIN_MILLIS} - the uses are kept in one map the force shares. In the squad simulation two soldiers
 * walking up to one gate each opened it the same tick, the second shutting it on the first, who broke it to get out.
 */
public final class GateHabit {

    private static final Logger LOG = LogManager.getLogger(GateHabit.class);

    /** How near a shut gate it's walking toward is opened. */
    static final double OPEN_WITHIN = 1.9;
    /** How far from an open gate it is before it shuts it, and how far it reaches to. */
    static final double SHUT_FROM = 1.5, REACH = 4.0;
    /** How near an open gate's middle anybody is for it to be left open: in it, or stepping into it. */
    static final double IN_IT = 1.5;
    /** How long after anybody's use of a gate before it's used again, by anybody. */
    static final long AGAIN_MILLIS = 600;
    /** Ticks stood still against a shut gate before it's opened without a step toward it: held by it. */
    static final int HELD_TICKS = 10;

    private final String name;
    private final PlaySession session;
    private final Map<BlockPos, Long> used;
    private final java.util.function.LongSupplier millis;
    private Position last;
    private int still;
    private int opened, shut;

    /** {@code used} is the force's: each gate and when it was last used, by anybody, on {@code millis}' clock. */
    public GateHabit(String name, PlaySession session, Map<BlockPos, Long> used, java.util.function.LongSupplier millis) {
        this.name = name;
        this.session = session;
        this.used = used;
        this.millis = millis;
    }

    /** On its own: nobody else's uses to mind. */
    public GateHabit(String name, PlaySession session, java.util.function.LongSupplier millis) {
        this(name, session, new HashMap<>(), millis);
    }

    /** Gates it has opened, and shut, since it joined. */
    public synchronized int opened() {
        return opened;
    }

    public synchronized int shut() {
        return shut;
    }

    /** Each tick: {@code at} its feet, {@code others} where the rest of the force stand. */
    public synchronized void tick(Position at, WorldView world, List<Position> others) {
        long now = millis.getAsLong();
        double dx = last == null ? 0 : at.x() - last.x(), dz = last == null ? 0 : at.z() - last.z();
        boolean moving = Math.hypot(dx, dz) > 0.02;
        still = moving ? 0 : still + 1;
        last = at;
        int fx = (int) Math.floor(at.x()), fy = (int) Math.floor(at.y() + 0.01), fz = (int) Math.floor(at.z());
        for (int x = fx - 4; x <= fx + 4; x++) {
            for (int z = fz - 4; z <= fz + 4; z++) {
                for (int y = fy - 1; y <= fy + 1; y++) {
                    if (!world.isGate(x, y, z)) {
                        continue;
                    }
                    BlockPos gate = new BlockPos(x, y, z);
                    Long before = used.get(gate);
                    if (before != null && now - before < AGAIN_MILLIS) {
                        continue;
                    }
                    double gx = x + 0.5 - at.x(), gz = z + 0.5 - at.z(), far = Math.hypot(gx, gz);
                    boolean toward = moving && gx * dx + gz * dz > 0;
                    if (world.isShutGate(x, y, z)) {
                        // In it and it's shut - shut on it: opened at once, never broken out of.
                        if (far <= OPEN_WITHIN && toward || far <= 1.1 && still >= HELD_TICKS || far <= 0.7) {
                            use(gate, "opens", now);
                            opened++;
                        }
                    } else if (far >= SHUT_FROM && far <= REACH && !(toward && far <= OPEN_WITHIN + 0.6)
                            && others.stream().noneMatch(o -> Math.hypot(o.x() - (gate.x() + 0.5), o.z() - (gate.z() + 0.5)) < IN_IT && Math.abs(o.y() - at.y()) < 2)) {
                        use(gate, "shuts", now);
                        shut++;
                    }
                }
            }
        }
    }

    private void use(BlockPos gate, String does, long now) {
        used.put(gate, now);
        session.useBlock(gate.x(), gate.y(), gate.z());
        LOG.debug("{} {} the gate at {}", name, does, gate);
    }
}
