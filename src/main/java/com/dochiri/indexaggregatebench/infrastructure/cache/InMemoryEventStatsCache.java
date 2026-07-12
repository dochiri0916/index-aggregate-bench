package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsDelta;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsCache implements EventStatsCachePort {

    private final Map<EventStatsCacheKey, EventStats> queryCache = new ConcurrentHashMap<>();

    @Override
    public Optional<EventStats> get(EventStatsCacheKey key) {
        return Optional.ofNullable(queryCache.get(key));
    }

    @Override
    public void put(EventStatsCacheKey key, EventStats stats) {
        queryCache.put(key, stats);
    }

    @Override
    public int evictRelated(Long targetId, Long segmentId) {
        int before = queryCache.size();
        queryCache.keySet().removeIf(key -> matches(key.targetId(), targetId)
                && matches(key.segmentId(), segmentId));
        return before - queryCache.size();
    }

    @Override
    public void evictRelatedAfterCommit(Long targetId, Long segmentId) {
        afterCommit(() -> evictRelated(targetId, segmentId));
    }

    @Override
    public void applyEventAfterCommit(AppendEventCommand command) {
        afterCommit(() -> queryCache.forEach((key, ignored) -> {
            if (!matches(key.targetId(), command.targetId())
                    || !matches(key.segmentId(), command.segmentId())) {
                return;
            }
            if (key.backend() == EventStatsBackend.RAW
                    || key.backend() == EventStatsBackend.MONTHLY_STATS) {
                queryCache.remove(key);
                return;
            }
            if (key.backend() != EventStatsBackend.MONTHLY_STATS_REALTIME) {
                return;
            }
            if (command.occurredAt().toLocalDate().isBefore(key.from())
                    || command.occurredAt().toLocalDate().isAfter(key.to())) {
                return;
            }
            EventStats delta = MonthlyStatsDelta.from(command).toEventStats();
            queryCache.computeIfPresent(key, (cacheKey, stats) -> stats.plus(delta));
        }));
    }

    @Override
    public int clear() {
        int size = queryCache.size();
        queryCache.clear();
        return size;
    }

    @Override
    public void clearAfterCommit() {
        afterCommit(this::clear);
    }

    @Override
    public int size() {
        return queryCache.size();
    }

    private boolean matches(Long cachedFilter, Long eventValue) {
        return cachedFilter == null || cachedFilter.equals(eventValue);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
