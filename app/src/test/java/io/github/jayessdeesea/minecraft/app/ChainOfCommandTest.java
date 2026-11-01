package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainOfCommandTest {

    @Test
    void chatLineIsAMeCommandWithKickCharactersBlanked() {
        assertEquals("me [Alpha -> squad] FRAGO: two lines  red",
                ChainOfCommand.chatLine("[Alpha -> squad] FRAGO: two lines\n§red"));
    }

    @Test
    void chatLineIsCutToChatLength() {
        assertTrue(ChainOfCommand.chatLine("x".repeat(1000)).length() <= ChainOfCommand.CHAT_LINE_LIMIT);
    }
}
