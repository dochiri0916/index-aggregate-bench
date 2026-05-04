package com.dochiri.indexaggregatebench.application.dto;

import java.math.BigDecimal;

public record EventStats(
        long logCount,
        long totalDurationSeconds,
        long totalMetricValue,
        long totalCostValue,
        BigDecimal averageDurationSeconds,
        BigDecimal metricPerCost
) {
}
