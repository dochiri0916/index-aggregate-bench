package com.dochiri.indexaggregatebench.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record EventStats(
        long logCount,
        long totalDurationSeconds,
        long totalMetricValue,
        long totalCostValue,
        BigDecimal averageDurationSeconds,
        BigDecimal metricPerCost
) {
    public EventStats plus(EventStats other) {
        long combinedCount = logCount + other.logCount;
        long combinedDuration = totalDurationSeconds + other.totalDurationSeconds;
        long combinedMetric = totalMetricValue + other.totalMetricValue;
        long combinedCost = totalCostValue + other.totalCostValue;
        BigDecimal average = combinedCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(combinedDuration)
                        .divide(BigDecimal.valueOf(combinedCount), 2, RoundingMode.HALF_UP);
        BigDecimal ratio = combinedCost == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(combinedMetric)
                        .divide(BigDecimal.valueOf(combinedCost), 2, RoundingMode.HALF_UP);
        return new EventStats(combinedCount, combinedDuration, combinedMetric, combinedCost, average, ratio);
    }
}
