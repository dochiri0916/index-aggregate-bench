package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.MonthlyStatsFlushBatch;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventMonthlyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
import com.dochiri.indexaggregatebench.application.port.out.EventMonthlyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventStatsQueryPort;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsCache;
import com.dochiri.indexaggregatebench.infrastructure.cache.EventStatsConsistencyLock;
import com.dochiri.indexaggregatebench.infrastructure.cache.InMemoryEventStatsWriteBehind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventLogServiceTest {

    @Test
    @DisplayName("실시간 월별 통계 캐시 미스는 저장된 값과 아직 flush되지 않은 값을 함께 반환한다")
    void realtimeMonthlyStatsCacheMissShouldIncludePendingWriteBehindValues() {
        // given
        Fixture fixture = new Fixture();
        fixture.writeBehind.recordAfterCommit(new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        ));

        // when
        var result = fixture.statsService.getStats(
                EventStatsBackend.MONTHLY_STATS_REALTIME, query(1L, 2L), false
        );

        // then
        assertThat(result.stats().logCount()).isEqualTo(2);
        assertThat(result.stats().totalDurationSeconds()).isEqualTo(6);
        assertThat(result.stats().totalMetricValue()).isEqualTo(10);
        assertThat(result.stats().totalCostValue()).isEqualTo(14);
    }

    @Test
    @DisplayName("기본 월별 통계는 아직 flush되지 않은 write-behind 값을 포함하지 않는다")
    void monthlyStatsShouldReadOnlyPersistedAggregate() {
        // given
        Fixture fixture = new Fixture();
        fixture.writeBehind.recordAfterCommit(new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        ));

        // when
        var result = fixture.statsService.getStats(EventStatsBackend.MONTHLY_STATS, query(1L, 2L), false);

        // then
        assertThat(result.stats().logCount()).isEqualTo(1);
        assertThat(result.stats().totalDurationSeconds()).isEqualTo(3);
        assertThat(result.stats().totalMetricValue()).isEqualTo(5);
        assertThat(result.stats().totalCostValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("월별 통계는 부분 월 조회를 허용하지 않는다")
    void monthlyStatsShouldRejectPartialMonthQuery() {
        // given
        Fixture fixture = new Fixture();
        EventStatsQuery partialMonth = new EventStatsQuery(
                1L, 2L, LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 30)
        );

        // when & then
        assertThatThrownBy(() -> fixture.statsService.getStats(
                EventStatsBackend.MONTHLY_STATS, partialMonth, false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("raw와 월별 통계 비교 결과는 값 일치 여부와 차이를 함께 반환한다")
    void compareShouldExposeStatsMatchAndDifference() {
        // given
        Fixture fixture = new Fixture();

        // when
        CompareEventStatsResult result = fixture.service.compare(query(1L, 2L), 1, false);

        // then
        assertThat(result.statsMatch()).isTrue();
        assertThat(result.difference().isZero()).isTrue();
    }

    @Test
    @DisplayName("raw와 월별 통계가 다르면 합계와 파생 지표의 차이를 반환한다")
    void compareShouldExposeAggregateAndDerivedDifferences() {
        // given
        Fixture fixture = new Fixture();
        fixture.monthlyStatsPort.aggregated = new EventStats(
                1, 2, 4, 8, BigDecimal.valueOf(2), BigDecimal.valueOf(0.5)
        );

        // when
        CompareEventStatsResult result = fixture.service.compare(query(1L, 2L), 1, false);

        // then
        assertThat(result.statsMatch()).isFalse();
        assertThat(result.difference().totalDurationSeconds()).isEqualTo(1);
        assertThat(result.difference().totalMetricValue()).isEqualTo(1);
        assertThat(result.difference().totalCostValue()).isEqualTo(-1);
        assertThat(result.difference().averageDurationSeconds()).isEqualByComparingTo("1.00");
        assertThat(result.difference().metricPerCost()).isEqualByComparingTo("0.21");
    }

    @Test
    @DisplayName("이벤트 추가는 원본과 실시간 월별 버퍼를 갱신하고 기본 월별 캐시는 무효화한다")
    void appendShouldStoreRawEventAndUpdateRealtimeWriteBehind() {
        // given
        Fixture fixture = new Fixture();
        AppendEventCommand command = new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        );
        EventStatsQuery broadQuery = query(null, 2L);
        fixture.cache.put(EventStatsCacheKey.of(EventStatsBackend.RAW, broadQuery), stats());
        EventStatsCacheKey monthlyKey = EventStatsCacheKey.of(EventStatsBackend.MONTHLY_STATS, broadQuery);
        fixture.cache.put(monthlyKey, stats());
        EventStatsCacheKey realtimeMonthlyKey = EventStatsCacheKey.of(
                EventStatsBackend.MONTHLY_STATS_REALTIME, broadQuery
        );
        fixture.cache.put(realtimeMonthlyKey, stats());

        // when
        fixture.service.append(command);

        // then
        assertThat(fixture.eventLogPort.appended).isEqualTo(command);
        assertThat(fixture.writeBehind.aggregatePending(query(1L, 2L)).logCount()).isEqualTo(1);
        assertThat(fixture.cache.get(EventStatsCacheKey.of(EventStatsBackend.RAW, broadQuery))).isEmpty();
        assertThat(fixture.cache.get(monthlyKey)).isEmpty();
        assertThat(fixture.cache.get(realtimeMonthlyKey).orElseThrow().logCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("월별 통계 재생성은 생성된 행 수를 반환하고 기존 캐시를 제거한다")
    void rebuildMonthlyStatsShouldReturnRowsAndClearCache() {
        // given
        Fixture fixture = new Fixture();
        fixture.monthlyStatsPort.rebuildRows = 30;
        fixture.cache.put(EventStatsCacheKey.of(EventStatsBackend.RAW, query(1L, 2L)), stats());

        // when
        RebuildEventMonthlyStatsResult result = fixture.service.rebuildMonthlyStats(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
        );

        // then
        assertThat(result.eventMonthlyStatsRows()).isEqualTo(30);
        assertThat(fixture.monthlyStatsPort.rebuiltFrom).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(fixture.monthlyStatsPort.rebuiltTo).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(fixture.cache.size()).isZero();
    }

    @Test
    @DisplayName("초기화 시 원본과 월별 통계를 모두 비우고 새 데이터만 적재한다")
    void seedWithTruncateShouldClearBothStoresAndCache() {
        // given
        Fixture fixture = new Fixture();
        fixture.eventLogPort.seedRows = 150;
        fixture.cache.put(EventStatsCacheKey.of(EventStatsBackend.RAW, query(1L, 2L)), stats());
        SeedEventCondition condition = new SeedEventCondition(
                LocalDate.of(2026, 4, 1), 1, 3, 50, true
        );

        // when
        var result = fixture.service.seed(condition);

        // then
        assertThat(result.insertedRows()).isEqualTo(150);
        assertThat(fixture.eventLogPort.truncated).isTrue();
        assertThat(fixture.monthlyStatsPort.truncated).isTrue();
        assertThat(fixture.eventLogPort.seedCondition).isEqualTo(condition);
        assertThat(fixture.cache.size()).isZero();
    }

    private static EventStatsQuery query(Long targetId, Long segmentId) {
        return new EventStatsQuery(targetId, segmentId,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    }

    private static EventStats stats() {
        return new EventStats(1, 3, 5, 7, BigDecimal.valueOf(3), BigDecimal.valueOf(0.71));
    }

    private static final class Fixture {
        private final FakeEventLogPort eventLogPort = new FakeEventLogPort();
        private final FakeMonthlyStatsPort monthlyStatsPort = new FakeMonthlyStatsPort();
        private final FakeStatsQueryPort statsQueryPort = new FakeStatsQueryPort();
        private final InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        private final InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        private final EventStatsConsistencyLock consistencyLock = new EventStatsConsistencyLock();
        private final EventStatsService statsService = new EventStatsService(
                statsQueryPort, monthlyStatsPort, cache, writeBehind, consistencyLock
        );
        private final EventLogService service = new EventLogService(
                eventLogPort, monthlyStatsPort, cache, writeBehind, consistencyLock, statsService
        );
    }

    private static final class FakeEventLogPort implements EventLogPort {
        private long seedRows;
        private boolean truncated;
        private SeedEventCondition seedCondition;
        private AppendEventCommand appended;

        @Override
        public long seed(SeedEventCondition condition) {
            seedCondition = condition;
            return seedRows;
        }

        @Override
        public void truncate() {
            truncated = true;
        }

        @Override
        public void append(AppendEventCommand command) {
            appended = command;
        }
    }

    private static final class FakeMonthlyStatsPort implements EventMonthlyStatsPort {
        private boolean truncated;
        private int rebuildRows;
        private LocalDate rebuiltFrom;
        private LocalDate rebuiltTo;
        private List<MonthlyStatsDelta> increasedBatch = List.of();
        private EventStats aggregated = stats();

        @Override
        public void truncate() {
            truncated = true;
        }

        @Override
        public int rebuild(LocalDate from, LocalDate to) {
            rebuiltFrom = from;
            rebuiltTo = to;
            return rebuildRows;
        }

        @Override
        public int increaseBatch(MonthlyStatsFlushBatch batch) {
            increasedBatch = batch.deltas();
            return batch.deltas().size();
        }

        @Override
        public EventStats aggregate(EventStatsQuery query) {
            return aggregated;
        }
    }

    private static final class FakeStatsQueryPort implements EventStatsQueryPort {
        @Override
        public EventStats aggregate(EventStatsQuery query) {
            return stats();
        }
    }
}
