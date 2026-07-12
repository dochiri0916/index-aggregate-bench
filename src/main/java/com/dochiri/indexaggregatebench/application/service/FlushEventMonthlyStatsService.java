package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.port.in.FlushEventMonthlyStatsUseCase;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlushEventMonthlyStatsService implements FlushEventMonthlyStatsUseCase {

    private final EventStatsConsistencyPort consistencyPort;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final EventMonthlyStatsPort monthlyStatsPort;
    private final EventStatsCachePort statsCache;

    public FlushEventMonthlyStatsService(EventStatsConsistencyPort consistencyPort,
                                         EventStatsWriteBehindPort writeBehindPort,
                                         EventMonthlyStatsPort monthlyStatsPort,
                                         EventStatsCachePort statsCache) {
        this.consistencyPort = consistencyPort;
        this.writeBehindPort = writeBehindPort;
        this.monthlyStatsPort = monthlyStatsPort;
        this.statsCache = statsCache;
    }

    @Override
    @Transactional
    public int flush() {
        return consistencyPort.executeExclusiveUntilCompletion(() -> {
            MonthlyStatsFlushBatch batch = writeBehindPort.drainForCurrentTransaction();
            int applied = monthlyStatsPort.increaseBatch(batch);
            if (!batch.isEmpty()) {
                statsCache.clearAfterCommit();
            }
            return applied;
        });
    }
}
