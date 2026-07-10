package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsQueryPort;
import org.springframework.stereotype.Service;

@Service
public class EventStatsService {

    private final EventStatsQueryPort eventStatsQueryPort;
    private final EventDailyStatsPort eventDailyStatsPort;
    private final EventStatsCachePort statsCache;

    public EventStatsService(EventStatsQueryPort eventStatsQueryPort,
                             EventDailyStatsPort eventDailyStatsPort,
                             EventStatsCachePort statsCache) {
        this.eventStatsQueryPort = eventStatsQueryPort;
        this.eventDailyStatsPort = eventDailyStatsPort;
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

    public int clearCache() {
        return statsCache.clear();
    }

    public int cacheSize() {
        return statsCache.size();
    }

    private EventStats aggregate(EventStatsBackend backend, EventStatsQuery query) {
        if (backend == EventStatsBackend.DAILY_STATS) {
            return eventDailyStatsPort.aggregate(query);
        }
        return eventStatsQueryPort.aggregate(query);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
