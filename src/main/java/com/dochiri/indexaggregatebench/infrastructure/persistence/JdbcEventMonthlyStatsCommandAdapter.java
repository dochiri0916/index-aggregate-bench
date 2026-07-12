package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcEventMonthlyStatsCommandAdapter implements EventMonthlyStatsPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventMonthlyStatsCommandAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE event_monthly_stats_flush_batches");
        jdbcTemplate.update("TRUNCATE TABLE event_monthly_stats");
    }

    @Override
    public int rebuild(LocalDate from, LocalDate to) {
        validateCompleteMonthRange(from, to);
        LocalDate fromMonth = from.withDayOfMonth(1);
        LocalDate toMonth = to.withDayOfMonth(1);
        jdbcTemplate.update("DELETE FROM event_monthly_stats WHERE stat_month BETWEEN ? AND ?", fromMonth, toMonth);
        return jdbcTemplate.update("""
                INSERT INTO event_monthly_stats (
                    stat_month, target_id, segment_id, log_count,
                    total_duration_seconds, total_metric_value, total_cost_value
                )
                SELECT
                    DATE_FORMAT(occurred_at, '%Y-%m-01'), target_id, segment_id, COUNT(*),
                    SUM(duration_seconds), SUM(metric_value), SUM(cost_value)
                FROM event_logs
                WHERE occurred_at >= ?
                  AND occurred_at < ?
                GROUP BY DATE_FORMAT(occurred_at, '%Y-%m-01'), target_id, segment_id
                """, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    @Override
    public int increaseBatch(MonthlyStatsFlushBatch batch) {
        if (batch.isEmpty() || !markBatchAsProcessed(batch.batchId())) {
            return 0;
        }
        String sql = """
                INSERT INTO event_monthly_stats (
                    stat_month, target_id, segment_id, log_count,
                    total_duration_seconds, total_metric_value, total_cost_value
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    log_count = log_count + VALUES(log_count),
                    total_duration_seconds = total_duration_seconds + VALUES(total_duration_seconds),
                    total_metric_value = total_metric_value + VALUES(total_metric_value),
                    total_cost_value = total_cost_value + VALUES(total_cost_value)
                """;
        int[][] counts = jdbcTemplate.batchUpdate(sql, batch.deltas(), batch.deltas().size(), (statement, delta) -> {
            statement.setObject(1, delta.key().statMonth().atDay(1));
            statement.setLong(2, delta.key().targetId());
            statement.setLong(3, delta.key().segmentId());
            statement.setLong(4, delta.logCount());
            statement.setLong(5, delta.totalDurationSeconds());
            statement.setLong(6, delta.totalMetricValue());
            statement.setLong(7, delta.totalCostValue());
        });
        int applied = 0;
        for (int[] countGroup : counts) {
            applied += countGroup.length;
        }
        return applied;
    }

    @Override
    public EventStats aggregate(EventStatsQuery query) {
        validateCompleteMonthRange(query.from(), query.to());
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(log_count), 0) AS log_count,
                    COALESCE(SUM(total_duration_seconds), 0) AS total_duration_seconds,
                    COALESCE(SUM(total_metric_value), 0) AS total_metric_value,
                    COALESCE(SUM(total_cost_value), 0) AS total_cost_value
                FROM event_monthly_stats
                WHERE stat_month BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(query.fromMonth().atDay(1));
        params.add(query.toMonth().atDay(1));
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
                    logCount, totalDurationSeconds, totalMetricValue, totalCostValue,
                    average(totalDurationSeconds, logCount), ratio(totalMetricValue, totalCostValue)
            );
        });
    }

    private boolean markBatchAsProcessed(String batchId) {
        try {
            return jdbcTemplate.update(
                    "INSERT INTO event_monthly_stats_flush_batches (batch_id) VALUES (?)", batchId
            ) > 0;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void validateCompleteMonthRange(LocalDate from, LocalDate to) {
        if (!from.equals(from.withDayOfMonth(1))
                || !to.equals(to.withDayOfMonth(1).plusMonths(1).minusDays(1))) {
            throw new IllegalArgumentException("monthly stats require complete calendar months");
        }
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

    private BigDecimal ratio(long metric, long cost) {
        if (cost == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(metric).divide(BigDecimal.valueOf(cost), 2, RoundingMode.HALF_UP);
    }
}
