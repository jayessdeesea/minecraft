package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolCodecTest {

    @ParameterizedTest
    @CsvSource({
            "0,   00",
            "1,   01",
            "127, 7F",
            "128, 80 01",
            "300, AC 02",
    })
    void encodesVarIntPerSpecExamples(int value, String expectedHex) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, value);
        assertArrayEquals(hexToBytes(expectedHex), readAll(buf));
    }

    @Test
    void encodesNegativeVarIntAsFiveBytes() {
        // -1 is 0xFFFFFFFF; VarInt encoding of it is the textbook "did you use >>> not >>" case.
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, -1);
        assertArrayEquals(hexToBytes("FF FF FF FF 0F"), readAll(buf));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, 127, 128, 300, Integer.MAX_VALUE, Integer.MIN_VALUE, -300})
    void varIntRoundTrips(int value) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, value);
        assertEquals(value, ProtocolCodec.readVarInt(buf));
        assertEquals(0, buf.readableBytes(), "decode should consume exactly what was written");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, 300L, Long.MAX_VALUE, Long.MIN_VALUE})
    void varLongRoundTrips(long value) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarLong(buf, value);
        assertEquals(value, ProtocolCodec.readVarLong(buf));
        assertEquals(0, buf.readableBytes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hello", "unicode: éèê 😀"})
    void stringRoundTrips(String value) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeString(buf, value);
        assertEquals(value, ProtocolCodec.readString(buf));
    }

    @Test
    void rejectsStringLongerThanMaxLength() {
        String tooLong = "x".repeat(ProtocolCodec.MAX_STRING_LENGTH + 1);
        ByteBuf buf = Unpooled.buffer();
        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.writeString(buf, tooLong));
    }

    private static byte[] hexToBytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    private static byte[] readAll(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
