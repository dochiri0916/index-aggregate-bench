package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;

import java.util.ArrayList;
import java.util.List;

record RawStatsQuery(String sql, List<Object> params) {

    static RawStatsQuery from(EventStatsQuery query, int timeoutMillis) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(COUNT(*), 0) AS log_count,
                    COALESCE(SUM(duration_seconds), 0) AS total_duration_seconds,
                    COALESCE(SUM(metric_value), 0) AS total_metric_value,
                    COALESCE(SUM(cost_value), 0) AS total_cost_value
                FROM event_logs
                WHERE occurred_at >= ?
                  AND occurred_at < ?
                """);
        addTimeoutHint(sql, timeoutMillis);

        List<Object> params = new ArrayList<>();
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
        return new RawStatsQuery(sql.toString(), List.copyOf(params));
    }

    private static void addTimeoutHint(StringBuilder sql, int timeoutMillis) {
        if (timeoutMillis <= 0) {
            return;
        }
        sql.insert("SELECT".length(), " /*+ MAX_EXECUTION_TIME(" + timeoutMillis + ") */");
    }
}
