package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.farm.StoresChunk;
import io.github.jayessdeesea.minecraft.bot.supply.Fixtures;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Each soldier's hands for a template's fixtures (operator, 2026-11-22: the supply point): while its squad's objective
 * is a supply point's template (`FarmMissions.stores`), what it needs made for it, and its turn at putting it up -
 * `bot.supply.Fixtures`, a soldier each. With no such objective its hands are let go.
 */
final class TemplateFixtures {

    private final Map<String, Fixtures> hands = new ConcurrentHashMap<>();

    private Fixtures of(String soldier, PlaySession session, Inventory inventory) {
        return hands.computeIfAbsent(soldier, n -> new Fixtures(n, session, inventory));
    }

    /** What {@code soldier} needs made to put up {@code stores}' next fixtures: nothing with no supply point to build. */
    Set<Recipe> wants(WorldView world, Optional<StoresChunk> stores, Inventory inventory) {
        return stores.map(s -> Fixtures.wants(world, s.fixtures(), inventory)).orElse(Set.of());
    }

    /** {@code soldier}'s turn: under way while it's putting a kind up; started only free, and out of contact. */
    Hands.Turn turn(String soldier, PlaySession session, Inventory inventory, Position at, WorldView world, Optional<StoresChunk> stores,
                    boolean threatened, Consumer<Position> lookAt) {
        Fixtures fixtures = of(soldier, session, inventory);
        return new Hands.Turn() {
            @Override
            public boolean active() {
                return fixtures.active();
            }

            @Override
            public void tick(boolean free) {
                if (stores.isEmpty() || threatened) {
                    fixtures.stop();
                    return;
                }
                fixtures.tick(at, world, stores.get().fixtures(), free);
                fixtures.lookAt().ifPresent(lookAt);
            }
        };
    }

    /** Where {@code soldier} stands for the fixture under way. */
    Optional<Position> standAt(String soldier) {
        return Optional.ofNullable(hands.get(soldier)).flatMap(Fixtures::standAt);
    }
}
