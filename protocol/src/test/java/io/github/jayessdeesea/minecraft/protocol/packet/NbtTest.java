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

    @Test
    void refusesNestingBeyondVanillasLimit() {
        assertThrows(IllegalStateException.class, () -> Nbt.skipUnnamedRoot(nestedLists(Nbt.MAX_DEPTH + 1)));
    }
}
