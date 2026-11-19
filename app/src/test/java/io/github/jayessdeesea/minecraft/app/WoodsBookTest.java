package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodsBookTest {

    @TempDir
    Path state;

    /** A chain of command that keeps what it's told of woods cut out. */
    private static final class Told extends ChainOfCommand {
        final List<String> woods = new ArrayList<>();

        Told() {
            super((io.github.jayessdeesea.minecraft.command.PlatoonLeader) Proxy.newProxyInstance(
                            io.github.jayessdeesea.minecraft.command.PlatoonLeader.class.getClassLoader(),
                            new Class<?>[]{io.github.jayessdeesea.minecraft.command.PlatoonLeader.class}, (proxy, method, args) -> null),
                    ChatClient.create((ChatModel) prompt -> {
                        throw new AssertionError("no model call expected");
                    }), new SquadLeaderPrompts("", ""), new Roster("Alpha:Alice"), true, "model");
        }

        @Override
        public void onWoodSpent(Position at, long millis) {
            woods.add(Math.round(at.x()) + "," + Math.round(at.z()) + "@" + millis);
        }
    }

    /** A wood found cut out is still cut out after a restart; one a day old is forgotten when the next is kept. */
    @Test
    void theWoodsCutOutSurviveARestart() {
        Told before = new Told();
        WoodsBook book = new WoodsBook(new WallStore(state, "chests"));
        book.spent(before, new Position(-26, 65, 78), 1000);
        assertEquals(List.of("-26,78@1000"), before.woods);

        Told after = new Told();
        new WoodsBook(new WallStore(state, "chests")).restoreInto(after);
        assertEquals(List.of("-26,78@1000"), after.woods);

        book.spent(before, new Position(60, 64, 47), 1000 + WoodSightings.KEPT_MILLIS + 1);
        Told later = new Told();
        new WoodsBook(new WallStore(state, "chests")).restoreInto(later);
        assertEquals(List.of("60,47@" + (1000 + WoodSightings.KEPT_MILLIS + 1)), later.woods, "the day-old one forgotten");
    }
}
