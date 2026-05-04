package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.BenchmarkProperties;
import com.dochiri.indexaggregatebench.application.dto.VehicleStats;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Repository
public class JdbcVehicleStatsAdapter {

    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkProperties benchmarkProperties;

    public JdbcVehicleStatsAdapter(JdbcTemplate jdbcTemplate, BenchmarkProperties benchmarkProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.benchmarkProperties = benchmarkProperties;
    }

    public VehicleStats aggregateFromRawVehicles(VehicleStatsQuery query) {
        QueryParts parts = rawGroupByQuery(query);
        return jdbcTemplate.query(parts.sql(), ps -> {
            for (int i = 0; i < parts.params().size(); i++) {
                ps.setObject(i + 1, parts.params().get(i));
            }
        }, rs -> {
            long logCount = 0;
            long totalDrivingSeconds = 0;
            long totalDistanceMeters = 0;
            long totalConsumedWh = 0;

            while (rs.next()) {
                logCount += rs.getLong("log_count");
                totalDrivingSeconds += rs.getLong("total_driving_seconds");
                totalDistanceMeters += rs.getLong("total_distance_meters");
                totalConsumedWh += rs.getLong("total_consumed_wh");
            }

            return new VehicleStats(
                    logCount,
                    totalDrivingSeconds,
                    totalDistanceMeters,
                    totalConsumedWh,
                    average(totalDrivingSeconds, logCount),
                    efficiency(totalDistanceMeters, totalConsumedWh)
            );
        });
    }

    public VehicleStats aggregateFromDailyStats(VehicleStatsQuery query) {
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
            long totalDrivingSeconds = rs.getLong("total_driving_seconds");
            long totalDistanceMeters = rs.getLong("total_distance_meters");
            long totalConsumedWh = rs.getLong("total_consumed_wh");

            return new VehicleStats(
                    logCount,
                    totalDrivingSeconds,
                    totalDistanceMeters,
                    totalConsumedWh,
                    average(totalDrivingSeconds, logCount),
                    efficiency(totalDistanceMeters, totalConsumedWh)
            );
        });
    }

    private QueryParts rawGroupByQuery(VehicleStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS log_count,
                    SUM(TIMESTAMPDIFF(SECOND, started_at, ended_at)) AS total_driving_seconds,
                    SUM(distance_meters) AS total_distance_meters,
                    SUM(consumed_wh) AS total_consumed_wh
                FROM vehicles
                WHERE started_at >= ?
                  AND started_at < ?
                """);
        addTimeoutHint(sql);

        List<Object> params = new java.util.ArrayList<>();
        params.add(query.from().atStartOfDay());
        params.add(query.to().plusDays(1).atStartOfDay());

        if (query.id() != null) {
            sql.append("  AND id = ?\n");
            params.add(query.id());
        }
        if (query.batteryId() != null) {
            sql.append("  AND battery_id = ?\n");
            params.add(query.batteryId());
        }

        sql.append("GROUP BY DATE(started_at), id, battery_id");
        return new QueryParts(sql.toString(), params);
    }

    private QueryParts dailyStatsQuery(VehicleStatsQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(log_count), 0) AS log_count,
                    COALESCE(SUM(total_driving_seconds), 0) AS total_driving_seconds,
                    COALESCE(SUM(total_distance_meters), 0) AS total_distance_meters,
                    COALESCE(SUM(total_consumed_wh), 0) AS total_consumed_wh
                FROM vehicle_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                """);
        List<Object> params = new java.util.ArrayList<>();
        params.add(query.from());
        params.add(query.to());

        if (query.id() != null) {
            sql.append("  AND id = ?\n");
            params.add(query.id());
        }
        if (query.batteryId() != null) {
            sql.append("  AND battery_id = ?\n");
            params.add(query.batteryId());
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

    private VehicleStats emptyStats() {
        return new VehicleStats(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal average(long total, long count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal efficiency(long totalDistanceMeters, long totalConsumedWh) {
        if (totalConsumedWh == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalDistanceMeters)
                .divide(BigDecimal.valueOf(totalConsumedWh), 2, RoundingMode.HALF_UP);
    }

    private record QueryParts(String sql, List<Object> params) {
    }
}
