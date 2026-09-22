package com.legacyforge.stats.controller;

import com.legacyforge.stats.dto.PlatformStats;
import com.legacyforge.stats.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    public ResponseEntity<PlatformStats.Response> platform() {
        return ResponseEntity.ok(stats.platformStats());
    }
}
