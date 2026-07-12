package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsDifference;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventMonthlyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedEventResult;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsCachePort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsConsistencyPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventLogService {

    private final EventLogPort eventLogPort;
    private final EventMonthlyStatsPort eventMonthlyStatsPort;
    private final EventStatsCachePort statsCache;
    private final EventStatsWriteBehindPort writeBehindPort;
    private final EventStatsConsistencyPort consistencyPort;
    private final EventStatsService eventStatsService;

    public EventLogService(EventLogPort eventLogPort,
                           EventMonthlyStatsPort eventMonthlyStatsPort,
                           EventStatsCachePort statsCache,
                           EventStatsWriteBehindPort writeBehindPort,
                           EventStatsConsistencyPort consistencyPort,
                           EventStatsService eventStatsService) {
        this.eventLogPort = eventLogPort;
        this.eventMonthlyStatsPort = eventMonthlyStatsPort;
        this.statsCache = statsCache;
        this.writeBehindPort = writeBehindPort;
        this.consistencyPort = consistencyPort;
        this.eventStatsService = eventStatsService;
    }

    public SeedEventResult seed(SeedEventCondition condition) {
        long started = System.nanoTime();
        long insertedRows = consistencyPort.executeExclusive(() -> {
            if (condition.truncate()) {
                writeBehindPort.clear();
                eventMonthlyStatsPort.truncate();
                eventLogPort.truncate();
                statsCache.clear();
            }
            return eventLogPort.seed(condition);
        });
        return new SeedEventResult(insertedRows, elapsedMillis(started));
    }

    @Transactional
    public RebuildEventMonthlyStatsResult rebuildMonthlyStats(LocalDate from, LocalDate to) {
        validateCompleteMonthRange(from, to);
        long started = System.nanoTime();
        return consistencyPort.executeExclusiveUntilCompletion(() -> {
            MonthlyStatsFlushBatch pending = writeBehindPort.drainForCurrentTransaction();
            eventMonthlyStatsPort.increaseBatch(pending);
            int rows = eventMonthlyStatsPort.rebuild(from, to);
            statsCache.clearAfterCommit();
            return new RebuildEventMonthlyStatsResult(rows, elapsedMillis(started));
        });
    }

    public CompareEventStatsResult compare(EventStatsQuery query, int iterations, boolean cache) {
        int count = Math.max(1, iterations);
        TimedEventStats lastRaw = null;
        TimedEventStats lastMonthly = null;
        long rawTotal = 0;
        long monthlyTotal = 0;
        long rawMin = Long.MAX_VALUE;
        long monthlyMin = Long.MAX_VALUE;
        List<Long> rawElapsedMillis = new ArrayList<>(count);
        List<Long> monthlyElapsedMillis = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lastRaw = eventStatsService.getStats(EventStatsBackend.RAW, query, cache);
            lastMonthly = eventStatsService.getStats(EventStatsBackend.MONTHLY_STATS, query, cache);
            rawTotal += lastRaw.elapsedMillis();
            monthlyTotal += lastMonthly.elapsedMillis();
            rawMin = Math.min(rawMin, lastRaw.elapsedMillis());
            monthlyMin = Math.min(monthlyMin, lastMonthly.elapsedMillis());
            rawElapsedMillis.add(lastRaw.elapsedMillis());
            monthlyElapsedMillis.add(lastMonthly.elapsedMillis());
        }
        rawElapsedMillis.sort(Long::compareTo);
        monthlyElapsedMillis.sort(Long::compareTo);
        EventStatsDifference difference = EventStatsDifference.between(lastRaw.stats(), lastMonthly.stats());
        return new CompareEventStatsResult(
                count, rawTotal / count, monthlyTotal / count, rawMin, monthlyMin,
                percentile95(rawElapsedMillis), percentile95(monthlyElapsedMillis),
                lastRaw, lastMonthly, difference.isZero(), difference
        );
    }

    private long percentile95(List<Long> elapsedMillis) {
        int index = Math.max(0, (int) Math.ceil(elapsedMillis.size() * 0.95) - 1);
        return elapsedMillis.get(index);
    }

    private void validateCompleteMonthRange(LocalDate from, LocalDate to) {
        if (from == null || to == null
                || !from.equals(from.withDayOfMonth(1))
                || !to.equals(to.withDayOfMonth(1).plusMonths(1).minusDays(1))) {
            throw new IllegalArgumentException("monthly stats require complete calendar months");
        }
    }

    @Transactional
    public void append(AppendEventCommand command) {
        consistencyPort.executeSharedUntilCompletion(() -> {
            eventLogPort.append(command);
            writeBehindPort.recordAfterCommit(command);
            statsCache.applyEventAfterCommit(command);
            return null;
        });
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
