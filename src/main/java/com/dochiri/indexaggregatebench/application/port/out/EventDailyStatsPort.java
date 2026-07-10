package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;

import java.time.LocalDate;

public interface EventDailyStatsPort {
    void truncate();
    int rebuild(LocalDate from, LocalDate to);
    void increase(AppendEventCommand command);
    EventStats aggregate(EventStatsQuery query);
}
