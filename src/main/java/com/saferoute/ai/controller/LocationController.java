package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.Place;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.service.GeocodingService;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final GeocodingService geocodingService;

    public LocationController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    /**
     * Search-as-you-type suggestions, optionally biased towards (lat, lon).
     * quick=true answers from the built-in place index only (instant).
     */
    @GetMapping("/search")
    public List<Place> search(@RequestParam String query,
                              @RequestParam(required = false) Double lat,
                              @RequestParam(required = false) Double lon,
                              @RequestParam(defaultValue = "false") boolean quick) {
        return geocodingService.suggest(query, lat, lon, quick);
    }

    /** A readable name for a point picked on the map or from GPS. */
    @GetMapping("/reverse")
    public Place reverse(@RequestParam double lat, @RequestParam double lon) {
        GeoPoint point = new GeoPoint(lat, lon);
        return geocodingService.reverse(point.lat(), point.lon());
    }
}
