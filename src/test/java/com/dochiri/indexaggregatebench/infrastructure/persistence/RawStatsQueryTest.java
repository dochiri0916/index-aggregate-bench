package com.dochiri.indexaggregatebench.infrastructure.persistence;

import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RawStatsQueryTest {

    @Test
    @DisplayName("원본 통계 SQL은 중간 그룹 행을 만들지 않고 데이터베이스에서 단일 결과로 집계한다")
    void rawStatsQueryShouldAggregateToSingleRow() {
        // given
        EventStatsQuery query = new EventStatsQuery(1L, 2L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        // when
        RawStatsQuery result = RawStatsQuery.from(query, 3_000);

        // then
        assertThat(result.sql()).contains("COALESCE(SUM(duration_seconds), 0)");
        assertThat(result.sql()).contains("MAX_EXECUTION_TIME(3000)");
        assertThat(result.sql()).doesNotContain("GROUP BY");
        assertThat(result.params()).containsExactly(
                LocalDate.of(2026, 4, 1).atStartOfDay(),
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                1L,
                2L
        );
    }
}
