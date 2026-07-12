package com.dochiri.indexaggregatebench.application.dto;

import java.time.YearMonth;

public record MonthlyStatsKey(YearMonth statMonth, long targetId, long segmentId) {

    public static MonthlyStatsKey from(AppendEventCommand command) {
        return new MonthlyStatsKey(YearMonth.from(command.occurredAt()), command.targetId(), command.segmentId());
    }
}
