package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendVehicleCommand;
import com.dochiri.indexaggregatebench.application.dto.SeedVehicleCondition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class VehiclePersistenceAdapter {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;

    public VehiclePersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long seed(SeedVehicleCondition condition) {
        long totalRows = (long) condition.days() * condition.idCount() * condition.recordsPerIdPerDay();
        if (totalRows >= BATCH_SIZE * 20L) {
            return seedWithInsertSelect(condition);
        }
        return seedWithJdbcBatch(condition);
    }

    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE vehicles");
    }

    public void append(AppendVehicleCommand command) {
        jdbcTemplate.update("""
                INSERT INTO vehicles (
                    id,
                    battery_id,
                    started_at,
                    ended_at,
                    distance_meters,
                    consumed_wh
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                command.id(),
                command.batteryId(),
                command.startedAt(),
                command.endedAt(),
                command.distanceMeters(),
                command.consumedWh()
        );
    }

    private long insertBatch(List<LogRow> rows) {
        String sql = """
                INSERT INTO vehicles (
                    id,
                    battery_id,
                    started_at,
                    ended_at,
                    distance_meters,
                    consumed_wh
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        int[][] counts = jdbcTemplate.batchUpdate(sql, rows, rows.size(), (PreparedStatement ps, LogRow row) -> {
            ps.setLong(1, row.id());
            ps.setLong(2, row.batteryId());
            ps.setObject(3, row.startedAt());
            ps.setObject(4, row.endedAt());
            ps.setInt(5, row.distanceMeters());
            ps.setInt(6, row.consumedWh());
        });

        long inserted = 0;
        for (int[] batchCounts : counts) {
            for (int count : batchCounts) {
                inserted += count;
            }
        }
        return inserted;
    }

    private long seedWithJdbcBatch(SeedVehicleCondition condition) {
        List<LogRow> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        for (int day = 0; day < condition.days(); day++) {
            LocalDate date = condition.from().plusDays(day);
            for (long id = 1; id <= condition.idCount(); id++) {
                long batteryId = (id % Math.max(1, condition.idCount() / 2)) + 1;
                for (int log = 0; log < condition.recordsPerIdPerDay(); log++) {
                    batch.add(randomLog(date, id, batteryId));
                    if (batch.size() == BATCH_SIZE) {
                        inserted += insertBatch(batch);
                        batch.clear();
                    }
                }
            }
        }

        if (!batch.isEmpty()) {
            inserted += insertBatch(batch);
        }

        return inserted;
    }

    private long seedWithInsertSelect(SeedVehicleCondition condition) {
        int maxSequence = Math.max(condition.idCount(), condition.recordsPerIdPerDay());
        createSequenceTable(maxSequence);

        long inserted = 0;
        int batteryRange = Math.max(1, condition.idCount() / 2);

        String sql = """
                INSERT INTO vehicles (
                    id,
                    battery_id,
                    started_at,
                    ended_at,
                    distance_meters,
                    consumed_wh
                )
                SELECT
                    v.n + 1,
                    ((v.n + 1) % ?) + 1,
                    TIMESTAMPADD(SECOND, ((l.n * 53 + v.n * 17 + ? * 31) % 86400), DATE_ADD(?, INTERVAL ? DAY)),
                    TIMESTAMPADD(SECOND,
                        300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900),
                        TIMESTAMPADD(SECOND, ((l.n * 53 + v.n * 17 + ? * 31) % 86400), DATE_ADD(?, INTERVAL ? DAY))
                    ),
                    (300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900)) * (5 + ((v.n + l.n + ?) % 20)),
                    GREATEST(1, ((300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900)) * (5 + ((v.n + l.n + ?) % 20))) DIV (4 + ((v.n + l.n + ?) % 5)))
                FROM bench_seq v
                JOIN bench_seq l
                WHERE v.n < ?
                  AND l.n < ?
                """;

        for (int day = 0; day < condition.days(); day++) {
            inserted += jdbcTemplate.update(sql,
                    batteryRange,
                    day, condition.from(), day,
                    day,
                    day, condition.from(), day,
                    day, day,
                    day, day, day,
                    condition.idCount(),
                    condition.recordsPerIdPerDay()
            );
        }

        return inserted;
    }

    private void createSequenceTable(int maxSequence) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS bench_seq (n INT NOT NULL PRIMARY KEY) ENGINE=MEMORY");
        jdbcTemplate.execute("TRUNCATE TABLE bench_seq");
        jdbcTemplate.update("INSERT INTO bench_seq (n) VALUES (0)");

        int size = 1;
        while (size < maxSequence) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO bench_seq (n) SELECT n + ? FROM bench_seq WHERE n + ? < ?",
                    size,
                    size,
                    maxSequence
            );
            size *= 2;
        }
    }

    private LogRow randomLog(LocalDate date, long id, long batteryId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime startedAt = date.atStartOfDay()
                .plusMinutes(random.nextInt(24 * 60))
                .plusSeconds(random.nextInt(60));
        int drivingSeconds = random.nextInt(300, 7_200);
        LocalDateTime endedAt = startedAt.plusSeconds(drivingSeconds);
        int distanceMeters = drivingSeconds * random.nextInt(5, 25);
        int consumedWh = Math.max(1, distanceMeters / random.nextInt(4, 9));

        return new LogRow(id, batteryId, startedAt, endedAt, distanceMeters, consumedWh);
    }

    private record LogRow(
            long id,
            long batteryId,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int distanceMeters,
            int consumedWh
    ) {
    }
}
