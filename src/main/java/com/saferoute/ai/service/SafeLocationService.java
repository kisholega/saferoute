package com.saferoute.ai.service;

import com.saferoute.ai.dto.SafeLocation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SafeLocationService {

    public List<SafeLocation> findNearbySafeLocations(double latitude, double longitude) {
        List<SafeLocation> locations = new ArrayList<>();

        SafeLocation police = new SafeLocation();
        police.setName("Sample Police Station");
        police.setType("POLICE");
        police.setLatitude(12.9762);
        police.setLongitude(77.6033);
        police.setDistanceKm(0.9);
        police.setDescription("Demo location only. This is not a verified real facility.");
        locations.add(police);

        SafeLocation hospital = new SafeLocation();
        hospital.setName("Sample Hospital");
        hospital.setType("HOSPITAL");
        hospital.setLatitude(12.9798);
        hospital.setLongitude(77.6084);
        hospital.setDistanceKm(1.2);
        hospital.setDescription("Demo location only. This is not a verified real facility.");
        locations.add(hospital);

        SafeLocation pharmacy = new SafeLocation();
        pharmacy.setName("Sample Pharmacy");
        pharmacy.setType("PHARMACY");
        pharmacy.setLatitude(12.9729);
        pharmacy.setLongitude(77.6008);
        pharmacy.setDistanceKm(0.7);
        pharmacy.setDescription("Demo location only. This is not a verified real facility.");
        locations.add(pharmacy);

        SafeLocation publicSpace = new SafeLocation();
        publicSpace.setName("Sample Public Plaza");
        publicSpace.setType("PUBLIC_PLACE");
        publicSpace.setLatitude(12.9778);
        publicSpace.setLongitude(77.5949);
        publicSpace.setDistanceKm(1.1);
        publicSpace.setDescription("Demo location only. This is not a verified real facility.");
        locations.add(publicSpace);

        return locations;
    }
}
