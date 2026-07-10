package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsCache;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcEventDailyStatsCommandAdapter;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcEventStatsAdapter;
import org.springframework.stereotype.Service;

@Service
public class EventStatsService {

    private final JdbcEventStatsAdapter eventStatsAdapter;
    private final JdbcEventDailyStatsCommandAdapter dailyStatsAdapter;
    private final InMemoryEventStatsCache statsCache;

    public EventStatsService(JdbcEventStatsAdapter eventStatsAdapter,
                             JdbcEventDailyStatsCommandAdapter dailyStatsAdapter,
                             InMemoryEventStatsCache statsCache) {
        this.eventStatsAdapter = eventStatsAdapter;
        this.dailyStatsAdapter = dailyStatsAdapter;
        this.statsCache = statsCache;
    }

    public TimedEventStats getStats(EventStatsBackend backend, EventStatsQuery query, boolean useCache) {
        long started = System.nanoTime();

        if (backend == EventStatsBackend.DAILY_STATS) {
            return getDailyStats(query, useCache, started);
        }

        return getRawStats(query, useCache, started);
    }

    private TimedEventStats getDailyStats(EventStatsQuery query, boolean useCache, long started) {
        if (!useCache) {
            EventStats stats = dailyStatsAdapter.aggregateFromDailyStats(query);
            return new TimedEventStats(EventStatsBackend.DAILY_STATS, false, elapsedMillis(started), stats);
        }

        EventStats cached = statsCache.aggregateFromCells(query).orElse(null);
        if (cached != null) {
            return new TimedEventStats(EventStatsBackend.DAILY_STATS, true, elapsedMillis(started), cached);
        }

        statsCache.loadCells(dailyStatsAdapter.loadCells(query));
        cached = statsCache.aggregateFromCells(query).orElse(null);
        if (cached != null) {
            return new TimedEventStats(EventStatsBackend.DAILY_STATS, true, elapsedMillis(started), cached);
        }

        EventStats stats = dailyStatsAdapter.aggregateFromDailyStats(query);
        return new TimedEventStats(EventStatsBackend.DAILY_STATS, false, elapsedMillis(started), stats);
    }

    private TimedEventStats getRawStats(EventStatsQuery query, boolean useCache, long started) {
        EventStatsCacheKey key = EventStatsCacheKey.of(EventStatsBackend.RAW, query);

        if (useCache) {
            EventStats cached = statsCache.get(key).orElse(null);
            if (cached != null) {
                return new TimedEventStats(EventStatsBackend.RAW, true, elapsedMillis(started), cached);
            }
        }

        EventStats stats = eventStatsAdapter.aggregateFromRawEventLogs(query);
        if (useCache) {
            statsCache.put(key, stats);
        }

        return new TimedEventStats(EventStatsBackend.RAW, false, elapsedMillis(started), stats);
    }

    public void incrementCell(AppendEventCommand command) {
        statsCache.incrementCell(command);
    }

    public int evictRelated(Long targetId, Long segmentId) {
        return statsCache.evictRelated(targetId, segmentId);
    }

    public int clearCache() {
        return statsCache.clear();
    }

    public int cacheSize() {
        return statsCache.size();
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
