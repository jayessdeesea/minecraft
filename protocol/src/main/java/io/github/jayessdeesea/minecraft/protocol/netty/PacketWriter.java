package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.function.Consumer;

/**
 * Shared by every client handler in this package: writes a packet ID followed by a
 * caller-supplied body into a fresh buffer and flushes it. A stateless static helper rather than
 * a base class - no handler is conceptually "a kind of" packet writer, this is pure code reuse
 * (see AGENTS.md's note on preferring composition/static helpers over inheritance for this shape
 * of shared tool logic).
 */
final class PacketWriter {

    private PacketWriter() {
    }

    static void write(ChannelHandlerContext ctx, int packetId, Consumer<ByteBuf> bodyWriter) {
        ByteBuf body = ctx.alloc().buffer();
        ProtocolCodec.writeVarInt(body, packetId);
        bodyWriter.accept(body);
        ctx.writeAndFlush(body);
    }
}
