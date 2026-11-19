package com.saferoute.ai.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.ReportDtos.ReportRequest;
import com.saferoute.ai.dto.ReportDtos.ReportView;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.model.ReportCategory;
import com.saferoute.ai.service.ReportService;

import jakarta.validation.Valid;

/** Community safety reports: anyone can read them, signed-in users can add them. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<ReportView> near(@RequestParam double latitude, @RequestParam double longitude,
                                 @RequestParam(defaultValue = "5") double radiusKm) {
        GeoPoint point = new GeoPoint(latitude, longitude);
        return reportService.near(point.lat(), point.lon(), radiusKm);
    }

    @PostMapping
    public ResponseEntity<ReportView> create(@Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(request));
    }

    /** The categories the UI offers, with labels. */
    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return Arrays.stream(ReportCategory.values())
                .map(c -> Map.<String, Object>of("value", c.name(), "label", c.getLabel(), "positive", c.getSeverity() < 0))
                .toList();
    }
}
