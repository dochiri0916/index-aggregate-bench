package com.dochiri.indexaggregatebench.application.dto;

public record CompareEventStatsResult(
        int iterations,
        long rawAverageMillis,
        long eventMonthlyStatsAverageMillis,
        long rawMinMillis,
        long eventMonthlyStatsMinMillis,
        long rawP95Millis,
        long eventMonthlyStatsP95Millis,
        TimedEventStats lastRaw,
        TimedEventStats lastMonthlyStats,
        boolean statsMatch,
        EventStatsDifference difference
) {
}
