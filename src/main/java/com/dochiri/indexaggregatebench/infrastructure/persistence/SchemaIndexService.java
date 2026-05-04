package com.dochiri.indexaggregatebench.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchemaIndexService {

    private static final List<IndexDefinition> RAW_INDEXES = List.of(
            new IndexDefinition("event_logs", "idx_event_logs_occurred_target_segment",
                    "CREATE INDEX idx_event_logs_occurred_target_segment ON event_logs (occurred_at, target_id, segment_id)"),
            new IndexDefinition("event_logs", "idx_event_logs_target_occurred",
                    "CREATE INDEX idx_event_logs_target_occurred ON event_logs (target_id, occurred_at)"),
            new IndexDefinition("event_logs", "idx_event_logs_segment_occurred",
                    "CREATE INDEX idx_event_logs_segment_occurred ON event_logs (segment_id, occurred_at)")
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaIndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Boolean> rawIndexStatus() {
        return Map.of(
                "idx_event_logs_occurred_target_segment", exists("event_logs", "idx_event_logs_occurred_target_segment"),
                "idx_event_logs_target_occurred", exists("event_logs", "idx_event_logs_target_occurred"),
                "idx_event_logs_segment_occurred", exists("event_logs", "idx_event_logs_segment_occurred")
        );
    }

    public Map<String, Long> rowCounts() {
        Long rawRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_logs", Long.class);
        Long eventDailyStatsRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_daily_stats", Long.class);
        return Map.of(
                "eventLogs", rawRows == null ? 0 : rawRows,
                "eventDailyStats", eventDailyStatsRows == null ? 0 : eventDailyStatsRows
        );
    }

    public Map<String, Boolean> createRawIndexes() {
        for (IndexDefinition index : RAW_INDEXES) {
            if (!exists(index.tableName(), index.indexName())) {
                jdbcTemplate.execute(index.createSql());
            }
        }
        return rawIndexStatus();
    }

    public Map<String, Boolean> dropRawIndexes() {
        for (IndexDefinition index : RAW_INDEXES) {
            if (exists(index.tableName(), index.indexName())) {
                jdbcTemplate.execute("DROP INDEX " + index.indexName() + " ON " + index.tableName());
            }
        }
        return rawIndexStatus();
    }

    private boolean exists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private record IndexDefinition(String tableName, String indexName, String createSql) {
    }
}
