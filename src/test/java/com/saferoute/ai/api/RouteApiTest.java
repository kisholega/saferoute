package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.IntegrationTestBase;
import com.saferoute.ai.client.RouteCandidate;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;

class RouteApiTest extends IntegrationTestBase {

    // Seeded "Demo zone: unlit underpass" (HIGH, 350 m) is centred on 12.9770, 77.6060.
    private static final GeoPoint START = new GeoPoint(12.9700, 77.6000);
    private static final GeoPoint END = new GeoPoint(12.9840, 77.6120);
    private static final List<GeoPoint> THROUGH_ZONE = List.of(START, new GeoPoint(12.9770, 77.6060), END);
    private static final List<GeoPoint> AROUND_ZONE = List.of(START, new GeoPoint(12.9700, 77.6120), END);

    // An area with no demo zones, for the live street data tests.
    private static final GeoPoint K_START = new GeoPoint(12.9300, 77.6200);
    private static final GeoPoint K_END = new GeoPoint(12.9420, 77.6320);
    private static final List<GeoPoint> BRIGHT = List.of(K_START, new GeoPoint(12.9300, 77.6320), K_END);
    private static final List<GeoPoint> DARK = List.of(K_START, new GeoPoint(12.9420, 77.6200), K_END);

    private MockHttpSession session;

    @BeforeEach
    void signIn() throws Exception {
        session = signUp();
    }

    private Map<String, Object> request(GeoPoint from, GeoPoint to, String time) {
        Map<String, Object> body = new HashMap<>();
        body.put("source", "Start");
        body.put("sourceLatitude", from.lat());
        body.put("sourceLongitude", from.lon());
        body.put("destination", "End");
        body.put("destinationLatitude", to.lat());
        body.put("destinationLongitude", to.lon());
        body.put("travelTime", time);
        return body;
    }

    private JsonNode recommend(Map<String, Object> body) throws Exception {
        return body(perform(post("/api/routes/recommend"), session, body).andExpect(status().isOk()));
    }

    @Test
    void liveStreetDataMakesTheLitBusyRouteWinAtNight() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(
                new RouteCandidate(2700, 1900, DARK),
                new RouteCandidate(2750, 2000, BRIGHT)));
        doReturn(new OsmBuilder()
                .along(BRIGHT, 30, "highway", "street_lamp")
                .along(BRIGHT, 25, "shop", "convenience", "name", "Corner Store")
                .along(BRIGHT, 3, "highway", "bus_stop")
                .node(new GeoPoint(12.9302, 77.6262), "amenity", "police", "name", "Koramangala Police Station")
                .build()).when(overpassClient).query(anyString());

        JsonNode result = recommend(request(K_START, K_END, "22:00"));
        JsonNode dark = result.path("routes").get(0);
        JsonNode bright = result.path("routes").get(1);

        assertThat(result.path("dataQuality").asText()).isEqualTo("live");
        assertThat(result.path("evaluatedHour").asInt()).isEqualTo(22);
        assertThat(bright.path("safetyScore").asDouble()).isGreaterThan(dark.path("safetyScore").asDouble() + 10);
        assertThat(result.path("recommendedRouteId").asText()).isEqualTo(bright.path("routeId").asText());
        assertThat(dark.path("tags").toString()).contains("Fastest");

        // explanations
        assertThat(bright.path("highlights").toString()).contains("Well lit").contains("1 police station within 500 m");
        assertThat(dark.path("highlights").toString()).contains("Poorly lit").contains("No police station");
        assertThat(bright.path("helpPoints").get(0).path("name").asText()).isEqualTo("Koramangala Police Station");
        assertThat(dark.path("weakestStretch").path("description").asText()).containsIgnoringCase("poorly lit");

        // coloured stretches cover the whole geometry
        JsonNode segments = bright.path("segments");
        assertThat(segments.size()).isGreaterThan(0);
        assertThat(segments.get(0).path("start").asInt()).isZero();
        assertThat(segments.get(segments.size() - 1).path("end").asInt()).isEqualTo(bright.path("geometry").size() - 1);
    }

    @Test
    void daytimeIsSaferThanLateNightOnTheSameRoute() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(new RouteCandidate(2700, 1900, DARK)));
        double day = recommend(request(K_START, K_END, "11:00")).path("routes").get(0).path("safetyScore").asDouble();
        double night = recommend(request(K_START, K_END, "23:30")).path("routes").get(0).path("safetyScore").asDouble();
        assertThat(night).isLessThan(day);
    }

    @Test
    void withoutStreetDataFlaggedZonesStillMakeTheDifference() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(
                new RouteCandidate(2400, 420, THROUGH_ZONE),
                new RouteCandidate(2600, 480, AROUND_ZONE)));

        JsonNode result = recommend(request(START, END, "22:00"));
        JsonNode through = result.path("routes").get(0);
        JsonNode around = result.path("routes").get(1);

        assertThat(result.path("dataQuality").asText()).isEqualTo("estimated");
        assertThat(through.path("zonesOnRoute").get(0).path("name").asText()).isEqualTo("Demo zone: unlit underpass");
        assertThat(around.path("zonesOnRoute")).isEmpty();
        assertThat(around.path("safetyScore").asDouble()).isGreaterThan(through.path("safetyScore").asDouble());
        assertThat(result.path("recommendedRouteId").asText()).isEqualTo(around.path("routeId").asText());
        assertThat(through.path("highlights").toString()).contains("estimated").contains("flagged");
    }

    @Test
    void safeDetoursAreAddedWhenTheRouterReturnsOnlyOneRoute() throws Exception {
        when(osrmClient.route(anyString(), argThat(points -> points != null && points.size() == 2), eq(true)))
                .thenReturn(List.of(new RouteCandidate(2700, 1900, DARK)));
        when(osrmClient.route(anyString(), argThat(points -> points != null && points.size() == 3), eq(false)))
                .thenReturn(List.of(new RouteCandidate(2900, 2100, BRIGHT)));

        JsonNode result = recommend(request(K_START, K_END, "21:00"));
        assertThat(result.path("routes").size()).isEqualTo(2); // the second detour duplicates the first and is dropped
    }

    @Test
    void cabModeFallsBackToTheSecondRoutingServer() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean()))
                .thenThrow(new ExternalServiceException("down", null))
                .thenReturn(List.of(new RouteCandidate(2700, 400, DARK)));
        Map<String, Object> body = request(K_START, K_END, "21:00");
        body.put("mode", "DRIVE");
        JsonNode result = recommend(body);
        assertThat(result.path("mode").asText()).isEqualTo("DRIVE");
        assertThat(result.path("demoMode").asBoolean()).isFalse();
    }

    @Test
    void fallsBackToALabelledStraightLineEstimateWhenRoutingIsDown() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenThrow(new ExternalServiceException("down", null));
        perform(post("/api/routes/recommend"), session, request(START, END, "21:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.routes.length()").value(1))
                .andExpect(jsonPath("$.routes[0].label").value("Straight-line estimate"));
    }

    @Test
    void unknownPlaceIsAClearErrorNotASilentDefaultLocation() throws Exception {
        when(nominatimClient.search("Nowhereville Xyz")).thenReturn(Optional.empty());
        perform(post("/api/routes/recommend"), session, Map.of("source", "Nowhereville Xyz", "destination", "Indiranagar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Nowhereville Xyz")));
        verify(osrmClient, never()).route(anyString(), anyList(), anyBoolean());
    }

    @Test
    void exactNamesOfKnownPlacesResolveWithoutTheNetwork() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(new RouteCandidate(5000, 3600,
                List.of(new GeoPoint(12.9698, 77.7500), new GeoPoint(12.9719, 77.6412)))));
        perform(post("/api/routes/recommend"), session,
                Map.of("source", "Whitefield", "destination", "Indiranagar", "travelTime", "9:30 PM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.source").value("index"))
                .andExpect(jsonPath("$.destination.name").value("Indiranagar"))
                .andExpect(jsonPath("$.evaluatedHour").value(21));
        verify(nominatimClient, never()).search(anyString());
    }

    @Test
    void typedNamesFallBackToNominatim() throws Exception {
        when(nominatimClient.search("Some Office Park")).thenReturn(Optional.of(
                new Place("Some Office Park", "Some Office Park, Bengaluru", 12.9763, 77.5929, "nominatim")));
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(new RouteCandidate(1800, 360,
                List.of(new GeoPoint(12.9763, 77.5929), new GeoPoint(12.9719, 77.6412)))));
        perform(post("/api/routes/recommend"), session,
                Map.of("source", "Some Office Park", "destination", "Indiranagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.displayName").value("Some Office Park, Bengaluru"));
    }

    @Test
    void rejectsSameStartAndDestinationAndBadInput() throws Exception {
        perform(post("/api/routes/recommend"), session, request(START, START, "21:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("same place")));
        perform(post("/api/routes/recommend"), session, Map.of("source", "A", "destination", "B", "travelTime", "25:99"))
                .andExpect(status().isBadRequest());
        perform(post("/api/routes/recommend"), session, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.source").exists());
    }

    @Test
    void searchesAreSavedToTheUsersHistory() throws Exception {
        when(osrmClient.route(anyString(), anyList(), anyBoolean())).thenReturn(List.of(new RouteCandidate(2600, 480, AROUND_ZONE)));
        recommend(request(START, END, "20:00"));
        perform(get("/api/routes/history"), session, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceLatitude").value(START.lat()));
    }
}
