package com.dochiri.indexaggregatebench.application.dto;

public record RebuildEventDailyStatsResult(
        int eventDailyStatsRows,
        long elapsedMillis
) {
}
