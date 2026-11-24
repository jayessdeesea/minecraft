package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.Contact.Response;
import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.bot.Contact.Response.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The contact decision's answers, one row a combination (operator, 2026-11-24: evals for all forms of combat - weapons,
 * terrain, armour, enemies - and each new combination found added). Each row is a soldier's kit and health, one hostile
 * and where it is, what the ground says, and the response {@link Contact#respond} gives: pure, so the whole table runs
 * in milliseconds. A row pins what the code answers, which is not a claim that the answer is right - what it should be
 * is backlog/engagement-rules.md's, and a change there flips rows here, in the same commit.
 *
 * <p>To add a row (docs/conventions.md, "A contact met live is a row first"): copy a line of {@link #rows}, set its
 * columns from the {@code [Contact]} line, run, and put down what it answers.
 *
 * <p>Columns:
 * <ul>
 * <li><b>weapon</b>: {@code none}, a melee weapon's item name, {@code bow} for a bow with 16 arrows, {@code bow0} for
 *     one with none; joined with {@code +} ({@code stone_sword+bow}, {@code stone_sword+shield}); {@code unknown} for
 *     a kit not yet reported.
 * <li><b>armour</b>: pieces worn, 0 to 4 - all the kit counts; leather and iron aren't told apart.
 * <li><b>health</b>: 20 whole; under 0, not yet reported.
 * <li><b>hostile, across, up</b>: its registry name, blocks off on the level, blocks above (below, negative).
 * <li><b>facts</b>: what the shell tells the decision, space-separated, none of them true unless named -
 *     {@code no-walk} no walk between them (a ledge, a pillar, a ravine, a pond's far bank); {@code pinned} the same,
 *     asked of an archer; {@code overhead} in the air with no ground under it; {@code no-shot} no arc reaches or a
 *     squadmate is in the line; {@code under-fire} hurt or shot at in the last five seconds; {@code begun} in this
 *     contact over a second; {@code stalled} its steps at the hostile gain no ground; {@code boxed-in} a withdrawal
 *     found no way off (a tunnel one wide, a wall behind); {@code wet} the soldier in water; {@code unfit} too hungry
 *     to sprint, or afloat; {@code shield-up}; {@code clearing} already getting clear of this creeper;
 *     {@code standoff=N} ticks this creeper has held it backing off.
 * </ul>
 * Not columns, because the decision doesn't read them: the time of day, light, the hostile being in water, how many
 * hostiles or squadmates are near (see {@link #firstAnswered} for which of several is answered).
 */
class ContactEvalTest {

    record Row(String name, String weapon, int armour, double health, String hostile, double across, double up, String facts,
               Response expected) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Row r(String name, String weapon, int armour, double health, String hostile, double across, double up,
                         String facts, Response expected) {
        return new Row(name, weapon, armour, health, hostile, across, up, facts, expected);
    }

    static List<Row> rows() {
        return List.of(
                // --- One that fights hand to hand, walking: the exchange (Engagement.melee) reckons each as a zombie.
                r("bare hands, whole, a zombie at hand", "none", 0, 20, "zombie", 2, 0, "", FIGHTS_BARE),
                r("bare hands, whole, a zombie 6 off", "none", 0, 20, "zombie", 6, 0, "", FIGHTS_BARE),
                r("bare hands, hurt, a zombie at hand: a sprint gets clear", "none", 0, 8, "zombie", 2, 0, "", GETS_CLEAR),
                r("bare hands, hurt, a zombie at hand, begun", "none", 0, 8, "zombie", 2, 0, "begun", GETS_CLEAR),
                r("bare hands, hurt, too hungry to sprint: fought", "none", 0, 8, "zombie", 2, 0, "unfit", FIGHTS_BARE),
                r("bare hands, hurt, boxed in: fought", "none", 0, 8, "zombie", 2, 0, "boxed-in", FIGHTS_BARE),
                r("bare hands, hurt, shield up: no sprint, fought", "none+shield", 0, 8, "zombie", 2, 0, "shield-up", FIGHTS_BARE),
                r("bare hands, at 4, a zombie on it: can't get clear alive", "none", 0, 4, "zombie", 2, 0, "", FIGHTS_BARE),
                r("bare hands, at 4, a zombie 6 off: gets clear", "none", 0, 4, "zombie", 6, 0, "", GETS_CLEAR),
                r("bare hands, no armour, at 10, a zombie at hand", "none", 0, 10, "zombie", 2, 0, "", GETS_CLEAR),
                r("bare hands, 4 armour, at 10, a zombie at hand: armour buys it", "none", 4, 10, "zombie", 2, 0, "", FIGHTS_BARE),
                r("bare hands, 4 armour, at 8, a zombie at hand", "none", 4, 8, "zombie", 2, 0, "", GETS_CLEAR),
                r("wooden sword, whole, a zombie at hand", "wooden_sword", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("stone sword, whole, a zombie at hand", "stone_sword", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("iron sword, whole, a zombie at hand", "iron_sword", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("stone axe, whole, a zombie at hand", "stone_axe", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("stone sword, whole, a zombie 6 off", "stone_sword", 0, 20, "zombie", 6, 0, "", FIGHTS),
                r("stone sword, at 8, a zombie at hand", "stone_sword", 0, 8, "zombie", 2, 0, "", FIGHTS),
                r("stone axe, at 8, a zombie at hand: its slow swing loses", "stone_axe", 0, 8, "zombie", 2, 0, "", GETS_CLEAR),
                r("wooden sword, at 8, a zombie at hand", "wooden_sword", 0, 8, "zombie", 2, 0, "", GETS_CLEAR),
                r("stone sword, at 6, a zombie on it: can't get clear alive", "stone_sword", 0, 6, "zombie", 2, 0, "", FIGHTS),
                r("stone sword, at 6, a zombie 6 off: gets clear", "stone_sword", 0, 6, "zombie", 6, 0, "", GETS_CLEAR),
                r("stone sword, 4 armour, at 6, a zombie 6 off: armour buys it", "stone_sword", 4, 6, "zombie", 6, 0, "", FIGHTS),
                r("stone sword, 1 armour, at 6, a zombie 6 off", "stone_sword", 1, 6, "zombie", 6, 0, "", GETS_CLEAR),
                r("stone sword, at 1, a zombie 6 off", "stone_sword", 0, 1, "zombie", 6, 0, "", GETS_CLEAR),
                r("stone sword, health not reported, a zombie at hand", "stone_sword", 0, -1, "zombie", 2, 0, "", FIGHTS),
                r("kit not reported, whole, a zombie at hand", "unknown", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("kit not reported, hurt, a zombie at hand: reckoned bare-handed", "unknown", 0, 8, "zombie", 2, 0, "", GETS_CLEAR),
                r("stone sword and shield, whole, a zombie at hand", "stone_sword+shield", 0, 20, "zombie", 2, 0, "", FIGHTS),
                r("stone sword, a husk at hand", "stone_sword", 0, 20, "husk", 2, 0, "", FIGHTS),
                r("stone sword, a spider at hand", "stone_sword", 0, 20, "spider", 2, 0, "", FIGHTS),
                r("stone sword, a drowned at hand, the soldier in water", "stone_sword", 0, 20, "drowned", 2, 0, "wet", FIGHTS),
                r("stone sword, at 6, a drowned 6 off, the soldier in water", "stone_sword", 0, 6, "drowned", 6, 0, "wet", GETS_CLEAR),
                r("stone sword, a ravager at hand: reckoned a zombie", "stone_sword", 0, 20, "ravager", 2, 0, "", FIGHTS),
                r("bare hands, a ravager at hand: reckoned a zombie", "none", 0, 20, "ravager", 2, 0, "", FIGHTS_BARE),
                r("stone sword, a witch 6 off: reckoned a zombie, closed on", "stone_sword", 0, 20, "witch", 6, 0, "", FIGHTS),
                r("bare hands, a witch 6 off", "none", 0, 20, "witch", 6, 0, "", FIGHTS_BARE),
                // --- The ground, against one that fights hand to hand.
                r("a zombie on a ledge, no walk to it", "stone_sword", 0, 20, "zombie", 4, 3, "no-walk", NO_WAY),
                r("a zombie across a ravine", "stone_sword", 0, 20, "zombie", 6, 0, "no-walk", NO_WAY),
                r("a zombie across a ravine, bare hands, hurt", "none", 0, 4, "zombie", 6, 0, "no-walk", NO_WAY),
                r("a zombie within reach, though no walk reaches it: fought", "stone_sword", 0, 20, "zombie", 2, 2, "no-walk", FIGHTS),
                r("a zombie on a ledge, a bow in hand: let be, not shot", "stone_sword+bow", 0, 20, "zombie", 6, 4, "no-walk", NO_WAY),
                r("a zombie on a ledge 4 up that it can walk down from", "stone_sword", 0, 20, "zombie", 4, 4, "", FIGHTS),
                r("a phantom overhead", "stone_sword", 0, 20, "phantom", 1, 6, "overhead", OVERHEAD),
                r("a phantom overhead, a bow in hand: let be, not shot", "stone_sword+bow", 0, 20, "phantom", 1, 6, "overhead", OVERHEAD),
                r("a phantom overhead, bare hands, hurt", "none", 0, 4, "phantom", 1, 6, "overhead", OVERHEAD),
                r("a phantom come down to a blow's reach", "stone_sword", 0, 20, "phantom", 1, 2, "", FIGHTS),
                r("a phantom overhead and no walk to it", "stone_sword", 0, 20, "phantom", 1, 6, "overhead no-walk", NO_WAY),
                r("a vex overhead", "stone_sword", 0, 20, "vex", 2, 5, "overhead", OVERHEAD),
                r("a zombie 6 off in a tunnel one wide, the soldier boxed in", "stone_sword", 0, 6, "zombie", 6, 0, "boxed-in", FIGHTS),
                // --- The bow, against one that fights hand to hand.
                r("sword and bow, a zombie 7 off: shot", "stone_sword+bow", 0, 20, "zombie", 7, 0, "", SHOOTS),
                r("sword and bow, a zombie 4 off: the sword", "stone_sword+bow", 0, 20, "zombie", 4, 0, "", FIGHTS),
                r("sword and bow, a zombie 7 off, a squadmate in the line", "stone_sword+bow", 0, 20, "zombie", 7, 0, "no-shot", FIGHTS),
                r("sword and bow, no arrows, a zombie 7 off", "stone_sword+bow0", 0, 20, "zombie", 7, 0, "", FIGHTS),
                r("a bow alone, a zombie at hand: shot", "bow", 0, 20, "zombie", 2, 0, "", SHOOTS),
                r("a bow alone, a zombie at hand, no shot: fists", "bow", 0, 20, "zombie", 2, 0, "no-shot", FIGHTS_BARE),
                r("a bow alone, no arrows, a zombie 7 off", "bow0", 0, 20, "zombie", 7, 0, "", FIGHTS_BARE),
                r("a bow alone, hurt, a zombie 7 off: shot", "bow", 0, 4, "zombie", 7, 0, "", SHOOTS),
                // --- A creeper: no weapon, armour or health changes the answer; a bow does, from outside its blast.
                r("no armour, no sword, a creeper 3 off", "none", 0, 20, "creeper", 3, 0, "", CLEAR_OF_BLAST),
                r("no armour, no sword, hurt, a creeper 3 off", "none", 0, 4, "creeper", 3, 0, "", CLEAR_OF_BLAST),
                r("stone sword, a creeper 3 off", "stone_sword", 0, 20, "creeper", 3, 0, "", CLEAR_OF_BLAST),
                r("iron sword, 4 armour, a creeper 3 off", "iron_sword", 4, 20, "creeper", 3, 0, "", CLEAR_OF_BLAST),
                r("sword and shield up, a creeper 3 off", "stone_sword+shield", 0, 20, "creeper", 3, 0, "shield-up", CLEAR_OF_BLAST),
                r("a creeper at the edge of its clearing, 4.5 off", "stone_sword", 0, 20, "creeper", 4.5, 0, "", CLEAR_OF_BLAST),
                r("a creeper 6 off, never come near: gone past", "stone_sword", 0, 20, "creeper", 6, 0, "", PAST_CREEPER),
                r("no armour, no sword, a creeper 6 off: gone past", "none", 0, 20, "creeper", 6, 0, "", PAST_CREEPER),
                r("a creeper 6 off, getting clear of it: on till its fuse is out", "stone_sword", 0, 20, "creeper", 6, 0, "clearing",
                        CLEAR_OF_BLAST),
                r("a creeper 8 off, getting clear of it: clear", "stone_sword", 0, 20, "creeper", 8, 0, "clearing", PAST_CREEPER),
                r("a creeper 3 off that has held it ten seconds", "stone_sword", 0, 20, "creeper", 3, 0, "standoff=200", CREEPER_STANDOFF),
                r("a creeper 3 off, a tick short of ten seconds", "stone_sword", 0, 20, "creeper", 3, 0, "standoff=199", CLEAR_OF_BLAST),
                r("bare hands, a creeper that has held it ten seconds", "none", 0, 20, "creeper", 3, 0, "standoff=200", CREEPER_STANDOFF),
                r("a creeper 6 off across a ravine", "stone_sword", 0, 20, "creeper", 6, 0, "no-walk", NO_WAY),
                r("a creeper 4 off on a ledge, no walk between", "stone_sword", 0, 20, "creeper", 3, 3, "no-walk", NO_WAY),
                r("a creeper 2 off, though no walk reaches it", "stone_sword", 0, 20, "creeper", 2, 0, "no-walk", CLEAR_OF_BLAST),
                r("a creeper dropping from overhead: not let be", "stone_sword", 0, 20, "creeper", 1, 4, "overhead", CLEAR_OF_BLAST),
                r("sword and bow, a creeper 6 off: shot", "stone_sword+bow", 0, 20, "creeper", 6, 0, "", SHOOTS),
                r("a bow alone, a creeper 6 off: shot", "bow", 0, 20, "creeper", 6, 0, "", SHOOTS),
                r("sword and bow, a creeper 4 off: inside its blast, got clear of", "stone_sword+bow", 0, 20, "creeper", 4, 0, "",
                        CLEAR_OF_BLAST),
                r("a bow alone, a creeper 5 off: shot", "bow", 0, 20, "creeper", 5, 0, "", SHOOTS),
                r("sword and bow, a creeper 5 off: not beyond the bow's line", "stone_sword+bow", 0, 20, "creeper", 5, 0, "", PAST_CREEPER),
                r("sword and bow, a creeper 6 off, no shot", "stone_sword+bow", 0, 20, "creeper", 6, 0, "no-shot", PAST_CREEPER),
                // --- An enderman, in contact only when the soldier was just hit and it's at hand (firstAnswered).
                r("bare hands, an enderman at hand", "none", 0, 20, "enderman", 2, 0, "under-fire", BACKS_OFF),
                r("bare hands, hurt, an enderman at hand", "none", 0, 4, "enderman", 2, 0, "under-fire", BACKS_OFF),
                r("stone sword, an enderman at hand: reckoned a zombie", "stone_sword", 0, 20, "enderman", 2, 0, "under-fire", FIGHTS),
                r("stone sword, at 6, an enderman on it", "stone_sword", 0, 6, "enderman", 2, 0, "under-fire", FIGHTS),
                r("wooden sword, at 8, an enderman at hand", "wooden_sword", 0, 8, "enderman", 2, 0, "under-fire", GETS_CLEAR),
                r("a bow alone, an enderman at hand: shot", "bow", 0, 20, "enderman", 2, 0, "under-fire", SHOOTS),
                r("a bow alone, no arrows, an enderman at hand", "bow0", 0, 20, "enderman", 2, 0, "under-fire", BACKS_OFF),
                // --- An archer: the exchange (Engagement.archer) reckons each as a skeleton.
                r("bare hands, a skeleton 7 off", "none", 0, 20, "skeleton", 7, 0, "", COVER_UNARMED),
                r("bare hands, a skeleton at hand", "none", 0, 20, "skeleton", 2, 0, "", COVER_UNARMED),
                r("bare hands, 4 armour, a skeleton 7 off", "none", 4, 20, "skeleton", 7, 0, "", COVER_UNARMED),
                r("a bow with no arrows, a skeleton 7 off", "bow0", 0, 20, "skeleton", 7, 0, "", COVER_UNARMED),
                r("stone sword, whole, a skeleton 7 off: closed on", "stone_sword", 0, 20, "skeleton", 7, 0, "", FIGHTS),
                r("wooden sword, whole, a skeleton 7 off", "wooden_sword", 0, 20, "skeleton", 7, 0, "", FIGHTS),
                r("stone axe, whole, a skeleton 7 off", "stone_axe", 0, 20, "skeleton", 7, 0, "", FIGHTS),
                r("iron sword, whole, a skeleton 12 off", "iron_sword", 0, 20, "skeleton", 12, 0, "", FIGHTS),
                r("stone sword, at 12, a skeleton 7 off", "stone_sword", 0, 12, "skeleton", 7, 0, "", FIGHTS),
                r("wooden sword, at 12, a skeleton 7 off: a slower kill loses", "wooden_sword", 0, 12, "skeleton", 7, 0, "",
                        COVER_EXCHANGE_LOST),
                r("stone sword, at 8, a skeleton 7 off", "stone_sword", 0, 8, "skeleton", 7, 0, "", COVER_EXCHANGE_LOST),
                r("stone sword, 4 armour, at 8, a skeleton 7 off: armour buys it", "stone_sword", 4, 8, "skeleton", 7, 0, "", FIGHTS),
                r("stone sword, 2 armour, at 8, a skeleton 7 off", "stone_sword", 2, 8, "skeleton", 7, 0, "", COVER_EXCHANGE_LOST),
                r("stone sword, at 8, a skeleton 7 off, begun: finished", "stone_sword", 0, 8, "skeleton", 7, 0, "begun", FIGHTS),
                r("stone sword, at 6, a skeleton 7 off, begun: can't get clear alive", "stone_sword", 0, 6, "skeleton", 7, 0, "begun", FIGHTS),
                r("stone sword, at 6, a skeleton 12 off, begun: gets clear", "stone_sword", 0, 6, "skeleton", 12, 0, "begun",
                        COVER_EXCHANGE_LOST),
                r("the same, shield up: at a walk it doesn't get clear", "stone_sword+shield", 0, 6, "skeleton", 12, 0, "begun shield-up",
                        FIGHTS),
                r("the same, in water: a swim doesn't get clear", "stone_sword", 0, 6, "skeleton", 12, 0, "begun wet", FIGHTS),
                r("the same, boxed in", "stone_sword", 0, 6, "skeleton", 12, 0, "begun boxed-in", FIGHTS),
                r("the same, shield up, gone for its bow with no shot: the shield down, it gets clear", "stone_sword+bow+shield", 0, 6,
                        "skeleton", 12, 0, "begun shield-up no-shot", COVER_EXCHANGE_LOST),
                r("stone sword, at 8, a skeleton at hand: struck", "stone_sword", 0, 8, "skeleton", 2, 0, "", FIGHTS),
                r("stone sword, at 1, a skeleton at hand: struck", "stone_sword", 0, 1, "skeleton", 2, 0, "", FIGHTS),
                r("stone sword, whole, its steps at a skeleton gaining nothing", "stone_sword", 0, 20, "skeleton", 7, 0, "stalled",
                        COVER_EXCHANGE_LOST),
                r("stone sword, health not reported, a skeleton 7 off", "stone_sword", 0, -1, "skeleton", 7, 0, "", FIGHTS),
                r("kit not reported, whole, a skeleton 7 off: taken as armed, closed on", "unknown", 0, 20, "skeleton", 7, 0, "", FIGHTS),
                r("kit not reported, at 16, a skeleton 7 off: its blows reckoned a fist's", "unknown", 0, 16, "skeleton", 7, 0, "",
                        COVER_EXCHANGE_LOST),
                r("stone sword, at 16, a skeleton 7 off", "stone_sword", 0, 16, "skeleton", 7, 0, "", FIGHTS),
                r("stone sword and shield, whole, a skeleton 7 off", "stone_sword+shield", 0, 20, "skeleton", 7, 0, "shield-up", FIGHTS),
                r("stone sword, whole, a skeleton 7 off, the soldier in water", "stone_sword", 0, 20, "skeleton", 7, 0, "wet", FIGHTS),
                r("stone sword, a pillager 7 off: reckoned a skeleton", "stone_sword", 0, 20, "pillager", 7, 0, "", FIGHTS),
                r("bare hands, a pillager 7 off", "none", 0, 20, "pillager", 7, 0, "", COVER_UNARMED),
                r("stone sword, at 8, a stray 7 off", "stone_sword", 0, 8, "stray", 7, 0, "", COVER_EXCHANGE_LOST),
                r("stone sword, at 8, a bogged 7 off", "stone_sword", 0, 8, "bogged", 7, 0, "", COVER_EXCHANGE_LOST),
                // --- The ground, against an archer.
                r("a skeleton 7 off on a ledge, no walk to it", "stone_sword", 0, 20, "skeleton", 6, 3, "pinned", COVER_NO_ROUTE),
                r("a skeleton 12 off across a ravine, not shooting: let be", "stone_sword", 0, 20, "skeleton", 12, 0, "pinned", PAST_ARCHER),
                r("the same, shooting", "stone_sword", 0, 20, "skeleton", 12, 0, "pinned under-fire", COVER_NO_ROUTE),
                r("the same, not shooting, at 10 health: let be", "stone_sword", 4, 10, "skeleton", 12, 0, "pinned", PAST_ARCHER),
                r("the same, not shooting, under 10 health: covered from", "stone_sword", 4, 9.9, "skeleton", 12, 0, "pinned",
                        COVER_NO_ROUTE),
                r("the same, health not reported", "stone_sword", 0, -1, "skeleton", 12, 0, "pinned", COVER_NO_ROUTE),
                r("a skeleton 12 off across a ravine, hurt: the exchange says first", "stone_sword", 0, 8, "skeleton", 12, 0, "pinned",
                        COVER_EXCHANGE_LOST),
                r("a skeleton 5 below, in contact", "stone_sword", 0, 20, "skeleton", 6, -5, "", COVER_BELOW),
                r("a skeleton 5 below, out of contact, not shooting: let be", "stone_sword", 0, 20, "skeleton", 10, -5, "", PAST_ARCHER),
                r("a skeleton 5 below, out of contact, shooting", "stone_sword", 0, 20, "skeleton", 10, -5, "under-fire", COVER_BELOW),
                r("a skeleton 4 below: not well below, closed on", "stone_sword", 0, 20, "skeleton", 6, -4, "", FIGHTS),
                r("a skeleton 5 above that it can walk up to", "stone_sword", 0, 20, "skeleton", 6, 5, "", FIGHTS),
                r("bare hands, a skeleton across a ravine", "none", 0, 20, "skeleton", 12, 0, "pinned", COVER_UNARMED),
                r("a pillager afloat 12 off it can swim to", "stone_sword", 0, 20, "pillager", 12, 0, "", FIGHTS),
                // --- The bow, against an archer.
                r("sword and bow, a skeleton 7 off: shot", "stone_sword+bow", 0, 20, "skeleton", 7, 0, "", SHOOTS),
                r("sword and bow, hurt, a skeleton 7 off: shot", "stone_sword+bow", 0, 4, "skeleton", 7, 0, "", SHOOTS),
                r("sword and bow, a skeleton 4 off: the sword", "stone_sword+bow", 0, 20, "skeleton", 4, 0, "", FIGHTS),
                r("sword and bow, a skeleton 4 off on a ledge: shot", "stone_sword+bow", 0, 20, "skeleton", 3, 3, "pinned", SHOOTS),
                r("sword and bow, a skeleton 12 off across a ravine: shot", "stone_sword+bow", 0, 20, "skeleton", 12, 0, "pinned", SHOOTS),
                r("sword and bow, a skeleton 7 off, a squadmate in the line: closed on", "stone_sword+bow", 0, 20, "skeleton", 7, 0,
                        "no-shot", FIGHTS),
                r("sword and bow, no arrows, a skeleton 7 off", "stone_sword+bow0", 0, 20, "skeleton", 7, 0, "", FIGHTS),
                r("a bow alone, a skeleton 4 off: shot", "bow", 0, 20, "skeleton", 4, 0, "", SHOOTS),
                r("a bow alone, a skeleton 14 off: shot", "bow", 0, 20, "skeleton", 14, 0, "", SHOOTS),
                r("a bow alone, a skeleton 7 off, no shot: cover", "bow", 0, 20, "skeleton", 7, 0, "no-shot", COVER_UNARMED),
                r("a bow alone, a skeleton 5 below: shot", "bow", 0, 20, "skeleton", 6, -5, "", SHOOTS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void theResponseIsPinned(Row row) {
        assertEquals(row.expected(), respond(row.weapon(), row.armour(), row.health(), row.hostile(), row.across(), row.up(),
                row.facts()), row.name());
    }

    /** How a hostile fights, as the decision tells them apart - four kinds, whatever else the game gives it. */
    private static String kind(String hostile) {
        return Set.of("skeleton", "stray", "bogged", "pillager").contains(hostile) ? "archer"
                : hostile.equals("creeper") ? "creeper" : hostile.equals("enderman") ? "enderman" : "melee";
    }

    /** Every hostile the registry lists, in four situations: its kind is all the decision knows of it. */
    static Stream<Row> everyHostile() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                HostileMobRegistry.class.getResourceAsStream("hostile-entity-types-777.txt"), StandardCharsets.UTF_8))) {
            for (String line : in.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList()) {
                String name = line.substring(line.indexOf(' ') + 1).trim();
                String kind = kind(name);
                rows.add(r(name + ": a stone sword, at hand", "stone_sword", 0, 20, name, 2, 0, "under-fire",
                        kind.equals("creeper") ? CLEAR_OF_BLAST : FIGHTS));
                rows.add(r(name + ": bare hands, 6 off", "none", 0, 20, name, 6, 0, "", switch (kind) {
                    case "archer" -> COVER_UNARMED;
                    case "creeper" -> PAST_CREEPER;
                    case "enderman" -> BACKS_OFF;
                    default -> FIGHTS_BARE;
                }));
                rows.add(r(name + ": bare hands, at 4, 6 off", "none", 0, 4, name, 6, 0, "", switch (kind) {
                    case "archer" -> COVER_UNARMED;
                    case "creeper" -> PAST_CREEPER;
                    case "enderman" -> BACKS_OFF;
                    default -> GETS_CLEAR;
                }));
                rows.add(r(name + ": a stone sword, 6 off, no walk between", "stone_sword", 0, 20, name, 6, 0, "no-walk pinned",
                        kind.equals("archer") ? COVER_NO_ROUTE : NO_WAY));
            }
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyHostile")
    void everyHostileIsAnsweredByItsKind(Row row) {
        assertEquals(row.expected(), respond(row.weapon(), row.armour(), row.health(), row.hostile(), row.across(), row.up(),
                row.facts()), row.name());
    }

    /** Several hostiles, nearest first as "name@blocks off": which is answered. */
    record Several(String name, String hostiles, boolean underFire, String first) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Several> firstAnswered() {
        return List.of(
                new Several("a zombie at hand before an archer", "zombie@2 skeleton@7", false, "zombie"),
                new Several("an archer before a zombie out of reach", "zombie@6 skeleton@7", false, "skeleton"),
                new Several("the nearest of three zombies", "zombie@5 husk@6 spider@7", false, "zombie"),
                new Several("a creeper in its clearing before an archer nearer", "skeleton@1 creeper@1.5", false, "creeper"),
                new Several("a creeper in its clearing before a zombie nearer", "zombie@2 creeper@4", false, "creeper"),
                new Several("a zombie at hand before a creeper outside its clearing", "zombie@2 creeper@6", false, "zombie"),
                new Several("an archer before a zombie out of reach, a creeper past both", "zombie@5 creeper@6 skeleton@7", false,
                        "skeleton"),
                new Several("a creeper nearest outside its clearing, an archer behind", "creeper@6 skeleton@7", false, "creeper"),
                new Several("an enderman passing is no contact", "enderman@2 zombie@5", false, "zombie"),
                new Several("an enderman at hand, the soldier just hit", "enderman@2 zombie@5", true, "enderman"),
                new Several("an enderman 5 off, the soldier just hit: no contact", "enderman@5", true, "none"),
                new Several("an enderman alone, not hit: no contact", "enderman@2", false, "none"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstAnswered")
    void theFirstAnsweredIsPinned(Several row) {
        List<TrackedEntity> seen = new ArrayList<>();
        for (String one : row.hostiles().split(" ")) {
            String[] parts = one.split("@");
            seen.add(new TrackedEntity(seen.size() + 1, HostileMobRegistry.typeIdOf(parts[0]),
                    new Position(Double.parseDouble(parts[1]), 64, 0)));
        }
        List<TrackedEntity> ordered = Contact.inOrder(new Position(0, 64, 0), seen, row.underFire());
        assertEquals(row.first(), ordered.isEmpty() ? "none" : HostileMobRegistry.nameOf(ordered.getFirst().typeId()), row.name());
    }

    /** The log's words, which evals of recorded contacts match on: a change here is a change to every log read since. */
    @Test
    void eachResponseSaysWhatTheLogSays() {
        assertEquals(List.of(
                "carries on: no way between them on foot",
                "carries on: overhead, out of a sword's reach",
                "shoots",
                "carries on: a creeper outside its blast",
                "gets clear of the creeper's blast",
                "closes on the creeper holding it in a standoff",
                "backs off: no weapon against one that fights only at hand",
                "gets clear: the exchange lost, hand to hand",
                "carries on: an archer out of contact, not shooting, with no way to close on it",
                "takes cover: an archer, and no melee weapon",
                "takes cover: an archer, the exchange lost",
                "takes cover: an archer 4.0 or more below",
                "takes cover: an archer with no route to strike it from",
                "fights hand to hand",
                "fights bare-handed"), Stream.of(Response.values()).map(Response::said).toList());
    }

    private static Response respond(String weapon, int armour, double health, String hostile, double across, double up, String facts) {
        Set<String> said = Set.of(facts.isBlank() ? new String[0] : facts.split(" "));
        int standoff = said.stream().filter(f -> f.startsWith("standoff=")).mapToInt(f -> Integer.parseInt(f.substring(9))).findFirst()
                .orElse(0);
        Position at = new Position(0, 64, 0);
        TrackedEntity one = new TrackedEntity(1, HostileMobRegistry.typeIdOf(hostile), new Position(across, 64 + up, 0));
        Contact.Situation situation = new Contact.Situation(at, health, kit(weapon, armour), List.of(one), said.contains("under-fire"),
                said.contains("begun"), !said.contains("stalled"), said.contains("boxed-in"), said.contains("wet"),
                !said.contains("unfit"), said.contains("shield-up"), said.contains("clearing"), standoff);
        return Contact.respond(situation, new Contact.Ground() {
            @Override
            public boolean noWalkTo(Position p) {
                return said.contains("no-walk");
            }

            @Override
            public boolean overhead(Position p) {
                return said.contains("overhead");
            }

            @Override
            public boolean pinned(Position p) {
                return said.contains("pinned");
            }

            @Override
            public boolean shotAt(Position p) {
                return !said.contains("no-shot");
            }
        }).response();
    }

    private static Kit kit(String weapon, int armour) {
        if (weapon.equals("unknown")) {
            return null;
        }
        List<String> parts = List.of(weapon.split("\\+"));
        Optional<String> melee = parts.stream().filter(p -> !Set.of("none", "bow", "bow0", "shield").contains(p)).findFirst();
        boolean bow = parts.contains("bow") || parts.contains("bow0");
        return new Kit(melee, parts.contains("bow") ? 16 : 0, armour, 0, 0, 0, 0, parts.contains("shield"), false, 0, bow);
    }
}
