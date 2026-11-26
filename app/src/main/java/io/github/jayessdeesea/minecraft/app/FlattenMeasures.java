package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Clearer;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.FlattenPlan;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Slopes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * A mission's flatten measured from the world: the plan over its chunk and slopes, what it says while columns are left
 * to cut, and when it is done - the chunk cut, with the slope columns out of reach left as they are once their count has
 * stood for {@link #SLOPE_PATIENCE_MILLIS}. The inputs the missions hold - the chunks made a template of, the chunks of
 * the other open missions - are the caller's to give; what's kept here is each flatten's slope patience, one instance to
 * a {@link FarmMissions}.
 */
final class FlattenMeasures {

    /** How long a flatten's slope columns stand uncut, the chunk itself cut, before the step is done with them left. */
    static final long SLOPE_PATIENCE_MILLIS = 15 * 60_000;
    /** Each flattening mission's slope columns left, and since when that count has stood: {count, since}. */
    private final Map<FarmMission, long[]> slopeLeft = new HashMap<>();

    /** The ground one mission's flatten works: its chunk, down to the step's level. */
    static Flatten ground(FarmMission m, FarmMission.Step step) {
        return new Flatten(m.minX(), m.minZ(), m.minX() + 15, m.minZ() + 15, step.level());
    }

    /**
     * Measures {@code m}'s flatten {@code step} in {@code world} at {@code now}, saying to {@code say} what is left, and
     * the reason if it's done. {@code made} the chunks a template stands on or is going up on (never slopes into them nor
     * dug), {@code open} the chunks of the other open missions.
     */
    Optional<String> measure(WorldView world, FarmMission m, FarmMission.Step step, Set<Long> made, Set<Long> open, long now, Consumer<String> say) {
        Optional<FlattenPlan> plan = plan(world, m, step, made, open);
        // Said a chunk's row of columns at a time, not each block: the count moves with every cut.
        say.accept(plan.map(p -> (p.dig().isEmpty() ? "no" : "up to " + (p.dig().size() + 15) / 16 * 16) + " column(s) to cut"
                + (p.steps().isEmpty() ? "" : ", " + String.join("; ", p.steps()))
                // The last of them named: live (field697), "up to 16 column(s)" stood four hours and said nothing of which.
                + (p.dig().isEmpty() || p.dig().size() > 16 ? "" : " - at " + p.dig().stream().limit(8).map(c -> c.x() + "," + c.z()).toList())).orElse("not all of it in sight"));
        // A block of no known break time over the level is in the way though the plan's ground measure can't see it: not
        // done while it stands, and said (FlattenTopsSimulationTest: a chest in the chunk, the flatten measured done round it).
        Box over = new Box(m.minX(), step.level() + 1, m.minZ(), m.minX() + 15, Keep.SKY_TOP, m.minZ() + 15);
        List<BlockPos> unknown = Clearing.unknownIn(world, over, Set.of());
        List<BlockPos> water = Clearing.waterIn(world, over, Set.of()); // the plan's ground measure sees no water: it's filled and dug first
        unknown.stream().findFirst().ifPresent(c -> say.accept(Clearer.unknownSaid(world, c)));
        // The chunk itself cut and only columns out on its slopes left, their count unchanged for SLOPE_PATIENCE: the
        // step is done with them left and said - the chunk is what the template wants; the slope is how it's walked onto.
        // Live (field697-698), a tree farm's chunk lay flat for five hours while nine columns ten blocks out, eleven
        // up a hill, were handed from one soldier to another every two minutes, neither reaching them.
        Optional<String> slopesLeft = plan.filter(p -> !p.cut() && unknown.isEmpty() && water.isEmpty() && chunkCut(m, p)).flatMap(p -> {
            long[] seen = slopeLeft.computeIfAbsent(m, k -> new long[]{p.dig().size(), now});
            if (seen[0] != p.dig().size()) {
                seen[0] = p.dig().size();
                seen[1] = now;
            }
            return now - seen[1] < SLOPE_PATIENCE_MILLIS ? Optional.<String>empty() : Optional.of("nothing over y=" + step.level() + " in the chunk; "
                    + p.dig().size() + " column(s) out on its slopes left as they are, none cut in " + SLOPE_PATIENCE_MILLIS / 60_000 + " min - out of reach: "
                    + p.dig().stream().limit(8).map(c -> c.x() + "," + c.z() + " at y=" + c.from()).toList());
        });
        if (plan.filter(p -> chunkCut(m, p) && !p.cut()).isEmpty()) {
            slopeLeft.remove(m);
        }
        return plan.filter(FlattenPlan::cut).filter(p -> unknown.isEmpty() && water.isEmpty()).map(p -> "nothing over y=" + step.level() + " in the chunk or its " + slopes(m, made, open).said()
                + (p.fill().isEmpty() ? "" : ", " + p.fill().size() + " column(s) under it - the floor's inside, the ground's outside")).or(() -> slopesLeft);
    }

    /** Whether {@code plan} has nothing left to cut in {@code m}'s own chunk: what's left, if anything, is out on its slopes. */
    static boolean chunkCut(FarmMission m, FlattenPlan plan) {
        return plan.dig().stream().noneMatch(c -> c.x() >= m.minX() && c.x() <= m.minX() + 15 && c.z() >= m.minZ() && c.z() <= m.minZ() + 15);
    }

    /** The flatten's plan for {@code m} as {@code world} stands, if every column it works is in sight. */
    Optional<FlattenPlan> plan(WorldView world, FarmMission m, FarmMission.Step step, Set<Long> made, Set<Long> open) {
        Slopes slopes = slopes(m, made, open);
        BiPredicate<Integer, Integer> kept = KeptGround.columns(made, m);
        if (FlattenPlan.reach(m.chunkX(), m.chunkZ(), step.level(), slopes, kept).stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
            return Optional.empty();
        }
        return Optional.of(FlattenPlan.of(m.chunkX(), m.chunkZ(), step.level(), slopes, (x, z) -> top(world, x, z), kept));
    }

    /** The columns {@code m}'s flatten at {@code step} works: its chunk and its slopes ({@link FlattenPlan#reach}). */
    static List<FlattenPlan.Column> reach(FarmMission m, FarmMission.Step step, Set<Long> made, Set<Long> open) {
        return FlattenPlan.reach(m.chunkX(), m.chunkZ(), step.level(), slopes(m, made, open), KeptGround.columns(made, m));
    }

    /** The highest solid block's y at ({@code x}, {@code z}): down from the top of the world, an open section at a time. */
    static int top(WorldView world, int x, int z) {
        for (int y = Keep.SKY_TOP; y > -64; y--) {
            int bottom = Math.floorDiv(y, 16) * 16;
            if (y > bottom && world.openSection(x, y, z)) {
                y = bottom + 1;
                continue;
            }
            if (world.isSolid(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The slopes of {@code m}'s flatten: each side sloped unless the chunk past it is a template's ({@code made}) - or
     * another open mission's ({@code open}), to be cut to its own level: a slope into it is that ground cut twice.
     */
    static Slopes slopes(FarmMission m, Set<Long> made, Set<Long> open) {
        return Slopes.around(m.chunkX(), m.chunkZ(), (cx, cz) -> made.contains(ChunkFarm.key(cx, cz)) || open.contains(ChunkFarm.key(cx, cz)));
    }
}
