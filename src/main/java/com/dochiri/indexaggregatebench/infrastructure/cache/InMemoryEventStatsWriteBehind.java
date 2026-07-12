package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsKey;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsWriteBehind implements EventStatsWriteBehindPort {

    private final Map<MonthlyStatsKey, MonthlyStatsDelta> pending = new ConcurrentHashMap<>();
    private volatile String restoredBatchId;

    @Override
    public void recordAfterCommit(AppendEventCommand command) {
        afterCommit(() -> merge(MonthlyStatsDelta.from(command)));
    }

    @Override
    public MonthlyStatsFlushBatch drainForCurrentTransaction() {
        MonthlyStatsFlushBatch batch = new MonthlyStatsFlushBatch(
                restoredBatchId == null ? UUID.randomUUID().toString() : restoredBatchId,
                List.copyOf(pending.values())
        );
        restoredBatchId = null;
        pending.clear();
        restoreAfterRollback(batch);
        return batch;
    }

    @Override
    public EventStats aggregatePending(EventStatsQuery query) {
        EventStats result = emptyStats();
        for (MonthlyStatsDelta delta : pending.values()) {
            if (!matches(delta.key(), query)) {
                continue;
            }
            result = result.plus(delta.toEventStats());
        }
        return result;
    }

    @Override
    public int clear() {
        int size = pending.size();
        pending.clear();
        restoredBatchId = null;
        return size;
    }

    @Override
    public int size() {
        return pending.size();
    }

    private void merge(MonthlyStatsDelta delta) {
        pending.merge(delta.key(), delta, MonthlyStatsDelta::plus);
    }

    private void restoreAfterRollback(MonthlyStatsFlushBatch batch) {
        if (batch.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public int getOrder() {
                return Integer.MAX_VALUE - 1;
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                restoredBatchId = batch.batchId();
                batch.deltas().forEach(InMemoryEventStatsWriteBehind.this::merge);
            }
        });
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

    private boolean matches(MonthlyStatsKey key, EventStatsQuery query) {
        if (key.statMonth().isBefore(query.fromMonth()) || key.statMonth().isAfter(query.toMonth())) {
            return false;
        }
        if (query.targetId() != null && query.targetId() != key.targetId()) {
            return false;
        }
        return query.segmentId() == null || query.segmentId() == key.segmentId();
    }

    private EventStats emptyStats() {
        return new EventStats(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
