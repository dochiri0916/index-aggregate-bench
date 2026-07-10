package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;

public interface EventStatsQueryPort {
    EventStats aggregate(EventStatsQuery query);
}
