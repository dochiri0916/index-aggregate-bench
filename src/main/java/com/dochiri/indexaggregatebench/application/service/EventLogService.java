package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedEventResult;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class EventLogService {

    private final EventLogPort eventLogPort;
    private final EventDailyStatsPort eventDailyStatsPort;
    private final EventStatsCachePort statsCache;
    private final EventStatsService eventStatsService;

    public EventLogService(EventLogPort eventLogPort,
                           EventDailyStatsPort eventDailyStatsPort,
                           EventStatsCachePort statsCache,
                           EventStatsService eventStatsService) {
        this.eventLogPort = eventLogPort;
        this.eventDailyStatsPort = eventDailyStatsPort;
        this.statsCache = statsCache;
        this.eventStatsService = eventStatsService;
    }

    public SeedEventResult seed(SeedEventCondition condition) {
        long started = System.nanoTime();
        if (condition.truncate()) {
            eventDailyStatsPort.truncate();
            eventLogPort.truncate();
            statsCache.clear();
        }
        long insertedRows = eventLogPort.seed(condition);
        return new SeedEventResult(insertedRows, elapsedMillis(started));
    }

    @Transactional
    public RebuildEventDailyStatsResult rebuildDailyStats(LocalDate from, LocalDate to) {
        long started = System.nanoTime();
        int rows = eventDailyStatsPort.rebuild(from, to);
        statsCache.clearAfterCommit();
        return new RebuildEventDailyStatsResult(rows, elapsedMillis(started));
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
        eventLogPort.append(command);
        eventDailyStatsPort.increase(command);
        statsCache.evictRelatedAfterCommit(command.targetId(), command.segmentId());
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
