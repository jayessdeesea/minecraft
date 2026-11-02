package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Each melee weapon's damage and attack speed, as the pinned server's attributes give them -
 * measured on the live server ({@code tools/weapon-stats-probe}), since an item's stats aren't in
 * the data reports. A hit's damage scales with how far the weapon has recharged (vanilla: damage x
 * (0.2 + 0.8 x charge squared)), so a soldier waits {@link Stats#ticksToCharge} between swings.
 *
 * <p>Pinned to protocol 777 by filename - re-measure and rename deliberately when the pin bumps.
 */
public final class WeaponStats {

    private static final String RESOURCE_NAME = "weapon-stats-777.txt";
    /** Held, these hit like a bare hand: they aren't melee weapons. */
    private static final Set<String> NOT_MELEE = Set.of("bow", "crossbow");

    /** A weapon's damage at full charge, and attacks a second. */
    public record Stats(double damage, double attacksPerSecond) {

        /** Ticks for a swing to recharge fully: 20 ticks a second over the attack speed, rounded up. */
        public int ticksToCharge() {
            return (int) Math.ceil(20 / attacksPerSecond);
        }
    }

    private static final Map<String, Stats> STATS = load();
    public static final Stats HAND = STATS.get("hand");

    private WeaponStats() {
    }

    /**
     * What a soldier holding {@code weapon} (an item name, empty if bare-handed) hits with. A weapon
     * that isn't listed is a gap in the table - every weapon the item registry knows is measured -
     * so it fails loudly rather than guessing.
     */
    public static Stats melee(Optional<String> weapon) {
        if (weapon.isEmpty() || NOT_MELEE.contains(weapon.get())) {
            return HAND;
        }
        Stats stats = STATS.get(weapon.get());
        if (stats == null) {
            throw new IllegalStateException("no measured stats for weapon " + weapon.get() + " in " + RESOURCE_NAME);
        }
        return stats;
    }

    /** Whether {@code weapon} is listed - for the test that every weapon is. */
    static boolean known(String weapon) {
        return NOT_MELEE.contains(weapon) || STATS.containsKey(weapon);
    }

    private static Map<String, Stats> load() {
        Map<String, Stats> stats = new HashMap<>();
        try (InputStream in = WeaponStats.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Missing weapon stats resource: " + RESOURCE_NAME);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.trim().split(" ");
                    stats.put(parts[0], new Stats(Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load weapon stats resource: " + RESOURCE_NAME, e);
        }
        if (!stats.containsKey("hand")) {
            throw new IllegalStateException(RESOURCE_NAME + " has no hand row");
        }
        return Map.copyOf(stats);
    }
}
