package io.github.jayessdeesea.minecraft.app;

import java.util.List;

/**
 * Who serves where: each squad's name and its soldiers' usernames, in formation order - the first
 * is the point man (and the first soldier of the first squad carries the radio). The one home of
 * the order of battle: {@link ChainOfCommand} gives each squad a squad leader, {@link SoldierRunner}
 * connects each soldier. The first squad is the one {@code !escort} attaches.
 *
 * <p>Hardcoded, not {@code application.yml}, for the same reason as the dev server's address:
 * nothing yet needs to change it without recompiling.
 */
final class Roster {

    record Squad(String name, List<String> soldiers) {
    }

    static final List<Squad> SQUADS = List.of(
            new Squad("Alpha", List.of("Alice", "Bob", "Carol")),
            new Squad("Bravo", List.of("Dana", "Eli", "Fran")));

    private Roster() {
    }
}
