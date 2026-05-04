package com.dochiri.indexaggregatebench.application.dto;

public record CompareVehicleStatsResult(
        int iterations,
        long rawAverageMillis,
        long vehicleDailyStatsAverageMillis,
        long rawMinMillis,
        long vehicleDailyStatsMinMillis,
        TimedVehicleStats lastRaw,
        TimedVehicleStats lastSummary
) {
}
