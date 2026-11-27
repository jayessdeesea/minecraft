package io.github.jayessdeesea.minecraft.app.purecore;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * One class for each way a pure core could read the time, the machine or chance without being handed it, each of
 * which the pure-core rule must refuse (ArchitectureTest), and one that is handed its clock and random source, which
 * it must pass. Never called.
 */
public final class PureCoreFixtures {

    private PureCoreFixtures() {
    }

    public static final class NowInAZone {
        Object read() {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public static final class SystemDefaultZone {
        Object read() {
            return ZoneId.systemDefault();
        }
    }

    public static final class NewDate {
        Object read() {
            return new Date();
        }
    }

    public static final class NewRandom {
        Object read() {
            return new Random();
        }
    }

    public static final class ThreadLocalRandomCurrent {
        Object read() {
            return ThreadLocalRandom.current();
        }
    }

    public static final class RandomUuid {
        Object read() {
            return UUID.randomUUID();
        }
    }

    public static final class SystemProperties {
        Object read() {
            return System.getProperties();
        }
    }

    public static final class LineSeparator {
        Object read() {
            return System.lineSeparator();
        }
    }

    public static final class Console {
        Object read() {
            return System.console();
        }
    }

    public static final class SystemIn {
        Object read() {
            return System.in;
        }
    }

    public static final class InstantNowReference {
        Supplier<Instant> read() {
            return Instant::now;
        }
    }

    public static final class DefaultRandomGenerator {
        Object read() {
            return RandomGenerator.getDefault();
        }
    }

    public static final class NamedRandomGenerator {
        Object read() {
            return RandomGenerator.of("L64X128MixRandom");
        }
    }

    public static final class NestedRandomGeneratorStatic {
        Object read() {
            return RandomGenerator.StreamableGenerator.of("L64X128MixRandom");
        }
    }

    public static final class MathRandom {
        Object read() {
            return Math.random();
        }
    }

    public static final class ClockTick {
        Object read() {
            return Clock.tickSeconds(ZoneOffset.UTC);
        }
    }

    public static final class InstantSourceSystem {
        Object read() {
            return InstantSource.system();
        }
    }

    public static final class NewSplittableRandom {
        Object read() {
            return new SplittableRandom();
        }
    }

    public static final class NewGregorianCalendar {
        Object read() {
            return new GregorianCalendar();
        }
    }

    public static final class ShuffleWithNoSource {
        Object read(List<String> names) {
            Collections.shuffle(names);
            return names;
        }
    }

    public static final class FormatterToAFile {
        Object read() {
            try {
                return new Formatter("landing.txt");
            } catch (Exception notFound) {
                return notFound;
            }
        }
    }

    /** Handed its clock and its random source: pure - a shuffle by the source it was handed is pure too. */
    public static final class Handed {
        Object read(Clock clock, RandomGenerator random, List<String> names) {
            Collections.shuffle(names, random);
            return clock.instant().toString() + random.nextInt() + new Formatter().format("%s", names);
        }
    }
}
