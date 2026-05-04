package com.dochiri.indexaggregatebench.infrastructure.web;

import com.dochiri.indexaggregatebench.infrastructure.persistence.SchemaIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bench")
public class BenchmarkAdminController {

    private final SchemaIndexService schemaIndexService;

    public BenchmarkAdminController(SchemaIndexService schemaIndexService) {
        this.schemaIndexService = schemaIndexService;
    }

    @GetMapping("/indexes/raw")
    public Map<String, Boolean> rawIndexStatus() {
        return schemaIndexService.rawIndexStatus();
    }

    @PostMapping("/indexes/raw/create")
    public Map<String, Boolean> createRawIndexes() {
        return schemaIndexService.createRawIndexes();
    }

    @PostMapping("/indexes/raw/drop")
    public Map<String, Boolean> dropRawIndexes() {
        return schemaIndexService.dropRawIndexes();
    }

    @GetMapping("/row-counts")
    public Map<String, Long> rowCounts() {
        return schemaIndexService.rowCounts();
    }
}
