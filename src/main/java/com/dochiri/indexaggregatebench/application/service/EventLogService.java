package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedEventResult;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcEventDailyStatsCommandAdapter;
import com.dochiri.indexaggregatebench.infrastructure.persistence.EventLogPersistenceAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class EventLogService {

    private final EventLogPersistenceAdapter eventLogPersistenceAdapter;
    private final JdbcEventDailyStatsCommandAdapter eventDailyStatsAdapter;
    private final EventStatsService eventStatsService;

    public EventLogService(EventLogPersistenceAdapter eventLogPersistenceAdapter,
                          JdbcEventDailyStatsCommandAdapter eventDailyStatsAdapter,
                          EventStatsService eventStatsService) {
        this.eventLogPersistenceAdapter = eventLogPersistenceAdapter;
        this.eventDailyStatsAdapter = eventDailyStatsAdapter;
        this.eventStatsService = eventStatsService;
    }

    public SeedEventResult seed(SeedEventCondition condition) {
        long started = System.nanoTime();

        if (condition.truncate()) {
            eventDailyStatsAdapter.truncate();
            eventLogPersistenceAdapter.truncate();
            eventStatsService.clearCache();
        }

        long insertedRows = eventLogPersistenceAdapter.seed(condition);
        return new SeedEventResult(insertedRows, elapsedMillis(started));
    }

    @Transactional
    public RebuildEventDailyStatsResult rebuildDailyStats(LocalDate from, LocalDate to) {
        long started = System.nanoTime();

        int rows = eventDailyStatsAdapter.rebuild(from, to);
        eventStatsService.clearCache();

        return new RebuildEventDailyStatsResult(rows, elapsedMillis(started));
    }

    public CompareEventStatsResult compare(EventStatsQuery query, int iterations, boolean cache) {
        int count = Math.max(1, iterations);

        TimedEventStats lastRaw = null;
        TimedEventStats lastSummary = null;
        long rawTotal = 0;
        long eventDailyStatsTotal = 0;
        long rawMin = Long.MAX_VALUE;
        long eventDailyStatsMin = Long.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            lastRaw = eventStatsService.getStats(EventStatsBackend.RAW, query, cache);
            lastSummary = eventStatsService.getStats(EventStatsBackend.DAILY_STATS, query, cache);

            rawTotal += lastRaw.elapsedMillis();
            eventDailyStatsTotal += lastSummary.elapsedMillis();
            rawMin = Math.min(rawMin, lastRaw.elapsedMillis());
            eventDailyStatsMin = Math.min(eventDailyStatsMin, lastSummary.elapsedMillis());
        }

        return new CompareEventStatsResult(
                count,
                rawTotal / count,
                eventDailyStatsTotal / count,
                rawMin,
                eventDailyStatsMin,
                lastRaw,
                lastSummary
        );
    }

    @Transactional
    public void append(AppendEventCommand command) {
        eventLogPersistenceAdapter.append(command);
        eventDailyStatsAdapter.increase(command);
        eventStatsService.evictRelated(command.targetId(), command.segmentId());
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
