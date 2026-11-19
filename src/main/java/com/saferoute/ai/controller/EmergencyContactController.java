package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.EmergencyContactRequest;
import com.saferoute.ai.model.EmergencyContact;
import com.saferoute.ai.dto.AlertDtos.AlertView;
import com.saferoute.ai.service.EmergencyContactService;
import com.saferoute.ai.service.alert.AlertService;

import jakarta.validation.Valid;

/** CRUD for the current user's emergency contacts. */
@RestController
@RequestMapping("/api/emergency-contacts")
public class EmergencyContactController {

    private final EmergencyContactService service;
    private final AlertService alertService;

    public EmergencyContactController(EmergencyContactService service, AlertService alertService) {
        this.service = service;
        this.alertService = alertService;
    }

    @GetMapping
    public List<EmergencyContact> list() {
        return service.listContacts();
    }

    @PostMapping
    public ResponseEntity<EmergencyContact> create(@Valid @RequestBody EmergencyContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public EmergencyContact update(@PathVariable Long id, @Valid @RequestBody EmergencyContactRequest request) {
        return service.update(id, request);
    }

    /** Sends a test alert to one contact over every available channel. */
    @PostMapping("/{id}/test")
    public AlertView test(@PathVariable Long id) {
        return alertService.sendTest(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
