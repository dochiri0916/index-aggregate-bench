package com.dochiri.indexaggregatebench.infrastructure.web;

import com.dochiri.indexaggregatebench.application.dto.AppendVehicleCommand;
import com.dochiri.indexaggregatebench.application.dto.CompareVehicleStatsResult;
import com.dochiri.indexaggregatebench.application.dto.RebuildVehicleDailyStatsResult;
import com.dochiri.indexaggregatebench.application.dto.SeedVehicleCondition;
import com.dochiri.indexaggregatebench.application.dto.SeedVehicleResult;
import com.dochiri.indexaggregatebench.application.dto.VehicleStatsQuery;
import com.dochiri.indexaggregatebench.application.service.VehicleService;
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
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping("/vehicles/seed")
    public SeedVehicleResult seed(@RequestBody SeedVehicleCondition condition) {
        return vehicleService.seed(condition);
    }

    @PostMapping("/vehicle-stats/daily/rebuild")
    public RebuildVehicleDailyStatsResult rebuildDailyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return vehicleService.rebuildDailyStats(from, to);
    }

    @GetMapping("/vehicle-stats/compare")
    public CompareVehicleStatsResult compare(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long batteryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int iterations,
            @RequestParam(defaultValue = "false") boolean cache
    ) {
        return vehicleService.compare(new VehicleStatsQuery(id, batteryId, from, to), iterations, cache);
    }

    @PostMapping("/vehicles")
    public Map<String, String> append(@RequestBody AppendVehicleCommand command) {
        vehicleService.append(command);
        return Map.of("status", "ok");
    }
}
