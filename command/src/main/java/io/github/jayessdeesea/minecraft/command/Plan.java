package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A soldier's standing plan, as its squad leader last set it: its {@link Role} and where, and the
 * {@link Directive}s it does first, in order. Kept on disk, so a restart finds each soldier where
 * its squad leader put it (docs/military-doctrine.md § 18). What a soldier does with nothing
 * directed is its role's work; a rifleman's is its squad's.
 *
 * <p>The plan names a soldier's <em>unit</em>: a rifleman with nothing directed is in its squad's
 * main body; one in another role is in its squad's detachment for that role; one with a directive
 * is a detachment of one until it's done. Each unit is led as a squad is, by the same drills, to
 * its own objective ({@link #objective}).
 */
public record Plan(Role role, Optional<Position> site, List<Directive> directives) {

    /** A soldier nobody has tasked: a rifleman, with its squad. */
    public static final Plan RIFLEMAN = new Plan(Role.RIFLEMAN, Optional.empty(), List.of());

    public Plan {
        Objects.requireNonNull(role, "role");
        directives = List.copyOf(directives);
        if (role != Role.RIFLEMAN && site.isEmpty()) {
            throw new IllegalArgumentException("a " + role + " needs a site");
        }
        if (role == Role.RIFLEMAN) {
            site = Optional.empty();
        }
    }

    /**
     * What a squad leader changes in one soldier's plan: its role and site, if given; its
     * directives, replaced, if given - an empty list cancels them.
     */
    public record Change(String soldier, Optional<Role> role, Optional<Position> site, Optional<List<Directive>> directives) {
        public Change {
            Objects.requireNonNull(soldier, "soldier");
            directives = directives.map(List::copyOf);
            if (role.isPresent() && role.get() != Role.RIFLEMAN && site.isEmpty()) {
                throw new IllegalArgumentException(soldier + " made a " + role.get() + " with no site");
            }
        }

        /** A new role at a site, its directives as they were. */
        public static Change role(String soldier, Role role, Position site) {
            return new Change(soldier, Optional.of(role), Optional.ofNullable(role == Role.RIFLEMAN ? null : site), Optional.empty());
        }

        /** Its directives replaced by {@code directives}, its role as it was. */
        public static Change directives(String soldier, List<Directive> directives) {
            return new Change(soldier, Optional.empty(), Optional.empty(), Optional.of(directives));
        }
    }

    /** This plan with {@code change} made to it. */
    public Plan with(Change change) {
        Role newRole = change.role().orElse(role);
        Optional<Position> newSite = change.role().isPresent() ? change.site() : site;
        return new Plan(newRole, newSite, change.directives().orElse(directives));
    }

    /** The directive it's on, if any. */
    public Optional<Directive> current() {
        return directives.isEmpty() ? Optional.empty() : Optional.of(directives.getFirst());
    }

    /**
     * Whether its current directive is done, as {@code soldier}'s own state shows: a
     * {@link Directive.GoTo} is done on arrival, at any height, for a soldier with a role to go on
     * to - a rifleman holds there.
     */
    public boolean currentDoneBy(SoldierStatus soldier) {
        return current().map(d -> d.doneBy(soldier)
                || role != Role.RIFLEMAN && d instanceof Directive.GoTo goTo && goTo.reachedBy(soldier, true)).orElse(false);
    }

    /** This plan with its current directive done. */
    public Plan done() {
        return directives.isEmpty() ? this : new Plan(role, site, directives.subList(1, directives.size()));
    }

    /**
     * The unit {@code soldier} of {@code squad} is in under this plan: the squad itself, "Alpha/miner"
     * for its role's detachment, "Alpha/Bob" while it has a directive.
     */
    public String unit(String squad, String soldier) {
        if (current().isPresent()) {
            return squad + "/" + soldier;
        }
        return role == Role.RIFLEMAN ? squad : squad + "/" + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Whether {@code unit} is a squad's detachment rather than its main body. */
    public static boolean detachment(String unit) {
        return unit.contains("/");
    }

    /** The squad a unit belongs to. */
    public static String squadOf(String unit) {
        int slash = unit.indexOf('/');
        return slash < 0 ? unit : unit.substring(0, slash);
    }

    /**
     * The objective a detachment works to under this plan, with {@code intent} its squad leader's:
     * its directive's point, or its role's task at its site. Empty for a rifleman with nothing
     * directed, who has the general's.
     */
    public Optional<Objective> objective(String intent) {
        Optional<Directive> directive = current();
        if (directive.isPresent()) {
            Objective.Task task = directive.get() instanceof Directive.Fetch fetch
                    ? new Objective.Task.Gather(fetch.resource()) : new Objective.Task.None();
            return Optional.of(new Objective(directive.get().describe(), directive.get().where(), intent, task));
        }
        return role.task().map(task -> new Objective(role.name().toLowerCase(java.util.Locale.ROOT) + " at the site", site.orElseThrow(),
                intent, task));
    }

    /** In words, for the squad leader's view. */
    public String describe() {
        String what = role == Role.RIFLEMAN ? "RIFLEMAN (with the squad)"
                : role + " at " + String.format(java.util.Locale.ROOT, "(%.0f, %.0f, %.0f)", site.get().x(), site.get().y(), site.get().z());
        return directives.isEmpty() ? what
                : what + "; first: " + String.join(", then ", directives.stream().map(Directive::describe).toList());
    }
}
