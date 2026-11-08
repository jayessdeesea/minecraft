/**
 * Packet definitions and their codecs for each protocol state (Handshake,
 * Status, Login, Configuration, Play). These read/write directly against
 * Netty's {@code ByteBuf} - we deliberately did not add a parallel
 * Netty-free framing abstraction on top of Netty's own pipeline decoupling;
 * see docs/architecture.md.
 */
package io.github.jayessdeesea.minecraft.protocol.packet;
