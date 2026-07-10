package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WriteBehindBufferTest {

    @Test
    @DisplayName("같은 일별 통계 키의 이벤트를 하나의 명령으로 병합한다")
    void drainShouldMergeCommandsByDailyStatsKey() {
        // given
        WriteBehindBuffer buffer = new WriteBehindBuffer();
        buffer.record(command(1L, 1L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7));
        buffer.record(command(1L, 1L, LocalDateTime.of(2026, 4, 1, 11, 0), 4, 6, 8));
        buffer.record(command(1L, 2L, LocalDateTime.of(2026, 4, 1, 12, 0), 5, 7, 9));

        // when
        List<WriteBehindBuffer.MergedCommand> result = buffer.drain();

        // then
        assertThat(result).hasSize(2);
        WriteBehindBuffer.MergedCommand first = result.getFirst();
        assertThat(first.key().statDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(first.key().targetId()).isEqualTo(1L);
        assertThat(first.key().segmentId()).isEqualTo(1L);
        assertThat(first.logCount()).isEqualTo(2);
        assertThat(first.totalDurationSeconds()).isEqualTo(7);
        assertThat(first.totalMetricValue()).isEqualTo(11);
        assertThat(first.totalCostValue()).isEqualTo(15);
    }

    @Test
    @DisplayName("flush 실패로 복원한 명령은 다음 drain에서 다시 반환된다")
    void restoreShouldMakeCommandsAvailableForRetry() {
        // given
        WriteBehindBuffer buffer = new WriteBehindBuffer();
        buffer.record(command(1L, 1L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7));
        List<WriteBehindBuffer.MergedCommand> drained = buffer.drain();

        // when
        buffer.restore(drained);
        List<WriteBehindBuffer.MergedCommand> retried = buffer.drain();

        // then
        assertThat(retried).hasSize(1);
        assertThat(retried.getFirst().logCount()).isEqualTo(1);
        assertThat(retried.getFirst().totalDurationSeconds()).isEqualTo(3);
        assertThat(retried.getFirst().totalMetricValue()).isEqualTo(5);
        assertThat(retried.getFirst().totalCostValue()).isEqualTo(7);
    }

    private AppendEventCommand command(long targetId,
                                       long segmentId,
                                       LocalDateTime occurredAt,
                                       int durationSeconds,
                                       int metricValue,
                                       int costValue) {
        return new AppendEventCommand(
                targetId,
                segmentId,
                occurredAt,
                durationSeconds,
                metricValue,
                costValue
        );
    }
}
