package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record EventStatsQuery(
        Long targetId,
        Long segmentId,
        LocalDate from,
        LocalDate to
) {
    public EventStatsQuery {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }
}
