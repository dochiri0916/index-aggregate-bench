package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.VehicleStats;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsCacheKey;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryVehicleStatsCache {

    private final Map<VehicleStatsCacheKey, VehicleStats> cache = new ConcurrentHashMap<>();

    public Optional<VehicleStats> get(VehicleStatsCacheKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(VehicleStatsCacheKey key, VehicleStats stats) {
        cache.put(key, stats);
    }

    public int evictRelated(Long id, Long batteryId) {
        int evicted = 0;
        Iterator<VehicleStatsCacheKey> iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            VehicleStatsCacheKey key = iterator.next();
            boolean matchesVehicle = id == null || id.equals(key.id());
            boolean matchesBattery = batteryId == null || batteryId.equals(key.batteryId());
            if (matchesVehicle && matchesBattery) {
                iterator.remove();
                evicted++;
            }
        }
        return evicted;
    }

    public int clear() {
        int size = cache.size();
        cache.clear();
        return size;
    }

    public int size() {
        return cache.size();
    }
}
