package com.dochiri.indexaggregatebench.infrastructure.web;

import com.dochiri.indexaggregatebench.application.dto.EventStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedEventStats;
import com.dochiri.indexaggregatebench.application.service.EventStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class EventStatsController {

    private final EventStatsService eventStatsService;

    public EventStatsController(EventStatsService eventStatsService) {
        this.eventStatsService = eventStatsService;
    }

    @GetMapping("/raw")
    public TimedEventStats raw(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long segmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return eventStatsService.getStats(EventStatsBackend.RAW, new EventStatsQuery(targetId, segmentId, from, to), cache);
    }

    @GetMapping("/monthly")
    public TimedEventStats monthly(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long segmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return eventStatsService.getStats(EventStatsBackend.MONTHLY_STATS, new EventStatsQuery(targetId, segmentId, from, to), cache);
    }

    @GetMapping("/monthly/realtime")
    public TimedEventStats monthlyRealtime(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long segmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return eventStatsService.getStats(
                EventStatsBackend.MONTHLY_STATS_REALTIME,
                new EventStatsQuery(targetId, segmentId, from, to),
                cache
        );
    }

    @DeleteMapping("/cache")
    public Map<String, Integer> clearCache() {
        return Map.of("evicted", eventStatsService.clearCache());
    }

    @GetMapping("/cache")
    public Map<String, Integer> cache() {
        return Map.of("size", eventStatsService.cacheSize());
    }
}
