import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * One-off, dev-time generator - NOT part of the Maven build (it depends on Jackson, which nothing at
 * runtime needs). Reads the pinned server jar's own {@code generated/reports/blocks.json} (produced
 * by {@code --reports}; see DEVELOPMENT.md section 7) for each block's states and properties, and
 * {@code tools/block-type-dumper}'s output for each block's type, and classifies every block state
 * for movement,
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
 *   <li>{@code L} log - solid like any other block, but wood a soldier can chop: every
 *       {@code *_log} block.
 *   <li>{@code Q} stone - solid, and quarried with a pickaxe for cobblestone: {@code stone}
 *       itself.
 *   <li>{@code W} workstation - solid, and opened to craft or smelt: {@code crafting_table},
 *       {@code furnace}. Named, so a soldier knows its camp's table from any other block.
 *   <li>{@code D} soil - solid, and dug by hand in about a second: the dirt family, clay, moss and
 *       snow blocks, hardness 0.65 or less. Not sand or gravel: they fall into what's dug under them.
 *   <li>{@code O} iron ore - solid, and mined with a stone pickaxe or better for raw iron:
 *       {@code iron_ore}, {@code deepslate_iron_ore}.
 *   <li>{@code F} farmland - solid, tilled for crops: never dug for a step, or a farm is trampled.
 *   <li>{@code R} rock - solid, and mined with a pickaxe: every other block the server's
 *       {@code mineable/pickaxe} tag names directly - deepslate, andesite, tuff, the ores. Its hardness
 *       and the least pickaxe that gets its drop ride along: 0 any, 1 stone, 2 iron, 3 diamond, from
 *       the {@code needs_*_tool} tags.
 *   <li>{@code V} leaves - solid, broken by hand at a touch: every {@code *_leaves} block. Broken,
 *       one sometimes drops its tree's sapling; the name says which tree.
 *   <li>{@code G} grit - solid, dug by hand (a shovel's work): what the {@code mineable/shovel} tag
 *       names that isn't soil - gravel, sand. Falls into what's dug under it. Hardness rides along.
 * </ul>
 * What a farm needs to know rides along as a fourth field: {@code level=N} on water (0 is a
 * source), {@code age=N} on wheat. Grass plants keep their names - {@code short_grass} and
 * {@code tall_grass} drop wheat seeds when broken.
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
            "wither_rose", "eyeblossom", "trip_wire", "wall_sign", "wall_hanging_sign", "wall_banner"
    );

    /** Hardness at most 0.65 (a dirt path), none of them falling blocks. */
    private static final Set<String> SOIL_BLOCKS = Set.of(
            "dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mycelium", "dirt_path",
            "clay", "moss_block", "snow_block"
    );

    private static final Set<String> TALL_TYPES = Set.of("fence", "wall", "fence_gate");

    /** Opening these makes them passable (an open gate or door is a gap, not a wall). */
    private static final Set<String> OPENABLE_TYPES = Set.of("fence_gate", "door", "weathering_copper_door");

    private static final Set<String> HAZARD_TYPES = Set.of(
            "liquid", "bubble_column", "fire", "soul_fire", "powder_snow",
            "kelp", "kelp_plant", "seagrass", "tall_seagrass"
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: GenerateBlockClasses <blocks.json> <block-types.txt> <output.txt> <server.jar>");
            System.exit(1);
        }
        Set<String> pickaxe;
        Set<String> shovel;
        Set<String> needsStone;
        Set<String> needsIron;
        Set<String> needsDiamond;
        try (ZipFile jar = new ZipFile(args[3])) {
            pickaxe = tag(jar, "mineable/pickaxe");
            shovel = tag(jar, "mineable/shovel");
            needsStone = tag(jar, "needs_stone_tool");
            needsIron = tag(jar, "needs_iron_tool");
            needsDiamond = tag(jar, "needs_diamond_tool");
        }
        Map<String, String> hardness = new HashMap<>();

        JsonNode root = new ObjectMapper().readTree(new File(args[0]));
        Map<String, String> types = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            int at = line.indexOf("[STDOUT]: ");
            String entry = (at >= 0 ? line.substring(at + "[STDOUT]: ".length()) : line).strip();
            if (entry.startsWith("minecraft:")) {
                String[] parts = entry.split(" ");
                types.put(parts[0], parts[1]);
                if (parts.length < 3) {
                    throw new IllegalStateException("no hardness for " + parts[0] + " - block types from an older dumper?");
                }
                hardness.put(parts[0], parts[2]);
            }
        }
        Map<Integer, String> classes = new TreeMap<>(); // stateId -> "<class> <blockName>"
        Map<Character, Integer> counts = new TreeMap<>();
        int totalStates = 0;

        Iterator<Map.Entry<String, JsonNode>> blocks = root.fields();
        while (blocks.hasNext()) {
            Map.Entry<String, JsonNode> block = blocks.next();
            String blockName = block.getKey().replace("minecraft:", "");
            String type = types.get(block.getKey());
            if (type == null) {
                throw new IllegalStateException("no type for " + block.getKey() + " - block types from another version?");
            }
            for (JsonNode state : block.getValue().get("states")) {
                totalStates++;
                char cls = blockName.endsWith("_log") && !"true".equals(state.path("properties").path("waterlogged").asText())
                        ? 'L' : blockName.equals("stone") ? 'Q'
                        : blockName.equals("crafting_table") || blockName.equals("furnace") ? 'W'
                        : blockName.equals("iron_ore") || blockName.equals("deepslate_iron_ore") ? 'O'
                        : blockName.equals("farmland") ? 'F'
                        : SOIL_BLOCKS.contains(blockName) && !"true".equals(state.path("properties").path("waterlogged").asText()) ? 'D'
                        : classify(type, state.path("properties"));
                String id = "minecraft:" + blockName;
                if (cls == 'S' && blockName.endsWith("_leaves")) {
                    cls = 'V';
                } else if (cls == 'S' && pickaxe.contains(id)) {
                    cls = 'R';
                } else if (cls == 'S' && shovel.contains(id)) {
                    cls = 'G';
                }
                if (cls != 'S') {
                    String detail = blockName.equals("water") ? " level=" + state.path("properties").path("level").asText()
                            : blockName.equals("wheat") ? " age=" + state.path("properties").path("age").asText()
                            : cls == 'R' ? " " + hardness.get(id) + " " + (needsDiamond.contains(id) ? 3 : needsIron.contains(id) ? 2
                                    : needsStone.contains(id) ? 1 : 0)
                            : cls == 'G' ? " " + hardness.get(id) : "";
                    classes.put(state.get("id").asInt(), cls + " " + blockName + detail);
                    counts.merge(cls, 1, Integer::sum);
                }
            }
        }

        try (PrintWriter out = new PrintWriter(args[2])) {
            out.println("# Generated from the pinned server jar's `--reports` blocks.json by");
            out.println("# tools/block-class-generator. One block state per line: <id> <class> <blockName>,");
            out.println("# class P = passable, T = tall (1.5-high collision), H = hazard (liquid, fire, ...), L = log, Q = stone, W = workstation, D = soil, O = iron ore, F = farmland (all solid);");
            out.println("# R = rock (mined with a pickaxe), G = grit (dug by hand, falls), V = leaves;");
            out.println("# then level=N on water (0 a source), age=N on wheat, <hardness> <least pickaxe tier> on rock, <hardness> on grit.");
            out.println("# Every state NOT listed is solid (the safe default).");
            classes.forEach((id, value) -> out.printf("%d %s%n", id, value));
        }
        System.out.println("States: " + totalStates + ", non-solid by class: " + counts);
    }

    /** The blocks a tag in the jar's data names directly - nested tags are other shapes (walls, buttons), not ground. */
    static Set<String> tag(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry("data/minecraft/tags/block/" + name + ".json");
        if (entry == null) {
            throw new IllegalStateException("no block tag " + name + " in the jar");
        }
        Set<String> blocks = new java.util.HashSet<>();
        try (var in = jar.getInputStream(entry)) {
            for (JsonNode value : new ObjectMapper().readTree(in).get("values")) {
                if (!value.asText().startsWith("#")) {
                    blocks.add(value.asText());
                }
            }
        }
        return blocks;
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
