package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record SeedVehicleCondition(
        LocalDate from,
        int days,
        int idCount,
        int recordsPerIdPerDay,
        boolean truncate
) {
    public SeedVehicleCondition {
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (days <= 0) {
            days = 30;
        }
        if (idCount <= 0) {
            idCount = 1_000;
        }
        if (recordsPerIdPerDay <= 0) {
            recordsPerIdPerDay = 10;
        }
    }
}
