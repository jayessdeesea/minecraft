package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Skip-only NBT reader (binary format per the Minecraft Wiki's NBT page, confirmed against the
 * real server jar's {@code NbtIo.writeAnyTag}/{@code readAnyTag} - see {@link RegistryDataPacket},
 * this package's first user) - enough to advance past an NBT value without misaligning the packet,
 * without decoding what it actually says. A full tree-materializing parser would be scope beyond
 * what's needed today - this grows into one if/when something needs actual values out of an NBT
 * payload.
 *
 * <p>Numeric fields and string-length prefixes are big-endian, unlike the VarInt/VarLong used
 * everywhere else in this protocol - NBT predates Minecraft's network protocol and keeps its own,
 * older Java-serialization-style encoding.
 */
final class Nbt {

    private static final int END = 0;
    private static final int BYTE = 1;
    private static final int SHORT = 2;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int DOUBLE = 6;
    private static final int BYTE_ARRAY = 7;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;
    private static final int INT_ARRAY = 11;
    private static final int LONG_ARRAY = 12;

    /**
     * Vanilla's own nesting limit ({@code NbtAccounter}, 512 in every preset). Deeper input
     * terminates the connection, like every other resource guard (docs/protocol-spec.md § 8):
     * recursion here is the one cost a length-capped frame doesn't already bound.
     */
    static final int MAX_DEPTH = 512;

    private Nbt() {
    }

    /**
     * Skips one "any tag" value as {@code NbtIo.writeAnyTag}/{@code FriendlyByteBuf.writeNbt}
     * write it: a type-ID byte, then - unlike every tag nested inside it, which keeps a normal
     * name - no name field at all, then the payload directly. {@code TAG_End} (0x00) means an
     * absent/empty value.
     */
    static void skipUnnamedRoot(ByteBuf in) {
        int typeId = in.readUnsignedByte();
        if (typeId != END) {
            skipPayload(in, typeId, 0);
        }
    }

    private static void skipPayload(ByteBuf in, int typeId, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("NBT nested deeper than " + MAX_DEPTH);
        }
        switch (typeId) {
            case BYTE -> in.skipBytes(1);
            case SHORT -> in.skipBytes(2);
            case INT, FLOAT -> in.skipBytes(4);
            case LONG, DOUBLE -> in.skipBytes(8);
            case BYTE_ARRAY -> in.skipBytes(in.readInt());
            case STRING -> in.skipBytes(in.readUnsignedShort());
            case LIST -> {
                int elementType = in.readUnsignedByte();
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    skipPayload(in, elementType, depth + 1);
                }
            }
            case COMPOUND -> {
                int childType;
                while ((childType = in.readUnsignedByte()) != END) {
                    in.skipBytes(in.readUnsignedShort()); // the child's name
                    skipPayload(in, childType, depth + 1);
                }
            }
            case INT_ARRAY -> {
                int length = in.readInt();
                in.skipBytes(length * 4);
            }
            case LONG_ARRAY -> {
                int length = in.readInt();
                in.skipBytes(length * 8);
            }
            default -> throw new IllegalStateException("Unknown NBT tag type: " + typeId);
        }
    }
}
