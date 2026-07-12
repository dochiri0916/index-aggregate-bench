package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import com.dochiri.indexaggregatebench.infrastructure.cache.EventStatsConsistencyLock;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsCache;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsWriteBehind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlushEventMonthlyStatsServiceTest {

    @Test
    @DisplayName("월별 flush 실패는 같은 batch를 복원하고 재시도 성공 시 한 번만 반영한다")
    void failedFlushShouldRestoreSameBatchAndApplyOnceOnRetry() {
        // given
        InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        writeBehind.recordAfterCommit(new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        ));
        RecordingMonthlyStatsPort monthlyStatsPort = new RecordingMonthlyStatsPort();
        InMemoryEventStatsCache statsCache = new InMemoryEventStatsCache();
        statsCache.put(EventStatsCacheKey.of(EventStatsBackend.MONTHLY_STATS, query()), stats());
        FlushEventMonthlyStatsService service = new FlushEventMonthlyStatsService(
                new EventStatsConsistencyLock(), writeBehind, monthlyStatsPort, statsCache
        );

        // when & then
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(service::flush).isInstanceOf(IllegalStateException.class);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // then
        assertThat(writeBehind.size()).isEqualTo(1);
        monthlyStatsPort.fail = false;
        TransactionSynchronizationManager.initSynchronization();
        try {
            int applied = service.flush();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            );

            assertThat(applied).isEqualTo(1);
            assertThat(monthlyStatsPort.processedBatchIds).hasSize(1);
            assertThat(writeBehind.size()).isZero();
            assertThat(statsCache.size()).isZero();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static EventStatsQuery query() {
        return new EventStatsQuery(1L, 2L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    }

    private static EventStats stats() {
        return new EventStats(1, 3, 5, 7, BigDecimal.valueOf(3), BigDecimal.valueOf(0.71));
    }

    private static final class RecordingMonthlyStatsPort implements EventMonthlyStatsPort {

        private final Set<String> processedBatchIds = new HashSet<>();
        private boolean fail = true;

        @Override
        public void truncate() {
        }

        @Override
        public int rebuild(LocalDate from, LocalDate to) {
            return 0;
        }

        @Override
        public int increaseBatch(MonthlyStatsFlushBatch batch) {
            if (fail) {
                throw new IllegalStateException();
            }
            if (!processedBatchIds.add(batch.batchId())) {
                return 0;
            }
            return batch.deltas().size();
        }

        @Override
        public EventStats aggregate(EventStatsQuery query) {
            return new EventStats(0, 0, 0, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        }
    }
}
