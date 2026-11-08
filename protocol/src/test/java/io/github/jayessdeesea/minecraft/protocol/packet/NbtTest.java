package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtTest {

    /** {@code depth} lists, each holding one list, around an empty innermost list. */
    private static ByteBuf nestedLists(int depth) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(9); // root: a list
        for (int i = 0; i < depth; i++) {
            buf.writeByte(9); // whose elements are lists
            buf.writeInt(1);  // of which there is one
        }
        buf.writeByte(0);     // innermost list: element type End,
        buf.writeInt(0);      // empty
        return buf;
    }

    @Test
    void skipsNestingUpToVanillasLimit() {
        ByteBuf buf = nestedLists(Nbt.MAX_DEPTH);

        Nbt.skipUnnamedRoot(buf);

        assertEquals(0, buf.readableBytes());
    }

    private static void name(ByteBuf buf, int type, String name) {
        buf.writeByte(type);
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void string(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    /** A death message as the server sends it: a translated compound, a player's name and a mob's translated name as its arguments. */
    @Test
    void aTranslatedComponentReadsAsItsKeyAndArguments() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(10);
        name(buf, 8, "translate");
        string(buf, "death.attack.arrow");
        name(buf, 9, "with");
        buf.writeByte(10);
        buf.writeInt(2);
        name(buf, 8, "text");
        string(buf, "Hal");
        buf.writeByte(0);
        name(buf, 8, "translate");
        string(buf, "entity.minecraft.skeleton");
        buf.writeByte(0);
        buf.writeByte(0);

        assertEquals("death.attack.arrow[Hal, entity.minecraft.skeleton]", Nbt.readText(buf));
        assertEquals(0, buf.readableBytes());
    }

    /** As the server sends it: the arguments first, then the key. */
    @Test
    void aTranslationsArgumentsBeforeItsKeyStillReadKeyFirst() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(10);
        name(buf, 9, "with");
        buf.writeByte(8);
        buf.writeInt(1);
        string(buf, "Carol");
        name(buf, 8, "translate");
        string(buf, "death.attack.explosion.player");
        buf.writeByte(0);

        assertEquals("death.attack.explosion.player[Carol]", Nbt.readText(buf));
    }

    @Test
    void refusesNestingBeyondVanillasLimit() {
        assertThrows(IllegalStateException.class, () -> Nbt.skipUnnamedRoot(nestedLists(Nbt.MAX_DEPTH + 1)));
    }
}
