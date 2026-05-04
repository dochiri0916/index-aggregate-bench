package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.BenchmarkProperties;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Repository
public class JdbcEventStatsAdapter {

    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkProperties benchmarkProperties;

    public JdbcEventStatsAdapter(JdbcTemplate jdbcTemplate, BenchmarkProperties benchmarkProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.benchmarkProperties = benchmarkProperties;
    }

    public EventStats aggregateFromRawEventLogs(EventStatsQuery query) {
        QueryParts parts = rawGroupByQuery(query);
        return jdbcTemplate.query(parts.sql(), ps -> {
            for (int i = 0; i < parts.params().size(); i++) {
                ps.setObject(i + 1, parts.params().get(i));
            }
        }, rs -> {
            long logCount = 0;
            long totalDurationSeconds = 0;
            long totalMetricValue = 0;
            long totalCostValue = 0;

            while (rs.next()) {
                logCount += rs.getLong("log_count");
                totalDurationSeconds += rs.getLong("total_duration_seconds");
                totalMetricValue += rs.getLong("total_metric_value");
                totalCostValue += rs.getLong("total_cost_value");
            }

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

    public EventStats aggregateFromDailyStats(EventStatsQuery query) {
        QueryParts parts = dailyStatsQuery(query);
        return jdbcTemplate.query(parts.sql(), ps -> {
            for (int i = 0; i < parts.params().size(); i++) {
                ps.setObject(i + 1, parts.params().get(i));
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

    private QueryParts rawGroupByQuery(EventStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS log_count,
                    SUM(duration_seconds) AS total_duration_seconds,
                    SUM(metric_value) AS total_metric_value,
                    SUM(cost_value) AS total_cost_value
                FROM event_logs
                WHERE occurred_at >= ?
                  AND occurred_at < ?
                """);
        addTimeoutHint(sql);

        List<Object> params = new java.util.ArrayList<>();
        params.add(query.from().atStartOfDay());
        params.add(query.to().plusDays(1).atStartOfDay());

        if (query.targetId() != null) {
            sql.append("  AND target_id = ?\n");
            params.add(query.targetId());
        }
        if (query.segmentId() != null) {
            sql.append("  AND segment_id = ?\n");
            params.add(query.segmentId());
        }

        sql.append("GROUP BY DATE(occurred_at), target_id, segment_id");
        return new QueryParts(sql.toString(), params);
    }

    private QueryParts dailyStatsQuery(EventStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(log_count), 0) AS log_count,
                    COALESCE(SUM(total_duration_seconds), 0) AS total_duration_seconds,
                    COALESCE(SUM(total_metric_value), 0) AS total_metric_value,
                    COALESCE(SUM(total_cost_value), 0) AS total_cost_value
                FROM event_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                """);
        List<Object> params = new java.util.ArrayList<>();
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

        return new QueryParts(sql.toString(), params);
    }

    private void addTimeoutHint(StringBuilder sql) {
        int timeoutMillis = benchmarkProperties.raw().queryTimeoutMillis();
        if (timeoutMillis > 0) {
            int insertPosition = "SELECT".length();
            sql.insert(insertPosition, " /*+ MAX_EXECUTION_TIME(" + timeoutMillis + ") */");
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

    private BigDecimal ratio(long totalMetricValue, long totalCostValue) {
        if (totalCostValue == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalMetricValue)
                .divide(BigDecimal.valueOf(totalCostValue), 2, RoundingMode.HALF_UP);
    }

    private record QueryParts(String sql, List<Object> params) {
    }
}
