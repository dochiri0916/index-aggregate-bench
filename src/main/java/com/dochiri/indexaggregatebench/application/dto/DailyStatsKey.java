package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;

public record DailyStatsKey(LocalDate statDate, long targetId, long segmentId) {

    public static DailyStatsKey from(AppendEventCommand command) {
        return new DailyStatsKey(command.occurredAt().toLocalDate(), command.targetId(), command.segmentId());
    }
}
