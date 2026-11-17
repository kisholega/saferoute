package com.saferoute.ai.service;

import com.saferoute.ai.model.SafetyZone;
import com.saferoute.ai.repository.SafetyZoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SafetyZoneService {

    private final SafetyZoneRepository safetyZoneRepository;

    public SafetyZoneService(SafetyZoneRepository safetyZoneRepository) {
        this.safetyZoneRepository = safetyZoneRepository;
    }

    public List<SafetyZone> getActiveZones() {
        return safetyZoneRepository.findByActiveTrue();
    }
}
