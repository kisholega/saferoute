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

import com.saferoute.ai.dto.AlertDtos.AlertView;
import com.saferoute.ai.dto.AlertDtos.LocationReport;
import com.saferoute.ai.service.alert.AlertService;

import jakarta.validation.Valid;

/** SOS and alert history. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** Raises an SOS to every emergency contact. The body (location) is optional. */
    @PostMapping("/sos")
    public ResponseEntity<AlertView> sos(@Valid @RequestBody(required = false) LocationReport location) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertService.sos(location == null ? new LocationReport(null, null, null, null) : location));
    }

    @PostMapping("/{id}/location")
    public AlertView location(@PathVariable Long id, @Valid @RequestBody LocationReport location) {
        return alertService.updateLocation(id, location);
    }

    @PostMapping("/{id}/resolve")
    public AlertView resolve(@PathVariable Long id) {
        return alertService.resolve(id);
    }

    @GetMapping("/{id}")
    public AlertView get(@PathVariable Long id) {
        return alertService.get(id);
    }

    @GetMapping
    public List<AlertView> history() {
        return alertService.history();
    }
}
