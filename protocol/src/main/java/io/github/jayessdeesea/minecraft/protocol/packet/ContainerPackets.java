package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * The Play packets for using a container block such as a chest (protocol 777), IDs from
 * {@code GameProtocols}' registration order, layouts from each packet's stream codec. A chest is
 * opened with {@link UseItemOn}; the server answers {@link OpenScreen} and then the chest's
 * Container Set Content ({@link InventoryPackets}, under the new container ID).
 */
public final class ContainerPackets {

    private ContainerPackets() {
    }

    /**
     * Serverbound "Use Item On", 0x42 ({@code ServerboundUseItemOnPacket}): VarInt hand, block
     * position (packed long), VarInt face, cursor position within the block as three floats,
     * boolean inside, boolean world-border hit, VarInt sequence. Always the main hand, on the top
     * face, at its centre. The server checks reach (about 4.5 blocks from the eyes).
     */
    public record UseItemOn(int x, int y, int z, int sequence) implements Packet {

        public static final int PACKET_ID = 0x42;
        private static final int MAIN_HAND = 0;
        private static final int FACE_UP = 1; // Direction: DOWN, UP, NORTH, SOUTH, WEST, EAST

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            ProtocolCodec.writeVarInt(out, MAIN_HAND);
            out.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
            ProtocolCodec.writeVarInt(out, FACE_UP);
            out.writeFloat(0.5f);
            out.writeFloat(1.0f);
            out.writeFloat(0.5f);
            out.writeBoolean(false);
            out.writeBoolean(false);
            ProtocolCodec.writeVarInt(out, sequence);
        }
    }

    /**
     * Serverbound "Container Click", 0x12 ({@code ServerboundContainerClickPacket}): VarInt
     * container ID, VarInt state ID, short slot, byte button, VarInt mode
     * ({@code ContainerInput}), then the client's prediction - the slots it changed (VarInt count,
     * each a short slot and a hashed stack) and the stack now on the cursor (hashed; empty is a
     * single {@code false}). This client predicts nothing: the server applies the click itself,
     * compares, and sends the corrected slots back - a wrong or empty prediction costs a resync,
     * not the click.
     */
    public record ContainerClick(int containerId, int stateId, int slot, int button, int mode) implements Packet {

        public static final int PACKET_ID = 0x12;
        /** Shift-click: moves the stack to the other side (chest to inventory; armour onto the body). */
        public static final int QUICK_MOVE = 1;
        /** Number key: swaps the slot with hotbar slot {@code button}. */
        public static final int SWAP = 2;

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            ProtocolCodec.writeVarInt(out, containerId);
            ProtocolCodec.writeVarInt(out, stateId);
            out.writeShort(slot);
            out.writeByte(button);
            ProtocolCodec.writeVarInt(out, mode);
            ProtocolCodec.writeVarInt(out, 0); // changed slots: none predicted
            out.writeBoolean(false);           // cursor: empty
        }
    }

    /** Serverbound "Container Close", 0x13 ({@code ServerboundContainerClosePacket}): VarInt container ID. */
    public record CloseContainer(int containerId) implements Packet {

        public static final int PACKET_ID = 0x13;

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            ProtocolCodec.writeVarInt(out, containerId);
        }
    }

    /** Serverbound "Set Carried Item", 0x36 ({@code ServerboundSetCarriedItemPacket}): short hotbar slot, 0-8. */
    public record SetCarriedItem(int hotbarSlot) implements Packet {

        public static final int PACKET_ID = 0x36;

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            out.writeShort(hotbarSlot);
        }
    }

    /**
     * Clientbound "Open Screen", 0x3C ({@code ClientboundOpenScreenPacket}): VarInt container ID,
     * VarInt menu type (registry ID; {@code generic_9x3} is a single chest), then the title as an
     * NBT text component - left unread, the frame being length-isolated.
     */
    public record OpenScreen(int containerId, int menuType) implements Packet {

        public static final int PACKET_ID = 0x3C;

        public static OpenScreen decode(ByteBuf in) {
            return new OpenScreen(ProtocolCodec.readVarInt(in), ProtocolCodec.readVarInt(in));
        }

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            throw new UnsupportedOperationException("Open Screen is clientbound-only");
        }
    }

    /** Clientbound "Container Close", 0x11 ({@code ClientboundContainerClosePacket}): VarInt container ID. */
    public record ContainerClosed(int containerId) implements Packet {

        public static final int PACKET_ID = 0x11;

        public static ContainerClosed decode(ByteBuf in) {
            return new ContainerClosed(ProtocolCodec.readVarInt(in));
        }

        @Override
        public int packetId() {
            return PACKET_ID;
        }

        @Override
        public void encode(ByteBuf out) {
            throw new UnsupportedOperationException("Container Close (clientbound) is never sent by this client");
        }
    }
}
