package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (docs/architecture.md's \"Behavior modeling\" section). {@code app}'s {@code SoldierRunner} is
 * the imperative shell: it calls {@link #tick} at 20Hz and reports the resulting position to the
 * server; tests call it directly with a fake {@code WorldView}.
 *
 * <p>{@code position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
public final class DeterministicSoldier implements Soldier {

    /** Blocks per tick - 4 blocks/s at 20Hz, just under vanilla walk speed (~4.3), not tuned.
     * Revisit if the server starts correcting reported positions (it would send Player Position). */
    private static final double STEP = 0.2;
    /**
     * Sprinting, vanilla's 5.6 blocks/s: only when the time is worth the food - an urgent move to a
     * goal more than {@link #SPRINT_DISTANCE} away, closing on a hostile it fights, moving under fire.
     * A routine walk is walked.
     */
    static final double SPRINT_STEP = 0.28;
    /** Far enough from its goal that a soldier sprints to catch up - a moving principal, a distant slot. */
    static final double SPRINT_DISTANCE = 8;
    /** At this food level or below a player can't sprint. */
    static final int SPRINT_FOOD_FLOOR = 6;

    /**
     * Whether the soldier is sprinting - to be told to the server, which charges sprinting's food cost
     * (0.1 exhaustion a metre) only to a player it knows is sprinting.
     */
    public boolean sprinting() {
        return sprinting;
    }

    /**
     * How far out an arrow in flight raises the shield: at 1.6 blocks a tick, one loosed from a
     * skeleton's 13 blocks arrives in 8 ticks, and a shield blocks only once it's been up 5.
     */
    public static final double ARROW_WATCH = 16;
    /**
     * Ticks the shield stays up after the last arrow seen: none to speak of. Raised, the soldier
     * closes on the archer at a fifth of its pace, and an archer it can't close on keeps shooting -
     * measured in {@code CombatScenarioTest}, holding it up longer only loses ground.
     */
    static final int SHIELD_HOLD_TICKS = 1;
    /** A player with an item in use moves at a fifth of its speed - the vanilla client's rule. */
    static final double SHIELD_SLOWDOWN = 0.2;

    /**
     * Whether the soldier holds its shield up - to be told to the server (raise it, or release it).
     * Up while arrows fly near it, for {@link #SHIELD_HOLD_TICKS} after the last; meanwhile it walks
     * at {@link #SHIELD_SLOWDOWN} of its pace and doesn't swing, as the vanilla client can't. An
     * arrow seen counts as being shot at, as a hit does: the soldier goes for the archer.
     */
    public boolean shieldRaised() {
        return shieldTicks > 0 && !drawing;
    }

    /**
     * Beyond this range a soldier with a bow and arrows shoots rather than closing: a sword's reach
     * is three blocks, and every block closed under an archer's fire is a block it shoots back.
     */
    static final double SHOOT_BEYOND = 5;
    /** How far a soldier with a bow engages what it sees: far enough to answer a skeleton's 15 blocks. */
    static final double BOW_RANGE = 16;
    /** Ticks a bow takes to draw to full power, which the arrow needs to fly true at range. */
    static final int FULL_DRAW_TICKS = 20;
    /** An arrow loosed at full draw: blocks a tick, then 1% drag and 0.05 gravity each tick (vanilla's). */
    static final double ARROW_SPEED = 3.0;
    private static final double ARROW_DRAG = 0.99;
    private static final double ARROW_GRAVITY = 0.05;

    /** Whether the soldier has a bow drawn - to be told to the server (use it, then release it). */
    public boolean drawing() {
        return drawing;
    }

    /** Whether the bow is drawn full and the arrow should go this tick - taken, so it's loosed once. */
    public boolean takeLoose() {
        boolean now = loose;
        loose = false;
        return now;
    }

    /** Drawing a bow - tick loop writes, its caller reads. */
    private volatile boolean drawing;
    /** The draw is full: release this tick. Tick loop sets, its caller takes. */
    private volatile boolean loose;
    /** Ticks the bow has been drawn. Tick loop only. */
    private int drawTicks;

    /**
     * The pitch, in degrees as the game counts them (up is negative), that sends an arrow loosed at
     * full draw from the eyes to a point {@code across} blocks away horizontally and {@code up}
     * blocks higher - the flatter of the two arcs, found by flying the arrow as the server does.
     * Empty when no arc reaches that far.
     */
    static OptionalDouble aimPitch(double across, double up) {
        OptionalDouble best = OptionalDouble.empty();
        double bestMiss = 0.5; // hit within half a block, or not at all
        for (double elevation = -60; elevation <= 45; elevation += 0.25) {
            double theta = Math.toRadians(elevation);
            double x = 0, y = 0, vx = ARROW_SPEED * Math.cos(theta), vy = ARROW_SPEED * Math.sin(theta);
            for (int tick = 0; tick < 100 && x < across; tick++) {
                double nx = x + vx, ny = y + vy;
                if (nx >= across) {
                    y += vy * (across - x) / vx;
                    x = across;
                    break;
                }
                x = nx;
                y = ny;
                vx *= ARROW_DRAG;
                vy = vy * ARROW_DRAG - ARROW_GRAVITY;
            }
            if (x >= across && Math.abs(y - up) < bestMiss) {
                bestMiss = Math.abs(y - up);
                best = OptionalDouble.of(-elevation);
                if (bestMiss < 0.05) {
                    break; // the flat arc, close enough: a steeper one only flies longer
                }
            }
        }
        return best;
    }

    /** Whether it carries what shooting takes: a bow, and arrows for it. */
    private boolean canShoot() {
        Kit carried = kit;
        return carried != null && carried.bow() && carried.arrows() > 0;
    }

    /** Aims at {@code target} along the arrow's arc and draws; loosed once the draw is full. False if no arc reaches. */
    private boolean shoot(Position target) {
        double dx = target.x() - position.x();
        double dz = target.z() - position.z();
        double up = target.y() + TARGET_HEIGHT - (position.y() + EYE_HEIGHT);
        OptionalDouble aim = aimPitch(Math.hypot(dx, dz), up);
        if (aim.isEmpty()) {
            return false;
        }
        yaw = yawToward(dx, dz);
        pitch = (float) aim.getAsDouble();
        drawing = true;
        if (++drawTicks >= FULL_DRAW_TICKS) {
            loose = true;
            drawTicks = 0;
        }
        return true;
    }

    /** Ticks left with the shield up; 0 when it's down. */
    private int shieldTicks;

    // Vanilla player physics constants (LivingEntity): per tick, velocity -= 0.08 then *= 0.98;
    // a jump starts at 0.42 (peaks ~1.25 blocks - enough for one block, not two).
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double BODY_HEIGHT = 1.8;
    private static final double HALF_WIDTH = 0.3; // a player's box is 0.6 x 1.8 x 0.6

    /**
     * The cell a route starts from: the one {@code at} is in, unless that has no floor and the body,
     * 0.6 wide, stands on the edge of a block beside it - then that block's cell, the nearest such.
     * Live, a farmer stood on a block's edge over the crop beside it, every plan started from the
     * crop's cell, with no floor, and it reported no route for good.
     */
    static BlockPos standingCell(WorldView world, Position at) {
        BlockPos here = BlockPos.containing(at.x(), at.y(), at.z());
        // On a block not quite a block high - farmland, a path - its feet are just under the next
        // cell, and that's the one it stands in: live, farmers planned every route from inside the
        // farmland under them, and reported "no route" to the cell they stood in.
        if (world.isSolid(here.x(), here.y(), here.z()) && at.y() - here.y() >= PARTIAL_BLOCK_TOP) {
            here = here.offset(0, 1, 0);
        }
        if (Pathfinder.standable(world, here) || Pathfinder.swimmable(world, here)) {
            return here;
        }
        BlockPos best = here;
        double bestDistance = Double.MAX_VALUE;
        for (int x = (int) Math.floor(at.x() - HALF_WIDTH); x <= (int) Math.floor(at.x() + HALF_WIDTH - EPSILON); x++) {
            for (int z = (int) Math.floor(at.z() - HALF_WIDTH); z <= (int) Math.floor(at.z() + HALF_WIDTH - EPSILON); z++) {
                BlockPos cell = new BlockPos(x, here.y(), z);
                double distance = Math.hypot(x + 0.5 - at.x(), z + 0.5 - at.z());
                if (!cell.equals(here) && Pathfinder.standable(world, cell) && distance < bestDistance) {
                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
    private static final double EPSILON = 1e-9;
    /** Feet this far up a solid cell are on its top: farmland and paths are 15/16 high. */
    static final double PARTIAL_BLOCK_TOP = 0.75;
    /** Swimming at the surface, blocks per tick: about vanilla's 2 blocks/s, half a walk. */
    static final double SWIM_STEP = 0.1;
    /** Afloat, the feet this far into the top block of water: the head stays out, so no drowning. */
    static final double SWIM_FLOAT = 0.2;
    /** Blocks per tick rising or settling in water, and climbing out onto a bank. */
    static final double SWIM_RISE = 0.2;

    private final String name;
    private volatile Position position;
    private volatile Order order = new Order.Idle();
    private final AtomicReference<Position> pendingCorrection = new AtomicReference<>();
    // Tick-loop-only, like position's writes - no other thread reads it.
    private double verticalVelocity = 0;
    private volatile boolean onGround = true;
    /** Whether it's swimming: its feet in water. Tick loop writes. */
    private volatile boolean afloat;
    /** Rising out of the water onto a bank this tick: the water doesn't settle it back. Tick loop only. */
    private boolean climbingOut;
    private volatile Report task;
    // Look direction, written by the tick loop, read by the caller reporting it.
    private volatile float yaw;
    private volatile float pitch;
    private int ticksUntilAttack = 0; // tick-loop only
    // Written only by the connection's thread (reportHealth), as position is only by the tick loop.
    private volatile Condition condition;
    /** Whether the last step was a sprint - tick thread writes, its caller reads. */
    private volatile boolean sprinting;
    private volatile Kit kit; // written only by the connection's thread (reportKit)
    private double lastReportedHealth = -1;
    private int deaths;
    // Set by the connection's thread on a hit, consumed by the tick loop.
    private final AtomicBoolean hurt = new AtomicBoolean();
    private int underFireTicks; // tick-loop only

    public DeterministicSoldier(String name, Position startingPosition) {
        this.name = name;
        this.position = startingPosition;
    }

    @Override
    public void receiveOrder(Order order) {
        this.order = order;
    }

    /**
     * The server says this soldier is somewhere else (a respawn, or a correction of a reported
     * move) - authoritative, so it wins. Applied at the start of the next {@link #tick} rather than
     * written here, so the tick loop stays the sole writer of {@code position}.
     */
    public void correctPosition(Position serverPosition) {
        pendingCorrection.set(serverPosition);
    }

    /**
     * The server's health and food for this soldier. Counts a death when health drops to 0 from
     * alive; a soldier already dead when it joins died in an earlier run, so that isn't counted.
     * Called from the connection's thread only - the sole writer of the condition fields.
     */
    public void reportHealth(double health, int food) {
        if (health <= 0 && lastReportedHealth > 0) {
            deaths++;
        } else if (health > 0 && health < lastReportedHealth) {
            hurt.set(true);
        }
        lastReportedHealth = health;
        condition = new Condition(health, food, deaths, Optional.empty());
    }

    /**
     * What the soldier is busy with besides its order - {@code Rearming} while it makes itself a
     * weapon, {@code Gathering} while it collects material ({@code bot.supply}) - reported instead of
     * its movement unless it's in contact; null when it's only following its order. Set by whoever
     * runs the routine.
     */
    public void task(Report task) {
        this.task = task;
    }

    /** What the soldier now carries (LACE ammunition and equipment). Connection thread only. */
    public void reportKit(Kit kit) {
        this.kit = kit;
    }

    /**
     * Where the soldier looks, reported with each move so other players see it turn: at the
     * hostile it's fighting, else along its last step, else wherever it last looked. Yaw in
     * degrees, 0 facing south (+z) and 90 west (-x); pitch negative looking up.
     */
    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Eye height of a standing player, and the height a hostile is looked at - roughly its chest. */
    public static final double EYE_HEIGHT = 1.62;
    public static final double TARGET_HEIGHT = 1.2;

    /** Yaw toward a horizontal direction, in Minecraft's convention. */
    static float yawToward(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    /** Whether the last tick ended standing on something - reported to the server with each move. */
    public boolean onGround() {
        return onGround;
    }

    @Override
    public SoldierStatus status() {
        Kit carried = kit;
        Report busy = task;
        // No route to where it's going outranks whatever its routine says: with a routine at work, the
        // order is that routine's own stand, so a soldier that can't reach it can't work either, and no
        // route is what the squad's recovery drill reads. Live, a replacement on a peak reported the
        // torches it couldn't make, and one boxed in a pocket reported digging, and nobody came for either.
        boolean noRoute = report instanceof Report.Cantco(String why) && why.startsWith("no route from");
        Report now = busy != null && !(report instanceof Report.InContact) && !noRoute ? busy : report;
        return new SoldierStatus(name, position, order, now, Optional.ofNullable(condition)
                .map(c -> new Condition(c.health(), c.food(), c.deaths(), Optional.ofNullable(carried))));
    }

    /**
     * Ballpark starting value, not tuned against real mob behavior - vanilla hostiles typically
     * notice a player around 16 blocks out, so this reacts well inside that. Revisit once a live
     * run shows soldiers reacting too late or too skittishly.
     */
    public static final double CONTACT_RADIUS = 8.0;

    /**
     * Centre-to-centre distance a soldier swings at. The server's check (eye to target hitbox,
     * {@code isWithinAttackRange}) is more generous; staying inside it avoids attacks it would
     * silently drop.
     */
    public static final double ATTACK_REACH = 3.0;

    /**
     * Closer than this, back off: outside a zombie's own reach (about 1.5) but inside ours, so a
     * soldier striking each time its weapon recharges, with knockback, keeps it from landing a
     * hit. Between this and {@link #ATTACK_REACH} the soldier holds its ground.
     */
    static final double KEEP_AWAY = 2.5;

    /**
     * Losing health with no hostile in contact radius means being shot at - a skeleton holds 11 to
     * 15 blocks off ({@code RangedBowAttackGoal}). For this long after a hit the soldier looks out
     * to {@link #UNDER_FIRE_RADIUS} for the shooter and goes for it.
     */
    static final int UNDER_FIRE_TICKS = 100;
    /** Just beyond a skeleton's 15-block bow range. */
    public static final double UNDER_FIRE_RADIUS = 16;

    /**
     * One tick of movement. First the "actions on contact" reflex: if a hostile is within {@link
     * #CONTACT_RADIUS}, the current order is set aside and the soldier fights the nearest one at
     * arm's length - closing to {@link #ATTACK_REACH}, striking whenever the attack has recharged,
     * backing off inside {@link #KEEP_AWAY}, holding its ground in between. A creeper is the
     * exception: the soldier gets away from it. Decided here at tick speed with no squad leader
     * involved. Running from a zombie can't break contact - it chases as fast as a soldier walks -
     * which is why the reflex holds rather than flees; the squad's react-to-contact drill brings
     * help ({@code command.ReactToContact}). A blocked step means standing still, never falling back
     * to the order (which might lead toward the threat). A hit with nothing in contact radius means
     * ranged fire: for {@link #UNDER_FIRE_TICKS} the soldier treats the nearest hostile within
     * {@link #UNDER_FIRE_RADIUS} as its contact.
     *
     * <p>Otherwise follows a route to the current order's goal - planned by {@link Pathfinder}
     * around trees, up one-block steps and down short drops, and re-planned when the order
     * changes, when the soldier stops making progress, or every {@link #REPLAN_TICKS} ticks (the
     * world changes, and a far goal is reached in budget-sized legs). A step needs room for the
     * whole ~1.8-block body; a one-block rise is jumped.
     *
     * <p>Then vertical movement with vanilla gravity: the server kicks a client that reports
     * standing while hovering over lower ground ("floating too long"), so falling is not optional.
     *
     * @return the entity to attack this tick, if any - the caller sends it, keeping this decision
     *     free of I/O like the rest of the tick
     */
    /**
     * How far round its target a sword's sweep reaches a body: vanilla sweeps every living entity
     * whose box meets the target's grown by a block across - a block, plus half a player's width
     * and half a mob's.
     */
    static final double SWEEP_REACH = 1.0 + 0.3 + 0.3;

    /**
     * Whether a swing now would sweep a squadmate: a sword in hand, and another player within
     * {@link #SWEEP_REACH} of the target across, its body meeting the target's grown box up or down. Three soldiers
     * died that way, cut down beside the zombie a squadmate swung at. The swing waits; the squadmate
     * moves on or the target does.
     */
    private boolean sweepHitsSquadmate(WorldView world, Position target) {
        Kit carried = kit;
        if (carried == null || carried.weapon().filter(w -> w.endsWith("_sword")).isEmpty()) {
            return false;
        }
        // Itself left out by its entity, once known - by where it stands, a squadmate in the same
        // spot is taken for it: live, one stood a tenth of a block from the soldier swinging, at a
        // third of a heart, and the sweep killed it.
        int self = entityId;
        Stream<Position> others = self >= 0 ? world.playersNear(target, SWEEP_REACH + 1, self).stream()
                : world.playersNear(target, SWEEP_REACH + 1).stream()
                .filter(p -> Math.hypot(p.x() - position.x(), p.z() - position.z()) > SELF_SEEN_WITHIN || Math.abs(p.y() - position.y()) > 1);
        // Up and down, the boxes: the target's grown a quarter block, against a player's height. Not
        // feet to feet - live, a phantom two blocks over a squadmate's feet was swung at, and the
        // sweep killed the squadmate at a heart.
        return others.anyMatch(p -> Math.hypot(p.x() - target.x(), p.z() - target.z()) <= SWEEP_REACH
                && target.y() - SWEEP_RISE <= p.y() + PLAYER_HEIGHT && p.y() <= target.y() + TALLEST_MOB + SWEEP_RISE);
    }

    /** How far a sweep grows its target's box up and down. */
    static final double SWEEP_RISE = 0.25;
    /** A player's box, feet to crown. */
    static final double PLAYER_HEIGHT = 1.8;
    /** The tallest box a soldier swings at, an enderman's: what the target reaches above its feet, at most. */
    static final double TALLEST_MOB = 2.9;

    /** Its own player entity's ID, as the server gave it on joining - -1 until told. */
    private volatile int entityId = -1;

    /** Tells it its own player entity's ID, so it never takes a squadmate for itself. */
    public void entityId(int id) {
        this.entityId = id;
    }

    /** The soldier itself, as its squadmates' connections see it, is within this of where it is. */
    private static final double SELF_SEEN_WITHIN = 0.5;

    public OptionalInt tick(WorldView world) {
        if (ticksUntilAttack > 0) {
            ticksUntilAttack--;
        }
        wet = world.isWater((int) Math.floor(position.x()), (int) Math.floor(position.y()), (int) Math.floor(position.z()));

        Position correction = pendingCorrection.getAndSet(null);
        if (correction != null) {
            position = correction;
            verticalVelocity = 0;
            onGround = false; // unknown until gravity settles it - and no route is planned mid-air
            route = null;
            return OptionalInt.empty();
        }

        OptionalInt attack = OptionalInt.empty();
        sprinting = false; // only a long move sprints; fighting and short moves walk
        Position before = position;
        Kit worn = kit;
        List<Position> arrows = worn != null && worn.shield() ? world.arrowsNear(position, ARROW_WATCH) : List.of();
        if (!arrows.isEmpty()) {
            shieldTicks = SHIELD_HOLD_TICKS;
        } else if (shieldTicks > 0) {
            shieldTicks--;
        }
        // Hurt, or an arrow on its way that a shield will take: either way, being shot at.
        if (hurt.getAndSet(false) || !arrows.isEmpty()) {
            underFireTicks = UNDER_FIRE_TICKS;
        }
        // A soldier with a bow reaches further than a sword: what it can see within bow range is contact.
        List<TrackedEntity> threats = visible(world, world.nearbyHostiles(position, canShoot() ? BOW_RANGE : CONTACT_RADIUS));
        if (threats.isEmpty() && underFireTicks > 0) {
            // Hit with nothing in reach: someone is shooting - find the shooter.
            threats = visible(world, world.nearbyHostiles(position, UNDER_FIRE_RADIUS));
        }
        if (underFireTicks > 0) {
            underFireTicks--;
        }
        cutting = List.of(); // set again below only while navigating onto a step being cut
        if (threats.isEmpty() || !threats.get(0).shoots()) {
            pinned = false; // a fresh contact gets a fresh look for a way to close
            pinnedCheckIn = 0;
        }
        if (!threats.isEmpty()) {
            TrackedEntity nearest = threats.get(0);
            Position threat = nearest.position();
            double range = distance(position, threat);
            boolean shot = false;
            if (!nearest.explodes() && canShoot() && (range > SHOOT_BEYOND || !armedForMelee()
                    || nearest.shoots() && pinned(world, threat))) {
                // Out of a sword's reach, or with no sword, or pinned under an archer it can't close
                // on: stand and shoot back, the bow's arc aimed at it.
                shieldTicks = 0;
                shot = shoot(threat);
            }
            if (shot) {
                holdCoverTicks = 0;
            } else if (nearest.explodes()) {
                // A creeper is never fought at arm's length: get clear of the blast - straight away,
                // else the heading nearest that the ground allows. Boxed in, strike it: a hit knocks
                // it back out of its blast's reach. Live, a soldier with a wall at her back stood still
                // while one closed from six blocks to three and blew.
                if (!evade(world, threat) && ticksUntilAttack == 0 && range <= ATTACK_REACH && !shieldRaised()) {
                    attack = OptionalInt.of(nearest.entityId());
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
            } else if (nearest.shoots() && (!armedForMelee() || pinned(world, threat))) {
                // An archer out-fights a bare hand from any range, and a sword it can't be closed on:
                // get out of its sight, and stay out.
                Optional<Position> cover = coverFrom(world, threat);
                if (cover.isPresent()) {
                    stepGuarded(world, cover.get().x(), cover.get().z());
                } else {
                    stepGuarded(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
                }
                holdCoverTicks = UNDER_FIRE_TICKS;
            } else {
                if (ticksUntilAttack == 0 && range <= ATTACK_REACH && !shieldRaised() && !sweepHitsSquadmate(world, threat)) {
                    attack = OptionalInt.of(nearest.entityId());
                    // A hit's damage scales with the swing's charge: wait for the held weapon's full one.
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
                if (range < KEEP_AWAY) {
                    stepGuarded(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
                } else if (range > ATTACK_REACH) {
                    // Closing on what it fights: every tick in the open is a tick under its fire.
                    sprinting = canSprint();
                    stepGuarded(world, threat.x(), threat.z(), sprinting ? SPRINT_STEP : STEP);
                }
            }
            route = null; // wherever the fight ends up, the old route no longer starts here
            report = new Report.InContact(threats.size());
            if (!shot) {
                stopDrawing();
                look(threat);
            }
        } else if (drawing && stopDrawing()) {
            // The fight is over: the bow comes down this tick.
        } else if (shieldRaised() && !arrows.isEmpty()) {
            // Shot at from out of sight: face where the arrow came from, and hold.
            look(arrows.getFirst());
        } else if (holdCoverTicks > 0) {
            // In cover from an archer it can't fight: out of its sight isn't out of its range. Hold.
            holdCoverTicks--;
        } else {
            navigate(world);
            double dx = position.x() - before.x();
            double dz = position.z() - before.z();
            if (dx * dx + dz * dz > 1e-4) {
                yaw = yawToward(dx, dz);
                pitch = 0;
            }
        }

        applyVerticalMovement(world);
        return attack;
    }

    /** How often, in contact with an archer, it checks there's still a way to close on it. */
    static final int PINNED_CHECK_TICKS = 20;
    /** Whether the last check found no way to the archer, and ticks until the next. */
    private boolean pinned;
    private int pinnedCheckIn;

    /**
     * Whether no route reaches within striking distance of the archer at {@code threat} - water, a
     * drop or a wall between them: an armed soldier that can't close is as outranged as a bare hand.
     * Planned once a {@link #PINNED_CHECK_TICKS}; an archer backing off as it comes on isn't pinning
     * it, so progress isn't the test - a route is.
     */
    private boolean pinned(WorldView world, Position threat) {
        if (pinnedCheckIn-- <= 0 && onGround) {
            pinnedCheckIn = PINNED_CHECK_TICKS;
            Goal reach = new Goal(threat.x(), threat.y(), threat.z(), ATTACK_REACH, Pathfinder.MAX_DROP);
            pinned = !Pathfinder.find(world, standingCell(world, position), reach, PLAN_BUDGET,
                    FallAllowance.NONE, pillarBlocks(), carriesPickaxe()).reachesGoal();
        }
        return pinned;
    }

    /** Ticks left holding cover from an archer, once out of its sight. */
    private int holdCoverTicks;
    /** How far a soldier looks for cover from an archer. */
    static final int COVER_RADIUS = 6;

    /** Whether the soldier holds a melee weapon - with only its hands (or a bow), an archer out-fights it. */
    /** Lowers the bow, if it's drawn. Always true, to chain in a condition. */
    private boolean stopDrawing() {
        drawing = false;
        drawTicks = 0;
        return true;
    }

    private boolean armedForMelee() {
        Kit carried = kit;
        return carried == null || WeaponStats.melee(carried.weapon()) != WeaponStats.HAND;
    }

    /**
     * The nearest standing cell within {@link #COVER_RADIUS} that {@code shooter} can't see - nothing
     * solid between its eyes and the soldier's - if there's one.
     */
    private Optional<Position> coverFrom(WorldView world, Position shooter) {
        Position shooterEye = new Position(shooter.x(), shooter.y() + EYE_HEIGHT, shooter.z());
        BlockPos here = BlockPos.containing(position.x(), position.y() + EPSILON, position.z());
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -COVER_RADIUS; dx <= COVER_RADIUS; dx++) {
            for (int dz = -COVER_RADIUS; dz <= COVER_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = here.offset(dx, dy, dz);
                    if (!Pathfinder.standable(world, cell)) {
                        continue;
                    }
                    Position spot = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    double distance = Math.hypot(spot.x() - position.x(), spot.z() - position.z());
                    if (distance < bestDistance
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + EYE_HEIGHT, spot.z()))
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + 0.5, spot.z()))) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Turns to face a squadmate standing at {@code target} - before handing something across, which
     * flies the way the soldier faces. The next step or fight turns it again.
     */
    public void lookAt(Position target) {
        look(target);
    }

    /**
     * The hostiles this soldier can see: a hostile behind a wall can't reach it and can't be
     * reached, so it isn't contact - a squad sealed in its patrol base isn't fighting the night
     * outside. Vanilla mobs likewise need a line of sight to target a player.
     */
    private List<TrackedEntity> visible(WorldView world, List<TrackedEntity> hostiles) {
        Position eye = new Position(position.x(), position.y() + EYE_HEIGHT, position.z());
        return hostiles.stream().filter(hostile -> lineOfSight(world, eye,
                new Position(hostile.position().x(), hostile.position().y() + TARGET_HEIGHT, hostile.position().z()))).toList();
    }

    /** Sampling step along a sight line: fine enough not to step over the corner of a block. */
    static final double SIGHT_STEP = 0.2;

    /** Whether no solid block lies on the straight line from {@code from} to {@code to}. */
    public static boolean lineOfSight(WorldView world, Position from, Position to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / SIGHT_STEP);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            if (world.isSolid((int) Math.floor(from.x() + dx * t), (int) Math.floor(from.y() + dy * t),
                    (int) Math.floor(from.z() + dz * t))) {
                return false;
            }
        }
        return true;
    }

    private void look(Position target) {
        double dx = target.x() - position.x();
        double dz = target.z() - position.z();
        double dy = target.y() + TARGET_HEIGHT - (position.y() + EYE_HEIGHT);
        yaw = yawToward(dx, dz);
        pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    /**
     * Partial plans in a row that gained no ground before this soldier reports CANTCO (unable to
     * comply, docs/military-doctrine.md §3): stuck in a pit, cut off by water, objective on a ledge.
     * It keeps re-planning - the terrain or the order may change - but stops pretending it's
     * on the way.
     */
    static final int CANTCO_AFTER_FAILED_PLANS = 3;

    /** How far an order's goal must move to count as a new task (resetting CANTCO), not slot drift. */
    static final double RETASK_DISTANCE = 8;

    /** Re-plan at least this often while moving - 2 seconds. */
    static final int REPLAN_TICKS = 40;
    /** Ticks without real progress along the route before re-planning. */
    private static final int STUCK_TICKS = 20;
    /** How long a cell it got stuck short of is routed round: long enough for a squadmate in the way to move on. */
    private static final int AVOID_TICKS = 200;
    /** Cells it got stuck short of, and the tick each is tried again from: plans route round them meanwhile. */
    private final Map<BlockPos, Integer> avoided = new HashMap<>();
    private static final Logger LOG = LogManager.getLogger(DeterministicSoldier.class);
    private int navTicks;
    /** Cell visits per plan - bounds the planning cost per tick (see {@link Pathfinder}). */
    private static final int PLAN_BUDGET = 4000;
    /** The goal a wide search was needed for, searched wide on every replan while it's the goal. Tick loop only. */
    private Goal wideGoal;
    /** The wider search, once the cheap ones gain no ground: a way round beyond one cheap search. */
    private static final int LONG_PLAN_BUDGET = 60_000;
    private static final double WAYPOINT_REACHED = 0.25;

    // Navigation state - tick-loop only, like verticalVelocity.
    private Order plannedOrder;
    private Goal goal;
    private List<BlockPos> route;
    private int routeIndex;
    private int ticksSincePlan;
    private int stuckTicks;
    private Position progressMark;
    private volatile String lastPlan = "none";
    private volatile Report report = new Report.Idle();
    /** Consecutive partial plans with no real headway - see {@link #CANTCO_AFTER_FAILED_PLANS}. */
    private int failedPlans;
    private Position lastPartialPlanFrom;

    /** Cutting a step counts as headway for this long - past it, a dig that isn't happening is being stuck. */
    static final int CUT_PATIENCE_TICKS = 300;
    /** The blocks to dig for the step the soldier is on - tick thread writes, its caller reads. */
    private volatile List<BlockPos> cutting = List.of();
    private int cuttingTicks;

    /**
     * The blocks the soldier needs dug to take its next step up a slope, in the order to dig them -
     * empty unless it's stopped to cut one ({@link Pathfinder#stepCut}). Its caller digs them; the
     * soldier waits, then jumps up once they're gone.
     */
    public List<BlockPos> cutting() {
        return cutting;
    }

    /** Observability only: the most recent route plan, e.g. "partial, 12 steps ending BlockPos[...]". */
    public String lastPlan() {
        return lastPlan;
    }

    private void navigate(WorldView world) {
        Order current = this.order;
        if (current != plannedOrder) {
            plannedOrder = current;
            Goal previous = goal;
            goal = goalFor(current);
            route = null;
            if (previous == null || goal == null
                    || Math.hypot(goal.x() - previous.x(), goal.z() - previous.z()) > RETASK_DISTANCE) {
                // A genuinely different task: earlier failures say nothing about this one. A slot
                // drifting with the point man is the same task, so its failures keep counting.
                failedPlans = 0;
                lastPartialPlanFrom = null;
            }
        }
        // Arrived = standing in a cell the goal accepts - the same cell-centre rule the planner uses,
        // so the two can never disagree about whether there's anywhere left to go.
        if (goal == null) {
            route = null;
            report = new Report.Idle();
            return;
        }
        if ((onGround || afloat) && goal.contains(BlockPos.containing(position.x(), position.y(), position.z()))) {
            route = null;
            failedPlans = 0;
            // A goal finer than half a block wants a spot, not just a cell - inside a hut a body off
            // its cell's centre leans into the wall being built. Step onto it before standing.
            if (goal.horizontalTolerance() < 0.5 && !goal.contains(position.x(), position.y(), position.z())) {
                stepToward(world, goal.x(), goal.z());
                report = new Report.Moving();
                return;
            }
            report = new Report.InPosition();
            return;
        }

        ticksSincePlan++;
        trackProgress();
        if (cuttingTicks > 0 && cuttingTicks < CUT_PATIENCE_TICKS) {
            stuckTicks = 0; // standing still to cut a step is headway
        }
        boolean replan = route == null || routeIndex >= route.size()
                || ticksSincePlan >= REPLAN_TICKS || stuckTicks >= STUCK_TICKS;
        navTicks++;
        avoided.values().removeIf(until -> until <= navTicks);
        if (replan && (onGround || afloat)) {
            boolean stuck = stuckTicks >= STUCK_TICKS;
            // Stuck short of a waypoint: not tried again straight away. The next plan goes round it if
            // there's a way - a squadmate may be on the same step - and comes back to it only later.
            if (stuck && route != null && routeIndex < route.size() && !goal.contains(route.get(routeIndex))) {
                BlockPos failed = route.get(routeIndex);
                if (avoided.put(failed, navTicks + AVOID_TICKS) == null) {
                    LOG.info("{} stuck short of {} - tries another way round it for a while", name, failed);
                }
            }
            Set<BlockPos> avoid = Set.copyOf(avoided.keySet());
            // A fall is the exception, never the plan: first a route with none - walked, then with
            // steps built from what it carries - and only with no such route one the order's risk allows.
            BlockPos start = standingCell(world, position);
            // Wide straight away for a goal a wide search was needed for: a cheap replan on the way
            // round would only lead back to the wall it's going round.
            Pathfinder.Result plan = Pathfinder.find(world, start, goal, goal.equals(wideGoal) ? LONG_PLAN_BUDGET : PLAN_BUDGET,
                    FallAllowance.NONE, pillarBlocks(), carriesPickaxe(), false, avoid);
            if (!plan.reachesGoal() && pillarBlocks() > 0) {
                Pathfinder.Result built = Pathfinder.find(world, start, goal, PLAN_BUDGET, FallAllowance.NONE, pillarBlocks(),
                        carriesPickaxe(), true, avoid);
                // Only one that gets there: a partial one, toward a goal beyond one search, leads it astray.
                if (built.reachesGoal()) {
                    plan = built;
                }
            }
            // A goal beyond one cheap search: once the cheap plans gain no ground, one search wider.
            // Live, two soldiers stood a day 80 blocks out, every plan ending where they stood.
            if (!plan.reachesGoal() && failedPlans > 0) {
                Pathfinder.Result wide = Pathfinder.find(world, start, goal, LONG_PLAN_BUDGET, FallAllowance.NONE, pillarBlocks(),
                        carriesPickaxe(), false, avoid);
                if (wide.reachesGoal()) {
                    plan = wide;
                    wideGoal = goal;
                }
            }
            FallAllowance allowance = fallAllowance(current);
            if (!plan.reachesGoal() && !allowance.equals(FallAllowance.NONE)) {
                Pathfinder.Result risky = Pathfinder.find(world, start, goal, PLAN_BUDGET, allowance, pillarBlocks(), carriesPickaxe(), false, avoid);
                if (risky.reachesGoal()) {
                    plan = risky;
                }
            }
            route = plan.path();
            pillarFrom = null;
            assessPlan(world, plan, stuck);
            lastPlan = (plan.reachesGoal() ? "to goal" : "partial") + ", " + route.size() + " steps"
                    + (route.isEmpty() ? "" : " ending " + route.getLast());
            routeIndex = 0;
            ticksSincePlan = 0;
            stuckTicks = 0;
        }
        if (route == null || routeIndex >= route.size()) {
            return; // nothing reachable makes headway - wait for the next re-plan
        }

        BlockPos waypoint = route.get(routeIndex);
        BlockPos here = BlockPos.containing(position.x(), position.y() + EPSILON, position.z());
        // Afloat too: the planner cuts a step out of a pool whose banks are too high to climb.
        List<BlockPos> cut = onGround || afloat ? toCut(world, here, waypoint, carriesPickaxe()) : List.of();
        if (!cut.isEmpty()) {
            cutting = cut;
            cuttingTicks++;
            report = new Report.Moving();
            return;
        }
        cuttingTicks = 0;
        if (onGround && buildStep(world, here, waypoint)) {
            report = new Report.Moving();
            return;
        }
        if (waypoint.x() == here.x() && waypoint.z() == here.z() && waypoint.y() == here.y() + 1
                || pillarFrom != null && waypoint.equals(pillarFrom.offset(0, 1, 0))) {
            pillar(world, waypoint);
            return;
        }
        double centreX = waypoint.x() + 0.5;
        double centreZ = waypoint.z() + 0.5;
        if (Math.hypot(centreX - position.x(), centreZ - position.z()) < WAYPOINT_REACHED
                && Math.abs(waypoint.y() - position.y()) < 0.6) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                return;
            }
            waypoint = route.get(routeIndex);
            centreX = waypoint.x() + 0.5;
            centreZ = waypoint.z() + 0.5;
        }
        boolean far = Math.hypot(goal.x() - position.x(), goal.z() - position.z()) > SPRINT_DISTANCE;
        // The server charges sprinting 0.1 exhaustion a metre and walking nothing: a routine walk is
        // walked, and only an urgent move, or one under fire, is run. Live, soldiers carrying rations
        // sprinted every walk past eight blocks and ate eleven loaves a day each - about the farm's
        // whole harvest (operator, 2026-11-13).
        boolean urgent = order instanceof Order.MoveTo move && move.urgent() || underFireTicks > 0;
        sprinting = far && canSprint() && urgent;
        stepToward(world, centreX, centreZ, sprinting ? SPRINT_STEP : STEP);
    }

    /**
     * Whether the game lets it sprint: not at food {@link #SPRINT_FOOD_FLOOR} or below, and not with
     * its shield up - an item in use slows a player and stops a sprint.
     */
    private boolean canSprint() {
        Condition known = condition;
        return (known == null || known.food() > SPRINT_FOOD_FLOOR) && !shieldRaised() && !afloat;
    }

    /** Whether it carries a pickaxe, to cut steps into stone with - unknown kit carries none. */
    private boolean carriesPickaxe() {
        Kit carried = kit;
        return carried != null && carried.pickaxe();
    }

    /** What's still in the way of a step up from {@code here} onto {@code next}: the cells {@link Pathfinder#stepCut} plans to dig. */
    private static List<BlockPos> toCut(WorldView world, BlockPos here, BlockPos next, boolean pickaxe) {
        if (next.y() != here.y() + 1 || Math.abs(next.x() - here.x()) + Math.abs(next.z() - here.z()) != 1) {
            return List.of();
        }
        List<BlockPos> solid = Stream.of(next, next.offset(0, 1, 0), here.offset(0, 2, 0))
                .filter(c -> world.isSolid(c.x(), c.y(), c.z())).toList();
        // Only soil, and stone with a pickaxe, is ever dug: anything else put there since the plan - a
        // wall, say - is left be, and the soldier re-plans round it once it's stuck.
        return solid.stream().allMatch(c -> world.isSoil(c.x(), c.y(), c.z()) || pickaxe && world.isStone(c.x(), c.y(), c.z()))
                ? solid : List.of();
    }

    /** The cell a pillar step rises from, while one is under way. */
    private BlockPos pillarFrom;
    /** A block to place this tick, under the soldier - tick thread writes, its caller takes it. */
    private BlockPos placement;

    /**
     * One tick of a pillar step to {@code top}: jump from the cell below it, and once the feet are
     * clear of that cell - the server won't place a block where a body is - ask for a block there,
     * then land on it.
     */
    private void pillar(WorldView world, BlockPos top) {
        BlockPos from = top.offset(0, -1, 0);
        if (pillarFrom == null) {
            if (!onGround) {
                return;
            }
            pillarFrom = from;
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
            return;
        }
        if (world.isSolid(from.x(), from.y(), from.z())) {
            if (onGround) {
                pillarFrom = null; // standing on it: the step is done
                routeIndex++;
            }
            return;
        }
        if (position.y() >= from.y() + 1) {
            placement = from;
        } else if (onGround) {
            pillarFrom = null; // landed back where it started - the block never went in; try again
        }
    }

    /** Ticks between asking for the same built step's block again, while the server hasn't shown it. */
    static final int BUILD_RETRY_TICKS = 10;
    private int buildWait;

    /**
     * Whether the step onto {@code next} still needs blocks placed ({@link Pathfinder#stepBuild}) -
     * if so, asks for the first one, standing in the middle of its own cell, so its body is clear of
     * a block going in beside its feet. The world model takes a placed block at once; the server's
     * answer corrects it.
     */
    private boolean buildStep(WorldView world, BlockPos here, BlockPos next) {
        List<BlockPos> build = Pathfinder.stepBuild(world, here, next);
        if (build.isEmpty()) {
            buildWait = 0;
            return false;
        }
        double cx = here.x() + 0.5;
        double cz = here.z() + 0.5;
        if (Math.hypot(cx - position.x(), cz - position.z()) > BUILD_CENTRED) {
            stepToward(world, cx, cz);
            return true;
        }
        if (buildWait-- <= 0) {
            placement = build.getFirst();
            buildWait = BUILD_RETRY_TICKS;
        }
        return true;
    }

    /** How near its cell's middle a soldier stands to build a step - a 0.6-wide body then clears the cells beside. */
    static final double BUILD_CENTRED = 0.15;

    /**
     * How many blocks a route can be planned on - pillar and built steps: one per cobblestone or
     * dirt carried. Logs are kept for tools, except in the water: getting out comes first. Live, a
     * farmer with two logs and no dirt floated beside its farm for minutes, with no route out.
     */
    private int pillarBlocks() {
        Kit carried = kit;
        return carried == null ? 0 : carried.stone() + carried.dirt() + (wet ? carried.logs() : 0);
    }

    /** Whether its feet were in water at its last tick. */
    private volatile boolean wet;

    /**
     * The block to place this tick, if a pillar or built step wants one - taken, so it's asked for
     * once. The caller places it, against any solid side, after reporting the soldier's new height,
     * and adds it to the world model so the soldier can land on it before the server confirms.
     */
    public Optional<BlockPos> takePlacement() {
        BlockPos taken = placement;
        placement = null;
        return Optional.ofNullable(taken);
    }

    /**
     * A re-plan forced by lack of progress counts as a failure even if the new route looks fine on
     * paper: the planner can't see everything that stops a body (an off-grid snag, an entity in the
     * way), and a soldier that keeps re-planning the same route without moving must eventually say
     * so rather than report "Moving" forever.
     */
    private void assessPlan(WorldView world, Pathfinder.Result plan, boolean stuck) {
        if (stuck) {
            failedPlans++;
        } else if (plan.reachesGoal()) {
            failedPlans = 0;
            lastPartialPlanFrom = null;
        } else {
            boolean gainedGround = lastPartialPlanFrom != null
                    && Math.hypot(position.x() - lastPartialPlanFrom.x(), position.z() - lastPartialPlanFrom.z()) > 1;
            failedPlans = plan.path().isEmpty() || !gainedGround ? failedPlans + 1 : 0;
            lastPartialPlanFrom = position;
        }
        report = failedPlans >= CANTCO_AFTER_FAILED_PLANS
                ? new Report.Cantco(String.format(Locale.ROOT,
                        "no route from (%.0f, %.0f, %.0f) to within %.0f of (%.0f, %.0f, %.0f)",
                        position.x(), position.y(), position.z(), goal.horizontalTolerance(), goal.x(), goal.y(), goal.z())
                        + priceOfARiskierRoute(world, plan))
                : new Report.Moving();
    }

    /**
     * When this soldier can't comply under its order's risk, whether a riskier order would let it -
     * and at what price - so the squad leader can weigh the mission against the soldier's health
     * rather than guess. Searches with every fall it could survive allowed (cheaply priced, to find
     * the least damaging such route), then names the least risk level that would accept it. "Would
     * let it" means reaching the goal, or - for a goal beyond one search - getting meaningfully
     * closer than the safe plan does: the shape of escaping a trap on the way somewhere far.
     */
    private String priceOfARiskierRoute(WorldView world, Pathfinder.Result safe) {
        Condition known = condition;
        if (known == null || known.health() < 2) {
            return "";
        }
        BlockPos start = standingCell(world, position);
        double factor = fallDamageFactor();
        Pathfinder.Result route = Pathfinder.find(world, start, goal, PLAN_BUDGET,
                new FallAllowance((int) Math.ceil(known.health()) - 1, 1, factor));
        BlockPos safeEnd = safe.path().isEmpty() ? start : safe.path().getLast();
        BlockPos riskyEnd = route.path().isEmpty() ? start : route.path().getLast();
        if (!route.reachesGoal() && goal.estimate(safeEnd) - goal.estimate(riskyEnd) < RETASK_DISTANCE) {
            return "";
        }
        int damage = (int) Math.ceil(Pathfinder.fallDamage(start, route.path()) * factor);
        double left = known.health() - damage;
        Risk needed = left >= Risk.MEDIUM.minHealthAfter() ? Risk.MEDIUM : left >= Risk.HIGH.minHealthAfter() ? Risk.HIGH : null;
        return String.format(Locale.ROOT, "; a route with falls costing %d of my %.0f health exists%s", damage,
                known.health(), needed == null ? ", too costly even at HIGH risk" : " (" + needed + " risk)");
    }

    private void trackProgress() {
        if (progressMark == null || Math.hypot(position.x() - progressMark.x(), position.z() - progressMark.z()) > 0.5
                || Math.abs(position.y() - progressMark.y()) > 0.5) {
            progressMark = position;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
    }

    /**
     * How much fall damage this soldier may take under {@code order}'s {@link Risk}: whatever keeps
     * it at or above the risk's floor, from the health it last reported - none if it hasn't
     * reported any. A well-fed soldier regenerates, so damage costs it half as much.
     */
    FallAllowance fallAllowance(Order order) {
        Condition known = condition;
        if (!(order instanceof Order.MoveTo move) || known == null) {
            return FallAllowance.NONE;
        }
        double spare = known.health() - move.risk().minHealthAfter();
        if (!(spare >= 1)) {
            return FallAllowance.NONE;
        }
        double cost = move.risk().costPerHealth() * (known.food() >= Condition.REGENERATION_FOOD ? 0.5 : 1);
        return new FallAllowance((int) Math.min(Math.floor(spare), move.risk().maxDamage()), cost, fallDamageFactor());
    }

    /** The share of fall damage this soldier's worn armour lets through - 1 until its kit is reported. */
    private double fallDamageFactor() {
        Kit carried = kit;
        return carried == null ? 1 : carried.fallDamageFactor();
    }

    private Goal goalFor(Order order) {
        return switch (order) {
            // Where to stand for the next cell is the building's to say, given as a move.
            case Order.Idle(), Order.Place _, Order.Dig _, Order.Fill _, Order.Gather _ -> null;
            case Order.MoveTo move -> new Goal(move.x(), move.y(), move.z(), move.horizontalTolerance(), move.verticalTolerance());
        };
    }

    private static double distance(Position a, Position b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private void applyVerticalMovement(WorldView world) {
        double x = position.x();
        double z = position.z();
        double y = position.y();
        int fx = (int) Math.floor(x);
        int fz = (int) Math.floor(z);
        int feet = (int) Math.floor(y + EPSILON);
        if (world.isWater(fx, feet, fz)) {
            // Afloat: no gravity, no fall - the body settles to the surface, head out.
            afloat = true;
            onGround = false;
            verticalVelocity = 0;
            if (climbingOut) {
                climbingOut = false;
                return;
            }
            int top = feet;
            while (world.isWater(fx, top + 1, fz)) {
                top++;
            }
            double rise = Math.max(-SWIM_RISE, Math.min(SWIM_RISE, top + SWIM_FLOAT - y));
            position = new Position(x, y + rise, z);
            return;
        }
        afloat = false;
        climbingOut = false;
        double velocity = onGround ? -GRAVITY * DRAG : verticalVelocity;
        double nextY = y + velocity;

        if (velocity < 0) {
            // Land on the highest solid block whose top lies between here and nextY.
            for (int blockY = (int) Math.floor(y + EPSILON) - 1; blockY >= (int) Math.floor(nextY); blockY--) {
                if (anySolid(world, x, z, blockY, blockY)) {
                    position = new Position(position.x(), blockY + 1, position.z());
                    verticalVelocity = 0;
                    onGround = true;
                    return;
                }
            }
        } else if (anySolid(world, x, z, (int) Math.floor(nextY + BODY_HEIGHT), (int) Math.floor(nextY + BODY_HEIGHT))) {
            nextY = y; // bumped a ceiling
            velocity = 0;
        }

        position = new Position(position.x(), nextY, position.z());
        verticalVelocity = (velocity - GRAVITY) * DRAG;
        onGround = false;
    }

    /** Room for the whole body, and no hazard but water (lava, fire) to step into - including when retreating. */
    private static boolean bodyFits(WorldView world, double x, double y, double z) {
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + BODY_HEIGHT - EPSILON);
        return !anySolid(world, x, z, minY, maxY) && !anyHazard(world, x, z, minY, maxY, false);
    }

    private static boolean anyHazard(WorldView world, double x, double z, int minY, int maxY) {
        return anyHazard(world, x, z, minY, maxY, true);
    }

    /** Whether any block in rows {@code minY..maxY} under the body's footprint is a hazard - water only if {@code water}. */
    private static boolean anyHazard(WorldView world, double x, double z, int minY, int maxY, boolean water) {
        int minX = (int) Math.floor(x - HALF_WIDTH);
        int maxX = (int) Math.floor(x + HALF_WIDTH - EPSILON);
        int minZ = (int) Math.floor(z - HALF_WIDTH);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isHazard(bx, by, bz) && (water || !world.isWater(bx, by, bz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether any block in rows {@code minY..maxY} under the body's 0.6-wide footprint at (x, z) is solid. */
    private static boolean anySolid(WorldView world, double x, double z, int minY, int maxY) {
        int minX = (int) Math.floor(x - HALF_WIDTH);
        int maxX = (int) Math.floor(x + HALF_WIDTH - EPSILON);
        int minZ = (int) Math.floor(z - HALF_WIDTH);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isSolid(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void stepToward(WorldView world, double targetX, double targetZ) {
        stepToward(world, targetX, targetZ, STEP);
    }

    /**
     * The deepest drop a step in a fight may leave the soldier over: one the server charges no
     * health for. Live, a soldier at 1 health fighting a phantom on a hut roof backed off its edge.
     */
    static final int FIGHT_DROP = Pathfinder.MAX_DROP;

    /** As {@link #stepToward(WorldView, double, double, double)}, not taken if it would leave the soldier over a deeper drop than {@link #FIGHT_DROP}. */
    private void stepGuarded(WorldView world, double targetX, double targetZ) {
        stepGuarded(world, targetX, targetZ, STEP);
    }

    private void stepGuarded(WorldView world, double targetX, double targetZ, double pace) {
        Position start = position;
        boolean startGround = onGround;
        double startVelocity = verticalVelocity;
        boolean startClimbing = climbingOut;
        stepToward(world, targetX, targetZ, pace);
        if (!position.equals(start) && overDrop(world, position)) {
            position = start;
            onGround = startGround;
            verticalVelocity = startVelocity;
            climbingOut = startClimbing;
        }
    }

    /** Whether nothing - no ground, no water - lies under {@code at} within {@link #FIGHT_DROP} blocks of its feet. */
    private static boolean overDrop(WorldView world, Position at) {
        int feet = (int) Math.floor(at.y() + EPSILON);
        for (int y = feet - 1; y >= feet - 1 - FIGHT_DROP; y--) {
            if (anySolid(world, at.x(), at.z(), y, y) || world.isWater((int) Math.floor(at.x()), y, (int) Math.floor(at.z()))) {
                return false;
            }
        }
        return true;
    }

    /** Headings tried to get away from a threat, in degrees off straight away from it: nearest first. */
    private static final int[] EVASION_HEADINGS = {0, 45, -45, 90, -90, 135, -135};

    /**
     * A step away from {@code threat}: of the headings round straight away, the one that gains the
     * most distance from it - a wall at the back turns it sideways - and never one that loses any.
     * Whether the soldier moved, or started a jump to.
     */
    private boolean evade(WorldView world, Position threat) {
        double away = Math.atan2(position.z() - threat.z(), position.x() - threat.x());
        Position start = position;
        boolean startGround = onGround;
        double startVelocity = verticalVelocity;
        boolean startClimbing = climbingOut;
        double from = Math.hypot(start.x() - threat.x(), start.z() - threat.z());
        double bestGain = 1e-6;
        int best = Integer.MIN_VALUE;
        for (int heading : EVASION_HEADINGS) {
            double angle = away + Math.toRadians(heading);
            stepToward(world, start.x() + Math.cos(angle) * 2, start.z() + Math.sin(angle) * 2);
            boolean dropsOff = !position.equals(start) && overDrop(world, position);
            // A jump started counts as a little ground gained: it lands farther on next tick.
            double gain = Math.hypot(position.x() - threat.x(), position.z() - threat.z()) - from
                    + (onGround != startGround && heading == 0 ? 0.05 : 0);
            if (gain > bestGain && !dropsOff) {
                bestGain = gain;
                best = heading;
            }
            position = start;
            onGround = startGround;
            verticalVelocity = startVelocity;
            climbingOut = startClimbing;
        }
        if (best == Integer.MIN_VALUE) {
            return false;
        }
        double angle = away + Math.toRadians(best);
        stepToward(world, start.x() + Math.cos(angle) * 2, start.z() + Math.sin(angle) * 2);
        return true;
    }

    private void stepToward(WorldView world, double targetX, double targetZ, double pace) {
        double step = (shieldRaised() ? pace * SHIELD_SLOWDOWN : pace) * (afloat ? SWIM_STEP / STEP : 1);
        double dx = targetX - position.x();
        double dz = targetZ - position.z();
        double distance = Math.hypot(dx, dz);
        if (distance < EPSILON) {
            return;
        }
        double move = Math.min(step, distance); // never overshoot - nor stall just short of the target

        double stepX = (dx / distance) * move;
        double stepZ = (dz / distance) * move;
        double nextX = position.x() + stepX;
        double nextZ = position.z() + stepZ;
        double y = position.y();
        if (bodyFits(world, nextX, y, nextZ)) {
            position = new Position(nextX, y, nextZ);
        } else if (afloat && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A bank one block above the water: rise out of it, then step on once clear of the edge.
            position = new Position(position.x(), Math.min(y + SWIM_RISE, Math.floor(y) + 1), position.z());
            climbingOut = true;
        } else if (onGround && !anyHazard(world, nextX, nextZ, (int) Math.floor(y), (int) Math.floor(y))
                && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A one-block rise with headroom here and there: jump; the step happens once airborne.
            // Never over water or lava: nothing says where the jump comes down.
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        } else if (Math.abs(stepX) > 1e-9 && bodyFits(world, nextX, y, position.z())) {
            // Blocked on the diagonal: slide along the obstacle, one axis at a time - how vanilla
            // resolves collisions. The route is planned on cell centres; a 0.6-wide body standing
            // off-centre can snag a corner the plan doesn't see.
            position = new Position(nextX, y, position.z());
        } else if (Math.abs(stepZ) > 1e-9 && bodyFits(world, position.x(), y, nextZ)) {
            position = new Position(position.x(), y, nextZ);
        }
    }
}
