package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.BenchmarkProperties;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Repository
public class JdbcEventStatsAdapter implements EventStatsQueryPort {

    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkProperties benchmarkProperties;

    public JdbcEventStatsAdapter(JdbcTemplate jdbcTemplate, BenchmarkProperties benchmarkProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.benchmarkProperties = benchmarkProperties;
    }

    @Override
    public EventStats aggregate(EventStatsQuery query) {
        RawStatsQuery rawStatsQuery = RawStatsQuery.from(query, benchmarkProperties.raw().queryTimeoutMillis());
        return jdbcTemplate.query(rawStatsQuery.sql(), ps -> {
            for (int i = 0; i < rawStatsQuery.params().size(); i++) {
                ps.setObject(i + 1, rawStatsQuery.params().get(i));
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
}
