package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class JdbcEventDailyStatsCommandAdapter {

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
}
