package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.AppendVehicleCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class JdbcVehicleDailyStatsCommandAdapter {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVehicleDailyStatsCommandAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void truncate() {
        jdbcTemplate.update("TRUNCATE TABLE vehicle_daily_stats");
    }

    public int rebuild(LocalDate from, LocalDate to) {
        jdbcTemplate.update("DELETE FROM vehicle_daily_stats WHERE stat_date BETWEEN ? AND ?", from, to);

        return jdbcTemplate.update("""
                INSERT INTO vehicle_daily_stats (
                    stat_date,
                    id,
                    battery_id,
                    log_count,
                    total_driving_seconds,
                    total_distance_meters,
                    total_consumed_wh
                )
                SELECT
                    DATE(started_at),
                    id,
                    battery_id,
                    COUNT(*),
                    SUM(TIMESTAMPDIFF(SECOND, started_at, ended_at)),
                    SUM(distance_meters),
                    SUM(consumed_wh)
                FROM vehicles
                WHERE started_at >= ?
                  AND started_at < DATE_ADD(?, INTERVAL 1 DAY)
                GROUP BY DATE(started_at), id, battery_id
                """, from, to);
    }

    public void increase(AppendVehicleCommand command) {
        jdbcTemplate.update("""
                INSERT INTO vehicle_daily_stats (
                    stat_date,
                    id,
                    battery_id,
                    log_count,
                    total_driving_seconds,
                    total_distance_meters,
                    total_consumed_wh
                )
                VALUES (:statDate, :id, :batteryId, 1, :drivingSeconds, :distanceMeters, :consumedWh)
                ON DUPLICATE KEY UPDATE
                    log_count = log_count + 1,
                    total_driving_seconds = total_driving_seconds + VALUES(total_driving_seconds),
                    total_distance_meters = total_distance_meters + VALUES(total_distance_meters),
                    total_consumed_wh = total_consumed_wh + VALUES(total_consumed_wh)
                """,
                command.startedAt().toLocalDate(),
                command.id(),
                command.batteryId(),
                command.drivingSeconds(),
                command.distanceMeters(),
                command.consumedWh()
        );
    }
}
