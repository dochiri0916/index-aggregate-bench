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
                CREATE TABLE IF NOT EXISTS event_logs (
                    record_id BIGINT NOT NULL AUTO_INCREMENT,
                    target_id BIGINT NOT NULL,
                    segment_id BIGINT NOT NULL,
                    occurred_at DATETIME(6) NOT NULL,
                    duration_seconds INT NOT NULL,
                    metric_value INT NOT NULL,
                    cost_value INT NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (record_id),
                    INDEX idx_event_logs_occurred_target_segment (occurred_at, target_id, segment_id),
                    INDEX idx_event_logs_target_occurred (target_id, occurred_at),
                    INDEX idx_event_logs_segment_occurred (segment_id, occurred_at)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS event_daily_stats (
                    stat_date DATE NOT NULL,
                    target_id BIGINT NOT NULL,
                    segment_id BIGINT NOT NULL,
                    log_count BIGINT NOT NULL,
                    total_duration_seconds BIGINT NOT NULL,
                    total_metric_value BIGINT NOT NULL,
                    total_cost_value BIGINT NOT NULL,
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (stat_date, target_id, segment_id),
                    INDEX idx_daily_target_date (target_id, stat_date),
                    INDEX idx_daily_segment_date (segment_id, stat_date)
                )
                """);
    }
}
