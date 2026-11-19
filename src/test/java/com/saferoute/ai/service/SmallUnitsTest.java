package com.saferoute.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.model.IncidentReport;
import com.saferoute.ai.model.ReportCategory;
import com.saferoute.ai.model.RiskLevel;
import com.saferoute.ai.service.safety.SafetyData;
import com.saferoute.ai.service.safety.SafetyData.Poi;
import com.saferoute.ai.service.safety.SegmentFeatureExtractor;
import com.saferoute.ai.service.safety.SegmentFeatureExtractor.PointFeatures;

/** Fast tests for helpers that don't need Spring. */
class SmallUnitsTest {

    @Test
    void travelTimeFormats() {
        assertThat(TravelTimeParser.hourOf("21:30")).isEqualTo(21);
        assertThat(TravelTimeParser.hourOf("7:05")).isEqualTo(7);
        assertThat(TravelTimeParser.hourOf("9:30 PM")).isEqualTo(21);
        assertThat(TravelTimeParser.hourOf("12:15 am")).isEqualTo(0);
        assertThat(TravelTimeParser.hourOf("2026-11-18T22:10")).isEqualTo(22);
        assertThat(TravelTimeParser.hourOf("NIGHT")).isEqualTo(21);
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T15:45:00Z"), ZoneOffset.UTC);
        assertThat(TravelTimeParser.hourOf("", clock)).isEqualTo(15);
        assertThatThrownBy(() -> TravelTimeParser.hourOf("25:00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void riskLevelLabelsAreUnified() {
        assertThat(RiskLevel.fromLabel("Moderate Risk")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromSafetyScore(82)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromSafetyScore(50)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromSafetyScore(20)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void typedCoordinatesAreAccepted() {
        assertThat(GeocodingService.parseCoordinates("12.9716, 77.5946")).isPresent();
        assertThat(GeocodingService.parseCoordinates("Cubbon Park")).isEmpty();
    }

    @Test
    void mergeDropsNearDuplicates() {
        List<Place> local = List.of(new Place("Whitefield", "Whitefield, Bengaluru", 12.9698, 77.75, "index"));
        List<Place> online = List.of(new Place("whitefield", "Whitefield", 12.9700, 77.7502, "photon"),
                new Place("Whitefield Road", "Whitefield Road", 12.98, 77.73, "photon"));
        assertThat(GeocodingService.merge(local, online)).extracting(Place::name).containsExactly("Whitefield", "Whitefield Road");
    }

    @Test
    void detourPointsLieEitherSideOfTheDirectLine() {
        GeoPoint from = new GeoPoint(12.93, 77.62);
        GeoPoint to = new GeoPoint(12.93, 77.66);
        List<GeoPoint> points = RoutingService.detourPoints(from, to);
        assertThat(points).hasSize(2);
        assertThat(points.get(0).lat()).isGreaterThan(12.93);
        assertThat(points.get(1).lat()).isLessThan(12.93);
        assertThat(GeoUtils.distanceMeters(points.get(0), new GeoPoint(12.93, 77.64))).isBetween(800.0, 1200.0);
    }

    @Test
    void streetFeaturesReflectLampsShopsPoliceAndReports() {
        GeoPoint here = new GeoPoint(12.93, 77.62);
        SafetyData lit = new SafetyData(
                List.of(new Poi(1, "POLICE", "Station", 12.9305, 77.6205, true)),
                List.of(new GeoPoint(12.93001, 77.62001), new GeoPoint(12.92999, 77.62002)),
                List.of(),
                List.of(new Poi(2, "SHOP", "A", 12.9301, 77.6201, false), new Poi(3, "FOOD", "B", 12.9302, 77.6199, true),
                        new Poi(4, "SHOP", "C", 12.9299, 77.6202, false), new Poi(5, "SHOP", "D", 12.9298, 77.6201, false)),
                List.of(new GeoPoint(12.9301, 77.6203)),
                true);
        SafetyData empty = new SafetyData(List.of(), List.of(), List.of(), List.of(), List.of(), true);
        LocalDateTime now = LocalDateTime.now();

        PointFeatures bright = new SegmentFeatureExtractor(22, lit, List.of(), List.of(), now).extract(here);
        PointFeatures dark = new SegmentFeatureExtractor(22, empty, List.of(), List.of(), now).extract(here);
        assertThat(bright.features().lightingCondition()).isGreaterThan(dark.features().lightingCondition());
        assertThat(bright.features().crowdDensity()).isGreaterThan(dark.features().crowdDensity());
        assertThat(bright.features().distanceToPoliceStation()).isLessThan(0.2);
        assertThat(dark.dark()).isTrue();
        assertThat(dark.farFromPolice()).isTrue();

        IncidentReport report = new IncidentReport();
        report.setLatitude(12.9301);
        report.setLongitude(77.6201);
        report.setCategory(ReportCategory.HARASSMENT);
        report.setCreatedAt(now.minusDays(1));
        PointFeatures reported = new SegmentFeatureExtractor(22, empty, List.of(), List.of(report), now).extract(here);
        assertThat(reported.features().reportedIncidentCount()).isGreaterThan(dark.features().reportedIncidentCount());

        PointFeatures daytime = new SegmentFeatureExtractor(11, empty, List.of(), List.of(), now).extract(here);
        assertThat(daytime.features().lightingCondition()).isEqualTo(5);
    }

    @Test
    void placeIndexPrefixSearchRanksTheRightPlaceFirst() throws Exception {
        PlaceIndex index = new PlaceIndex(new org.springframework.core.io.DefaultResourceLoader(), new ObjectMapper(),
                defaultProperties());
        assertThat(index.size()).isGreaterThan(3000);
        assertThat(index.search("whi", null, 5).get(0).name()).isEqualTo("Whitefield");
        assertThat(index.search("Koramangala", null, 3).get(0).type()).isEqualTo("Suburb");
        assertThat(index.search("indira", new GeoPoint(12.97, 77.64), 3).get(0).name()).isEqualTo("Indiranagar");
        assertThat(index.search("x", null, 5)).isEmpty();
        assertThat(index.nearest(new GeoPoint(12.9698, 77.7500), 3).name()).isNotBlank();
    }

    private static com.saferoute.ai.config.SafeRouteProperties defaultProperties() {
        return new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(java.util.Map.of()))
                .bindOrCreate("saferoute", com.saferoute.ai.config.SafeRouteProperties.class);
    }
}
