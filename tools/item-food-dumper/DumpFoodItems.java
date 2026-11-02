import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * One-off, dev-time tool - NOT part of the Maven build. Prints the name of every food in the pinned
 * server, one per line, for {@code tools/item-class-generator}: the {@code FoodProperties} constants
 * of vanilla's {@code Foods}, whose names are the items' ({@code BREAD} is {@code minecraft:bread}).
 * The data reports don't carry item components, and an item's components aren't bound until a world
 * loads, so this reads the constants instead; the generator checks every name is a real item. See
 * DEVELOPMENT.md section 7 for the classpath.
 */
public class DumpFoodItems {
    public static void main(String[] args) throws IllegalAccessException {
        for (Field field : Foods.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == FoodProperties.class) {
                System.out.println(field.getName().toLowerCase());
            }
        }
    }
}
