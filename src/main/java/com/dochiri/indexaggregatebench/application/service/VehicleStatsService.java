package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.VehicleStats;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedVehicleStats;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryVehicleStatsCache;
import com.dochiri.indexaggregatebench.infrastructure.persistence.JdbcVehicleStatsAdapter;
import org.springframework.stereotype.Service;

@Service
public class VehicleStatsService {

    private final JdbcVehicleStatsAdapter vehicleStatsAdapter;
    private final InMemoryVehicleStatsCache statsCache;

    public VehicleStatsService(JdbcVehicleStatsAdapter vehicleStatsAdapter, InMemoryVehicleStatsCache statsCache) {
        this.vehicleStatsAdapter = vehicleStatsAdapter;
        this.statsCache = statsCache;
    }

    public TimedVehicleStats getStats(VehicleStatsBackend backend, VehicleStatsQuery query, boolean useCache) {
        long started = System.nanoTime();
        VehicleStatsCacheKey key = VehicleStatsCacheKey.of(backend, query);

        if (useCache) {
            VehicleStats cached = statsCache.get(key).orElse(null);
            if (cached != null) {
                return new TimedVehicleStats(backend, true, elapsedMillis(started), cached);
            }
        }

        VehicleStats stats = aggregate(backend, query);
        if (useCache) {
            statsCache.put(key, stats);
        }

        return new TimedVehicleStats(backend, false, elapsedMillis(started), stats);
    }

    public int evictRelated(Long id, Long batteryId) {
        return statsCache.evictRelated(id, batteryId);
    }

    public int clearCache() {
        return statsCache.clear();
    }

    public int cacheSize() {
        return statsCache.size();
    }

    private VehicleStats aggregate(VehicleStatsBackend backend, VehicleStatsQuery query) {
        return switch (backend) {
            case RAW -> vehicleStatsAdapter.aggregateFromRawVehicles(query);
            case DAILY_STATS -> vehicleStatsAdapter.aggregateFromDailyStats(query);
        };
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
