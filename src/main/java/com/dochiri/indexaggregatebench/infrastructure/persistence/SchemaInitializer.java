package com.dochiri.indexaggregatebench.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS vehicles (
                    record_id BIGINT NOT NULL AUTO_INCREMENT,
                    id BIGINT NOT NULL,
                    battery_id BIGINT NOT NULL,
                    started_at DATETIME(6) NOT NULL,
                    ended_at DATETIME(6) NOT NULL,
                    distance_meters INT NOT NULL,
                    consumed_wh INT NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (record_id),
                    INDEX idx_vehicles_started_id_battery (started_at, id, battery_id),
                    INDEX idx_vehicles_id_started (id, started_at),
                    INDEX idx_vehicles_battery_started (battery_id, started_at)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS vehicle_daily_stats (
                    stat_date DATE NOT NULL,
                    id BIGINT NOT NULL,
                    battery_id BIGINT NOT NULL,
                    log_count BIGINT NOT NULL,
                    total_driving_seconds BIGINT NOT NULL,
                    total_distance_meters BIGINT NOT NULL,
                    total_consumed_wh BIGINT NOT NULL,
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (stat_date, id, battery_id),
                    INDEX idx_daily_id_date (id, stat_date),
                    INDEX idx_daily_battery_date (battery_id, stat_date)
                )
                """);
    }
}
