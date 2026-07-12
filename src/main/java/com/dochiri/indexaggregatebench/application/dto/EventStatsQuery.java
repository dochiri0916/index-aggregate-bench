package com.dochiri.indexaggregatebench.application.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

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

    public YearMonth fromMonth() {
        return YearMonth.from(from);
    }

    public YearMonth toMonth() {
        return YearMonth.from(to);
    }

    public boolean isCompleteMonthRange() {
        return from.equals(from.withDayOfMonth(1))
                && to.equals(to.with(TemporalAdjusters.lastDayOfMonth()));
    }
}
