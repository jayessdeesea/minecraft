import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One-off, dev-time generator - NOT part of the Maven build (deliberately: it depends on Jackson,
 * which nothing at runtime needs). Reads the pinned server jar's own {@code
 * generated/reports/blocks.json} (produced by {@code --reports}) and emits the resource
 * {@code bot/src/main/resources/.../non-solid-block-states-<version>.txt} that {@code
 * bot.world.ChunkCache} loads at runtime. See ../../DEVELOPMENT.md's "Known gaps" for the full
 * methodology, confidence caveats, and how to regenerate this when the version pin bumps.
 */
public class GenerateBlockSolidity {

    // Block "definition.type" values confirmed (from well-established vanilla Minecraft mechanics,
    // not guessed) to have no meaningful collision - entities can freely occupy these blocks.
    // Deliberately NOT exhaustive: anything not confidently known is left OUT (defaults solid),
    // matching this project's own "unknown = blocked" policy elsewhere in ChunkCache. Expected to
    // grow incrementally as real testing surfaces specific wrong classifications - the same
    // methodology already used for this project's packet catalogs.
    private static final Set<String> NON_SOLID_TYPES = Set.of(
            "air", "bush", "flower", "tall_flower", "crop", "potato", "carrot", "beetroot",
            "torchflower_crop", "pitcher_crop", "nether_wart", "tall_grass", "short_dry_grass",
            "tall_dry_grass", "dry_vegetation", "double_plant", "sapling", "bamboo_sapling",
            "fire", "soul_fire", "vine", "cave_vines", "cave_vines_plant", "weeping_vines",
            "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "hanging_moss",
            "hanging_roots", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "coral",
            "coral_fan", "coral_plant", "coral_wall_fan", "base_coral_fan", "base_coral_plant",
            "base_coral_wall_fan", "lily_pad", "torch", "wall_torch", "redstone_torch",
            "redstone_wall_torch", "rail", "powered_rail", "detector_rail", "redstone_wire",
            "pressure_plate", "weighted_pressure_plate", "carpet", "wool_carpet", "mossy_carpet",
            "web", "sea_pickle", "glow_lichen", "sculk_vein", "nether_fungus", "nether_roots",
            "nether_sprouts", "attached_stem", "stem", "small_dripleaf", "sweet_berry_bush",
            "light", "structure_void", "end_portal", "end_gateway", "nether_portal",
            "leaf_litter", "firefly_bush", "flower_bed", "spore_blossom", "cactus_flower",
            "wither_rose", "eyeblossom", "tripwire"
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: GenerateBlockSolidity <blocks.json> <output.txt>");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(args[0]));

        // stateId -> (blockName, type) - kept sorted for a stable, diffable, human-reviewable output.
        Map<Integer, String[]> nonSolidStates = new TreeMap<>();
        int totalStates = 0;
        int totalBlocks = 0;

        Iterator<Map.Entry<String, JsonNode>> blocks = root.fields();
        while (blocks.hasNext()) {
            Map.Entry<String, JsonNode> block = blocks.next();
            String blockName = block.getKey();
            JsonNode definition = block.getValue().get("definition");
            String type = definition.get("type").asText().replace("minecraft:", "");
            totalBlocks++;

            boolean nonSolid = NON_SOLID_TYPES.contains(type);
            for (JsonNode state : block.getValue().get("states")) {
                totalStates++;
                if (nonSolid) {
                    nonSolidStates.put(state.get("id").asInt(), new String[]{blockName, type});
                }
            }
        }

        try (PrintWriter out = new PrintWriter(args[1])) {
            out.println("# Generated from the pinned server jar's `--reports` blocks.json - see");
            out.println("# DEVELOPMENT.md's \"Known gaps\" for the generation method and confidence");
            out.println("# caveats. One non-solid global block-state ID per line: <id> <blockName> <type>");
            out.println("# Everything NOT listed here is treated as solid (the safe default).");
            for (Map.Entry<Integer, String[]> entry : nonSolidStates.entrySet()) {
                out.printf("%d %s %s%n", entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
            }
        }

        System.out.println("Blocks: " + totalBlocks + ", total states: " + totalStates
                + ", non-solid states: " + nonSolidStates.size());
    }
}
