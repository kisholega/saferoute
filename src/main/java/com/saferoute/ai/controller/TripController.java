package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.TripDtos.CheckInRequest;
import com.saferoute.ai.dto.TripDtos.PingRequest;
import com.saferoute.ai.dto.TripDtos.StartTripRequest;
import com.saferoute.ai.dto.TripDtos.TripView;
import com.saferoute.ai.service.trip.TripService;

import jakarta.validation.Valid;

/** Safe Trip: start, live position, check-ins, end. */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripView> start(@Valid @RequestBody StartTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.start(request));
    }

    @GetMapping
    public List<TripView> recent() {
        return tripService.recent();
    }

    /** The trip in progress, or 204 when there is none. */
    @GetMapping("/active")
    public ResponseEntity<TripView> active() {
        TripView trip = tripService.active();
        return trip == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(trip);
    }

    @GetMapping("/{id}")
    public TripView get(@PathVariable Long id) {
        return tripService.get(id);
    }

    @PostMapping("/{id}/location")
    public TripView ping(@PathVariable Long id, @Valid @RequestBody PingRequest request) {
        return tripService.ping(id, request);
    }

    @PostMapping("/{id}/checkin")
    public TripView checkIn(@PathVariable Long id, @Valid @RequestBody CheckInRequest request) {
        return tripService.checkIn(id, request);
    }

    @PostMapping("/{id}/end")
    public TripView end(@PathVariable Long id) {
        return tripService.end(id);
    }
}
