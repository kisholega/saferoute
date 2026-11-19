package com.saferoute.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.service.safety.CitySnapshot;
import com.saferoute.ai.service.safety.OsmSafetyDataService;
import com.saferoute.ai.service.safety.SafetyData;

/** The bundled Bengaluru OpenStreetMap snapshot loads and answers without any network. */
class CitySnapshotTest {

    private final SafeRouteProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
            .bindOrCreate("saferoute", SafeRouteProperties.class);
    private final CitySnapshot snapshot = new CitySnapshot(new DefaultResourceLoader(), new ObjectMapper(), properties);
    // The Overpass client is never called for points inside the snapshot, so null is safe here.
    private final OsmSafetyDataService service = new OsmSafetyDataService(null, snapshot, properties);

    @Test
    void loadsTheCityAndKnowsItsBounds() {
        assertThat(snapshot.isLoaded()).isTrue();
        assertThat(snapshot.covers(new GeoPoint(12.9763, 77.5929))).isTrue();   // Cubbon Park
        assertThat(snapshot.covers(new GeoPoint(28.61, 77.21))).isFalse();      // Delhi
    }

    @Test
    void findsRealPoliceStationsNearCubbonPark() {
        SafetyData help = service.helpAround(new GeoPoint(12.9763, 77.5929), 1500);
        assertThat(help.live()).isTrue();
        assertThat(help.help()).anyMatch(p -> p.kind().equals("POLICE") && p.name().contains("Police"));
    }

    @Test
    void returnsLampsShopsAndTransitAlongACentralRoute() {
        List<GeoPoint> mgRoad = List.of(new GeoPoint(12.9755, 77.6068), new GeoPoint(12.9730, 77.6190));
        SafetyData data = service.alongRoutes(List.of(mgRoad));
        assertThat(data.live()).isTrue();
        assertThat(data.activity()).hasSizeGreaterThan(50);
        assertThat(data.transit()).isNotEmpty();
        assertThat(data.help()).isNotEmpty();
    }
}
