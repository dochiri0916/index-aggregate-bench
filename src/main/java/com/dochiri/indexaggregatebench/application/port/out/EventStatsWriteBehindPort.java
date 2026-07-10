package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;

import java.util.List;

public interface EventStatsWriteBehindPort {
    void recordAfterCommit(AppendEventCommand command);
    List<DailyStatsDelta> drainForCurrentTransaction();
    EventStats aggregatePending(EventStatsQuery query);
    int clear();
    int size();
}
