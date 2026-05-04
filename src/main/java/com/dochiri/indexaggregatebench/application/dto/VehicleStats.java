package com.dochiri.indexaggregatebench.application.dto;

import java.math.BigDecimal;

public record VehicleStats(
        long logCount,
        long totalDrivingSeconds,
        long totalDistanceMeters,
        long totalConsumedWh,
        BigDecimal averageDrivingSeconds,
        BigDecimal averageEfficiencyKmPerKwh
) {
}
