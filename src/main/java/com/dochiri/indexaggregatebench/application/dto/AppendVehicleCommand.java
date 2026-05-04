package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AppendVehicleCommand(
        long id,
        long batteryId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int drivingSeconds,
        int distanceMeters,
        int consumedWh
) {
    public AppendVehicleCommand {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (endedAt == null && drivingSeconds > 0) {
            endedAt = startedAt.plusSeconds(drivingSeconds);
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt or drivingSeconds is required");
        }
        if (endedAt.isBefore(startedAt) || endedAt.isEqual(startedAt)) {
            throw new IllegalArgumentException("endedAt must be after startedAt");
        }
        if (drivingSeconds <= 0) {
            drivingSeconds = Math.toIntExact(ChronoUnit.SECONDS.between(startedAt, endedAt));
        }
    }
}
