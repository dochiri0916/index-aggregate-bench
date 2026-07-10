package com.dochiri.indexaggregatebench.infrastructure.cache;

import com.dochiri.indexaggregatebench.application.dto.EventStats;
import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsCacheKey;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStatsCacheTest {

    @Test
    @DisplayName("이벤트가 추가되면 정확히 일치하는 조회와 전체 범위 조회 캐시를 함께 제거한다")
    void evictRelatedShouldRemoveExactAndWildcardQueries() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStats stats = stats();
        EventStatsQuery exact = query(1L, 1L);
        EventStatsQuery allTargets = query(null, 1L);
        EventStatsQuery allSegments = query(1L, null);
        EventStatsQuery unrelated = query(2L, 2L);
        cache.put(key(exact), stats);
        cache.put(key(allTargets), stats);
        cache.put(key(allSegments), stats);
        cache.put(key(unrelated), stats);

        // when
        int evicted = cache.evictRelated(1L, 1L);

        // then
        assertThat(evicted).isEqualTo(3);
        assertThat(cache.get(key(exact))).isEmpty();
        assertThat(cache.get(key(allTargets))).isEmpty();
        assertThat(cache.get(key(allSegments))).isEmpty();
        assertThat(cache.get(key(unrelated))).contains(stats);
    }

    @Test
    @DisplayName("캐시에 저장한 통계는 같은 조건으로 다시 조회할 수 있다")
    void putShouldMakeStatsAvailableBySameKey() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        EventStatsCacheKey key = key(query(1L, 1L));
        EventStats stats = stats();

        // when
        cache.put(key, stats);

        // then
        assertThat(cache.get(key)).contains(stats);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 캐시 제거는 제거한 항목 수를 반환한다")
    void clearShouldReturnEvictedEntryCount() {
        // given
        InMemoryEventStatsCache cache = new InMemoryEventStatsCache();
        cache.put(key(query(1L, 1L)), stats());
        cache.put(key(query(2L, 2L)), stats());

        // when
        int evicted = cache.clear();

        // then
        assertThat(evicted).isEqualTo(2);
        assertThat(cache.size()).isZero();
    }

    private EventStatsCacheKey key(EventStatsQuery query) {
        return EventStatsCacheKey.of(EventStatsBackend.RAW, query);
    }

    private EventStatsQuery query(Long targetId, Long segmentId) {
        return new EventStatsQuery(targetId, segmentId,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    }

    private EventStats stats() {
        return new EventStats(1, 3, 5, 7, BigDecimal.valueOf(3), BigDecimal.valueOf(0.71));
    }
}
