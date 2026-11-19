package com.saferoute.ai.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeoTest {

    private static final List<GeoPoint> EAST = List.of(new GeoPoint(12.93, 77.62), new GeoPoint(12.93, 77.626),
            new GeoPoint(12.93, 77.632));

    @Test
    void haversineMatchesKnownDistance() {
        double meters = GeoUtils.distanceMeters(new GeoPoint(12.9755, 77.6068), new GeoPoint(12.9763, 77.5929));
        assertThat(meters).isBetween(1400.0, 1600.0);
    }

    @Test
    void distanceToSegmentUsesTheClosestPoint() {
        GeoPoint a = new GeoPoint(12.97, 77.60);
        GeoPoint b = new GeoPoint(12.97, 77.62);
        assertThat(GeoUtils.distanceToSegmentMeters(new GeoPoint(12.9709, 77.61), a, b)).isCloseTo(100.0, within(2.0));
    }

    @Test
    void polylineProjectsPositionsOntoTheRoute() {
        Polyline line = new Polyline(EAST);
        assertThat(line.lengthMeters()).isCloseTo(1300, within(15.0));
        Polyline.Projection halfwayAndOff = line.project(new GeoPoint(12.9309, 77.626));
        assertThat(halfwayAndOff.offRouteMeters()).isCloseTo(100, within(3.0));
        assertThat(halfwayAndOff.alongMeters()).isCloseTo(650, within(10.0));
    }

    @Test
    void resamplingIsEvenAndIncludesBothEnds() {
        List<Polyline.Sample> samples = new Polyline(EAST).resample(120);
        assertThat(samples.get(0).alongMeters()).isZero();
        assertThat(samples.get(samples.size() - 1).alongMeters()).isCloseTo(1300, within(15.0));
        assertThat(samples.get(1).alongMeters()).isCloseTo(120, within(0.1));
        assertThat(samples.get(samples.size() - 1).vertexIndex()).isEqualTo(2);
    }

    @Test
    void overlapTellsSimilarRoutesFromDifferentOnes() {
        Polyline a = new Polyline(EAST);
        Polyline nearlySame = new Polyline(List.of(new GeoPoint(12.9301, 77.62), new GeoPoint(12.9301, 77.632)));
        Polyline other = new Polyline(List.of(new GeoPoint(12.93, 77.62), new GeoPoint(12.942, 77.62), new GeoPoint(12.942, 77.632)));
        assertThat(a.overlapWith(nearlySame, 35)).isGreaterThan(0.95);
        assertThat(a.overlapWith(other, 35)).isLessThan(0.2);
    }

    @Test
    void pointIndexFindsNearbyItems() {
        PointIndex<GeoPoint> index = PointIndex.ofPoints(List.of(new GeoPoint(12.93, 77.62), new GeoPoint(12.931, 77.62),
                new GeoPoint(12.95, 77.65)));
        assertThat(index.countWithin(new GeoPoint(12.93, 77.62), 150)).isEqualTo(2);
        assertThat(index.nearestDistance(new GeoPoint(12.93, 77.62), 5000)).isZero();
        assertThat(index.nearestDistance(new GeoPoint(13.5, 78.0), 1000)).isInfinite();
    }

    @Test
    void rejectsImpossibleCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -181)).isInstanceOf(IllegalArgumentException.class);
    }
}
