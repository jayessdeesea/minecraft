package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisguisedChatPacketTest {

    private static void string(ByteBuf out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.writeBytes(bytes);
    }

    /** A player's name as the server sends it: a compound with its text and a hover event naming the entity. */
    private static void playerName(ByteBuf out, String name) {
        out.writeByte(10);                // compound
        out.writeByte(8);                 // "insertion": name
        string(out, "insertion");
        string(out, name);
        out.writeByte(10);                // "hover_event": { action: show_entity }
        string(out, "hover_event");
        out.writeByte(8);
        string(out, "action");
        string(out, "show_entity");
        out.writeByte(0);
        out.writeByte(8);                 // "text": name
        string(out, "text");
        string(out, name);
        out.writeByte(0);
    }

    @Test
    void aWhisperIsReadAsItsTextAndItsSendersName() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(8);                  // the message: a plain string tag
        string(in, "!escort");
        ProtocolCodec.writeVarInt(in, 3); // chat type msg_command_incoming, registry id 2
        playerName(in, "Jake");
        in.writeBoolean(true);            // a target name, left unread
        in.writeByte(8);
        string(in, "Bob");

        assertEquals(new DisguisedChatPacket("!escort", "Jake"), DisguisedChatPacket.decode(in));
    }

    @Test
    void aMessageWithExtraPartsReadsAsTheirConcatenation() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(10);
        in.writeByte(8);
        string(in, "text");
        string(in, "!mission ");
        in.writeByte(9);                  // "extra": [ "hold ", { text: "x=0" } ]
        string(in, "extra");
        in.writeByte(8);
        in.writeInt(1);
        string(in, "hold x=0");
        in.writeByte(0);
        ProtocolCodec.writeVarInt(in, 1);
        in.writeByte(8);
        string(in, "Jake");

        assertEquals(new DisguisedChatPacket("!mission hold x=0", "Jake"), DisguisedChatPacket.decode(in));
    }

    @Test
    void aChatTypeSentInlineFailsLoudly() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(8);
        string(in, "hi");
        ProtocolCodec.writeVarInt(in, 0);

        assertThrows(IllegalStateException.class, () -> DisguisedChatPacket.decode(in));
    }
}
