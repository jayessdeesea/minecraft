import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;

/**
 * One-off, dev-time tool - NOT part of the Maven build. Prints {@code <block> <type>} for every
 * block in the pinned server's own registry, the type being the block's class in snake case without
 * "Block" ({@code DoorBlock} is {@code door}). {@code tools/block-class-generator} classifies by
 * it. The server's {@code blocks.json} report carried each block's type until 26.3 removed block
 * codecs, so this asks the server itself. See DEVELOPMENT.md section 7 for the classpath.
 *
 * <p>The server logs through its own logger, which wraps {@code System.out}; the lines carry a
 * {@code [STDOUT]: } prefix, stripped by the generator.
 */
public class DumpBlockTypes {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Block block : BuiltInRegistries.BLOCK) {
            String type = block.getClass().getSimpleName().replaceFirst("Block$", "")
                    .replaceAll("(?<!^)(?=[A-Z])", "_").toLowerCase();
            System.out.println(BuiltInRegistries.BLOCK.getKey(block) + " " + (type.isEmpty() ? "block" : type));
        }
    }
}
