package io.github.jayessdeesea.minecraft.command;

/**
 * The phase of the force's lodgment, as a forcible entry runs (JP 3-18; FM 3-99; docs/military-doctrine.md
 * section 17): the platoon leader's to track and set the direction by (operator, 2026-11-18) - the squad leaders
 * and soldiers carry out its orders, they don't decide by it. {@link PhaseTracker} moves it.
 */
public enum Phase {
    /** The airhead seized: shelter in place at night, the camp secured and every soldier armed by day. */
    SEIZURE("the airhead seized - shelter in place at night; secure the camp and arm every soldier, no KEEP, no MINE"),
    /** The airhead held and reorganised: the food and wood pipelines built, sheltered at night, one squad on the mission. */
    CONSOLIDATION("consolidating - shelter in the camp at night; food and wood first, at most one squad on KEEP, no MINE"),
    /** Combat power built up on a sustained supply: the night shift mines, the mission takes the squads the shortfalls spare. */
    BUILDUP("building up - the night shift mines; the shortfalls covered, the rest on the mission"),
    /** Ready for operations beyond the lodgment: supply and armour to spare. */
    BREAKOUT("breaking out - supply and armour to spare; the mission leads");

    private final String meaning;

    Phase(String meaning) {
        this.meaning = meaning;
    }

    /** What it means for the day's and the night's work, in the platoon leader's words. */
    public String meaning() {
        return meaning;
    }

    /** Whether the force is still building its supply base - before {@link #BUILDUP}: no mining, sheltered at night. */
    public boolean beforeBuildup() {
        return compareTo(BUILDUP) < 0;
    }
}
