package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Which entity-type registry IDs are hostile - backed by a resource generated from the pinned
 * server jar's own {@code --reports} output (see {@code tools/entity-hostility-generator}), same
 * approach as {@link BlockClassRegistry}.
 *
 * <p>The default is the opposite of {@code BlockClassRegistry}'s, deliberately: there the curated
 * list is the exception (non-solid) to a safe default (solid); here the curated list <em>is</em> the
 * classification, and the uncurated majority - items, passive mobs, players, projectiles - is
 * correctly "not hostile." A hostile mob missing from the list goes unnoticed, which is a tracked,
 * incremental gap (grow the list), not a violated invariant.
 *
 * <p>Pinned to protocol 777 by filename - regenerate and rename deliberately when the pin bumps.
 */
public final class HostileMobRegistry {

    private static final String RESOURCE_NAME = "hostile-entity-types-777.txt";

    private static final java.util.Map<String, Integer> TYPE_IDS = new java.util.HashMap<>();
    private static final BitSet HOSTILE_TYPE_IDS = load();
    /** Hostiles to get away from rather than fight at arm's length: they explode. */
    private static final int CREEPER = typeIdOf("creeper");

    private HostileMobRegistry() {
    }

    /** The protocol entity-type ID of a hostile, by registry name (without "minecraft:"). */
    public static int typeIdOf(String name) {
        Integer id = TYPE_IDS.get(name);
        if (id == null) {
            throw new IllegalArgumentException("not a listed hostile: " + name);
        }
        return id;
    }

    static boolean explodes(int typeId) {
        return typeId == CREEPER;
    }

    public static boolean isHostile(int typeId) {
        return typeId >= 0 && HOSTILE_TYPE_IDS.get(typeId);
    }

    private static BitSet load() {
        BitSet ids = new BitSet();
        try (InputStream in = HostileMobRegistry.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Missing hostile entity resource: " + RESOURCE_NAME);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    int spaceIndex = line.indexOf(' ');
                    int id = Integer.parseInt(spaceIndex < 0 ? line : line.substring(0, spaceIndex));
                    ids.set(id);
                    if (spaceIndex >= 0) {
                        TYPE_IDS.put(line.substring(spaceIndex + 1).trim(), id);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load hostile entity resource: " + RESOURCE_NAME, e);
        }
        return ids;
    }
}
