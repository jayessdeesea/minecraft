package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A soldier's contacts, said as they go (operator, 2026-11-20: enough in the log to build an eval of any contact): a
 * {@code [Contact]} line when one starts or the soldier's response to it changes - each hostile's type, where it is,
 * how far, how far up or down, in water or not; the soldier's health and kit; and the rule that chose the response -
 * and one when it ends, with how long it went on and the health it cost. One per soldier, on its own tick thread.
 */
final class ContactLog {

    private static final Logger LOG = LogManager.getLogger(ContactLog.class);

    /**
     * Ticks with no hostile before a contact is over: one at the edge of contact goes in and out of it each second.
     * Live (field554), a skeleton 8 blocks off said Eli's contact begun and over twelve times in ten seconds.
     */
    static final int END_TICKS = 40;

    private final String name;
    private String response;
    private long lastSeen;
    private int with = -1;
    private long ticks, startedAt;
    private double healthAtStart;

    ContactLog(String name) {
        this.name = name;
    }

    /**
     * This tick's contact: {@code threats}, nearest first, and the {@code response} the soldier chose to them - or none,
     * with no threats, which ends a contact under way.
     */
    void tick(WorldView world, Position at, double health, Kit kit, List<TrackedEntity> threats, String response) {
        ticks++;
        if (threats.isEmpty() || response == null) {
            if (this.response != null && ticks - lastSeen >= END_TICKS) {
                LOG.info("[Contact] {} out of contact after {} s, health {} -> {}", name, String.format(Locale.ROOT, "%.1f",
                        (lastSeen - startedAt) / 20.0), fmt(healthAtStart), fmt(health));
                this.response = null;
                with = -1;
            }
            return;
        }
        lastSeen = ticks;
        if (this.response == null) {
            startedAt = ticks;
            healthAtStart = health;
        }
        int nearest = threats.getFirst().entityId();
        if (response.equals(this.response) && nearest == with) {
            return;
        }
        this.response = response;
        with = nearest;
        LOG.info("[Contact] {} at {} ({} health; {}): {} - {}", name, where(at), fmt(health), kit(kit), response,
                threats.stream().limit(4).map(t -> hostile(world, at, t)).collect(Collectors.joining("; ")));
    }

    private static String hostile(WorldView world, Position at, TrackedEntity t) {
        Position p = t.position();
        boolean wet = world.isWater((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
        return String.format(Locale.ROOT, "%s at %s, %.1f off, %+.1f up%s", HostileMobRegistry.nameOf(t.typeId()), where(p),
                Math.hypot(p.x() - at.x(), p.z() - at.z()), p.y() - at.y(), wet ? ", in water" : "");
    }

    private static String kit(Kit kit) {
        if (kit == null) {
            return "kit unknown";
        }
        return kit.weapon().orElse("no weapon") + (kit.bow() ? ", bow and " + kit.arrows() + " arrows" : "")
                + (kit.shield() ? ", shield" : "") + ", armour " + kit.armour();
    }

    private static String where(Position p) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", p.x(), p.y(), p.z());
    }

    private static String fmt(double health) {
        return String.format(Locale.ROOT, "%.0f", health);
    }
}
