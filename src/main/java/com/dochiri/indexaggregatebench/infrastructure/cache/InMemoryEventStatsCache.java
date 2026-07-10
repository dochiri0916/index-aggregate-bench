package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsCache {

    private final Map<EventStatsCacheKey, EventStats> queryCache = new ConcurrentHashMap<>();
    private final Map<DailyStatsKey, AtomicAggregate> baseCellCache = new ConcurrentHashMap<>();
    private final Map<DailyStatsKey, AtomicAggregate> pendingCellCache = new ConcurrentHashMap<>();
    private final Set<EventStatsCacheKey> loadedDailyQueries = ConcurrentHashMap.newKeySet();
    private final Set<DailyStatsKey> loadedBaseKeys = ConcurrentHashMap.newKeySet();

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
        pendingCellCache.computeIfAbsent(key, k -> new AtomicAggregate())
                .add(command.durationSeconds(), command.metricValue(), command.costValue());
    }

    public boolean hasLoadedCells(EventStatsQuery query) {
        return loadedDailyQueries.contains(EventStatsCacheKey.of(EventStatsBackend.DAILY_STATS, query));
    }

    public void loadCells(EventStatsQuery query, Map<DailyStatsKey, AtomicAggregate> cells) {
        loadedDailyQueries.add(EventStatsCacheKey.of(EventStatsBackend.DAILY_STATS, query));
        for (Map.Entry<DailyStatsKey, AtomicAggregate> entry : cells.entrySet()) {
            if (loadedBaseKeys.add(entry.getKey())) {
                baseCellCache.put(entry.getKey(), copyOf(entry.getValue()));
            }
        }
    }

    public Optional<EventStats> aggregateFromCells(EventStatsQuery query) {
        if (!hasLoadedCells(query)) {
            return Optional.empty();
        }

        AtomicAggregate result = new AtomicAggregate();
        addMatchingCells(query, result, baseCellCache);
        addMatchingCells(query, result, pendingCellCache);
        return Optional.of(result.toEventStats());
    }

    public void markFlushed(Iterable<WriteBehindBuffer.MergedCommand> batch) {
        for (WriteBehindBuffer.MergedCommand command : batch) {
            DailyStatsKey key = command.key();
            if (loadedBaseKeys.contains(key)) {
                baseCellCache.computeIfAbsent(key, ignored -> new AtomicAggregate())
                        .add(command.logCount(), command.totalDurationSeconds(),
                                command.totalMetricValue(), command.totalCostValue());
            }
            AtomicAggregate pending = pendingCellCache.get(key);
            if (pending == null) {
                continue;
            }
            pending.add(-command.logCount(), -command.totalDurationSeconds(),
                    -command.totalMetricValue(), -command.totalCostValue());
            if (pending.isZero()) {
                pendingCellCache.remove(key, pending);
            }
        }
    }

    private void addMatchingCells(EventStatsQuery query,
                                  AtomicAggregate result,
                                  Map<DailyStatsKey, AtomicAggregate> source) {
        LocalDate current = query.from();
        while (!current.isAfter(query.to())) {
            for (Map.Entry<DailyStatsKey, AtomicAggregate> entry : source.entrySet()) {
                DailyStatsKey key = entry.getKey();
                if (!key.statDate().equals(current)) {
                    continue;
                }
                if (query.targetId() != null && !query.targetId().equals(key.targetId())) {
                    continue;
                }
                if (query.segmentId() != null && !query.segmentId().equals(key.segmentId())) {
                    continue;
                }
                AtomicAggregate cell = entry.getValue();
                result.add(cell.logCount(), cell.totalDurationSeconds(),
                        cell.totalMetricValue(), cell.totalCostValue());
            }
            current = current.plusDays(1);
        }
    }

    private AtomicAggregate copyOf(AtomicAggregate source) {
        AtomicAggregate copied = new AtomicAggregate();
        copied.add(source.logCount(), source.totalDurationSeconds(),
                source.totalMetricValue(), source.totalCostValue());
        return copied;
    }

    // ── Shared ──

    public int clear() {
        int querySize = queryCache.size();
        int cellSize = baseCellCache.size() + pendingCellCache.size();
        queryCache.clear();
        baseCellCache.clear();
        pendingCellCache.clear();
        loadedDailyQueries.clear();
        loadedBaseKeys.clear();
        return querySize + cellSize;
    }

    public int size() {
        return queryCache.size() + baseCellCache.size() + pendingCellCache.size();
    }

    public int queryCacheSize() {
        return queryCache.size();
    }

    public int cellCacheSize() {
        return baseCellCache.size() + pendingCellCache.size();
    }
}
