package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.port.in.FlushEventDailyStatsUseCase;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FlushEventDailyStatsService implements FlushEventDailyStatsUseCase {

    private final EventStatsConsistencyPort consistencyPort;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final EventDailyStatsPort dailyStatsPort;

    public FlushEventDailyStatsService(EventStatsConsistencyPort consistencyPort,
                                       EventStatsWriteBehindPort writeBehindPort,
                                       EventDailyStatsPort dailyStatsPort) {
        this.consistencyPort = consistencyPort;
        this.writeBehindPort = writeBehindPort;
        this.dailyStatsPort = dailyStatsPort;
    }

    @Override
    @Transactional
    public int flush() {
        return consistencyPort.executeExclusiveUntilCompletion(() -> {
            List<DailyStatsDelta> batch = writeBehindPort.drainForCurrentTransaction();
            return dailyStatsPort.increaseBatch(batch);
        });
    }
}
