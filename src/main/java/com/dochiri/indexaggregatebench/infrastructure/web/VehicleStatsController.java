package com.dochiri.indexaggregatebench.infrastructure.web;

import com.dochiri.indexaggregatebench.application.dto.VehicleStatsBackend;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsQuery;
import com.dochiri.indexaggregatebench.application.dto.TimedVehicleStats;
import com.dochiri.indexaggregatebench.application.service.VehicleStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle-stats")
public class VehicleStatsController {

    private final VehicleStatsService vehicleStatsService;

    public VehicleStatsController(VehicleStatsService vehicleStatsService) {
        this.vehicleStatsService = vehicleStatsService;
    }

    @GetMapping("/raw")
    public TimedVehicleStats raw(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long batteryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return vehicleStatsService.getStats(VehicleStatsBackend.RAW, new VehicleStatsQuery(id, batteryId, from, to), cache);
    }

    @GetMapping("/daily")
    public TimedVehicleStats daily(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long batteryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return vehicleStatsService.getStats(VehicleStatsBackend.DAILY_STATS, new VehicleStatsQuery(id, batteryId, from, to), cache);
    }

    @DeleteMapping("/cache")
    public Map<String, Integer> clearCache() {
        return Map.of("evicted", vehicleStatsService.clearCache());
    }

    @GetMapping("/cache")
    public Map<String, Integer> cache() {
        return Map.of("size", vehicleStatsService.cacheSize());
    }
}
