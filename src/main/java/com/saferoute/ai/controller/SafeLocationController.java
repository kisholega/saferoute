package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.SafeLocation;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.service.SafeLocationService;

@RestController
@RequestMapping("/api")
public class SafeLocationController {

    private final SafeLocationService safeLocationService;

    public SafeLocationController(SafeLocationService safeLocationService) {
        this.safeLocationService = safeLocationService;
    }

    /** Real police stations, hospitals, clinics, pharmacies and fuel stations near a point, nearest first. */
    @GetMapping("/safe-locations")
    public List<SafeLocation> nearby(@RequestParam double latitude, @RequestParam double longitude,
                                     @RequestParam(defaultValue = "2000") int radius) {
        GeoPoint point = new GeoPoint(latitude, longitude); // rejects out-of-range coordinates with a 400
        return safeLocationService.findNearby(point.lat(), point.lon(), radius);
    }
}
