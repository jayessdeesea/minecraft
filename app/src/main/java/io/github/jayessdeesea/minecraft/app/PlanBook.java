package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Role;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every soldier's standing {@link Plan}, kept on disk so a restart finds each where its squad
 * leader put it (docs/military-doctrine.md § 18): one JSON file for the garrison, written on every
 * change. A soldier with none kept is a rifleman. A file that won't read is a fault, not a fresh start.
 */
final class PlanBook {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FILE = "plans.json";

    /** A plan as the file holds it: the role's name, its site, and each directive by kind. */
    record Kept(String role, double[] site, List<KeptDirective> directives) {
    }

    record KeptDirective(String kind, double[] at, String resource, int amount, int[] box) {
    }

    private final Path directory;
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    PlanBook(Path directory) {
        this.directory = directory;
        Path file = directory.resolve(FILE);
        if (Files.exists(file)) {
            try {
                Map<String, Kept> kept = JSON.readValue(file.toFile(), new TypeReference<LinkedHashMap<String, Kept>>() {
                });
                kept.forEach((soldier, k) -> plans.put(soldier, fromKept(k)));
            } catch (IOException e) {
                throw new UncheckedIOException("the soldiers' plans in " + file + " won't read", e);
            }
        }
    }

    /** {@code soldier}'s plan: a rifleman's if none is kept. */
    Plan of(String soldier) {
        return plans.getOrDefault(soldier, Plan.RIFLEMAN);
    }

    /** The plans of {@code soldiers}, a rifleman's for each with none kept. */
    Map<String, Plan> of(List<String> soldiers) {
        Map<String, Plan> of = new LinkedHashMap<>();
        soldiers.forEach(s -> of.put(s, of(s)));
        return of;
    }

    /** Keeps {@code plan} as {@code soldier}'s; says whether it changed anything. */
    boolean put(String soldier, Plan plan) {
        if (plan.equals(of(soldier))) {
            return false;
        }
        if (plan.equals(Plan.RIFLEMAN)) {
            plans.remove(soldier);
        } else {
            plans.put(soldier, plan);
        }
        save();
        return true;
    }

    private synchronized void save() {
        Map<String, Kept> kept = new java.util.TreeMap<>();
        plans.forEach((soldier, plan) -> kept.put(soldier, toKept(plan)));
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(FILE + ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), kept);
            Files.move(temporary, directory.resolve(FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("the soldiers' plans won't write to " + directory, e);
        }
    }

    private static Kept toKept(Plan plan) {
        return new Kept(plan.role().name(), plan.site().map(PlanBook::xyz).orElse(null),
                plan.directives().stream().map(d -> switch (d) {
                    case Directive.GoTo(Position at) -> new KeptDirective("GO_TO", xyz(at), null, 0, null);
                    case Directive.Fetch(Resource resource, int amount, Position near) ->
                            new KeptDirective("FETCH", xyz(near), resource.name(), amount, null);
                    case Directive.Level(int minX, int minZ, int maxX, int maxZ, int y) ->
                            new KeptDirective("LEVEL", null, null, 0, new int[]{minX, minZ, maxX, maxZ, y});
                }).toList());
    }

    private static Plan fromKept(Kept kept) {
        return new Plan(Role.valueOf(kept.role()), Optional.ofNullable(kept.site()).map(PlanBook::position),
                kept.directives().stream().map(d -> (Directive) switch (d.kind()) {
                    case "GO_TO" -> new Directive.GoTo(position(d.at()));
                    case "FETCH" -> new Directive.Fetch(Resource.valueOf(d.resource()), d.amount(), position(d.at()));
                    case "LEVEL" -> new Directive.Level(d.box()[0], d.box()[1], d.box()[2], d.box()[3], d.box()[4]);
                    default -> throw new IllegalArgumentException("a kept directive of no kind known: " + d.kind());
                }).toList());
    }

    private static double[] xyz(Position p) {
        return new double[]{p.x(), p.y(), p.z()};
    }

    private static Position position(double[] xyz) {
        return new Position(xyz[0], xyz[1], xyz[2]);
    }
}
