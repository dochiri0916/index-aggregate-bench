package com.dochiri.indexaggregatebench.infrastructure.web;

import com.dochiri.indexaggregatebench.application.dto.AppendEventCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareEventStatsResult;
import com.dochiri.indexaggregatebench.application.dto.RebuildEventMonthlyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedEventCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedEventResult;
import com.dochiri.indexaggregatebench.application.dto.EventStatsQuery;
import com.dochiri.indexaggregatebench.application.service.EventLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EventLogController {

    private final EventLogService eventLogService;

    public EventLogController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @PostMapping("/events/seed")
    public SeedEventResult seed(@RequestBody SeedEventCondition condition) {
        return eventLogService.seed(condition);
    }

    @PostMapping("/stats/monthly/rebuild")
    public RebuildEventMonthlyStatsResult rebuildMonthlyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return eventLogService.rebuildMonthlyStats(from, to);
    }

    @GetMapping("/stats/compare")
    public CompareEventStatsResult compare(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long segmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int iterations,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return eventLogService.compare(new EventStatsQuery(targetId, segmentId, from, to), iterations, cache);
    }

    @PostMapping("/events")
    public Map<String, String> append(@RequestBody AppendEventCommand command) {
        eventLogService.append(command);
        return Map.of("status", "ok");
    }
}
