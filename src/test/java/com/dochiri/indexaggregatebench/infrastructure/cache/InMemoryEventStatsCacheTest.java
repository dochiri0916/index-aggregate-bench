package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsKey;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStatsCacheTest {

    @Test
    @DisplayName("조회 범위가 로드되기 전에는 일부 증분 셀만으로 통계를 반환하지 않는다")
    void aggregateShouldMissBeforeQueryCellsLoaded() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStatsQuery query = new EventStatsQuery(1L, 1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        cache.incrementCell(command(LocalDateTime.of(2026, 4, 1, 10, 0)));

        // when
        var result = cache.aggregateFromCells(query);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("DB 기준 셀과 아직 flush되지 않은 증분 셀을 함께 집계한다")
    void aggregateShouldCombineLoadedBaseCellsAndPendingCells() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStatsQuery query = new EventStatsQuery(1L, 1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        DailyStatsKey key = new DailyStatsKey(LocalDate.of(2026, 4, 1), 1L, 1L);
        AtomicAggregate base = new AtomicAggregate();
        base.add(10, 100, 50, 25);
        cache.incrementCell(command(LocalDateTime.of(2026, 4, 1, 10, 0)));
        cache.loadCells(query, Map.of(key, base));

        // when
        EventStats result = cache.aggregateFromCells(query).orElseThrow();

        // then
        assertThat(result.logCount()).isEqualTo(11);
        assertThat(result.totalDurationSeconds()).isEqualTo(103);
        assertThat(result.totalMetricValue()).isEqualTo(55);
        assertThat(result.totalCostValue()).isEqualTo(32);
    }

    @Test
    @DisplayName("flush된 증분 셀은 로드된 기준 셀로 이동해 중복 집계하지 않는다")
    void markFlushedShouldMovePendingCellsToLoadedBaseCells() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStatsQuery query = new EventStatsQuery(1L, 1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        DailyStatsKey key = new DailyStatsKey(LocalDate.of(2026, 4, 1), 1L, 1L);
        AtomicAggregate base = new AtomicAggregate();
        base.add(10, 100, 50, 25);
        cache.incrementCell(command(LocalDateTime.of(2026, 4, 1, 10, 0)));
        cache.loadCells(query, Map.of(key, base));
        WriteBehindBuffer.MergedCommand flushed = new WriteBehindBuffer.MergedCommand(key, 1, 3, 5, 7);

        // when
        cache.markFlushed(List.of(flushed));
        EventStats result = cache.aggregateFromCells(query).orElseThrow();

        // then
        assertThat(result.logCount()).isEqualTo(11);
        assertThat(result.totalDurationSeconds()).isEqualTo(103);
        assertThat(result.totalMetricValue()).isEqualTo(55);
        assertThat(result.totalCostValue()).isEqualTo(32);
    }

    @Test
    @DisplayName("DB에 데이터가 없는 조회 범위도 로드 후에는 빈 통계로 반환한다")
    void aggregateShouldReturnEmptyStatsAfterEmptyQueryLoaded() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStatsQuery query = new EventStatsQuery(1L, 1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        cache.loadCells(query, Map.of());

        // when
        EventStats result = cache.aggregateFromCells(query).orElseThrow();

        // then
        assertThat(result.logCount()).isZero();
        assertThat(result.totalDurationSeconds()).isZero();
        assertThat(result.totalMetricValue()).isZero();
        assertThat(result.totalCostValue()).isZero();
    }

    private AppendEventCommand command(LocalDateTime occurredAt) {
        return new AppendEventCommand(1L, 1L, occurredAt, 3, 5, 7);
    }
}
