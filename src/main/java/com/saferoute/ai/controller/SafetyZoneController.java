package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.model.SafetyZone;
import com.saferoute.ai.service.SafetyZoneService;

@RestController
@RequestMapping("/api")
public class SafetyZoneController {

    private final SafetyZoneService safetyZoneService;

    public SafetyZoneController(SafetyZoneService safetyZoneService) {
        this.safetyZoneService = safetyZoneService;
    }

    @GetMapping("/safety-zones")
    public List<SafetyZone> zones() {
        return safetyZoneService.getActiveZones();
    }
}
