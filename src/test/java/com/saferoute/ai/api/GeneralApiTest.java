package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.IntegrationTestBase;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;

class GeneralApiTest extends IntegrationTestBase {

    @Test
    void suggestionsMatchPrefixesInstantlyFromTheBuiltInPlaceIndex() throws Exception {
        JsonNode quick = body(mockMvc.perform(get("/api/locations/search").param("query", "whitef").param("quick", "true"))
                .andExpect(status().isOk()));
        assertThat(quick.get(0).path("name").asText()).isEqualTo("Whitefield");
        assertThat(quick.get(0).path("source").asText()).isEqualTo("index");
        assertThat(quick.get(0).path("displayName").asText()).contains("Bengaluru");

        JsonNode mg = body(mockMvc.perform(get("/api/locations/search").param("query", "mg road").param("quick", "true")));
        assertThat(mg.toString()).contains("Mahatma Gandhi Road");
    }

    @Test
    void fullSearchAddsOnlineResultsWithoutDuplicates() throws Exception {
        when(photonClient.autocomplete(anyString(), any(), any())).thenReturn(List.of(
                new Place("Whitefield", "Whitefield, Bengaluru", 12.9698, 77.7500, "photon", "Suburb"),
                new Place("Whitefield Road", "Whitefield Road, Bengaluru", 12.9800, 77.7300, "photon", "Primary")));
        JsonNode full = body(mockMvc.perform(get("/api/locations/search").param("query", "whitefield")));
        assertThat(full.toString()).contains("Whitefield Road");
        long whitefieldSuburbs = 0;
        for (JsonNode place : full) {
            if (place.path("name").asText().equals("Whitefield") && place.path("source").asText().equals("photon")) {
                whitefieldSuburbs++;
            }
        }
        assertThat(whitefieldSuburbs).isZero(); // the index already has it
    }

    @Test
    void suggestionsStillWorkWhenOnlineSearchIsDown() throws Exception {
        when(photonClient.autocomplete(anyString(), any(), any())).thenThrow(new ExternalServiceException("down", null));
        mockMvc.perform(get("/api/locations/search").param("query", "koramangala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Koramangala"));
    }

    @Test
    void reverseGeocodingNamesAPointAndFallsBackToTheNearestArea() throws Exception {
        when(photonClient.reverse(anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new Place("Whitefield Main Road", "Whitefield Main Road, Bengaluru", 12.9698, 77.75, "user")))
                .thenThrow(new ExternalServiceException("down", null));
        mockMvc.perform(get("/api/locations/reverse").param("lat", "12.9698").param("lon", "77.75"))
                .andExpect(jsonPath("$.name").value("Whitefield Main Road"));
        mockMvc.perform(get("/api/locations/reverse").param("lat", "12.9698").param("lon", "77.75"))
                .andExpect(jsonPath("$.name").value(Matchers.startsWith("Near ")));
    }

    @Test
    void safePlacesComeFromLiveOpenStreetMapData() throws Exception {
        doReturn(new OsmBuilder()
                .node(new GeoPoint(12.9763, 77.5929), "amenity", "police", "name", "Cubbon Park Police Station")
                .node(new GeoPoint(12.9800, 77.5950), "amenity", "hospital", "name", "City Hospital")
                .node(new GeoPoint(12.9765, 77.5931), "amenity", "pharmacy", "opening_hours", "24/7")
                .build()).when(overpassClient).query(anyString());
        mockMvc.perform(get("/api/safe-locations").param("latitude", "12.9762").param("longitude", "77.5928"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cubbon Park Police Station"))
                .andExpect(jsonPath("$[0].open24h").value(true))
                .andExpect(jsonPath("$[1].name").value("Pharmacy"))
                .andExpect(jsonPath("$[2].type").value("HOSPITAL"));
    }

    @Test
    void safePlacesSayClearlyWhenLiveDataIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/safe-locations").param("latitude", "12.97").param("longitude", "77.59"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("unavailable")));
    }

    @Test
    void communityReportsCanBeAddedAndRead() throws Exception {
        MockHttpSession session = signUp();
        perform(post("/api/reports"), session,
                Map.of("latitude", 12.9352, "longitude", 77.6245, "category", "POOR_LIGHTING", "note", "Street lights out"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryLabel").value("Poor lighting"));
        mockMvc.perform(get("/api/reports").param("latitude", "12.9352").param("longitude", "77.6245"))
                .andExpect(jsonPath("$[0].note").value("Street lights out"))
                .andExpect(jsonPath("$[0].userId").doesNotExist());
        mockMvc.perform(get("/api/reports/categories")).andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void seedDataLoadsRiskZones() throws Exception {
        mockMvc.perform(get("/api/safety-zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].radiusKm", Matchers.everyItem(Matchers.lessThan(1.0))));
    }

    @Test
    void appInfoReportsVersionChannelsAndModel() throws Exception {
        mockMvc.perform(get("/api/app-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion").value(3))
                .andExpect(jsonPath("$.alertChannels.push").value(true))
                .andExpect(jsonPath("$.alertChannels.email").value(false))
                .andExpect(jsonPath("$.alertChannels.sms").value(false))
                .andExpect(jsonPath("$.placesIndexed").value(Matchers.greaterThan(3000)))
                .andExpect(jsonPath("$.model.trainingRows").value(3000));
    }

    @Test
    void riskPredictEndpointValidatesRanges() throws Exception {
        mockMvc.perform(post("/api/risk/predict").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeOfTravel\":30,\"lightingCondition\":3,\"crowdDensity\":3,"
                                + "\"reportedIncidentCount\":1,\"roadIsolationLevel\":2,"
                                + "\"distanceToPoliceStation\":1.0,\"distanceToHospital\":1.0,\"nearbyPublicPlaces\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timeOfTravel").exists());
    }

    @Test
    void errorsAreFriendlyJson() throws Exception {
        MockHttpSession session = signUp();
        perform(post("/api/emergency-contacts"), session, null).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("not valid JSON")));
        mockMvc.perform(get("/does-not-exist.png")).andExpect(status().isNotFound());
    }

    @Test
    void pagesAreServedIncludingTheTrackingPage() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/track/some-token")).andExpect(forwardedUrl("/track.html"));
    }
}
