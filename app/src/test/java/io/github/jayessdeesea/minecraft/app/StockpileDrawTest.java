package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockpileDrawTest {

    private static final Optional<Position> CHEST = Optional.of(new Position(0.5, 64, 0.5));
    private static final Optional<String> NO_TREE = Optional.of("can't make wooden sword: no LOG within 48 blocks");

    @Test
    void aSoldierWithNoTreeInReachDrawsLogsForItsKitFromTheStockpile() {
        assertTrue(SoldierRunner.drawsLogs(NO_TREE, 0, Optional.of(new Stockpile(85, 0, 0)), CHEST));
        assertFalse(SoldierRunner.drawsLogs(NO_TREE, 0, Optional.of(new Stockpile(3, 0, 0)), CHEST), "too few in the chest");
        assertTrue(SoldierRunner.drawsLogs(NO_TREE, 0, Optional.empty(), CHEST), "the chest unseen since a restart: it goes to look");
        assertFalse(SoldierRunner.drawsLogs(Optional.empty(), 0, Optional.of(new Stockpile(85, 0, 0)), CHEST), "its kit not wanting logs");
        assertFalse(SoldierRunner.drawsLogs(NO_TREE, 0, Optional.of(new Stockpile(85, 0, 0)), Optional.empty()), "no chest of its own");
    }
}
