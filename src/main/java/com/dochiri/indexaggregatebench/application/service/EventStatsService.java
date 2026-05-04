package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsCache;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcEventStatsAdapter;
import org.springframework.stereotype.Service;

@Service
public class EventStatsService {

    private final JdbcEventStatsAdapter eventStatsAdapter;
    private final InMemoryEventStatsCache statsCache;

    public EventStatsService(JdbcEventStatsAdapter eventStatsAdapter, InMemoryEventStatsCache statsCache) {
        this.eventStatsAdapter = eventStatsAdapter;
        this.statsCache = statsCache;
    }

    public TimedEventStats getStats(EventStatsBackend backend, EventStatsQuery query, boolean useCache) {
        long started = System.nanoTime();
        EventStatsCacheKey key = EventStatsCacheKey.of(backend, query);

        if (useCache) {
            EventStats cached = statsCache.get(key).orElse(null);
            if (cached != null) {
                return new TimedEventStats(backend, true, elapsedMillis(started), cached);
            }
        }

        EventStats stats = aggregate(backend, query);
        if (useCache) {
            statsCache.put(key, stats);
        }

        return new TimedEventStats(backend, false, elapsedMillis(started), stats);
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

    private EventStats aggregate(EventStatsBackend backend, EventStatsQuery query) {
        return switch (backend) {
            case RAW -> eventStatsAdapter.aggregateFromRawEventLogs(query);
            case DAILY_STATS -> eventStatsAdapter.aggregateFromDailyStats(query);
        };
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
