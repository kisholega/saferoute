package com.saferoute.ai.controller;

import com.saferoute.ai.dto.EmergencyContactRequest;
import com.saferoute.ai.model.EmergencyContact;
import com.saferoute.ai.service.EmergencyContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    public EmergencyContactController(EmergencyContactService emergencyContactService) {
        this.emergencyContactService = emergencyContactService;
    }

    @GetMapping("/emergency-contacts")
    public ResponseEntity<List<EmergencyContact>> getContacts(@RequestParam Long userId) {
        return ResponseEntity.ok(emergencyContactService.getContactsByUser(userId));
    }

    @PostMapping("/emergency-contacts")
    public ResponseEntity<EmergencyContact> createContact(@Valid @RequestBody EmergencyContactRequest request) {
        return ResponseEntity.ok(emergencyContactService.saveContact(request));
    }

    @PutMapping("/emergency-contacts/{id}")
    public ResponseEntity<EmergencyContact> updateContact(@PathVariable Long id, @Valid @RequestBody EmergencyContactRequest request) {
        return ResponseEntity.ok(emergencyContactService.updateContact(id, request));
    }

    @DeleteMapping("/emergency-contacts/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        emergencyContactService.deleteContact(id);
        return ResponseEntity.noContent().build();
    }
}
