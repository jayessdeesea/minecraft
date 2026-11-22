package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The platoon leader's registers (operator, 2026-11-21): the chunks - keyed by x and z, what the force has made of each,
 * its level and its stage as measured - and the objectives, each a mission's step on a chunk with the squad on it and
 * how it stands. An embedded H2 database in one file, its schema versioned by Flyway ({@code db/registers}); a
 * register's own, never shared - each garrison and each simulation its own file. Every write is one statement or one
 * transaction, so a step done and its chunk's stage agree. Fails loud: a write that doesn't go through throws.
 * Thread-safe: H2 serialises the statements; each call takes its own connection.
 */
final class Registers {

    private final JdbcDataSource source;

    /** The registers in {@code file} (H2 adds ".mv.db"), its schema brought up to date. */
    Registers(Path file) {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + file.toAbsolutePath().toString().replace('\\', '/'));
        Flyway.configure().dataSource(source).locations("classpath:db/registers").load().migrate();
    }

    /** Keeps {@code chunk}, in place of what was kept of the same chunk. */
    void put(ChunkRecord chunk) {
        update("MERGE INTO chunk (chunk_x, chunk_z, kind, level, stage, measured_at) KEY (chunk_x, chunk_z) VALUES (?, ?, ?, ?, ?, ?)", s -> {
            s.setInt(1, chunk.chunkX());
            s.setInt(2, chunk.chunkZ());
            s.setString(3, chunk.kind().name());
            s.setInt(4, chunk.level());
            setOptional(s, 5, chunk.stage());
            s.setLong(6, chunk.measuredAt());
        });
    }

    /** What's kept of the chunk at {@code chunkX}, {@code chunkZ}, if anything. */
    Optional<ChunkRecord> chunk(int chunkX, int chunkZ) {
        return query("SELECT * FROM chunk WHERE chunk_x = ? AND chunk_z = ?", s -> {
            s.setInt(1, chunkX);
            s.setInt(2, chunkZ);
        }, Registers::chunk).stream().findFirst();
    }

    /** Every chunk kept, by x then z. */
    List<ChunkRecord> chunks() {
        return query("SELECT * FROM chunk ORDER BY chunk_x, chunk_z", s -> {
        }, Registers::chunk);
    }

    /** Keeps {@code objective} as a new one, its id the register's: one per mission and step. */
    ObjectiveRecord add(ObjectiveRecord objective) {
        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement("INSERT INTO objective (mission, step, task, chunk_x, chunk_z, squad, state, changed_at, why)"
                     + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, objective.mission());
            s.setInt(2, objective.step());
            s.setString(3, objective.task());
            s.setInt(4, objective.chunkX());
            s.setInt(5, objective.chunkZ());
            setOptional(s, 6, objective.squad());
            s.setString(7, objective.state().name());
            s.setLong(8, objective.changedAt());
            setOptional(s, 9, objective.why());
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                keys.next();
                return new ObjectiveRecord(keys.getLong(1), objective.mission(), objective.step(), objective.task(), objective.chunkX(),
                        objective.chunkZ(), objective.squad(), objective.state(), objective.changedAt(), objective.why());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the objective register didn't take " + objective, e);
        }
    }

    /** Keeps how {@code objective} - one kept before - stands now: its squad, state, when and why. */
    void update(ObjectiveRecord objective) {
        int changed = update("UPDATE objective SET squad = ?, state = ?, changed_at = ?, why = ? WHERE id = ?", s -> {
            setOptional(s, 1, objective.squad());
            s.setString(2, objective.state().name());
            s.setLong(3, objective.changedAt());
            setOptional(s, 4, objective.why());
            s.setLong(5, objective.id());
        });
        if (changed != 1) {
            throw new IllegalStateException("no objective " + objective.id() + " kept to update: " + objective);
        }
    }

    /** Every objective kept, by mission then step. */
    List<ObjectiveRecord> objectives() {
        return query("SELECT * FROM objective ORDER BY mission, step", s -> {
        }, Registers::objective);
    }

    /** {@code squad}'s objective still open - under way or CANTCO, the last given - if it has one. */
    Optional<ObjectiveRecord> open(String squad) {
        return query("SELECT * FROM objective WHERE squad = ? AND state IN ('UNDER_WAY', 'CANTCO') ORDER BY id DESC LIMIT 1", s -> s.setString(1, squad),
                Registers::objective).stream().findFirst();
    }

    /** Every objective still open - under way or CANTCO - by squad. */
    List<ObjectiveRecord> openObjectives() {
        return query("SELECT * FROM objective WHERE state IN ('UNDER_WAY', 'CANTCO') ORDER BY squad, id", s -> {
        }, Registers::objective);
    }

    /** The objectives on the chunk at {@code chunkX}, {@code chunkZ}, by mission then step. */
    List<ObjectiveRecord> objectivesOn(int chunkX, int chunkZ) {
        return query("SELECT * FROM objective WHERE chunk_x = ? AND chunk_z = ? ORDER BY mission, step", s -> {
            s.setInt(1, chunkX);
            s.setInt(2, chunkZ);
        }, Registers::objective);
    }

    private interface Binder {
        void bind(PreparedStatement s) throws SQLException;
    }

    private interface Reader<T> {
        T read(ResultSet r) throws SQLException;
    }

    private int update(String sql, Binder binder) {
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            binder.bind(s);
            return s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("the registers didn't take: " + sql, e);
        }
    }

    private <T> List<T> query(String sql, Binder binder, Reader<T> reader) {
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            binder.bind(s);
            List<T> rows = new ArrayList<>();
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    rows.add(reader.read(r));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("the registers couldn't be read: " + sql, e);
        }
    }

    private static void setOptional(PreparedStatement s, int at, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            s.setString(at, value.get());
        } else {
            s.setNull(at, Types.VARCHAR);
        }
    }

    private static ChunkRecord chunk(ResultSet r) throws SQLException {
        return new ChunkRecord(r.getInt("chunk_x"), r.getInt("chunk_z"), ChunkRecord.Kind.valueOf(r.getString("kind")), r.getInt("level"),
                Optional.ofNullable(r.getString("stage")), r.getLong("measured_at"));
    }

    private static ObjectiveRecord objective(ResultSet r) throws SQLException {
        return new ObjectiveRecord(r.getLong("id"), r.getString("mission"), r.getInt("step"), r.getString("task"), r.getInt("chunk_x"),
                r.getInt("chunk_z"), Optional.ofNullable(r.getString("squad")), ObjectiveRecord.State.valueOf(r.getString("state")),
                r.getLong("changed_at"), Optional.ofNullable(r.getString("why")));
    }
}
