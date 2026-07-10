package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsCache {

    private final Map<EventStatsCacheKey, EventStats> queryCache = new ConcurrentHashMap<>();
    private final Map<DailyStatsKey, AtomicAggregate> cellCache = new ConcurrentHashMap<>();

    // ── Query-level cache (RAW backend, legacy) ──

    public Optional<EventStats> get(EventStatsCacheKey key) {
        return Optional.ofNullable(queryCache.get(key));
    }

    public void put(EventStatsCacheKey key, EventStats stats) {
        queryCache.put(key, stats);
    }

    public int evictRelated(Long targetId, Long segmentId) {
        int evicted = 0;
        Iterator<EventStatsCacheKey> iterator = queryCache.keySet().iterator();
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

    // ── Cell-level cache (DAILY_STATS backend, write-behind) ──

    public void incrementCell(AppendEventCommand command) {
        DailyStatsKey key = DailyStatsKey.from(command);
        cellCache.computeIfAbsent(key, k -> new AtomicAggregate())
                .add(command.durationSeconds(), command.metricValue(), command.costValue());
    }

    public void loadCells(Map<DailyStatsKey, AtomicAggregate> cells) {
        for (Map.Entry<DailyStatsKey, AtomicAggregate> entry : cells.entrySet()) {
            cellCache.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    public Optional<EventStats> aggregateFromCells(EventStatsQuery query) {
        LocalDate current = query.from();
        AtomicAggregate result = new AtomicAggregate();
        boolean anyCellFound = false;

        while (!current.isAfter(query.to())) {
            for (Map.Entry<DailyStatsKey, AtomicAggregate> entry : cellCache.entrySet()) {
                DailyStatsKey key = entry.getKey();
                if (!key.statDate().equals(current)) {
                    continue;
                }
                if (query.targetId() != null && key.targetId() != query.targetId()) {
                    continue;
                }
                if (query.segmentId() != null && key.segmentId() != query.segmentId()) {
                    continue;
                }
                AtomicAggregate cell = entry.getValue();
                result.add(cell.logCount(), cell.totalDurationSeconds(),
                        cell.totalMetricValue(), cell.totalCostValue());
                anyCellFound = true;
            }
            current = current.plusDays(1);
        }

        if (!anyCellFound) {
            return Optional.empty();
        }
        return Optional.of(result.toEventStats());
    }

    // ── Shared ──

    public int clear() {
        int querySize = queryCache.size();
        int cellSize = cellCache.size();
        queryCache.clear();
        cellCache.clear();
        return querySize + cellSize;
    }

    public int size() {
        return queryCache.size() + cellCache.size();
    }

    public int queryCacheSize() {
        return queryCache.size();
    }

    public int cellCacheSize() {
        return cellCache.size();
    }
}
