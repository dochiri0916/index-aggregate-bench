package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record EventStatsCacheKey(
        EventStatsBackend backend,
        Long targetId,
        Long segmentId,
        LocalDate from,
        LocalDate to
) {
    public static EventStatsCacheKey of(EventStatsBackend backend, EventStatsQuery query) {
        return new EventStatsCacheKey(
                backend,
                query.targetId(),
                query.segmentId(),
                query.from(),
                query.to()
        );
    }
}
