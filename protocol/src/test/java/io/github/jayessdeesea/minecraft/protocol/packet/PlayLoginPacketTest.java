package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayLoginPacketTest {

    /**
     * Field values from the live dev server capture (see the class javadoc): entityId, hardcore,
     * dimensionNames, maxPlayers, viewDistance, simulationDistance, and gameMode are all
     * independently cross-checked against the server console log and server.properties.
     * hashedSeed is the real captured bytes too, but - being a hash - has no independent value to
     * check it against; it's here for realism, not as a second confirmed field.
     */
    @Test
    void roundTripsTheFieldsConfirmedAgainstTheLiveServer() {
        PlayLoginPacket original = new PlayLoginPacket(
                1441, false,
                List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"),
                20, 10, 10,
                false, true, false,
                0, "minecraft:overworld",
                0xB9E145D262E4A8D1L,
                0, -1,
                false, false, false);

        ByteBuf buf = Unpooled.buffer();
        original.encode(buf);
        PlayLoginPacket decoded = PlayLoginPacket.decode(buf);

        assertEquals(original, decoded);
    }
}
