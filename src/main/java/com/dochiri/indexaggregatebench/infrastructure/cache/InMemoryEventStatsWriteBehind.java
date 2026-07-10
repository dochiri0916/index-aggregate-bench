package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStatsWriteBehind implements EventStatsWriteBehindPort {

    private final Map<DailyStatsKey, DailyStatsDelta> pending = new ConcurrentHashMap<>();

    @Override
    public void recordAfterCommit(AppendEventCommand command) {
        afterCommit(() -> merge(DailyStatsDelta.from(command)));
    }

    @Override
    public List<DailyStatsDelta> drainForCurrentTransaction() {
        List<DailyStatsDelta> batch = List.copyOf(pending.values());
        pending.clear();
        restoreAfterRollback(batch);
        return batch;
    }

    @Override
    public EventStats aggregatePending(EventStatsQuery query) {
        EventStats result = emptyStats();
        for (DailyStatsDelta delta : pending.values()) {
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
        return size;
    }

    @Override
    public int size() {
        return pending.size();
    }

    private void merge(DailyStatsDelta delta) {
        pending.merge(delta.key(), delta, DailyStatsDelta::plus);
    }

    private void restoreAfterRollback(List<DailyStatsDelta> batch) {
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
                batch.forEach(InMemoryEventStatsWriteBehind.this::merge);
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

    private boolean matches(DailyStatsKey key, EventStatsQuery query) {
        if (key.statDate().isBefore(query.from()) || key.statDate().isAfter(query.to())) {
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
