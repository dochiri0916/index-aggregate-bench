package com.dochiri.indexaggregatebench.application.port.out;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;

import java.util.Optional;

public interface EventStatsCachePort {
    Optional<EventStats> get(EventStatsCacheKey key);
    void put(EventStatsCacheKey key, EventStats stats);
    int evictRelated(Long targetId, Long segmentId);
    void evictRelatedAfterCommit(Long targetId, Long segmentId);
    int clear();
    void clearAfterCommit();
    int size();
}
