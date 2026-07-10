package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedEventResult;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EventLogService {

    private final EventLogPort eventLogPort;
    private final EventDailyStatsPort eventDailyStatsPort;
    private final EventStatsCachePort statsCache;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final EventStatsConsistencyPort consistencyPort;
    private final EventStatsService eventStatsService;

    public EventLogService(EventLogPort eventLogPort,
                           EventDailyStatsPort eventDailyStatsPort,
                           EventStatsCachePort statsCache,
                           EventStatsWriteBehindPort writeBehindPort,
                           EventStatsConsistencyPort consistencyPort,
                           EventStatsService eventStatsService) {
        this.eventLogPort = eventLogPort;
        this.eventDailyStatsPort = eventDailyStatsPort;
        this.statsCache = statsCache;
        this.writeBehindPort = writeBehindPort;
        this.consistencyPort = consistencyPort;
        this.eventStatsService = eventStatsService;
    }

    public SeedEventResult seed(SeedEventCondition condition) {
        long started = System.nanoTime();
        long insertedRows = consistencyPort.executeExclusive(() -> {
            if (condition.truncate()) {
                writeBehindPort.clear();
                eventDailyStatsPort.truncate();
                eventLogPort.truncate();
                statsCache.clear();
            }
            return eventLogPort.seed(condition);
        });
        return new SeedEventResult(insertedRows, elapsedMillis(started));
    }

    @Transactional
    public RebuildEventDailyStatsResult rebuildDailyStats(LocalDate from, LocalDate to) {
        long started = System.nanoTime();
        return consistencyPort.executeExclusiveUntilCompletion(() -> {
            List<DailyStatsDelta> pending = writeBehindPort.drainForCurrentTransaction();
            eventDailyStatsPort.increaseBatch(pending);
            int rows = eventDailyStatsPort.rebuild(from, to);
            statsCache.clearAfterCommit();
            return new RebuildEventDailyStatsResult(rows, elapsedMillis(started));
        });
    }

    public CompareEventStatsResult compare(EventStatsQuery query, int iterations, boolean cache) {
        int count = Math.max(1, iterations);
        TimedEventStats lastRaw = null;
        TimedEventStats lastDaily = null;
        long rawTotal = 0;
        long dailyTotal = 0;
        long rawMin = Long.MAX_VALUE;
        long dailyMin = Long.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            lastRaw = eventStatsService.getStats(EventStatsBackend.RAW, query, cache);
            lastDaily = eventStatsService.getStats(EventStatsBackend.DAILY_STATS, query, cache);
            rawTotal += lastRaw.elapsedMillis();
            dailyTotal += lastDaily.elapsedMillis();
            rawMin = Math.min(rawMin, lastRaw.elapsedMillis());
            dailyMin = Math.min(dailyMin, lastDaily.elapsedMillis());
        }
        return new CompareEventStatsResult(
                count, rawTotal / count, dailyTotal / count, rawMin, dailyMin, lastRaw, lastDaily
        );
    }

    @Transactional
    public void append(AppendEventCommand command) {
        consistencyPort.executeSharedUntilCompletion(() -> {
            eventLogPort.append(command);
            writeBehindPort.recordAfterCommit(command);
            statsCache.applyEventAfterCommit(command);
            return null;
        });
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
