package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * The real block-state solidity answer, replacing {@code ChunkCache}'s old "state 0 = air"
 * placeholder. Backed by a resource generated (not hand-typed) from the pinned server jar's own
 * {@code --reports} output - see {@code tools/block-solidity-generator/GenerateBlockSolidity.java}
 * and DEVELOPMENT.md's "Known gaps" for the generation method and, importantly, its confidence
 * caveats: the resource lists global block-state IDs confirmed (from well-established vanilla
 * mechanics - flowers, torches, rails, tall grass, and similar) to have no collision. It is
 * deliberately <strong>not exhaustive</strong> - anything not confidently classified is treated as
 * solid, the same "unknown = blocked" default {@link ChunkCache} already uses for unloaded chunks.
 * Expected to grow incrementally as real testing surfaces specific wrong classifications, the same
 * methodology this project already uses for its packet catalogs.
 *
 * <p>The resource filename is pinned to protocol 776 (AGENTS.md's Target environment) on purpose -
 * regenerate and rename it deliberately when that pin bumps, not silently.
 */
final class BlockSolidityRegistry {

    private static final String RESOURCE_NAME = "non-solid-block-states-776.txt";

    private static final BitSet NON_SOLID_STATE_IDS = load();

    private BlockSolidityRegistry() {
    }

    static boolean isSolid(int stateId) {
        return stateId < 0 || !NON_SOLID_STATE_IDS.get(stateId);
    }

    private static BitSet load() {
        BitSet ids = new BitSet();
        try (InputStream in = BlockSolidityRegistry.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Missing block solidity resource: " + RESOURCE_NAME);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    int spaceIndex = line.indexOf(' ');
                    int stateId = Integer.parseInt(spaceIndex < 0 ? line : line.substring(0, spaceIndex));
                    ids.set(stateId);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load block solidity resource: " + RESOURCE_NAME, e);
        }
        return ids;
    }
}
