package io.github.jayessdeesea.minecraft.bot;

import static io.github.jayessdeesea.minecraft.bot.Ballistics.*;
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
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.bot.Body.*;
import static io.github.jayessdeesea.minecraft.bot.Gait.*;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (docs/architecture.md's \"Behavior modeling\" section). {@code app}'s {@code SoldierRunner} is
 * the imperative shell: it calls {@link #tick} at 20Hz and reports the resulting gait.position to the
 * server; tests call it directly with a fake {@code WorldView}.
 *
 * <p>{@code gait.position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
public final class DeterministicSoldier extends Mover implements Soldier {

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
    @Override
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


    /** Whether it carries what shooting takes: a bow, and arrows for it. */
    private boolean canShoot() {
        Kit carried = kit;
        return carried != null && carried.bow() && carried.arrows() > 0;
    }

    /** Aims at {@code target} along the arrow's arc and draws; loosed once the draw is full. False if no arc reaches. */
    private boolean shoot(Position target) {
        double dx = target.x() - gait.position.x();
        double dz = target.z() - gait.position.z();
        double up = target.y() + TARGET_HEIGHT - (gait.position.y() + EYE_HEIGHT);
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

    /** Whether a squadmate stands in the line from its eyes to {@code target} (Ballistics.inTheLine): the arrow waits. */
    private boolean squadmateInTheLine(WorldView world, Position target) {
        double reach = Math.hypot(target.x() - gait.position.x(), target.z() - gait.position.z()) + 2;
        int self = entityId;
        List<Position> others = (self >= 0 ? world.playersNear(gait.position, reach, self) : world.playersNear(gait.position, reach)).stream()
                .filter(p -> Math.hypot(p.x() - gait.position.x(), p.z() - gait.position.z()) > SELF_SEEN_WITHIN || Math.abs(p.y() - gait.position.y()) > 1).toList();
        return inTheLine(gait.position, target, others);
    }

    /** Ticks left with the shield up; 0 when it's down. */
    private int shieldTicks;

    static final double BODY_HEIGHT = 1.8;
    static final double HALF_WIDTH = 0.3; // a player's box is 0.6 x 1.8 x 0.6

    static final double EPSILON = 1e-9;
    /** Feet this far up a solid cell are on its top: farmland and paths are 15/16 high. */
    static final double PARTIAL_BLOCK_TOP = Pathfinder.PARTIAL_BLOCK_TOP;
    /** Swimming at the surface, blocks per tick: about vanilla's 2 blocks/s, half a walk. */
    static final double SWIM_STEP = 0.1;
    /** Afloat, the feet this far into the top block of water: the head stays out, so no drowning. */
    static final double SWIM_FLOAT = 0.2;
    /** Blocks per tick rising or settling in water, and climbing out onto a bank. */
    static final double SWIM_RISE = 0.2;

    private final AtomicReference<Position> pendingCorrection = new AtomicReference<>();
    private volatile Report task;
    // Look direction, written by the tick loop, read by the caller reporting it.
    private volatile float yaw;
    private volatile float pitch;
    private int ticksUntilAttack = 0; // tick-loop only
    private double lastReportedHealth = -1;
    private int deaths;
    // Set by the connection's thread on a hit, consumed by the tick loop.
    private final AtomicBoolean hurt = new AtomicBoolean();

    public DeterministicSoldier(String name, Position startingPosition) {
        super(name, startingPosition);
        contactLog = new ContactLog(name);
    }

    /** Its contacts said as they go, for evals of them (ContactLog). */
    private final ContactLog contactLog;

    @Override
    public void receiveOrder(Order order) {
        this.order = order;
    }

    /**
     * The server says this soldier is somewhere else (a respawn, or a correction of a reported
     * move) - authoritative, so it wins. Applied at the start of the next {@link #tick} rather than
     * written here, so the tick loop stays the sole writer of {@code gait.position}.
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


    /** Whether the last tick ended standing on something - reported to the server with each move. */
    public boolean onGround() {
        return gait.onGround;
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
        return new SoldierStatus(name, gait.position, order, now, Optional.ofNullable(condition)
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
     * How far round its target a sword's sweep reaches a body, along each axis: vanilla sweeps every living entity whose
     * box meets the target's grown by a block on each side - a block, plus half a player's width and half the widest
     * mob's, a spider's. Boxes, not circles: live (field498-507), five soldiers were cut down by squadmates, one stood
     * a block and a half out on each axis from a zombie - 2.1 across from it, read clear, and swept.
     */
    static final double SWEEP_REACH = 1.0 + 0.3 + 0.7;

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
        Stream<Position> others = self >= 0 ? world.playersNear(target, SWEEP_REACH * Math.sqrt(2) + 1, self).stream()
                : world.playersNear(target, SWEEP_REACH * Math.sqrt(2) + 1).stream()
                .filter(p -> Math.hypot(p.x() - gait.position.x(), p.z() - gait.position.z()) > SELF_SEEN_WITHIN || Math.abs(p.y() - gait.position.y()) > 1);
        // Up and down, the boxes: the target's grown a quarter block, against a player's height. Not
        // feet to feet - live, a phantom two blocks over a squadmate's feet was swung at, and the
        // sweep killed the squadmate at a heart.
        return others.anyMatch(p -> Math.abs(p.x() - target.x()) <= SWEEP_REACH && Math.abs(p.z() - target.z()) <= SWEEP_REACH
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
        wet = world.isWater((int) Math.floor(gait.position.x()), (int) Math.floor(gait.position.y()), (int) Math.floor(gait.position.z()));

        Position correction = pendingCorrection.getAndSet(null);
        if (correction != null) {
            // Put back by the server on its way somewhere: it walked into what the model doesn't
            // show. The waypoint it was short of is routed round for a while, as when it stalls
            // short of one, and three such at one cell are a snag (takeSnag).
            if (route != null && routeIndex < route.size() && !goal.contains(route.get(routeIndex))
                    && Math.hypot(correction.x() - gait.position.x(), correction.z() - gait.position.z()) > 0.05) {
                stuckShortOf(route.get(routeIndex));
            }
            gait.position = correction;
            gait.verticalVelocity = 0;
            gait.onGround = false; // unknown until gravity settles it - and no route is planned mid-air
            route = null;
            return OptionalInt.empty();
        }

        OptionalInt attack = OptionalInt.empty();
        sprinting = false; // only a long move sprints; fighting and short moves walk
        Position before = gait.position;
        Kit worn = kit;
        List<Position> arrows = worn != null && worn.shield() ? world.arrowsNear(gait.position, ARROW_WATCH) : List.of();
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
        List<TrackedEntity> threats = visible(world, world.nearbyHostiles(gait.position, canShoot() ? BOW_RANGE : CONTACT_RADIUS));
        // An archer in sight within ARCHER_ENGAGE, to one armed for melee: closed on, not left to shoot when it will
        // (operator, 2026-11-20). Live, pillagers floating in a pond held soldiers off their work, fought by none.
        if (threats.isEmpty() && armedForMelee()) {
            threats = visible(world, world.nearbyHostiles(gait.position, ARCHER_ENGAGE)).stream().filter(TrackedEntity::shoots).toList();
        }
        if (threats.isEmpty() && underFireTicks > 0) {
            // Hit with nothing in reach: someone is shooting - find the shooter.
            threats = visible(world, world.nearbyHostiles(gait.position, UNDER_FIRE_RADIUS));
        }
        // An enderman only in self-defense: just hit, and it at hand - a swing at one passing provokes it. Live
        // (field451), Eli at the mine's bottom swung at three in eight seconds, and was killed by them.
        threats = threats.stream().filter(t -> !t.fightsOnlyAtHand() || underFireTicks > 0
                && Math.hypot(t.position().x() - gait.position.x(), t.position().z() - gait.position.z()) <= ATTACK_REACH + 1).toList();
        if (underFireTicks > 0) {
            underFireTicks--;
        }
        cutting = List.of(); // set again below only while navigating onto a step being cut
        int nearestId = threats.isEmpty() ? -1 : threats.get(0).entityId();
        if (nearestId != pinnedOn) {
            pinned = false; // a fresh contact gets a fresh look for a way to close
            pinnedCheckIn = 0;
            pinnedOn = nearestId;
            noWalk = false;
            noWalkCheckIn = 0;
        }
        // A hostile that fights hand to hand, out of reach with no way to it - water, a drop, a wall
        // between - has no way to the soldier either: not a fight, and the soldier carries on. Live,
        // the farm squad stood a night's start "in contact" with one across from it, stepping at it
        // into what stopped it, none of them striking; once it comes within reach, it's a fight.
        // A creeper too: one with no way to the soldier never comes within its blast. Live, three of a
        // squad stood a night in a cave 5 to 8 blocks from creepers, each backing off it with nowhere to
        // back to and too far to strike, and none of them moved.
        String response = null;
        List<TrackedEntity> seen = threats;
        if (!threats.isEmpty() && !threats.get(0).shoots()
                && gait.position.distanceTo(threats.get(0).position()) > ATTACK_REACH && noWalkTo(world, threats.get(0).position())) {
            threats = List.of();
            response = "carries on: no way between them on foot";
        }
        if (!threats.isEmpty()) {
            TrackedEntity nearest = threats.get(0);
            Position threat = nearest.position();
            double range = gait.position.distanceTo(threat);
            boolean shot = false;
            // A creeper too, from outside its blast (operator, 2026-11-18: soldiers with bows shoot far-away
            // enemies): live (field436-443), six soldiers were killed by creepers in melee, boxed in or after the
            // standoff, a stone sword's four hits outlasting its fuse; at arm's length it's still got away from.
            if ((!nearest.explodes() || range > CREEPER_CLEAR) && canShoot() && (range > SHOOT_BEYOND || !armedForMelee()
                    || nearest.shoots() && pinned(world, threat))) {
                // Out of a sword's reach, or with no sword, or pinned under an archer it can't close
                // on: stand and shoot back, the bow's arc aimed at it.
                shieldTicks = 0;
                shot = !squadmateInTheLine(world, threat) && shoot(threat);
            }
            if (shot) {
                holdCoverTicks = 0;
                response = "shoots";
            } else if (nearest.explodes() && range > CREEPER_CLEAR) {
                // In contact with a creeper well outside its blast: it carries on with its order, keeping
                // its route - a soldier never closes on one, so one that doesn't come on held it standing
                // for good. Live, three of a squad stood hours in a cave 5 to 8 blocks from creepers that
                // could walk to them and didn't. Closing inside this, it backs off as below.
                navigate(world);
                report = new Report.InContact(threats.size());
                contactLog.tick(world, gait.position, lastReportedHealth, kit, threats, "carries on: a creeper outside its blast");
                gait.fall(world);
                return attack;
            } else if (nearest.explodes()) {
                // A creeper is never fought at arm's length: get clear of the blast - straight away,
                // else the heading nearest that the ground allows. Boxed in, strike it: a hit knocks
                // it back out of its blast's reach. Live, a soldier with a wall at her back stood still
                // while one closed from six blocks to three and blew.
                // And one that holds it there - in a tunnel it can't be gone round, and backing off only
                // keeps the distance - is engaged once the standoff has gone on (the engagement
                // doctrine: both can reach, blocking the path): live (field320), a miner stood a minute
                // 2.9 blocks from one in a one-wide tunnel.
                creeperStandoff = nearest.entityId() == standoffWith ? creeperStandoff + 1 : 1;
                standoffWith = nearest.entityId();
                boolean engage = creeperStandoff > CREEPER_STANDOFF_TICKS;
                response = engage ? "closes on the creeper holding it in a standoff" : "gets clear of the creeper's blast";
                if (engage && range > ATTACK_REACH) {
                    stepGuarded(world, threat.x(), threat.z());
                } else if ((engage || !evade(world, threat)) && ticksUntilAttack == 0 && range <= ATTACK_REACH && !shieldRaised()) {
                    attack = OptionalInt.of(nearest.entityId());
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
            // An enderman at hand is fought only with a weapon: bare fists against its 40 health lose. Live (field441),
            // Pete, on his way to make a stone sword, punched one ten times at the depot and was killed, twice in 90 s.
            } else if (nearest.fightsOnlyAtHand() && !armedForMelee()) {
                response = "backs off: no weapon against one that fights only at hand";
                stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z());
            // An archer well below - across a cave's drop - out-shoots a sword as surely as one it has no route to:
            // the engagement doctrine's it-can-I-can't cell, cover and carry on. Live (field410-419), Alice, Hal and
            // Gus closed on skeletons in the caves under their faces and were shot 12-28 blocks down.
            } else if (nearest.shoots() && armedForMelee() && range > CONTACT_RADIUS && underFireTicks == 0
                    && (threat.y() < gait.position.y() - ARCHER_BELOW || pinned(world, threat))) {
                // Out of reach for its sword, out of contact and not shooting: let be, and the work goes on - hidden
                // from, it held soldiers off their work (operator, 2026-11-20).
                response = "carries on: an archer out of contact, not shooting, with no way to close on it";
                navigate(world);
            } else if (nearest.shoots() && (!armedForMelee() || threat.y() < gait.position.y() - ARCHER_BELOW || pinned(world, threat))) {
                response = !armedForMelee() ? "takes cover: an archer, and no melee weapon"
                        : threat.y() < gait.position.y() - ARCHER_BELOW ? "takes cover: an archer " + ARCHER_BELOW + " or more below"
                        : "takes cover: an archer with no route to strike it from";
                // An archer out-fights a bare hand from any range, and a sword it can't be closed on:
                // get out of its sight, and stay out.
                Optional<Position> cover = coverFrom(world, threat);
                if (cover.isPresent()) {
                    stepGuarded(world, cover.get().x(), cover.get().z());
                } else {
                    stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z());
                }
                holdCoverTicks = UNDER_FIRE_TICKS;
            } else {
                response = armedForMelee() ? "fights hand to hand" : "fights bare-handed";
                if (ticksUntilAttack == 0 && range <= ATTACK_REACH && !shieldRaised() && !sweepHitsSquadmate(world, threat)) {
                    attack = OptionalInt.of(nearest.entityId());
                    // A hit's damage scales with the swing's charge: wait for the held weapon's full one.
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
                if (range < KEEP_AWAY) {
                    stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z());
                } else if (range > ATTACK_REACH) {
                    // Closing on what it fights: every tick in the open is a tick under its fire.
                    sprinting = canSprint();
                    stepGuarded(world, threat.x(), threat.z(), sprinting ? SPRINT_STEP : STEP);
                }
            }
            route = null; // wherever the fight ends up, the old route no longer starts here
            report = new Report.InContact(threats.size());
            contactLog.tick(world, gait.position, lastReportedHealth, kit, threats, response);
            if (!shot) {
                stopDrawing();
                look(threat);
            }
        } else if (response != null) {
            contactLog.tick(world, gait.position, lastReportedHealth, kit, seen, response); // seen, and let be
            navigate(world);
        } else if (drawing && stopDrawing()) {
            // The fight is over: the bow comes down this tick.
        } else if (shieldRaised() && !arrows.isEmpty()) {
            // Shot at from out of sight: face where the arrow came from, and hold.
            look(arrows.getFirst());
        } else if (holdCoverTicks > 0) {
            // In cover from an archer it can't fight: out of its sight isn't out of its range. Hold.
            holdCoverTicks--;
        } else {
            contactLog.tick(world, gait.position, lastReportedHealth, kit, List.of(), null);
            navigate(world);
            double dx = gait.position.x() - before.x();
            double dz = gait.position.z() - before.z();
            if (dx * dx + dz * dz > 1e-4) {
                yaw = yawToward(dx, dz);
                pitch = 0;
            }
        }

        gait.fall(world);
        return attack;
    }

    /** How far off an archer in sight is closed on by a soldier armed for melee, shooting or not. */
    static final double ARCHER_ENGAGE = 16;

    /** How far below a soldier an archer is out of its reach for a sword, however a route might go down to it. */
    static final double ARCHER_BELOW = 4;

    /** Ticks a creeper holds a soldier inside its blast's reach before it's engaged: ten seconds. */
    static final int CREEPER_STANDOFF_TICKS = 200;
    /** How long the creeper it's backing off from has held it, and which one. */
    private int creeperStandoff;
    private int standoffWith = -1;

    /** How far off a creeper stops being contact for a soldier with no bow: past its blast. */
    static final double CREEPER_CLEAR = 4.5;

    /** Whether the last walk-only check found no way to the hostile, and ticks until the next. */
    private boolean noWalk;
    private int noWalkCheckIn;

    /**
     * Whether no walk - no steps put, no block dug, as a hostile goes - reaches within striking
     * distance of {@code threat}: a way it could dig or build to isn't one the hostile can come by.
     * Live, three of a squad stood a night 5 to 8 blocks from creepers they could have dug to and
     * the creepers couldn't walk to. Planned once a {@link #PINNED_CHECK_TICKS}, on the ground or
     * swimming.
     */
    private boolean noWalkTo(WorldView world, Position threat) {
        if (noWalkCheckIn-- <= 0 && (gait.onGround || wet)) {
            noWalkCheckIn = PINNED_CHECK_TICKS;
            Goal reach = new Goal(threat.x(), threat.y(), threat.z(), ATTACK_REACH, Pathfinder.MAX_DROP);
            noWalk = !Pathfinder.find(world, standingCell(world, gait.position), reach, PLAN_BUDGET,
                    FallAllowance.NONE, 0, false).reachesGoal();
        }
        return noWalk;
    }

    /** How often, in contact with an archer, it checks there's still a way to close on it. */
    static final int PINNED_CHECK_TICKS = 20;
    /** Whether the last check found no way to the archer, and ticks until the next. */
    private boolean pinned;
    private int pinnedCheckIn;
    /** The hostile {@link #pinned} was last looked at for. */
    private int pinnedOn = -1;

    /**
     * Whether no route reaches within striking distance of the archer at {@code threat} - water, a
     * drop or a wall between them: an armed soldier that can't close is as outranged as a bare hand.
     * Planned once a {@link #PINNED_CHECK_TICKS}; an archer backing off as it comes on isn't pinning
     * it, so progress isn't the test - a route is.
     */
    private boolean pinned(WorldView world, Position threat) {
        // On the ground, or swimming: live, a soldier in water was never on the ground, so never found
        // the hostile out of reach, and stood "in contact" across two restarts, its drill held off.
        if (pinnedCheckIn-- <= 0 && (gait.onGround || wet)) {
            pinnedCheckIn = PINNED_CHECK_TICKS;
            Goal reach = new Goal(threat.x(), threat.y(), threat.z(), ATTACK_REACH, Pathfinder.MAX_DROP);
            pinned = !Pathfinder.find(world, standingCell(world, gait.position), reach, PLAN_BUDGET,
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
        BlockPos here = BlockPos.containing(gait.position.x(), gait.position.y() + EPSILON, gait.position.z());
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
                    double distance = Math.hypot(spot.x() - gait.position.x(), spot.z() - gait.position.z());
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
        Position eye = new Position(gait.position.x(), gait.position.y() + EYE_HEIGHT, gait.position.z());
        return hostiles.stream().filter(hostile -> lineOfSight(world, eye,
                new Position(hostile.position().x(), hostile.position().y() + TARGET_HEIGHT, hostile.position().z()))).toList();
    }

    /** Sampling step along a sight line: fine enough not to step over the corner of a block. */
    static final double SIGHT_STEP = 0.2;


    private void look(Position target) {
        double dx = target.x() - gait.position.x();
        double dz = target.z() - gait.position.z();
        double dy = target.y() + TARGET_HEIGHT - (gait.position.y() + EYE_HEIGHT);
        yaw = yawToward(dx, dz);
        pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
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
        Gait.Snapshot start = gait.snapshot();
        stepToward(world, targetX, targetZ, pace);
        if (!gait.position.equals(start.position()) && overDrop(world, gait.position)) {
            gait.restore(start);
        }
    }


    /** Headings tried to get away from a threat, in degrees off straight away from it: nearest first. */
    private static final int[] EVASION_HEADINGS = {0, 45, -45, 90, -90, 135, -135};

    /**
     * A step away from {@code threat}: of the headings round straight away, the one that gains the
     * most distance from it - a wall at the back turns it sideways - and never one that loses any.
     * Whether the soldier moved, or started a jump to.
     */
    private boolean evade(WorldView world, Position threat) {
        double away = Math.atan2(gait.position.z() - threat.z(), gait.position.x() - threat.x());
        Gait.Snapshot snapshot = gait.snapshot();
        Position start = snapshot.position();
        boolean startGround = snapshot.onGround();
        double from = Math.hypot(start.x() - threat.x(), start.z() - threat.z());
        double bestGain = 1e-6;
        int best = Integer.MIN_VALUE;
        for (int heading : EVASION_HEADINGS) {
            double angle = away + Math.toRadians(heading);
            stepToward(world, start.x() + Math.cos(angle) * 2, start.z() + Math.sin(angle) * 2);
            boolean dropsOff = !gait.position.equals(start) && overDrop(world, gait.position);
            // A jump started counts as a little ground gained: it lands farther on next tick.
            double gain = Math.hypot(gait.position.x() - threat.x(), gait.position.z() - threat.z()) - from
                    + (gait.onGround != startGround && heading == 0 ? 0.05 : 0);
            if (gain > bestGain && !dropsOff) {
                bestGain = gain;
                best = heading;
            }
            gait.restore(snapshot);
        }
        if (best == Integer.MIN_VALUE) {
            return false;
        }
        double angle = away + Math.toRadians(best);
        stepToward(world, start.x() + Math.cos(angle) * 2, start.z() + Math.sin(angle) * 2);
        return true;
    }
}
