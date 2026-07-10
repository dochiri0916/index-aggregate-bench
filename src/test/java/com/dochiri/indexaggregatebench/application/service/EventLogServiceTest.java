package com.dochiri.indexaggregatebench.application.service;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.DailyStatsDelta;
import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.port.out.EventDailyStatsPort;
import com.dochiri.indexaggregatebench.application.port.out.EventLogPort;
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

class EventLogServiceTest {

    @Test
    @DisplayName("일별 통계 캐시 미스는 저장된 값과 아직 flush되지 않은 값을 함께 반환한다")
    void dailyStatsCacheMissShouldIncludePendingWriteBehindValues() {
        // given
        Fixture fixture = new Fixture();
        fixture.writeBehind.recordAfterCommit(new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        ));

        // when
        var result = fixture.statsService.getStats(EventStatsBackend.DAILY_STATS, query(1L, 2L), false);

        // then
        assertThat(result.stats().logCount()).isEqualTo(2);
        assertThat(result.stats().totalDurationSeconds()).isEqualTo(6);
        assertThat(result.stats().totalMetricValue()).isEqualTo(10);
        assertThat(result.stats().totalCostValue()).isEqualTo(14);
    }

    @Test
    @DisplayName("이벤트 추가는 원본을 저장하고 일별 통계 버퍼와 캐시에 즉시 반영한다")
    void appendShouldStoreRawEventAndUpdateWriteBehindAndCache() {
        // given
        Fixture fixture = new Fixture();
        AppendEventCommand command = new AppendEventCommand(
                1L, 2L, LocalDateTime.of(2026, 4, 1, 10, 0), 3, 5, 7
        );
        EventStatsQuery broadQuery = query(null, 2L);
        fixture.cache.put(EventStatsCacheKey.of(EventStatsBackend.RAW, broadQuery), stats());
        EventStatsCacheKey dailyKey = EventStatsCacheKey.of(EventStatsBackend.DAILY_STATS, broadQuery);
        fixture.cache.put(dailyKey, stats());

        // when
        fixture.service.append(command);

        // then
        assertThat(fixture.eventLogPort.appended).isEqualTo(command);
        assertThat(fixture.writeBehind.aggregatePending(query(1L, 2L)).logCount()).isEqualTo(1);
        assertThat(fixture.cache.get(EventStatsCacheKey.of(EventStatsBackend.RAW, broadQuery))).isEmpty();
        assertThat(fixture.cache.get(dailyKey).orElseThrow().logCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("일별 통계 재생성은 생성된 행 수를 반환하고 기존 캐시를 제거한다")
    void rebuildShouldReturnRowsAndClearCache() {
        // given
        Fixture fixture = new Fixture();
        fixture.dailyStatsPort.rebuildRows = 30;
        fixture.cache.put(EventStatsCacheKey.of(EventStatsBackend.RAW, query(1L, 2L)), stats());

        // when
        RebuildEventDailyStatsResult result = fixture.service.rebuildDailyStats(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
        );

        // then
        assertThat(result.eventDailyStatsRows()).isEqualTo(30);
        assertThat(fixture.dailyStatsPort.rebuiltFrom).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(fixture.dailyStatsPort.rebuiltTo).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(fixture.cache.size()).isZero();
    }

    @Test
    @DisplayName("초기화 시 원본과 일별 통계를 모두 비우고 새 데이터만 적재한다")
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
        assertThat(fixture.dailyStatsPort.truncated).isTrue();
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
        private final FakeDailyStatsPort dailyStatsPort = new FakeDailyStatsPort();
        private final InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        private final InMemoryEventStatsWriteBehind writeBehind = new InMemoryEventStatsWriteBehind();
        private final EventStatsConsistencyLock consistencyLock = new EventStatsConsistencyLock();
        private final EventStatsService statsService = new EventStatsService(
                new FakeStatsQueryPort(), dailyStatsPort, cache, writeBehind, consistencyLock
        );
        private final EventLogService service = new EventLogService(
                eventLogPort, dailyStatsPort, cache, writeBehind, consistencyLock, statsService
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

    private static final class FakeDailyStatsPort implements EventDailyStatsPort {
        private boolean truncated;
        private int rebuildRows;
        private LocalDate rebuiltFrom;
        private LocalDate rebuiltTo;
        private List<DailyStatsDelta> increasedBatch = List.of();

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
        public int increaseBatch(List<DailyStatsDelta> batch) {
            increasedBatch = List.copyOf(batch);
            return batch.size();
        }

        @Override
        public EventStats aggregate(EventStatsQuery query) {
            return stats();
        }
    }

    private static final class FakeStatsQueryPort implements EventStatsQueryPort {
        @Override
        public EventStats aggregate(EventStatsQuery query) {
            return stats();
        }
    }
}
