package com.saferoute.ai.controller;

import com.saferoute.ai.dto.SafeLocation;
import com.saferoute.ai.service.SafeLocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SafeLocationController {

    private final SafeLocationService safeLocationService;

    public SafeLocationController(SafeLocationService safeLocationService) {
        this.safeLocationService = safeLocationService;
    }

    @GetMapping("/safe-locations")
    public ResponseEntity<List<SafeLocation>> getNearbySafeLocations(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        List<SafeLocation> locations = safeLocationService.findNearbySafeLocations(latitude, longitude);
        return ResponseEntity.ok(locations);
    }
}
