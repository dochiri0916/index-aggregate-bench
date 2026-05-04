package com.dochiri.indexaggregatebench.application.dto;

public record RebuildVehicleDailyStatsResult(
        int vehicleDailyStatsRows,
        long elapsedMillis
) {
}
