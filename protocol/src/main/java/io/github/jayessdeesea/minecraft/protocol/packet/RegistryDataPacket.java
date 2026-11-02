package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound Configuration-state packet, officially "Registry Data" (protocol 777), 0x07 - ID
 * from {@code ConfigurationProtocols}' clientbound registration order.
 *
 * <p>Sent once per data-driven registry during Configuration (dimension_type, biome, damage_type,
 * and a dozen or so others) - a registry name, then a list of entries, each an identifier plus an
 * optional NBT payload. Decodes the registry name and every entry's identifier (useful and cheap -
 * "what entries does dimension_type have" is now answerable without guessing) but does not parse
 * each entry's NBT payload's actual contents, only skips past it: that payload's schema differs
 * per registry (dimension_type's height/min_y, biome's climate params, etc.) and nothing consumes
 * any of it yet. The frame is already length-isolated, so
 * skipping instead of parsing is safe, same precedent as {@link PlayLoginPacket}'s trailing fields.
 *
 * <p>Confirmed by decompiling {@code ClientboundRegistryDataPacket}/{@code
 * RegistrySynchronization.PackedRegistryEntry}: the registry name and each entry's identifier are
 * length-prefixed UTF-8 strings (this protocol's usual VarInt-prefixed string, not NBT); each
 * entry's payload presence is one boolean, and when present, an NBT value in the same
 * root-name-omitted shape {@code FriendlyByteBuf.writeNbt}/{@code NbtIo.writeAnyTag} always use
 * (see {@link Nbt}) - not the "network NBT" convention Minecraft uses for some other fields, but
 * the same result for a root value either way (no name), which is what matters for skipping it.
 */
public record RegistryDataPacket(String registry, List<String> entryIds) implements Packet {

    public static final int PACKET_ID = 0x07;

    /** Generous cap - vanilla's largest registry (biome-adjacent lists) has well under 200 entries. */
    private static final int MAX_ENTRIES = 8192;

    public static RegistryDataPacket decode(ByteBuf in) {
        String registry = ProtocolCodec.readString(in);
        int count = ProtocolCodec.readVarIntCount(in, MAX_ENTRIES);
        List<String> entryIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entryIds.add(ProtocolCodec.readString(in));
            boolean hasData = in.readBoolean();
            if (hasData) {
                Nbt.skipUnnamedRoot(in);
            }
        }
        return new RegistryDataPacket(registry, List.copyOf(entryIds));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("RegistryDataPacket is clientbound-only and never sent by this client");
    }
}
