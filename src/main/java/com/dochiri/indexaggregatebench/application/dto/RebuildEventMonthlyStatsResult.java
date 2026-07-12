package com.dochiri.indexaggregatebench.application.dto;

public record RebuildEventMonthlyStatsResult(
        int eventMonthlyStatsRows,
        long elapsedMillis
) {
}
