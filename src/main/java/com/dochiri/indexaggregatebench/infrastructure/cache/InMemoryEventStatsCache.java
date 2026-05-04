package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsCache {

    private final Map<EventStatsCacheKey, EventStats> cache = new ConcurrentHashMap<>();

    public Optional<EventStats> get(EventStatsCacheKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(EventStatsCacheKey key, EventStats stats) {
        cache.put(key, stats);
    }

    public int evictRelated(Long targetId, Long segmentId) {
        int evicted = 0;
        Iterator<EventStatsCacheKey> iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            EventStatsCacheKey key = iterator.next();
            boolean matchesTarget = targetId == null || targetId.equals(key.targetId());
            boolean matchesSegment = segmentId == null || segmentId.equals(key.segmentId());
            if (matchesTarget && matchesSegment) {
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
