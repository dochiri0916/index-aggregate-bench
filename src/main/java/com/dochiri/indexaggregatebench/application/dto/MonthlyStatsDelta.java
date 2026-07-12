package com.dochiri.indexaggregatebench.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MonthlyStatsDelta(
        MonthlyStatsKey key,
        long logCount,
        long totalDurationSeconds,
        long totalMetricValue,
        long totalCostValue
) {

    public static MonthlyStatsDelta from(AppendEventCommand command) {
        return new MonthlyStatsDelta(MonthlyStatsKey.from(command), 1,
                command.durationSeconds(), command.metricValue(), command.costValue());
    }

    public MonthlyStatsDelta plus(MonthlyStatsDelta other) {
        return new MonthlyStatsDelta(
                key,
                logCount + other.logCount,
                totalDurationSeconds + other.totalDurationSeconds,
                totalMetricValue + other.totalMetricValue,
                totalCostValue + other.totalCostValue
        );
    }

    public EventStats toEventStats() {
        BigDecimal average = logCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalDurationSeconds)
                        .divide(BigDecimal.valueOf(logCount), 2, RoundingMode.HALF_UP);
        BigDecimal ratio = totalCostValue == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalMetricValue)
                        .divide(BigDecimal.valueOf(totalCostValue), 2, RoundingMode.HALF_UP);
        return new EventStats(logCount, totalDurationSeconds, totalMetricValue, totalCostValue, average, ratio);
    }
}
