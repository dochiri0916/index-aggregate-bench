package com.dochiri.indexaggregatebench.application.dto;

import java.math.BigDecimal;

public record EventStatsDifference(
        long logCount,
        long totalDurationSeconds,
        long totalMetricValue,
        long totalCostValue,
        BigDecimal averageDurationSeconds,
        BigDecimal metricPerCost
) {

    public static EventStatsDifference between(EventStats raw, EventStats monthly) {
        return new EventStatsDifference(
                raw.logCount() - monthly.logCount(),
                raw.totalDurationSeconds() - monthly.totalDurationSeconds(),
                raw.totalMetricValue() - monthly.totalMetricValue(),
                raw.totalCostValue() - monthly.totalCostValue(),
                raw.averageDurationSeconds().subtract(monthly.averageDurationSeconds()),
                raw.metricPerCost().subtract(monthly.metricPerCost())
        );
    }

    public boolean isZero() {
        return logCount == 0
                && totalDurationSeconds == 0
                && totalMetricValue == 0
                && totalCostValue == 0
                && averageDurationSeconds.compareTo(BigDecimal.ZERO) == 0
                && metricPerCost.compareTo(BigDecimal.ZERO) == 0;
    }
}
