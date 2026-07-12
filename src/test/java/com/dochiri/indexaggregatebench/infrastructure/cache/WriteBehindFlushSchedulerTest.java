package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.WriteBehindProperties;
import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.dto.WriteBehindFlushStatus;
import com.dochiri.indexaggregatebench.application.port.in.FlushEventMonthlyStatsUseCase;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsWriteBehindPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class WriteBehindFlushSchedulerTest {

    @Test
    @DisplayName("flush가 반복 실패하면 시도 횟수와 최종 실패 상태를 관측할 수 있다")
    void repeatedFlushFailureShouldExposeFinalFailureStatus() {
        // given
        WriteBehindFlushScheduler scheduler = new WriteBehindFlushScheduler(
                new FailingFlushUseCase(), new PendingWriteBehindPort(), new WriteBehindProperties(2, 0)
        );

        // when
        scheduler.flush();
        scheduler.flush();

        // then
        WriteBehindFlushStatus status = scheduler.status();
        assertThat(status.pendingDeltaCount()).isEqualTo(2);
        assertThat(status.attemptCount()).isEqualTo(2);
        assertThat(status.lastFailureAt()).isNotNull();
        assertThat(status.lastFailureReason()).contains("simulated flush failure");
        assertThat(status.nextRetryAt()).isNull();
        assertThat(status.finalFailure()).isTrue();
    }

    private static final class FailingFlushUseCase implements FlushEventMonthlyStatsUseCase {
        @Override
        public int flush() {
            throw new IllegalStateException("simulated flush failure");
        }
    }

    private static final class PendingWriteBehindPort implements EventStatsWriteBehindPort {
        @Override
        public void recordAfterCommit(AppendEventCommand command) {
        }

        @Override
        public MonthlyStatsFlushBatch drainForCurrentTransaction() {
            return MonthlyStatsFlushBatch.of(java.util.List.of());
        }

        @Override
        public EventStats aggregatePending(EventStatsQuery query) {
            return new EventStats(0, 0, 0, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        }

        @Override
        public int clear() {
            return 0;
        }

        @Override
        public int size() {
            return 2;
        }
    }
}
