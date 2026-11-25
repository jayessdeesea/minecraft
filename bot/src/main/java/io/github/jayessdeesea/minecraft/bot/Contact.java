package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.ARCHER_BELOW;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.ATTACK_REACH;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.CONTACT_RADIUS;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.CREEPER_CLEAR;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.CREEPER_DEFUSE;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.CREEPER_STANDOFF_TICKS;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.HURT_HEALTH;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.SHOOT_BEYOND;

/**
 * A soldier's response to the hostiles in contact, pure: which of them it answers first, and what it does about that
 * one - fight, shoot, cover, get clear, or carry on with its order. {@link DeterministicSoldier} gathers the
 * {@link Situation}, answers the {@link Ground}'s questions from the world, and acts on the {@link Choice}; nothing
 * here moves the soldier or reads the world. Each response is what the {@code [Contact]} log line says, so every
 * combination of weapon, armour, health, hostile and ground has one answer that a table can pin
 * ({@code ContactEvalTest}).
 */
public final class Contact {

    private Contact() {
    }

    /** What the soldier does about the first hostile in contact, with the words its {@code [Contact]} line says. */
    public enum Response {
        /** Fights hand to hand, out of reach, and no walk between them: it can't come either, so it's no fight. */
        NO_WAY("carries on: no way between them on foot"),
        /** In the air overhead with no ground under it: neither strikes till it comes down. */
        OVERHEAD("carries on: overhead, out of a sword's reach"),
        /** Stands and looses an arrow at it. */
        SHOOTS("shoots"),
        /** A creeper outside its blast is gone past, the route kept: a soldier never closes on one. */
        PAST_CREEPER("carries on: a creeper outside its blast"),
        /** A creeper within its blast is got clear of - struck only when boxed in, to knock it back. */
        CLEAR_OF_BLAST("gets clear of the creeper's blast"),
        /** A creeper that has held it backing off past {@link DeterministicSoldier#CREEPER_STANDOFF_TICKS} is closed on. */
        CREEPER_STANDOFF("closes on the creeper holding it in a standoff"),
        /** One fought only in self-defense, with no weapon to fight it with: stepped away from. */
        BACKS_OFF("backs off: no weapon against one that fights only at hand"),
        /** One that fights hand to hand would win, and can be got away from: got clear of at a sprint, to heal. */
        GETS_CLEAR("gets clear: the exchange lost, hand to hand"),
        /** An archer out of contact, not shooting, that can't be closed on: let be, and the work goes on. */
        PAST_ARCHER("carries on: an archer out of contact, not shooting, with no way to close on it"),
        /** An archer that hasn't shot in {@link #QUIET_SECONDS} s, with no walk to it: it has no shot, and is no contact. */
        QUIET_ARCHER("carries on: an archer with no shot in " + QUIET_SECONDS + " s and no way to close on it"),
        /** An archer out-fights a bare hand from any range: out of its sight, and kept out. */
        COVER_UNARMED("takes cover: an archer, and no melee weapon"),
        /** The crossing under its fire and the blows to kill it outlast the soldier. */
        COVER_EXCHANGE_LOST("takes cover: an archer, the exchange lost"),
        /** An archer well below out-shoots a sword, whatever route goes down to it. */
        COVER_BELOW("takes cover: an archer " + ARCHER_BELOW + " or more below"),
        /** No walk reaches within a blow of the archer. */
        COVER_NO_ROUTE("takes cover: an archer with no route to strike it from"),
        /** Closes to arm's length and strikes as its weapon recharges. */
        FIGHTS("fights hand to hand"),
        /** As {@link #FIGHTS}, with its fists. */
        FIGHTS_BARE("fights bare-handed");

        private final String said;

        Response(String said) {
            this.said = said;
        }

        /** The response as the {@code [Contact]} line and {@code contactResponse()} say it. */
        public String said() {
            return said;
        }

        /** Whether the hostile is no contact at all: the soldier follows its order as if it weren't there. */
        public boolean letsBe() {
            return this == NO_WAY || this == OVERHEAD || this == QUIET_ARCHER;
        }

        /** Whether it gets out of an archer's sight. */
        public boolean takesCover() {
            return this == COVER_UNARMED || this == COVER_EXCHANGE_LOST || this == COVER_BELOW || this == COVER_NO_ROUTE;
        }
    }

    /**
     * What the decision reads of the soldier and its contact, this tick.
     *
     * @param at        where the soldier stands
     * @param health    its health as last reported, under 0 before any report
     * @param kit       what it carries, null before any report - taken as armed for melee, with no bow
     * @param threats   the hostiles in contact, the one to answer first ({@link #inOrder})
     * @param underFire whether it was hurt, or saw an arrow coming, within the last few seconds
     * @param begun     whether this contact is more than a second old: a fight begun is one to finish
     * @param closing   whether its steps at the hostile still gain ground - one that isn't closing strikes nothing
     * @param boxedIn   whether a withdrawal from this hostile found no step that got it further off
     * @param wet       whether it stands in water: it swims from an archer, slower than the archer follows
     * @param fit       whether its food and footing let it sprint
     * @param shieldUp  whether its shield is raised - no sprint under it, till the bow brings it down
     * @param clearing  whether it's already getting clear of the first hostile, a creeper it came within the blast of
     * @param standoff  ticks that creeper has held it backing off so far
     * @param quiet     whether this contact has gone {@link #QUIET_SECONDS} s with the soldier neither hurt nor seeing an arrow come
     */
    public record Situation(Position at, double health, Kit kit, List<TrackedEntity> threats, boolean underFire, boolean begun,
                            boolean closing, boolean boxedIn, boolean wet, boolean fit, boolean shieldUp, boolean clearing,
                            int standoff, boolean quiet) {

        /** What of this is so, in the words the table of evals names them by: a logged contact is a row. */
        public String facts() {
            return java.util.stream.Stream.of(underFire ? "under-fire" : "", begun ? "begun" : "", closing ? "" : "stalled",
                    boxedIn ? "boxed-in" : "", wet ? "wet" : "", fit ? "" : "unfit", shieldUp ? "shield-up" : "", clearing ? "clearing" : "",
                    quiet ? "quiet" : "")
                    .filter(f -> !f.isEmpty()).collect(java.util.stream.Collectors.joining(" "));
        }
    }

    /**
     * What the decision asks of the ground between the soldier and a hostile - asked only when the answer decides
     * something, since two of them cost a path search, which the soldier plans on a slower clock than its tick.
     */
    public interface Ground {

        /** Whether no walk - no step put, no block dug, as a hostile goes - reaches within a blow of {@code hostile}. */
        boolean noWalkTo(Position hostile);

        /** Whether {@code hostile} is in the air over the soldier, higher than a strike reaches, no ground under it. */
        boolean overhead(Position hostile);

        /** Whether no walk reaches within a blow of the archer at {@code archer}. */
        boolean pinned(Position archer);

        /** Whether an arrow can go at {@code hostile}: an arc reaches it, and no squadmate stands in the line. */
        boolean shotAt(Position hostile);
    }

    /**
     * The response, and whether the soldier went for its bow on the way to it - the shield comes down for the bow
     * whether or not the arrow goes.
     */
    public record Choice(Response response, boolean bowUp) {
    }

    /** Whether {@code kit} holds a melee weapon; an unknown kit is taken as armed. With hands or a bow an archer out-fights it. */
    public static boolean armed(Kit kit) {
        return kit == null || WeaponStats.melee(kit.weapon()) != WeaponStats.HAND;
    }

    /** Whether {@code kit} carries what shooting takes: a bow, and arrows for it. */
    public static boolean canShoot(Kit kit) {
        return kit != null && kit.bow() && kit.arrows() > 0;
    }

    /** The blocks a second it gets away at: none boxed in, a swim in water, else a sprint or a walk. */
    public static double pace(boolean boxedIn, boolean swims, boolean sprints) {
        return boxedIn ? 0 : swims ? Engagement.SWIM : sprints ? Engagement.SPRINT : Engagement.WALK;
    }

    /**
     * The hostiles {@code seen}, nearest first, as the soldier at {@code at} answers them. One fought only at hand - an
     * enderman - counts only in self-defense, {@code underFire} and within a step of a blow: a swing at one passing
     * provokes it. A creeper inside its blast's clearing comes first whatever is nearer, and an archer before a
     * hand-to-hand hostile still out of reach - else the answer flips between the two each tick, and neither is carried out.
     */
    public static List<TrackedEntity> inOrder(Position at, List<TrackedEntity> seen, boolean underFire) {
        List<TrackedEntity> threats = seen.stream().filter(t -> !t.fightsOnlyAtHand() || underFire
                && Math.hypot(t.position().x() - at.x(), t.position().z() - at.z()) <= ATTACK_REACH + 1).toList();
        Optional<TrackedEntity> blast = threats.stream()
                .filter(t -> t.explodes() && at.distanceTo(t.position()) <= CREEPER_CLEAR).findFirst();
        if (blast.isPresent() && !threats.getFirst().equals(blast.get())) {
            threats = first(threats, blast.get());
        }
        if (!threats.isEmpty() && !threats.getFirst().shoots() && !threats.getFirst().explodes()
                && at.distanceTo(threats.getFirst().position()) > ATTACK_REACH) {
            Optional<TrackedEntity> archer = threats.stream().filter(TrackedEntity::shoots).findFirst();
            if (archer.isPresent()) {
                threats = first(threats, archer.get());
            }
        }
        return threats;
    }

    private static List<TrackedEntity> first(List<TrackedEntity> threats, TrackedEntity one) {
        List<TrackedEntity> first = new ArrayList<>(threats);
        first.remove(one);
        first.addFirst(one);
        return List.copyOf(first);
    }

    /** How near an archer a soldier with no way clear of it assaults: inside this the archer itself backs off to shoot. */
    public static final double NEAR_AMBUSH = 4;
    /**
     * How long an archer that hasn't hurt the soldier nor loosed an arrow it saw is taken to have no shot at it. Held in
     * cover from one no walk reaches, the soldier is held for good: the archer shuffles in and out of the contact
     * radius, the answer goes between cover and carrying on with it, and the soldier's squad stays in contact.
     */
    public static final int QUIET_SECONDS = 30;

    /**
     * What the soldier does about the first of {@code s.threats()}, which must not be empty. The rules, first that
     * holds:
     * <ol>
     * <li>One that doesn't shoot, out of reach with no walk to it, or in the air overhead (not a creeper): no contact.
     * <li>With a bow and arrows, outside a creeper's blast: shoot what is beyond {@link DeterministicSoldier#SHOOT_BEYOND} - every block
     *     closed under fire is a block shot back over - or anything with no melee weapon, or an archer it can't walk to.
     * <li>A creeper: gone past outside its blast, got clear of within it, closed on once it has held the soldier there.
     * <li>One fought only at hand, and no weapon: backed off from.
     * <li>One that fights hand to hand: fought unless the exchange is lost ({@link Engagement#melee}).
     * <li>An archer: no contact once it hasn't shot in {@link #QUIET_SECONDS} s, no walk reaches it and the soldier isn't hurt; let be when
     *     it's out of contact, not shooting, the soldier unhurt and no way to close on it;
     *     covered from with no melee weapon, the exchange lost ({@link Engagement#archer}), it well below, or no walk
     *     to it; else closed on and fought. Boxed in within {@link #NEAR_AMBUSH} of it: fought, with what it has.
     * </ol>
     */
    public static Choice respond(Situation s, Ground ground) {
        TrackedEntity nearest = s.threats().getFirst();
        Position threat = nearest.position();
        double range = s.at().distanceTo(threat);
        if (!nearest.shoots() && range > ATTACK_REACH && ground.noWalkTo(threat)) {
            return new Choice(Response.NO_WAY, false);
        }
        if (!nearest.shoots() && !nearest.explodes() && ground.overhead(threat)) {
            return new Choice(Response.OVERHEAD, false);
        }
        boolean armed = armed(s.kit());
        boolean bowUp = (!nearest.explodes() || range > CREEPER_CLEAR) && canShoot(s.kit())
                && (range > SHOOT_BEYOND || !armed || nearest.shoots() && ground.pinned(threat));
        if (bowUp && ground.shotAt(threat)) {
            return new Choice(Response.SHOOTS, true);
        }
        return new Choice(withoutTheBow(s, ground, nearest, range, armed, s.fit() && !(s.shieldUp() && !bowUp)), bowUp);
    }

    /** The response once no arrow goes; {@code sprints} is whether it can sprint, its shield down if it went for the bow. */
    private static Response withoutTheBow(Situation s, Ground ground, TrackedEntity nearest, double range, boolean armed,
                                          boolean sprints) {
        Position threat = nearest.position();
        if (nearest.explodes()) {
            // Past where its fuse goes out it's no contact; outside its blast it's gone past unless the soldier is
            // already getting clear of it, which goes on till the fuse is out.
            if (range > CREEPER_DEFUSE || range > CREEPER_CLEAR && !s.clearing()) {
                return Response.PAST_CREEPER;
            }
            return s.standoff() + 1 > CREEPER_STANDOFF_TICKS ? Response.CREEPER_STANDOFF : Response.CLEAR_OF_BLAST;
        }
        if (nearest.fightsOnlyAtHand() && !armed) {
            return Response.BACKS_OFF;
        }
        if (!nearest.shoots()) {
            Engagement.Kind exchange = Engagement.melee(s.health(), s.kit(), range, s.begun(), pace(s.boxedIn(), false, sprints));
            return exchange != Engagement.Kind.ENGAGE ? Response.GETS_CLEAR : armed ? Response.FIGHTS : Response.FIGHTS_BARE;
        }
        if (s.quiet() && !s.underFire() && s.health() >= HURT_HEALTH && ground.pinned(threat)) {
            return Response.QUIET_ARCHER;
        }
        boolean below = threat.y() < s.at().y() - ARCHER_BELOW;
        if (armed && range > CONTACT_RADIUS && !s.underFire() && s.health() >= HURT_HEALTH && (below || ground.pinned(threat))) {
            return Response.PAST_ARCHER;
        }
        // In its kill zone with no way out of it, the soldier assaults (ATP 3-21.8, react to a near ambush): cover it
        // can't get to is no cover, and one that stands there is shot till it's dead.
        // Not one out of a blow's reach that no walk gets to: there is nothing to assault by.
        if (s.boxedIn() && range <= NEAR_AMBUSH && (range <= ATTACK_REACH || !ground.pinned(threat))) {
            return armed ? Response.FIGHTS : Response.FIGHTS_BARE;
        }
        boolean lost = range > ATTACK_REACH && Engagement.archer(s.health(), s.kit(), range, s.begun(),
                pace(s.boxedIn(), s.wet(), sprints), s.closing()) != Engagement.Kind.ENGAGE;
        if (!armed) {
            return Response.COVER_UNARMED;
        }
        if (lost) {
            return Response.COVER_EXCHANGE_LOST;
        }
        if (below) {
            return Response.COVER_BELOW;
        }
        return ground.pinned(threat) ? Response.COVER_NO_ROUTE : Response.FIGHTS;
    }
}
