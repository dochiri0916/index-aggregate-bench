package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.infrastructure.cache.AtomicAggregate;
import com.dochiri.indexaggregatebench.infrastructure.cache.WriteBehindBuffer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcEventDailyStatsCommandAdapter {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventDailyStatsCommandAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE event_daily_stats");
    }

    public int rebuild(LocalDate from, LocalDate to) {
        jdbcTemplate.update("DELETE FROM event_daily_stats WHERE stat_date BETWEEN ? AND ?", from, to);

        return jdbcTemplate.update("""
                INSERT INTO event_daily_stats (
                    stat_date,
                    target_id,
                    segment_id,
                    log_count,
                    total_duration_seconds,
                    total_metric_value,
                    total_cost_value
                )
                SELECT
                    DATE(occurred_at),
                    target_id,
                    segment_id,
                    COUNT(*),
                    SUM(duration_seconds),
                    SUM(metric_value),
                    SUM(cost_value)
                FROM event_logs
                WHERE occurred_at >= ?
                  AND occurred_at < DATE_ADD(?, INTERVAL 1 DAY)
                GROUP BY DATE(occurred_at), target_id, segment_id
                """, from, to);
    }

    public void increase(AppendEventCommand command) {
        jdbcTemplate.update("""
                INSERT INTO event_daily_stats (
                    stat_date,
                    target_id,
                    segment_id,
                    log_count,
                    total_duration_seconds,
                    total_metric_value,
                    total_cost_value
                )
                VALUES (?, ?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    log_count = log_count + 1,
                    total_duration_seconds = total_duration_seconds + VALUES(total_duration_seconds),
                    total_metric_value = total_metric_value + VALUES(total_metric_value),
                    total_cost_value = total_cost_value + VALUES(total_cost_value)
                """,
                command.occurredAt().toLocalDate(),
                command.targetId(),
                command.segmentId(),
                command.durationSeconds(),
                command.metricValue(),
                command.costValue()
        );
    }

    public int increaseBatch(List<WriteBehindBuffer.MergedCommand> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO event_daily_stats (
                    stat_date,
                    target_id,
                    segment_id,
                    log_count,
                    total_duration_seconds,
                    total_metric_value,
                    total_cost_value
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    log_count = log_count + VALUES(log_count),
                    total_duration_seconds = total_duration_seconds + VALUES(total_duration_seconds),
                    total_metric_value = total_metric_value + VALUES(total_metric_value),
                    total_cost_value = total_cost_value + VALUES(total_cost_value)
                """;

        List<WriteBehindBuffer.MergedCommand> remaining = batch;
        int totalApplied = 0;
        while (!remaining.isEmpty()) {
            int chunkSize = Math.min(remaining.size(), BATCH_SIZE);
            List<WriteBehindBuffer.MergedCommand> chunk = remaining.subList(0, chunkSize);
            remaining = remaining.subList(chunkSize, remaining.size());

            int[][] counts = jdbcTemplate.batchUpdate(sql, chunk, chunk.size(),
                    (PreparedStatement ps, WriteBehindBuffer.MergedCommand cmd) -> {
                        ps.setObject(1, cmd.key().statDate());
                        ps.setLong(2, cmd.key().targetId());
                        ps.setLong(3, cmd.key().segmentId());
                        ps.setLong(4, cmd.logCount());
                        ps.setLong(5, cmd.totalDurationSeconds());
                        ps.setLong(6, cmd.totalMetricValue());
                        ps.setLong(7, cmd.totalCostValue());
                    });

            for (int[] batchCounts : counts) {
                for (int count : batchCounts) {
                    totalApplied += count;
                }
            }
        }
        return totalApplied;
    }

    public Map<DailyStatsKey, AtomicAggregate> loadCells(EventStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    stat_date,
                    target_id,
                    segment_id,
                    log_count,
                    total_duration_seconds,
                    total_metric_value,
                    total_cost_value
                FROM event_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(query.from());
        params.add(query.to());

        if (query.targetId() != null) {
            sql.append("  AND target_id = ?\n");
            params.add(query.targetId());
        }
        if (query.segmentId() != null) {
            sql.append("  AND segment_id = ?\n");
            params.add(query.segmentId());
        }

        Map<DailyStatsKey, AtomicAggregate> cells = new HashMap<>();
        jdbcTemplate.query(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> {
            while (rs.next()) {
                LocalDate statDate = rs.getDate("stat_date").toLocalDate();
                long targetId = rs.getLong("target_id");
                long segmentId = rs.getLong("segment_id");
                long logCount = rs.getLong("log_count");
                long duration = rs.getLong("total_duration_seconds");
                long metric = rs.getLong("total_metric_value");
                long cost = rs.getLong("total_cost_value");

                DailyStatsKey key = new DailyStatsKey(statDate, targetId, segmentId);
                AtomicAggregate agg = new AtomicAggregate();
                agg.add(logCount, duration, metric, cost);
                cells.put(key, agg);
            }
        });
        return cells;
    }

    public EventStats aggregateFromDailyStats(EventStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(log_count), 0) AS log_count,
                    COALESCE(SUM(total_duration_seconds), 0) AS total_duration_seconds,
                    COALESCE(SUM(total_metric_value), 0) AS total_metric_value,
                    COALESCE(SUM(total_cost_value), 0) AS total_cost_value
                FROM event_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(query.from());
        params.add(query.to());

        if (query.targetId() != null) {
            sql.append("  AND target_id = ?\n");
            params.add(query.targetId());
        }
        if (query.segmentId() != null) {
            sql.append("  AND segment_id = ?\n");
            params.add(query.segmentId());
        }

        return jdbcTemplate.query(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> {
            if (!rs.next()) {
                return emptyStats();
            }

            long logCount = rs.getLong("log_count");
            long totalDurationSeconds = rs.getLong("total_duration_seconds");
            long totalMetricValue = rs.getLong("total_metric_value");
            long totalCostValue = rs.getLong("total_cost_value");

            return new EventStats(
                    logCount,
                    totalDurationSeconds,
                    totalMetricValue,
                    totalCostValue,
                    average(totalDurationSeconds, logCount),
                    ratio(totalMetricValue, totalCostValue)
            );
        });
    }

    private EventStats emptyStats() {
        return new EventStats(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal average(long total, long count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long totalMetricValue, long totalCostValue) {
        if (totalCostValue == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalMetricValue)
                .divide(BigDecimal.valueOf(totalCostValue), 2, RoundingMode.HALF_UP);
    }
}
