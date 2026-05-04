package com.dochiri.indexaggregatebench.application.dto;

public record CompareEventStatsResult(
        int iterations,
        long rawAverageMillis,
        long eventDailyStatsAverageMillis,
        long rawMinMillis,
        long eventDailyStatsMinMillis,
        TimedEventStats lastRaw,
        TimedEventStats lastDailyStats
) {
}
