package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * One soldier handing logs to a squadmate - cross-leveling (docs/military-doctrine.md §13): walk up
 * to it, face it, throw the logs across one at a time, then step back so the receiver, not the
 * giver, picks them up once the server lets anyone (two seconds after the throw). Who gives what to
 * whom is {@code command.CrossLevel}'s; this only carries it out. Driven by the soldier's tick, like
 * {@link Depositor}.
 */
public final class Handover {

    private static final Logger LOG = LogManager.getLogger(Handover.class);

    /** Close enough to throw: a thrown item flies about two and a half blocks before it lands. */
    static final double THROW_DISTANCE = 2.5;
    /** How far the giver backs off after throwing - out of its own pickup reach of what it threw. */
    static final double BACK_OFF = 3;
    static final int BACK_OFF_TICKS = 60;
    static final int WALK_TIMEOUT_TICKS = 600;
    static final int SETTLE_TICKS = 5;
    /**
     * Before giving again: long enough for the receiver to pick the logs up and stop asking for
     * them, so one shortfall isn't covered twice.
     */
    static final int COOLDOWN_TICKS = 200;

    /** Hand {@code logs} logs to {@code receiver}, now standing at {@code at}. */
    public record Delivery(String receiver, Position at, int logs) {
    }

    private enum State { IDLE, WALKING, THROWING, BACKING_OFF }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private String receiver;
    private Position receiverAt;
    private Position soldierAt;
    private int logs;
    private int remaining;
    private Position backTo;

    public Handover(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /** What the soldier is doing, for its SITREP, while {@link #active}. */
    public synchronized Optional<Report> report() {
        return receiver == null ? Optional.empty() : Optional.of(new Report.CrossLeveling(receiver, logs));
    }

    /**
     * Where the soldier should go: to a throw's length short of the receiver - a thrown item flies
     * past someone standing right beside it - then nowhere while it throws, then back off.
     */
    public synchronized Optional<Position> standAt() {
        return switch (state) {
            case WALKING -> Optional.of(awayFromReceiver(STAND_OFF));
            case THROWING -> Optional.of(soldierAt);
            case BACKING_OFF -> Optional.of(backTo);
            case IDLE -> Optional.empty();
        };
    }

    /** Where the giver stands to throw. */
    static final double STAND_OFF = 2;

    /** The point {@code distance} from the receiver, on the giver's side of it. */
    private Position awayFromReceiver(double distance) {
        double dx = soldierAt.x() - receiverAt.x();
        double dz = soldierAt.z() - receiverAt.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            dx = 1;
            length = 1;
        }
        return new Position(receiverAt.x() + dx / length * distance, receiverAt.y(), receiverAt.z() + dz / length * distance);
    }

    /**
     * Each tick: starts {@code delivery} when there is one, and advances one under way; a delivery
     * withdrawn before the throw (the squad in contact, the receiver no longer short) is dropped.
     *
     * @return where to face this tick - the receiver, while throwing
     */
    public synchronized Optional<Position> tick(Position soldier, Optional<Delivery> delivery) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        soldierAt = soldier;
        switch (state) {
            case IDLE -> {
                if (delivery.isPresent() && cooldown == 0) {
                    receiver = delivery.get().receiver();
                    receiverAt = delivery.get().at();
                    logs = delivery.get().logs();
                    remaining = logs;
                    LOG.info("{} takes {} logs over to {}", name, remaining, receiver);
                    enter(State.WALKING);
                }
            }
            case WALKING -> {
                if (delivery.isEmpty() || !delivery.get().receiver().equals(receiver)) {
                    finish();
                } else {
                    receiverAt = delivery.get().at();
                    if (horizontalDistance(soldier, receiverAt) <= THROW_DISTANCE) {
                        enter(State.THROWING);
                    } else if (ticksInState > WALK_TIMEOUT_TICKS) {
                        LOG.warn("{} couldn't reach {} to hand over logs", name, receiver);
                        finish();
                    }
                }
            }
            case THROWING -> {
                delivery.filter(d -> d.receiver().equals(receiver)).ifPresent(d -> receiverAt = d.at());
                if (ticksInState < SETTLE_TICKS) {
                    return Optional.of(receiverAt); // turn first: a throw goes the way the soldier faces
                }
                int slot = inventory.slotOf(ItemClass.LOG);
                if (slot < 0) {
                    LOG.warn("{} ran out of logs with {} still to give {}", name, remaining, receiver);
                    backOff();
                    return Optional.of(receiverAt);
                }
                // One slot per settle: the next slot's count is only right once the server has
                // answered for this one.
                int throwing = Math.min(remaining, inventory.countAt(slot));
                for (int i = 0; i < throwing; i++) {
                    session.throwOne(0, 0, slot);
                }
                remaining -= throwing;
                if (remaining > 0) {
                    enter(State.THROWING);
                } else {
                    LOG.info("{} handed the logs to {}", name, receiver);
                    backOff();
                }
                return Optional.of(receiverAt);
            }
            case BACKING_OFF -> {
                if (ticksInState >= BACK_OFF_TICKS) {
                    finish();
                }
            }
        }
        return Optional.empty();
    }

    private void backOff() {
        backTo = awayFromReceiver(BACK_OFF + horizontalDistance(soldierAt, receiverAt));
        enter(State.BACKING_OFF);
    }

    private void finish() {
        receiver = null;
        cooldown = COOLDOWN_TICKS;
        enter(State.IDLE);
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }

    private static double horizontalDistance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
