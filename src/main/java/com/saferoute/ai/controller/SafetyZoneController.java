package com.saferoute.ai.controller;

import com.saferoute.ai.model.SafetyZone;
import com.saferoute.ai.service.SafetyZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SafetyZoneController {

    private final SafetyZoneService safetyZoneService;

    public SafetyZoneController(SafetyZoneService safetyZoneService) {
        this.safetyZoneService = safetyZoneService;
    }

    @GetMapping("/safety-zones")
    public ResponseEntity<List<SafetyZone>> getSafetyZones() {
        return ResponseEntity.ok(safetyZoneService.getActiveZones());
    }
}
