/**
 * Netty ChannelHandlers implementing the wire-level layers as a pipeline:
 * length-prefixed framing, compression, encryption, then packet
 * encoding/decoding. Package-private where possible; nothing outside the
 * {@code protocol} module should reference these types directly.
 */
package io.github.jayessdeesea.minecraft.protocol.netty;
