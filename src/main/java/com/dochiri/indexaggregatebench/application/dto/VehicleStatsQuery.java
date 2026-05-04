package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record VehicleStatsQuery(
        Long id,
        Long batteryId,
        LocalDate from,
        LocalDate to
) {
    public VehicleStatsQuery {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }
}
