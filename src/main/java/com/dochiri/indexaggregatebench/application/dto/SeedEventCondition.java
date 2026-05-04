package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record SeedEventCondition(
        LocalDate from,
        int days,
        int targetCount,
        int recordsPerTargetPerDay,
        boolean truncate
) {
    public SeedEventCondition {
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (days <= 0) {
            days = 30;
        }
        if (targetCount <= 0) {
            targetCount = 1_000;
        }
        if (recordsPerTargetPerDay <= 0) {
            recordsPerTargetPerDay = 10;
        }
    }
}
