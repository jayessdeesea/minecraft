package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/** One entry of a Known Packs list (docs/protocol-spec.md § 3.4): a data pack identity. */
public record KnownPack(String namespace, String id, String version) {

    public static KnownPack decode(ByteBuf in) {
        return new KnownPack(ProtocolCodec.readString(in), ProtocolCodec.readString(in), ProtocolCodec.readString(in));
    }

    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, namespace);
        ProtocolCodec.writeString(out, id);
        ProtocolCodec.writeString(out, version);
    }
}
