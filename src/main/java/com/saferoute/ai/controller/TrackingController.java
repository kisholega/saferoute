package com.saferoute.ai.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.saferoute.ai.dto.TripDtos.TrackingSnapshot;
import com.saferoute.ai.service.trip.TrackingHub;
import com.saferoute.ai.service.trip.TrackingService;

/** Public live tracking for emergency contacts. The unguessable token is the only key. */
@RestController
@RequestMapping("/api/track")
public class TrackingController {

    private final TrackingService trackingService;
    private final TrackingHub trackingHub;

    public TrackingController(TrackingService trackingService, TrackingHub trackingHub) {
        this.trackingService = trackingService;
        this.trackingHub = trackingHub;
    }

    @GetMapping("/{token}")
    public TrackingSnapshot snapshot(@PathVariable String token) {
        return trackingService.snapshot(token);
    }

    /** Server-Sent Events: "position" and "status" events as they happen. */
    @GetMapping(value = "/{token}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String token) {
        trackingService.snapshot(token); // 404 / 410 for invalid or expired links
        return trackingHub.subscribe(token);
    }
}
