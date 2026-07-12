package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsQueryPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Service;

@Service
public class EventStatsService {

    private final EventStatsQueryPort eventStatsQueryPort;
    private final EventMonthlyStatsPort eventMonthlyStatsPort;
    private final EventStatsCachePort statsCache;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final EventStatsConsistencyPort consistencyPort;

    public EventStatsService(EventStatsQueryPort eventStatsQueryPort,
                             EventMonthlyStatsPort eventMonthlyStatsPort,
                             EventStatsCachePort statsCache,
                             EventStatsWriteBehindPort writeBehindPort,
                             EventStatsConsistencyPort consistencyPort) {
        this.eventStatsQueryPort = eventStatsQueryPort;
        this.eventMonthlyStatsPort = eventMonthlyStatsPort;
        this.statsCache = statsCache;
        this.writeBehindPort = writeBehindPort;
        this.consistencyPort = consistencyPort;
    }

    public TimedEventStats getStats(EventStatsBackend backend, EventStatsQuery query, boolean useCache) {
        validateQuery(backend, query);
        long started = System.nanoTime();
        EventStatsCacheKey key = EventStatsCacheKey.of(backend, query);
        if (useCache) {
            EventStats cached = statsCache.get(key).orElse(null);
            if (cached != null) {
                return timed(backend, true, started, cached);
            }
        }
        if (backend == EventStatsBackend.RAW && !useCache) {
            return timed(backend, false, started, eventStatsQueryPort.aggregate(query));
        }
        return consistencyPort.executeExclusive(() -> loadAndCache(backend, query, useCache, key, started));
    }

    public int clearCache() {
        return statsCache.clear();
    }

    public int cacheSize() {
        return statsCache.size();
    }

    private TimedEventStats loadAndCache(EventStatsBackend backend,
                                         EventStatsQuery query,
                                         boolean useCache,
                                         EventStatsCacheKey key,
                                         long started) {
        if (useCache) {
            EventStats cached = statsCache.get(key).orElse(null);
            if (cached != null) {
                return timed(backend, true, started, cached);
            }
        }
        EventStats stats = aggregate(backend, query);
        if (useCache) {
            statsCache.put(key, stats);
        }
        return timed(backend, false, started, stats);
    }

    private EventStats aggregate(EventStatsBackend backend, EventStatsQuery query) {
        if (backend == EventStatsBackend.MONTHLY_STATS) {
            return eventMonthlyStatsPort.aggregate(query);
        }
        if (backend == EventStatsBackend.MONTHLY_STATS_REALTIME) {
            EventStats stored = eventMonthlyStatsPort.aggregate(query);
            return stored.plus(writeBehindPort.aggregatePending(query));
        }
        return eventStatsQueryPort.aggregate(query);
    }

    private void validateQuery(EventStatsBackend backend, EventStatsQuery query) {
        if ((backend == EventStatsBackend.MONTHLY_STATS
                || backend == EventStatsBackend.MONTHLY_STATS_REALTIME)
                && !query.isCompleteMonthRange()) {
            throw new IllegalArgumentException("monthly stats require complete calendar months");
        }
    }

    private TimedEventStats timed(EventStatsBackend backend, boolean cacheHit, long started, EventStats stats) {
        return new TimedEventStats(backend, cacheHit, elapsedMillis(started), stats);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
