package io.github.jayessdeesea.minecraft.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who serves where: each squad's name and its soldiers' usernames, in formation order - the first
 * is the point man (and the first soldier of the first squad carries the radio). The one home of
 * the order of battle: {@link ChainOfCommand} gives each squad a squad leader, {@link SoldierRunner}
 * connects each soldier. The first squad is the one {@code !escort} attaches.
 *
 * <p>Four to a squad: a doctrinal fire team, which the wedge, a 2x2 patrol base and buddy pairs all fit.
 *
 * <p>From {@code minecraft.roster}, {@code Squad:Soldier,Soldier;Squad:...}, so two apps can run at
 * once on one server without their soldiers' names colliding - a garrison living near the players,
 * and the live scenario harness's squads at its arena. A malformed roster fails at startup.
 */
@Component
final class Roster {

    record Squad(String name, List<String> soldiers) {
        Squad {
            soldiers = List.copyOf(soldiers);
        }
    }

    private final List<Squad> squads;

    Roster(@Value("${minecraft.roster}") String roster) {
        this.squads = parse(roster);
    }

    List<Squad> squads() {
        return squads;
    }

    /** The squads in {@code roster}, failing on anything that isn't a well-formed order of battle. */
    static List<Squad> parse(String roster) {
        List<Squad> squads = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String entry : roster.split(";")) {
            String[] parts = entry.strip().split(":");
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("roster entry '" + entry + "' isn't Squad:Soldier,Soldier,... in '" + roster + "'");
            }
            List<String> soldiers = new ArrayList<>();
            for (String soldier : parts[1].split(",")) {
                String name = soldier.strip();
                if (name.isEmpty() || !names.add(name)) {
                    throw new IllegalArgumentException("roster soldier '" + name + "' is empty or serves twice, in '" + roster + "'");
                }
                soldiers.add(name);
            }
            if (!names.add("squad:" + parts[0].strip())) {
                throw new IllegalArgumentException("roster squad '" + parts[0].strip() + "' appears twice, in '" + roster + "'");
            }
            squads.add(new Squad(parts[0].strip(), soldiers));
        }
        return List.copyOf(squads);
    }
}
