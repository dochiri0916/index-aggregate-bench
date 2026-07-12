package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;

import java.time.LocalDate;

public interface EventMonthlyStatsPort {
    void truncate();
    int rebuild(LocalDate from, LocalDate to);
    int increaseBatch(MonthlyStatsFlushBatch batch);
    EventStats aggregate(EventStatsQuery query);
}
