package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcEventDailyStatsCommandAdapter implements EventDailyStatsPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventDailyStatsCommandAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE event_daily_stats");
    }

    @Override
    public int rebuild(LocalDate from, LocalDate to) {
        jdbcTemplate.update("DELETE FROM event_daily_stats WHERE stat_date BETWEEN ? AND ?", from, to);
        return jdbcTemplate.update("""
                INSERT INTO event_daily_stats (
                    stat_date, target_id, segment_id, log_count,
                    total_duration_seconds, total_metric_value, total_cost_value
                )
                SELECT
                    DATE(occurred_at), target_id, segment_id, COUNT(*),
                    SUM(duration_seconds), SUM(metric_value), SUM(cost_value)
                FROM event_logs
                WHERE occurred_at >= ?
                  AND occurred_at < DATE_ADD(?, INTERVAL 1 DAY)
                GROUP BY DATE(occurred_at), target_id, segment_id
                """, from, to);
    }

    @Override
    public void increase(AppendEventCommand command) {
        jdbcTemplate.update("""
                INSERT INTO event_daily_stats (
                    stat_date, target_id, segment_id, log_count,
                    total_duration_seconds, total_metric_value, total_cost_value
                )
                VALUES (?, ?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    log_count = log_count + 1,
                    total_duration_seconds = total_duration_seconds + VALUES(total_duration_seconds),
                    total_metric_value = total_metric_value + VALUES(total_metric_value),
                    total_cost_value = total_cost_value + VALUES(total_cost_value)
                """,
                command.occurredAt().toLocalDate(), command.targetId(), command.segmentId(),
                command.durationSeconds(), command.metricValue(), command.costValue()
        );
    }

    @Override
    public EventStats aggregate(EventStatsQuery query) {
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
                    logCount, totalDurationSeconds, totalMetricValue, totalCostValue,
                    average(totalDurationSeconds, logCount), ratio(totalMetricValue, totalCostValue)
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

    private BigDecimal ratio(long metric, long cost) {
        if (cost == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(metric).divide(BigDecimal.valueOf(cost), 2, RoundingMode.HALF_UP);
    }
}
