package io.github.jayessdeesea.minecraft.command;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When a squad is in contact, and when its contact is over: from its first soldier's contact until none of its
 * soldiers has been in contact for {@link #SETTLE_MILLIS} (docs/military-doctrine.md § 8: consolidation and
 * reorganisation follow once the contact is over, not between its bursts). A soldier at the edge of contact reports
 * it on and off each second, and what the squad leader orders by it - rearming - went on and off with it.
 *
 * <p>A value, carried forward SITREP by SITREP on the clock it's given ({@link #seen}): nothing here reads a clock.
 *
 * @param lastInContact by soldier, when each was last in contact - only those within the settle time of the last SITREP seen
 * @param began         the soldiers whose contact began at the last SITREP seen: each one's own contact, likewise, is one
 *                      contact till it has been out of it the settle time, and is reported once
 */
public record SquadContact(Map<String, Long> lastInContact, Set<String> began) {

    /**
     * How long with no soldier in contact before a squad's contact is over: long enough that a phantom's passes and
     * a skeleton's bursts, seconds apart, are one contact - live, a soldier alone went between fighting and not 262
     * times in an hour of night. A soldier's own contact log ends one after 2 seconds, which is between passes.
     */
    public static final long SETTLE_MILLIS = 10_000;

    /** No contact, and none lately. */
    public static final SquadContact NONE = new SquadContact(Map.of(), Set.of());

    public SquadContact {
        lastInContact = Map.copyOf(lastInContact);
        began = Set.copyOf(began);
    }

    /** This after {@code squad}'s SITREP at {@code now}. */
    public SquadContact seen(List<SoldierStatus> squad, long now) {
        Map<String, Long> last = new HashMap<>();
        lastInContact.forEach((soldier, at) -> {
            if (now - at < SETTLE_MILLIS) {
                last.put(soldier, at);
            }
        });
        Set<String> beginning = new HashSet<>();
        for (SoldierStatus soldier : squad) {
            if (soldier.report() instanceof Report.InContact) {
                if (!last.containsKey(soldier.name())) {
                    beginning.add(soldier.name());
                }
                last.put(soldier.name(), now);
            }
        }
        return new SquadContact(last, beginning);
    }

    /** Whether the squad is in contact, as of the last SITREP seen: a soldier in it then, or within the settle time before. */
    public boolean under() {
        return !lastInContact.isEmpty();
    }
}
