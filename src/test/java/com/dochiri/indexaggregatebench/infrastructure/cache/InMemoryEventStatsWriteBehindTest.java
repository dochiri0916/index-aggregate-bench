package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStatsWriteBehindTest {

    @Test
    @DisplayName("저장 대기 이벤트는 데이터베이스 flush 전에도 실시간 통계에 반영된다")
    void pendingEventShouldBeVisibleBeforeFlush() {
        // given
        InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        EventStatsQuery query = new EventStatsQuery(1L, 2L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        // when
        writeBehind.recordAfterCommit(command(LocalDateTime.of(2026, 4, 1, 10, 0)));
        writeBehind.recordAfterCommit(command(LocalDateTime.of(2026, 4, 1, 11, 0)));
        EventStats result = writeBehind.aggregatePending(query);

        // then
        assertThat(result.logCount()).isEqualTo(2);
        assertThat(result.totalDurationSeconds()).isEqualTo(6);
        assertThat(result.totalMetricValue()).isEqualTo(10);
        assertThat(result.totalCostValue()).isEqualTo(14);
    }

    @Test
    @DisplayName("이벤트 델타는 원본 저장 트랜잭션이 커밋된 후에만 버퍼에 기록된다")
    void eventShouldBeRecordedOnlyAfterCommit() {
        // given
        InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        TransactionSynchronizationManager.initSynchronization();
        try {
            writeBehind.recordAfterCommit(command(LocalDateTime.of(2026, 4, 1, 10, 0)));

            // when
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // then
            assertThat(writeBehind.size()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("flush 트랜잭션이 롤백되면 비운 델타를 버퍼로 복원한다")
    void drainedBatchShouldBeRestoredAfterRollback() {
        // given
        InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        writeBehind.recordAfterCommit(command(LocalDateTime.of(2026, 4, 1, 10, 0)));
        TransactionSynchronizationManager.initSynchronization();
        try {
            writeBehind.drainForCurrentTransaction();

            // when
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );

            // then
            assertThat(writeBehind.size()).isEqualTo(1);
            assertThat(writeBehind.aggregatePending(query()).logCount()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AppendEventCommand command(LocalDateTime occurredAt) {
        return new AppendEventCommand(1L, 2L, occurredAt, 3, 5, 7);
    }

    private EventStatsQuery query() {
        return new EventStatsQuery(1L, 2L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    }
}
