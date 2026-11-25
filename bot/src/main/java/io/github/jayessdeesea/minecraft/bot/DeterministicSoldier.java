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

    /** Whether it let be the hostiles it saw this tick - "carries on" - so its work goes on: field652, Eli's mine held 5 minutes. */ public boolean lettingBe() { return lettingBe; }
    private volatile boolean lettingBe;
    /** Whether, taking or holding cover, it can eat: no archer sees it, nothing within a blow of it - field666, Eli sat a minute at 1 health in cover, 12 rations, uneaten. */ public boolean eatsInCover() { return eatsInCover; } private volatile boolean eatsInCover;

    /** Whether the hit this tick returned is a knockback hit: its sprint told the server first; the server stops it (Strike). */
    public boolean takeKnockbackHit() {
        boolean was = knockbackHit;
        knockbackHit = false;
        return was;
    }

    /** The swing it chose this tick, WAIT with none - for the battle simulation to strike as the server would. */
    private volatile Strike.Kind swingKind = Strike.Kind.WAIT;

    /** The swing it chose this tick (Strike), and WAIT once taken. */
    public Strike.Kind takeSwingKind() {
        Strike.Kind was = swingKind;
        swingKind = Strike.Kind.WAIT;
        return was;
    }

    /** Whether its arm swings this tick with no hit, the charge started over (Strike.Kind.RESET). */
    public boolean takeArmSwing() {
        boolean was = armSwing;
        armSwing = false;
        return was;
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
        return Contact.canShoot(kit);
    }

    /** The pitch of the arrow's arc to {@code target}, empty if none reaches. */
    private OptionalDouble aim(Position target) {
        double up = target.y() + TARGET_HEIGHT - (gait.position.y() + EYE_HEIGHT);
        return aimPitch(Math.hypot(target.x() - gait.position.x(), target.z() - gait.position.z()), up);
    }

    /** Turns to {@code target} at {@code aim} and draws; loosed once the draw is full. */
    private void draw(Position target, double aim) {
        yaw = yawToward(target.x() - gait.position.x(), target.z() - gait.position.z());
        pitch = (float) aim;
        drawing = true;
        if (++drawTicks >= FULL_DRAW_TICKS) {
            loose = true;
            drawTicks = 0;
        }
    }

    /** The ground's answers to what the contact decision asks this tick (Contact.Ground), and the aim it found for an arrow. */
    private final class Asked implements Contact.Ground {
        private final WorldView world;
        private OptionalDouble aim = OptionalDouble.empty();

        Asked(WorldView world) {
            this.world = world;
        }

        @Override public boolean noWalkTo(Position hostile) { return DeterministicSoldier.this.noWalkTo(world, hostile); }
        @Override public boolean overhead(Position hostile) { return DeterministicSoldier.this.overhead(world, hostile); }
        @Override public boolean pinned(Position archer) { return DeterministicSoldier.this.pinned(world, archer); }

        @Override
        public boolean shotAt(Position hostile) {
            aim = squadmateInTheLine(world, hostile) ? OptionalDouble.empty() : aim(hostile);
            return aim.isPresent();
        }
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
    /** Ticks till it says again what's round it when hurt with nothing in contact - tick-loop only. */
    private int hurtSaidIn;
    /** Whether this tick's hit is a knockback hit - sprinting told the server before it - and whether its arm swings alone. */
    private volatile boolean knockbackHit;
    private volatile boolean armSwing;
    private double lastReportedHealth = -1;
    private int deaths;
    // Set by the connection's thread on a hit, consumed by the tick loop.
    private final AtomicBoolean hurt = new AtomicBoolean();

    public DeterministicSoldier(String name, Position startingPosition) {
        super(name, startingPosition);
        contactLog = new ContactLog(name);
    }

    /** Its contacts said as they go, for evals of them (ContactLog); and the response to the one under way, null out of contact. */
    private final ContactLog contactLog;
    public String contactResponse() { return contactLog.response(); }

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
        lettingBe = false; eatsInCover = false;
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
        boolean hurtNow = hurt.getAndSet(false);
        if (hurtNow || !arrows.isEmpty()) {
            underFireTicks = UNDER_FIRE_TICKS;
        }
        // The hostiles near it looked up once a tick, out to the furthest any rule below looks, and each rule takes those within
        // its own reach: commit 960's profile, four lookups a tick each walking every hostile tracked, a tenth of the garrison.
        List<TrackedEntity> near = world.nearbyHostiles(gait.position, FLED_PAST);
        // A soldier with a bow reaches further than a sword: what it can see within bow range is contact.
        List<TrackedEntity> threats = visible(world, within(near, canShoot() ? BOW_RANGE : CONTACT_RADIUS));
        // An archer in sight within ARCHER_ENGAGE, to one armed for melee: closed on, not left to shoot when it will
        // (operator, 2026-11-20). Live, pillagers floating in a pond held soldiers off their work, fought by none.
        if (threats.isEmpty() && armedForMelee()) {
            threats = visible(world, within(near, ARCHER_ENGAGE).stream().filter(TrackedEntity::shoots).toList());
        }
        // One fleeing an archer keeps fleeing it till well past its range, not back into it as it leaves contact: in the
        // combat eval matrix a bare hand sprinted to 9 off, a skeleton's arrows reaching 15, and walked back to its order.
        if (threats.isEmpty() && fleeingFrom != -1) {
            int from = fleeingFrom;
            threats = near.stream().filter(t -> t.entityId() == from).toList();
            if (threats.isEmpty()) {
                fleeingFrom = -1;
            }
        }
        if (threats.isEmpty() && underFireTicks > 0) {
            // Hit with nothing in reach: someone is shooting - find the shooter.
            threats = visible(world, within(near, UNDER_FIRE_RADIUS));
        }
        threats = Contact.inOrder(gait.position, threats, underFireTicks > 0);
        if (underFireTicks > 0) {
            underFireTicks--;
        }
        cutting = List.of(); // set again below only while navigating onto a step being cut
        // Hurt with nothing in contact: what's round it, seen or not, for the eval - live (field605), Olga was shot dead by a
        // skeleton no [Contact] line named, fighting zombies out of reach, and its place couldn't be replayed.
        if (hurtSaidIn > 0) {
            hurtSaidIn--;
        }
        if (hurtNow && threats.stream().noneMatch(t -> gait.position.distanceTo(t.position()) <= CONTACT_RADIUS) && hurtSaidIn == 0) {
            hurtSaidIn = 40;
            contactLog.hurtWithNothing(world, gait.position, lastReportedHealth, near);
        }
        int nearestId = threats.isEmpty() ? -1 : threats.get(0).entityId();
        contactTicks++;
        if (nearestId != pinnedOn) {
            pinned = false; // a fresh contact gets a fresh look for a way to close
            pinnedCheckIn = 0;
            pinnedOn = nearestId;
            contactTicks = 0; // a fight begun, a second on, is one to finish
            byWalk = false; // a fresh contact is closed on straight, till that gains no ground
            lastClose = null;
            noWalk = false;
            noWalkCheckIn = 0;
            boxedIn = false;
            coverStalledTicks = 0;
            stalledTicks = 0;
            quietTicks = 0;
        }
        // An arrow in the air near it breaks the quiet, shield or none - looked for only while an archer is the contact.
        quietTicks = underFireTicks > 0 || !threats.isEmpty() && threats.getFirst().shoots() && !world.arrowsNear(gait.position, ARROW_WATCH).isEmpty() ? 0 : quietTicks + 1;
        // Withdrawn from one that won the exchange (Engagement.withdrawal): clear of it, held there to eat and heal - not fought to
        // the death, not fled for good - till healed past its line; a fresh hostile nearer is met as any is.
        contactLog.facts = ""; // a withdrawal's line is not this tick's decision
        if (withdrawingFrom != -1 && (threats.isEmpty() || threats.getFirst().entityId() == withdrawingFrom) && withdraw(world, near)) {
            gait.fall(world);
            return attack;
        }
        // What it does about the first of them is Contact's to say; here it's done.
        String response = null;
        List<TrackedEntity> seen = threats;
        Asked asked = threats.isEmpty() ? null : new Asked(world);
        Contact.Situation situation = threats.isEmpty() ? null : new Contact.Situation(gait.position, lastReportedHealth, kit,
                threats, underFireTicks > 0, contactTicks > BEGUN_TICKS, stalledTicks <= BEGUN_TICKS, boxedIn, wet, fitToSprint(),
                shieldRaised(), clearingFrom == nearestId, nearestId == standoffWith ? creeperStandoff : 0, quietTicks > Contact.QUIET_SECONDS * 20);
        Contact.Choice choice = situation == null ? null : Contact.respond(situation, asked);
        contactLog.facts = situation == null ? "" : situation.facts();
        if (choice != null && choice.response().letsBe()) {
            threats = List.of();
            response = choice.response().said();
        }
        if (!threats.isEmpty()) {
            Contact.Response chosen = choice.response();
            response = chosen.said();
            TrackedEntity nearest = threats.get(0);
            Position threat = nearest.position();
            double range = gait.position.distanceTo(threat);
            boolean shot = chosen == Contact.Response.SHOOTS;
            if (choice.bowUp()) {
                shieldTicks = 0; // the bow in hand, the shield is down
            }
            if (shot) {
                holdCoverTicks = 0;
                draw(threat, asked.aim.getAsDouble());
            } else if (chosen == Contact.Response.PAST_CREEPER) {
                // Getting clear of one it came within lighting distance of goes on past where its fuse goes out; one
                // never come that close is gone round, the route kept.
                creeperStandoff = 0;
                if (range > CREEPER_DEFUSE) {
                    clearingFrom = -1;
                }
                // In contact with a creeper well outside its blast: it carries on with its order, keeping
                // its route - a soldier never closes on one, so one that doesn't come on held it standing
                // for good. Live, three of a squad stood hours in a cave 5 to 8 blocks from creepers that
                // could walk to them and didn't. Closing inside this, it backs off as below.
                navigate(world);
                report = new Report.InContact(threats.size());
                contactLog.tick(world, gait.position, lastReportedHealth, kit, threats, response);
                gait.fall(world);
                return attack;
            } else if (chosen == Contact.Response.CLEAR_OF_BLAST || chosen == Contact.Response.CREEPER_STANDOFF) {
                // A creeper is never fought at arm's length: get clear of the blast - straight away,
                // else the heading nearest that the ground allows. Boxed in, strike it: a hit knocks
                // it back out of its blast's reach. Live, a soldier with a wall at her back stood still
                // while one closed from six blocks to three and blew.
                // And one that holds it there - in a tunnel it can't be gone round, and backing off only
                // keeps the distance - is engaged once the standoff has gone on (the engagement
                // doctrine: both can reach, blocking the path): live (field320), a miner stood a minute
                // 2.9 blocks from one in a one-wide tunnel.
                clearingFrom = nearest.entityId();
                creeperStandoff = nearest.entityId() == standoffWith ? creeperStandoff + 1 : 1;
                standoffWith = nearest.entityId();
                boolean engage = chosen == Contact.Response.CREEPER_STANDOFF;
                if (engage && range > ATTACK_REACH) {
                    stepGuarded(world, threat.x(), threat.z());
                } else if ((engage || !evade(world, threat)) && ticksUntilAttack == 0 && range <= ATTACK_REACH && !shieldRaised()) {
                    attack = OptionalInt.of(nearest.entityId());
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
            } else if (chosen == Contact.Response.BACKS_OFF) {
                stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z());
            } else if (chosen == Contact.Response.GETS_CLEAR) {
                withdrawFrom(nearest, false);
                sprinting = canSprint();
                stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z(), sprinting ? SPRINT_STEP : STEP);
            } else if (chosen == Contact.Response.PAST_ARCHER) {
                navigate(world);
            } else if (chosen.takesCover()) {
                // An archer out-fights a bare hand from any range, and a sword it can't be closed on:
                // get out of its sight, and stay out.
                Optional<Position> cover = coverFrom(world, gait.position, threat);
                // To cover at a sprint, or with none in reach, out of its range at one: a walk away from an archer
                // stays inside its range, and in the combat eval matrix a bare hand in the open was shot dead 10 or
                // more times in 12.
                sprinting = canSprint();
                double pace = sprinting ? SPRINT_STEP : STEP;
                Position stoodAt = gait.position;
                if (!armedForMelee()) {
                    fleeingFrom = nearest.entityId();
                } else if (archerExchange(range) != Engagement.Kind.ENGAGE) {
                    withdrawFrom(nearest, true);
                }
                if (cover.isPresent()) {
                    stepGuarded(world, cover.get().x(), cover.get().z(), pace);
                } else {
                    stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z(), pace);
                }
                // Hurt where it has stood a second with no step toward cover or away taken: that is no cover, and there
                // is no getting clear of this one (Contact). In cover that holds it stands as still, and isn't hurt.
                coverStalledTicks = gait.position.equals(stoodAt) ? coverStalledTicks + 1 : 0;
                if (coverStalledTicks <= 1) {
                    coverStalledHealth = lastReportedHealth;
                }
                boxedIn |= coverStalledTicks > BEGUN_TICKS && lastReportedHealth < coverStalledHealth;
                holdCoverTicks = UNDER_FIRE_TICKS;
                eatsInCover = threats.stream().noneMatch(t -> !t.shoots() && gait.position.distanceTo(t.position()) <= ATTACK_REACH + 1) && !lineOfSight(world, new Position(threat.x(), threat.y() + EYE_HEIGHT, threat.z()), new Position(gait.position.x(), gait.position.y() + TARGET_HEIGHT, gait.position.z()));
            } else {
                // Which swing (Strike): the sword's sweep with none of the force's in its box, else one that doesn't sweep - a
                // knockback hit sprinting, or one short of a sweep's charge (operator, 2026-11-21: the sweep has its place, and it's
                // not always). Live (field584), four squadmates in one cell of a slot each held fire for the others, and a zombie
                // killed two; and the second's hold that answered it then swept them.
                Kit carried = kit;
                Optional<String> item = carried == null ? Optional.empty() : carried.weapon();
                WeaponStats.Stats held = WeaponStats.melee(item);
                if (range <= ATTACK_REACH && !shieldRaised()) {
                    long others = threats.stream().filter(t -> t.entityId() != nearest.entityId() && Math.abs(t.position().x() - threat.x()) <= SWEEP_REACH
                            && Math.abs(t.position().z() - threat.z()) <= SWEEP_REACH).count();
                    Strike.Kind swing = Strike.choose(Strike.Weapon.of(item), new Strike.Situation(held.ticksToCharge() - ticksUntilAttack,
                            sweepHitsSquadmate(world, threat), (int) others, canSprint()));
                    swingKind = swing;
                    switch (swing) {
                        case FULL, SHORT, KNOCKBACK -> {
                            attack = OptionalInt.of(nearest.entityId());
                            ticksUntilAttack = held.ticksToCharge();
                            if (swing == Strike.Kind.KNOCKBACK) {
                                sprinting = true; // told the server before the hit, which stops the sprint with it
                                knockbackHit = true;
                            }
                        }
                        case RESET -> {
                            armSwing = true; // the charge started over, for a short hit after
                            ticksUntilAttack = held.ticksToCharge();
                        }
                        case WAIT -> {
                        }
                    }
                }
                if (range < KEEP_AWAY) {
                    stepGuarded(world, 2 * gait.position.x() - threat.x(), 2 * gait.position.z() - threat.z());
                } else if (range > ATTACK_REACH) {
                    // Closing on what it fights: every tick in the open is a tick under its fire.
                    sprinting = canSprint();
                    // An archer by the walk to it - down round a drop - not straight at it: live (field597), Alice stood 46 s on
                    // a ledge 4 over a skeleton 6.4 off, "fights hand to hand", stepping at the drop, and was shot dead.
                    // Straight at it while that gains ground; the walk once a step at it gains none.
                    boolean gaining = lastClose == null || Math.hypot(gait.position.x() - lastClose.x(), gait.position.z() - lastClose.z()) > 0.02;
                    lastClose = gait.position;
                    stalledTicks = gaining ? 0 : stalledTicks + 1;
                    byWalk = nearest.shoots() && (byWalk || !gaining);
                    Optional<BlockPos> by = byWalk ? nextOnClosePath() : Optional.empty();
                    stepGuarded(world, by.map(b -> b.x() + 0.5).orElse(threat.x()), by.map(b -> b.z() + 0.5).orElse(threat.z()),
                            sprinting ? SPRINT_STEP : STEP);
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
            lettingBe = true;
            navigate(world);
        } else if (drawing && stopDrawing()) {
            // The fight is over: the bow comes down this tick.
        } else if (shieldRaised() && !arrows.isEmpty()) {
            // Shot at from out of sight: face where the arrow came from, and hold.
            look(arrows.getFirst());
        } else if (holdCoverTicks > 0) {
            // In cover from an archer it can't fight: out of its sight isn't out of its range. Hold - and eat, hurt.
            holdCoverTicks--;
            eatsInCover = true;
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

    /** Those of {@code near} - nearest first - within {@code radius} of the soldier. */
    private List<TrackedEntity> within(List<TrackedEntity> near, double radius) {
        double r2 = radius * radius;
        List<TrackedEntity> in = new java.util.ArrayList<>(near.size());
        for (TrackedEntity t : near) {
            double dx = t.position().x() - gait.position.x(), dy = t.position().y() - gait.position.y(), dz = t.position().z() - gait.position.z();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                in.add(t);
            }
        }
        return in;
    }

    /**
     * Under this health an archer it can't close on isn't let be between its shots: kept out of its sight, as one with no
     * weapon keeps out of it. Live (field568), Eli at 1 health walked back to his cave edge in a skeleton's line, "carries
     * on", and was shot dead.
     */
    static final double HURT_HEALTH = 10;
    /** The exchange with one that fights hand to hand {@code range} off (Engagement.melee). */
    private Engagement.Kind meleeExchange(double range) {
        return Engagement.melee(lastReportedHealth, kit, range, contactTicks > BEGUN_TICKS, Contact.pace(boxedIn, false, canSprint()));
    }

    /** The exchange with an archer {@code range} off (Engagement.archer). */
    private Engagement.Kind archerExchange(double range) {
        return Engagement.archer(lastReportedHealth, kit, range, contactTicks > BEGUN_TICKS, Contact.pace(boxedIn, wet, canSprint()),
                stalledTicks <= BEGUN_TICKS);
    }
    /**
     * Ticks since it met the hostile it's in contact with now: past {@link #BEGUN_TICKS} the fight's begun. Not the health
     * lost since: with the server's regeneration a soldier heals between arrows, read as not begun, and broke off mid-fight.
     */
    private int contactTicks;
    /** A second into a contact, its fight is begun. */
    static final int BEGUN_TICKS = 20;

    /** The hostile it's withdrawn from, or -1; whether that one shoots; and the health it re-engages past (Engagement.line). */
    private int withdrawingFrom = -1;
    private boolean withdrawnFromArcher;
    private double withdrawLine;
    /** Whether, withdrawing from the hostile in contact now, it can't get clear of it: it's fought, as one that can't be got away from. */
    private boolean boxedIn;
    /** Ticks in a row with no step taken toward cover or away from the archer, and its health when they began. */
    private int coverStalledTicks;
    private double coverStalledHealth;
    /** Ticks closing on the hostile in contact with no ground gained: past {@link #BEGUN_TICKS}, it isn't closing, and strikes nothing. */
    private int stalledTicks;
    /** Ticks of this contact since the soldier was last hurt or saw an arrow come (Contact.QUIET_SECONDS). */
    private int quietTicks;
    /** Whether the withdrawal's cover proved none - seen or shot at in it. */
    private boolean coverFailed;

    /** Withdraws from {@code hostile}, the exchange with it lost - unless it's past its line to re-engage at already. */
    private void withdrawFrom(TrackedEntity hostile, boolean archer) {
        double line = Engagement.line(kit, archer, Condition.MAX_HEALTH);
        if (lastReportedHealth < line) {
            withdrawingFrom = hostile.entityId();
            coverFailed = false;
            withdrawnFromArcher = archer;
            withdrawLine = line;
        }
    }

    /**
     * This tick of the withdrawal, if it isn't over: away from the hostile while it's near and in sight or following - to
     * cover from an archer, else the heading that gains most - and held clear of it, out of contact so the runner's
     * rations are eaten, while it heals. Over once past its line, or with nothing to heal on (Engagement.withdrawal), or
     * boxed in where no step gets it further off: the hostile met afresh.
     */
    private boolean withdraw(WorldView world, List<TrackedEntity> near) {
        int from = withdrawingFrom;
        Optional<TrackedEntity> hostile = near.stream().filter(t -> t.entityId() == from).findFirst();
        Condition known = condition;
        Kit carried = kit;
        boolean canHeal = known != null && (known.food() >= Condition.REGENERATION_FOOD || carried != null && carried.rations() > 0);
        Engagement.Withdrawal next = Engagement.withdrawal(lastReportedHealth, withdrawLine, canHeal,
                hostile.map(t -> gait.position.distanceTo(t.position())).orElse(Double.POSITIVE_INFINITY),
                hostile.isPresent() && (underFireTicks > 0 || !visible(world, List.of(hostile.get())).isEmpty()), !withdrawnFromArcher);
        if (next == Engagement.Withdrawal.BREAK_CONTACT) {
            Position threat = hostile.orElseThrow().position(), was = gait.position;
            // Still the exchange's to say, each step: one it can't get away from after all - swimming, the archer keeping up -
            // is a fight forced on it, fought to the end: at a bank's edge, swimming and sprinting flipped it in and out.
            double range = gait.position.distanceTo(threat);
            boxedIn = (withdrawnFromArcher ? archerExchange(range) : meleeExchange(range)) == Engagement.Kind.ENGAGE;
            if (boxedIn) {
                withdrawingFrom = -1;
                return false;
            }
            // Cover from an archer - till it stands in cover still seen or shot at: then that's no cover, and it gets off.
            Optional<Position> cover = withdrawnFromArcher && !coverFailed ? coverFrom(world, gait.position, threat) : Optional.empty();
            coverFailed |= cover.filter(c -> Math.hypot(c.x() - gait.position.x(), c.z() - gait.position.z()) <= 0.5).isPresent();
            sprinting = canSprint();
            cover.filter(c -> !coverFailed).ifPresent(c -> stepGuarded(world, c.x(), c.z(), sprinting ? SPRINT_STEP : STEP));
            if (gait.position.equals(was) && !evade(world, threat)) {
                withdrawingFrom = -1; // boxed in: no getting clear of it, and it's fought
                boxedIn = true;
                return false;
            }
            route = null;
            report = new Report.InContact(1);
            contactLog.tick(world, gait.position, lastReportedHealth, kit, List.of(hostile.get()),
                    String.format(java.util.Locale.ROOT, "withdraws: the exchange lost, to heal past %.0f", withdrawLine));
            look(threat);
            return true;
        }
        if (next == Engagement.Withdrawal.RESUME) {
            withdrawingFrom = -1;
            contactTicks = 0; // met again, a fresh contact: closed on with the margin, healed
            stalledTicks = 0;
            return false;
        }
        report = new Report.Moving(); // clear of it: out of contact, holding to heal
        contactLog.tick(world, gait.position, lastReportedHealth, kit, List.of(), null);
        return true;
    }

    /** How far a soldier fleeing an archer runs before it's fled: past a skeleton's 15 blocks of range. */
    static final double FLED_PAST = 20;
    /** The archer it's fleeing with no melee weapon, or -1. */
    private int fleeingFrom = -1;

    /** How far off an archer in sight is closed on by a soldier armed for melee, shooting or not. */
    static final double ARCHER_ENGAGE = 16;

    /** How far below a soldier an archer is out of its reach for a sword, however a route might go down to it. */
    static final double ARCHER_BELOW = 4;

    /** Ticks a creeper holds a soldier inside its blast's reach before it's engaged: ten seconds. */
    static final int CREEPER_STANDOFF_TICKS = 200;
    /** How long the creeper it's backing off from has held it, and which one. */
    private int creeperStandoff;
    private int standoffWith = -1;

    /** How far off a creeper is shot at by a soldier with a bow: outside where it lights. */
    static final double CREEPER_CLEAR = 4.5;
    /**
     * How far off a creeper stops being contact: past where its fuse, once lit, goes out - 7 in vanilla - so a lit one
     * is never left burning within the 6 of its blast. In the combat eval matrix every creeper fight blew with the
     * soldier at 4.5-6, backed off only that far and gone back to its order; live (field554), 367 contacts in an hour
     * got clear only to step back in.
     */
    static final double CREEPER_DEFUSE = 7.5;
    /** The creeper it's getting clear of, once come within {@link #CREEPER_CLEAR} of it - till past {@link #CREEPER_DEFUSE}. */
    private int clearingFrom = -1;

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

    /** Whether {@code threat} flies or falls higher over the soldier's feet than a strike, feet to feet, reaches: no ground a jump under it. */
    private boolean overhead(WorldView world, Position threat) {
        int x = (int) Math.floor(threat.x()), y = (int) Math.floor(threat.y()), z = (int) Math.floor(threat.z());
        return threat.y() - gait.position.y() > ATTACK_REACH && !world.isSolid(x, y - 1, z) && !world.isSolid(x, y - 2, z);
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
            // A walk, no step built or block dug: the close walks it (nextOnClosePath) - live (field597), a route by building
            // read Alice not pinned, and her close, straight at the skeleton below her ledge, never moved.
            Goal reach = new Goal(threat.x(), threat.y(), threat.z(), ATTACK_REACH, Pathfinder.MAX_DROP);
            Pathfinder.Result walk = Pathfinder.find(world, standingCell(world, gait.position), reach, PLAN_BUDGET, FallAllowance.NONE, 0, false);
            pinned = !walk.reachesGoal();
            closePath = walk.reachesGoal() ? new java.util.ArrayList<>(walk.path()) : new java.util.ArrayList<>();
        }
        return pinned;
    }

    /** Where it stood at its last step closing on a hostile, and whether it's closing on an archer by the walk to it. */
    private Position lastClose;
    private boolean byWalk;

    /** The walk to strike an archer from, as last planned ({@link #pinned}) - its cells left to step through. */
    private List<BlockPos> closePath = new java.util.ArrayList<>();

    /** The next cell of the walk to the archer it's closing on, the ones it's at dropped; empty with none planned. */
    private Optional<BlockPos> nextOnClosePath() {
        while (!closePath.isEmpty() && Math.hypot(closePath.getFirst().x() + 0.5 - gait.position.x(), closePath.getFirst().z() + 0.5 - gait.position.z()) < 0.7) {
            closePath.removeFirst();
        }
        return closePath.isEmpty() ? Optional.empty() : Optional.of(closePath.getFirst());
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
        return Contact.armed(kit);
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
}
