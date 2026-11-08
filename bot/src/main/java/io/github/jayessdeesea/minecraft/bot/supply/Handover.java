package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * One soldier handing logs to a squadmate - cross-leveling (docs/military-doctrine.md §13): walk up
 * to it, face it, throw the logs across a slot at a time, then step back so the receiver, not the
 * giver, picks them up once the server lets anyone (two seconds after the throw). Who gives what to
 * whom is {@code command.CrossLevel}'s; this only carries it out.
 *
 * <p>An expect script (docs/architecture.md § Behavior modeling): a throw is confirmed by the server's resent
 * inventory showing the logs gone, so the next slot is thrown from counts the server has answered
 * for. A delivery withdrawn before the first throw (the squad in contact, the receiver no longer
 * short) interrupts it; once logs are on the ground the giver finishes stepping back. Driven by the
 * soldier's tick.
 */
public final class Handover {

    private static final Logger LOG = LogManager.getLogger(Handover.class);

    /** Close enough to throw: a thrown item flies about two and a half blocks before it lands. */
    static final double THROW_DISTANCE = 2.5;
    /** Where the giver stands to throw. */
    static final double STAND_OFF = 2;
    /** How far the giver backs off after throwing - out of its own pickup reach of what it threw. */
    static final double BACK_OFF = 3;
    /** A thrown item's pickup delay (40 ticks), with a margin for the receiver to walk over it. */
    static final int PICKUP_DELAY_TICKS = 60;
    static final int WALK_TIMEOUT_TICKS = 600;
    /** For the server to resend the inventory after a throw. */
    static final int ANSWER_TICKS = 40;
    /**
     * Before giving again: long enough for the receiver to pick the logs up and stop asking for
     * them, so one shortfall isn't covered twice.
     */
    static final int COOLDOWN_TICKS = 200;

    /** Hand {@code count} of {@code item} to {@code receiver}, now standing at {@code at}. */
    public record Delivery(String receiver, Position at, CrossLevel.Item item, int count) {

        /** As before cross-leveling took food: logs. */
        public Delivery(String receiver, Position at, int logs) {
            this(receiver, at, CrossLevel.Item.LOGS, logs);
        }
    }

    private enum Phase { WALKING, THROWING, BACKING_OFF }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private Script script;
    private Phase phase;
    private int cooldown;
    private String receiver;
    private Position receiverAt;
    private Position soldierAt;
    private Optional<Delivery> offered = Optional.empty();
    private int logs;
    private CrossLevel.Item item = CrossLevel.Item.LOGS;
    private ItemClass thrown = ItemClass.LOG;
    private int remaining;
    private Position backTo;
    private int sinceThrow;
    /** Logs the giver kept, once the throws are answered for. */
    private int kept;

    public Handover(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return script != null;
    }

    /** What the soldier is doing, for its SITREP, while {@link #active}. */
    public synchronized Optional<Report> report() {
        return receiver == null ? Optional.empty() : Optional.of(new Report.CrossLeveling(receiver, item, logs));
    }

    /**
     * Where the soldier should go: to a throw's length short of the receiver - a thrown item flies
     * past someone standing right beside it - then nowhere while it throws, then back off.
     */
    public synchronized Optional<Position> standAt() {
        if (script == null) {
            return Optional.empty();
        }
        return Optional.of(switch (phase) {
            case WALKING -> awayFromReceiver(STAND_OFF);
            case THROWING -> soldierAt;
            case BACKING_OFF -> backTo;
        });
    }

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
     * Each tick: starts {@code delivery} when there is one, and advances one under way.
     *
     * @return where to face this tick - the receiver, while throwing
     */
    public synchronized Optional<Position> tick(Position soldier, Optional<Delivery> delivery) {
        if (cooldown > 0) {
            cooldown--;
        }
        soldierAt = soldier;
        offered = delivery;
        sinceThrow++;
        if (script == null) {
            if (delivery.isEmpty() || cooldown > 0) {
                return Optional.empty();
            }
            start(delivery.get());
        }
        delivery.filter(d -> d.receiver().equals(receiver)).ifPresent(d -> receiverAt = d.at());
        Script.Status status = script.tick();
        Optional<Position> facing = phase == Phase.THROWING ? Optional.of(receiverAt) : Optional.empty();
        switch (status) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                LOG.info("{} handed {} {} to {}", name, logs, what(), receiver);
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't hand {} to {}: {}: {}", name, what(), receiver, failed.step(), failed.reason());
                finish();
            }
        }
        return facing;
    }

    private void start(Delivery delivery) {
        receiver = delivery.receiver();
        receiverAt = delivery.at();
        logs = delivery.count();
        item = delivery.item();
        thrown = switch (item) {
            case LOGS -> ItemClass.LOG;
            case RATIONS -> ItemClass.FOOD;
            case WEAPON -> ItemClass.WEAPON;
        };
        remaining = logs;
        phase = Phase.WALKING;
        LOG.info("{} takes {} {} over to {}", name, remaining, what(), receiver);
        script = new Script(name, List.of(
                new Step("walk up to " + receiver, () -> phase = Phase.WALKING,
                        new Expect("within a throw of " + receiver, () -> horizontalDistance(soldierAt, receiverAt) <= THROW_DISTANCE),
                        WALK_TIMEOUT_TICKS),
                // Facing goes out with this tick's position report, ahead of the first throw: a
                // throw goes the way the server thinks the soldier faces.
                new Step("face " + receiver, () -> phase = Phase.THROWING, new Expect("facing " + receiver, () -> true), 1),
                Step.untilEach("throw the " + what(), () -> remaining == 0, this::throwSlot, ANSWER_TICKS),
                // Done when the receiver stops asking - it has them - not when the giver gets clear:
                // inside a hut there may be nowhere clear to go.
                new Step("back off", this::backOff,
                        new Expect(receiver + " to pick the " + what() + " up", () -> {
                            if (carried() > kept) {
                                throw new IllegalStateException("picked its own " + what() + " back up");
                            }
                            return sinceThrow >= PICKUP_DELAY_TICKS && offered.filter(d -> d.receiver().equals(receiver)).isEmpty();
                        }), PICKUP_DELAY_TICKS + WALK_TIMEOUT_TICKS)),
                List.of(new Guard("the delivery is off", () -> remaining < logs
                        || offered.filter(d -> d.receiver().equals(receiver)).isPresent())));
    }

    private String what() {
        return item.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Throws the first slot of what's handed, as many as are still to give; confirmed by the server's count. */
    private Expect throwSlot() {
        // A weapon given is a spare - the worst beyond the best - never the one in hand.
        int slot = item == CrossLevel.Item.WEAPON ? inventory.spareWeaponSlot() : inventory.slotOf(thrown);
        if (slot < 0) {
            throw new IllegalStateException("out of " + what() + " with " + remaining + " still to give");
        }
        int before = carried();
        int throwing = Math.min(remaining, inventory.countAt(slot));
        for (int i = 0; i < throwing; i++) {
            session.throwOne(0, 0, slot); // stale state: the server resends the inventory
        }
        remaining -= throwing;
        sinceThrow = 0;
        return new Expect(throwing + " " + what() + " gone from slot " + slot, () -> carried() <= before - throwing);
    }

    /** How many of what's handed the giver still has: for a weapon, every melee weapon, best included. */
    private int carried() {
        return item == CrossLevel.Item.WEAPON ? inventory.meleeWeapons() : inventory.count(thrown);
    }

    private void backOff() {
        phase = Phase.BACKING_OFF;
        kept = carried();
        backTo = awayFromReceiver(BACK_OFF + horizontalDistance(soldierAt, receiverAt));
    }

    private void finish() {
        receiver = null;
        script = null;
        cooldown = COOLDOWN_TICKS;
    }

    private static double horizontalDistance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
