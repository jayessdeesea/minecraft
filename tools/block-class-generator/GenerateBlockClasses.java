import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One-off, dev-time generator - NOT part of the Maven build (it depends on Jackson, which nothing at
 * runtime needs). Reads the pinned server jar's own {@code generated/reports/blocks.json} (produced
 * by {@code --reports}; see DEVELOPMENT.md section 7) and classifies every block state for movement,
 * emitting {@code bot/src/main/resources/.../block-classes-<version>.txt}, which
 * {@code bot.world.BlockClassRegistry} loads at runtime.
 *
 * <p>Classes (anything not listed in the output is SOLID, the safe default):
 * <ul>
 *   <li>{@code P} passable - no collision: air, plants, torches, rails, carpets, open doors/gates.
 *   <li>{@code T} tall - collision 1.5 blocks high (fences, walls, closed fence gates): can't be
 *       jumped onto, so movement treats it as also blocking the block above.
 *   <li>{@code H} hazard - never stand in or walk through: liquids, anything waterlogged or
 *       underwater, fire, powder snow.
 * </ul>
 * The type lists are from well-established vanilla mechanics, not guessed. Deliberately not
 * exhaustive - an unlisted type stays SOLID, the same "unknown = blocked" policy as unloaded chunks.
 */
public class GenerateBlockClasses {

    private static final Set<String> PASSABLE_TYPES = Set.of(
            "air", "bush", "flower", "tall_flower", "crop", "potato", "carrot", "beetroot",
            "torchflower_crop", "pitcher_crop", "nether_wart", "tall_grass", "short_dry_grass",
            "tall_dry_grass", "dry_vegetation", "double_plant", "sapling", "bamboo_sapling",
            "vine", "cave_vines", "cave_vines_plant", "weeping_vines",
            "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "hanging_moss",
            "hanging_roots", "coral_plant", "base_coral_plant", "coral_wall_fan", "base_coral_wall_fan",
            "coral_fan", "base_coral_fan", "lily_pad", "torch", "wall_torch", "redstone_torch",
            "redstone_wall_torch", "rail", "powered_rail", "detector_rail", "redstone_wire",
            "pressure_plate", "weighted_pressure_plate", "carpet", "wool_carpet", "mossy_carpet",
            "web", "sea_pickle", "glow_lichen", "sculk_vein", "nether_fungus", "nether_roots",
            "nether_sprouts", "attached_stem", "stem", "small_dripleaf", "sweet_berry_bush",
            "light", "structure_void", "end_portal", "end_gateway", "nether_portal",
            "leaf_litter", "firefly_bush", "flower_bed", "spore_blossom", "cactus_flower",
            "wither_rose", "eyeblossom", "tripwire", "wall_sign", "wall_hanging_sign", "wall_banner"
    );

    private static final Set<String> TALL_TYPES = Set.of("fence", "wall", "fence_gate");

    /** Opening these makes them passable (an open gate or door is a gap, not a wall). */
    private static final Set<String> OPENABLE_TYPES = Set.of("fence_gate", "door", "weathering_copper_door");

    private static final Set<String> HAZARD_TYPES = Set.of(
            "liquid", "bubble_column", "fire", "soul_fire", "powder_snow",
            "kelp", "kelp_plant", "seagrass", "tall_seagrass"
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: GenerateBlockClasses <blocks.json> <output.txt>");
            System.exit(1);
        }

        JsonNode root = new ObjectMapper().readTree(new File(args[0]));
        Map<Integer, String> classes = new TreeMap<>(); // stateId -> "<class> <blockName>"
        Map<Character, Integer> counts = new TreeMap<>();
        int totalStates = 0;

        Iterator<Map.Entry<String, JsonNode>> blocks = root.fields();
        while (blocks.hasNext()) {
            Map.Entry<String, JsonNode> block = blocks.next();
            String blockName = block.getKey().replace("minecraft:", "");
            String type = block.getValue().get("definition").get("type").asText().replace("minecraft:", "");
            for (JsonNode state : block.getValue().get("states")) {
                totalStates++;
                char cls = classify(type, state.path("properties"));
                if (cls != 'S') {
                    classes.put(state.get("id").asInt(), cls + " " + blockName);
                    counts.merge(cls, 1, Integer::sum);
                }
            }
        }

        try (PrintWriter out = new PrintWriter(args[1])) {
            out.println("# Generated from the pinned server jar's `--reports` blocks.json by");
            out.println("# tools/block-class-generator. One block state per line: <id> <class> <blockName>,");
            out.println("# class P = passable, T = tall (1.5-high collision), H = hazard (liquid, fire, ...).");
            out.println("# Every state NOT listed is solid (the safe default).");
            classes.forEach((id, value) -> out.printf("%d %s%n", id, value));
        }
        System.out.println("States: " + totalStates + ", non-solid by class: " + counts);
    }

    static char classify(String type, JsonNode properties) {
        if (HAZARD_TYPES.contains(type) || "true".equals(properties.path("waterlogged").asText())) {
            return 'H';
        }
        if (OPENABLE_TYPES.contains(type) && "true".equals(properties.path("open").asText())) {
            return 'P';
        }
        if (TALL_TYPES.contains(type)) {
            return 'T';
        }
        if (PASSABLE_TYPES.contains(type)) {
            return 'P';
        }
        if (type.equals("snow_layer") && "1".equals(properties.path("layers").asText())) {
            return 'P'; // a single snow layer has no collision height
        }
        return 'S';
    }
}
