package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplyTest {

    @Test
    void theLevelsAreSetFromTheBurnRate() {
        Supply rations = new Supply("Class I (rations)", 300, 80, 20, 1);

        assertEquals(160, rations.safety(), 1e-9, "two days' upkeep");
        assertEquals(260, rations.reorderPoint(), 1e-9, "and a lead time's burn");
        assertEquals(280, rations.objective(), 1e-9, "and a day's committed work");
        assertEquals(3.0, rations.days(), 1e-9);
        assertEquals(Supply.State.ADVANCE, rations.state());
        assertEquals(Supply.State.HOLD, new Supply("c", 200, 80, 20, 1).state());
        assertEquals(Supply.State.PAUSE, new Supply("c", 150, 80, 20, 1).state());
    }

    @Test
    void upkeepIsTheFallsInTheStockNotItsRises() {
        long day = 1_200_000;
        Supply.Burn burn = new Supply.Burn(day);
        burn.stock(100, 0);
        burn.stock(90, day / 4);       // 10 eaten
        burn.stock(150, day / 2);      // 60 baked: not consumption
        burn.stock(120, day);          // 30 eaten

        assertEquals(40, burn.perDay(), 1e-9, "40 eaten over the day");
        burn.stock(100, 2 * day);      // 20 more over the second day
        assertEquals(30, burn.perDay(), 1e-9, "60 over two days");
    }

    @Test
    void lessThanADaySeenCountsAsADay() {
        Supply.Burn burn = new Supply.Burn(1_200_000);
        burn.stock(64, 0);
        burn.stock(60, 60_000);

        assertEquals(4, burn.perDay(), 1e-9, "a minute's meal isn't eighty a day");
    }
}
