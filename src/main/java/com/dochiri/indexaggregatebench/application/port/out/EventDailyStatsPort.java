package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;

import java.time.LocalDate;
import java.util.List;

public interface EventDailyStatsPort {
    void truncate();
    int rebuild(LocalDate from, LocalDate to);
    int increaseBatch(List<DailyStatsDelta> batch);
    EventStats aggregate(EventStatsQuery query);
}
