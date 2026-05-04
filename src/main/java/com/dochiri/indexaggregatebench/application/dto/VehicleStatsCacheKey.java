package com.dochiri.indexaggregatebench.application.dto;

public record VehicleStatsCacheKey(
        VehicleStatsBackend backend,
        Long id,
        Long batteryId,
        String from,
        String to
) {
    public static VehicleStatsCacheKey of(VehicleStatsBackend backend, VehicleStatsQuery query) {
        return new VehicleStatsCacheKey(
                backend,
                query.id(),
                query.batteryId(),
                query.from().toString(),
                query.to().toString()
        );
    }
}
