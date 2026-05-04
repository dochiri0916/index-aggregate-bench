package com.dochiri.indexaggregatebench.application.dto;

public record TimedVehicleStats(
        VehicleStatsBackend backend,
        boolean cacheHit,
        long elapsedMillis,
        VehicleStats stats
) {
}
