package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * VarInt/VarLong/String encode-decode per docs/protocol-spec.md § 1. Shift-right uses {@code
 * >>>} throughout so negative values (whose sign bit would otherwise get smeared across the
 * remaining bytes by an arithmetic shift) round-trip correctly - a negative VarInt is legal and
 * always encodes to the maximum length (5 bytes for VarInt, 10 for VarLong).
 */
public final class ProtocolCodec {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    /** Wire limit from docs/protocol-spec.md § 1.1 - reject before allocating on a bad length. */
    public static final int MAX_STRING_LENGTH = 32767;

    /**
     * 2 MiB - generous for Status/Login/Configuration; revisit once large Play packets (chunk
     * data) exist. Shared by {@code FrameDecoder} (the raw frame length) and
     * {@code CompressionDecoder} (the declared decompressed size) since both are the same class
     * of guard: never let a wire-supplied length size an allocation without a cap.
     */
    public static final int MAX_FRAME_LENGTH = 1 << 21;

    private ProtocolCodec() {
    }

    public static void writeVarInt(ByteBuf out, int value) {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
    }

    public static int readVarInt(ByteBuf in) {
        int result = 0;
        int position = 0;
        while (true) {
            byte current = in.readByte();
            result |= (current & SEGMENT_BITS) << position;
            if ((current & CONTINUE_BIT) == 0) {
                return result;
            }
            position += 7;
            if (position >= 32) {
                throw new IllegalStateException("VarInt is too long");
            }
        }
    }

    public static void writeVarLong(ByteBuf out, long value) {
        while (true) {
            if ((value & ~((long) SEGMENT_BITS)) == 0) {
                out.writeByte((int) value);
                return;
            }
            out.writeByte((int) ((value & SEGMENT_BITS) | CONTINUE_BIT));
            value >>>= 7;
        }
    }

    public static long readVarLong(ByteBuf in) {
        long result = 0;
        int position = 0;
        while (true) {
            byte current = in.readByte();
            result |= (long) (current & SEGMENT_BITS) << position;
            if ((current & CONTINUE_BIT) == 0) {
                return result;
            }
            position += 7;
            if (position >= 64) {
                throw new IllegalStateException("VarLong is too long");
            }
        }
    }

    public static void writeString(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("String exceeds max length of " + MAX_STRING_LENGTH + " bytes");
        }
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    public static String readString(ByteBuf in) {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new IllegalStateException("String length " + length + " exceeds max of " + MAX_STRING_LENGTH);
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a VarInt meant to be used as a following-collection's element count, rejecting it if
     * negative or above {@code max} <strong>before</strong> the caller sizes any allocation from
     * it (e.g. {@code new ArrayList<>(count)}) - a wire-supplied count sizing an eager allocation
     * with no upper bound is a real, previously-shipped bug in this codebase (see
     * {@code KnownPacksPacket}), not a hypothetical one. Every packet that decodes a
     * count-prefixed list should read its count through this, not through {@link #readVarInt}
     * directly.
     */
    public static int readVarIntCount(ByteBuf in, int max) {
        int count = readVarInt(in);
        if (count < 0 || count > max) {
            throw new IllegalStateException("Count " + count + " outside allowed range (max " + max + ")");
        }
        return count;
    }

    public static void writeUuid(ByteBuf out, UUID uuid) {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(ByteBuf in) {
        long mostSignificantBits = in.readLong();
        long leastSignificantBits = in.readLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
