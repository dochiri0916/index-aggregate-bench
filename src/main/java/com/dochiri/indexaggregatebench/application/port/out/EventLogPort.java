package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;

public interface EventLogPort {
    long seed(SeedEventCondition condition);
    void truncate();
    void append(AppendEventCommand command);
}
