package com.dochiri.indexaggregatebench.application.dto;

public record EventStatsCacheKey(
        EventStatsBackend backend,
        Long targetId,
        Long segmentId,
        String from,
        String to
) {
    public static EventStatsCacheKey of(EventStatsBackend backend, EventStatsQuery query) {
        return new EventStatsCacheKey(
                backend,
                query.targetId(),
                query.segmentId(),
                query.from().toString(),
                query.to().toString()
        );
    }
}
