package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class EventLogPersistenceAdapter implements EventLogPort {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;

    public EventLogPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long seed(SeedEventCondition condition) {
        long totalRows = (long) condition.days() * condition.targetCount() * condition.recordsPerTargetPerDay();
        if (totalRows >= BATCH_SIZE * 20L) {
            return seedWithInsertSelect(condition);
        }
        return seedWithJdbcBatch(condition);
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE event_logs");
    }

    @Override
    public void append(AppendEventCommand command) {
        jdbcTemplate.update("""
                INSERT INTO event_logs (
                    target_id,
                    segment_id,
                    occurred_at,
                    duration_seconds,
                    metric_value,
                    cost_value
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                command.targetId(),
                command.segmentId(),
                command.occurredAt(),
                command.durationSeconds(),
                command.metricValue(),
                command.costValue()
        );
    }

    private long insertBatch(List<LogRow> rows) {
        String sql = """
                INSERT INTO event_logs (
                    target_id,
                    segment_id,
                    occurred_at,
                    duration_seconds,
                    metric_value,
                    cost_value
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        int[][] counts = jdbcTemplate.batchUpdate(sql, rows, rows.size(), (PreparedStatement ps, LogRow row) -> {
            ps.setLong(1, row.targetId());
            ps.setLong(2, row.segmentId());
            ps.setObject(3, row.occurredAt());
            ps.setInt(4, row.durationSeconds());
            ps.setInt(5, row.metricValue());
            ps.setInt(6, row.costValue());
        });

        long inserted = 0;
        for (int[] batchCounts : counts) {
            for (int count : batchCounts) {
                inserted += count;
            }
        }
        return inserted;
    }

    private long seedWithJdbcBatch(SeedEventCondition condition) {
        List<LogRow> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        for (int day = 0; day < condition.days(); day++) {
            LocalDate date = condition.from().plusDays(day);
            for (long targetId = 1; targetId <= condition.targetCount(); targetId++) {
                long segmentId = (targetId % Math.max(1, condition.targetCount() / 2)) + 1;
                for (int log = 0; log < condition.recordsPerTargetPerDay(); log++) {
                    batch.add(randomLog(date, targetId, segmentId));
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

    private long seedWithInsertSelect(SeedEventCondition condition) {
        int maxSequence = Math.max(condition.targetCount(), condition.recordsPerTargetPerDay());
        createSequenceTable(maxSequence);

        long inserted = 0;
        int segmentRange = Math.max(1, condition.targetCount() / 2);

        String sql = """
                INSERT INTO event_logs (
                    target_id,
                    segment_id,
                    occurred_at,
                    duration_seconds,
                    metric_value,
                    cost_value
                )
                SELECT
                    v.n + 1,
                    ((v.n + 1) % ?) + 1,
                    TIMESTAMPADD(SECOND, ((l.n * 53 + v.n * 17 + ? * 31) % 86400), DATE_ADD(?, INTERVAL ? DAY)),
                    300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900),
                    (300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900)) * (5 + ((v.n + l.n + ?) % 20)),
                    GREATEST(1, ((300 + ((v.n * 13 + l.n * 29 + ? * 7) % 6900)) * (5 + ((v.n + l.n + ?) % 20))) DIV (4 + ((v.n + l.n + ?) % 5)))
                FROM bench_seq v
                JOIN bench_seq l
                WHERE v.n < ?
                  AND l.n < ?
                """;

        for (int day = 0; day < condition.days(); day++) {
            inserted += jdbcTemplate.update(sql,
                    segmentRange,
                    day, condition.from(), day,
                    day,
                    day, day,
                    day, day, day,
                    condition.targetCount(),
                    condition.recordsPerTargetPerDay()
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

    private LogRow randomLog(LocalDate date, long targetId, long segmentId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime occurredAt = date.atStartOfDay()
                .plusMinutes(random.nextInt(24 * 60))
                .plusSeconds(random.nextInt(60));
        int durationSeconds = random.nextInt(300, 7_200);
        int metricValue = durationSeconds * random.nextInt(5, 25);
        int costValue = Math.max(1, metricValue / random.nextInt(4, 9));

        return new LogRow(targetId, segmentId, occurredAt, durationSeconds, metricValue, costValue);
    }

    private record LogRow(
            long targetId,
            long segmentId,
            LocalDateTime occurredAt,
            int durationSeconds,
            int metricValue,
            int costValue
    ) {
    }
}
