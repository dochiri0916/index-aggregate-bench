package com.dochiri.indexaggregatebench.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchemaIndexService {

    private static final List<IndexDefinition> RAW_INDEXES = List.of(
            new IndexDefinition("vehicles", "idx_vehicles_started_id_battery",
                    "CREATE INDEX idx_vehicles_started_id_battery ON vehicles (started_at, id, battery_id)"),
            new IndexDefinition("vehicles", "idx_vehicles_id_started",
                    "CREATE INDEX idx_vehicles_id_started ON vehicles (id, started_at)"),
            new IndexDefinition("vehicles", "idx_vehicles_battery_started",
                    "CREATE INDEX idx_vehicles_battery_started ON vehicles (battery_id, started_at)")
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaIndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Boolean> rawIndexStatus() {
        return Map.of(
                "idx_vehicles_started_id_battery", exists("vehicles", "idx_vehicles_started_id_battery"),
                "idx_vehicles_id_started", exists("vehicles", "idx_vehicles_id_started"),
                "idx_vehicles_battery_started", exists("vehicles", "idx_vehicles_battery_started")
        );
    }

    public Map<String, Long> rowCounts() {
        Long rawRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicles", Long.class);
        Long vehicleDailyStatsRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle_daily_stats", Long.class);
        return Map.of(
                "vehicles", rawRows == null ? 0 : rawRows,
                "vehicleDailyStats", vehicleDailyStatsRows == null ? 0 : vehicleDailyStatsRows
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
