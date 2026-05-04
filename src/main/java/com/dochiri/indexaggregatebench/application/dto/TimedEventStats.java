package com.dochiri.indexaggregatebench.application.dto;

public record TimedEventStats(
        EventStatsBackend backend,
        boolean cacheHit,
        long elapsedMillis,
        EventStats stats
) {
}
