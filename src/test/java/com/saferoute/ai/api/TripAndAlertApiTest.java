package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.IntegrationTestBase;
import com.saferoute.ai.model.Trip;
import com.saferoute.ai.repository.TripRepository;
import com.saferoute.ai.service.trip.TripService;

class TripAndAlertApiTest extends IntegrationTestBase {

    // A straight ~1.3 km route heading east.
    private static final List<List<Double>> ROUTE = List.of(
            List.of(12.9300, 77.6200), List.of(12.9300, 77.6260), List.of(12.9300, 77.6320));

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;

    private MockHttpSession session;
    private String contactTopic;

    @BeforeEach
    void signInWithAContact() throws Exception {
        session = signUp("Priya Sharma");
        contactTopic = body(perform(post("/api/emergency-contacts"), session,
                Map.of("name", "Mom", "phone", "9876543210", "relation", "Mother"))).path("pushTopic").asText();
        pushes.clear();
    }

    private JsonNode startTrip(boolean notify) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("originName", "Koramangala");
        body.put("destinationName", "HSR Layout");
        body.put("destinationLatitude", 12.9300);
        body.put("destinationLongitude", 77.6320);
        body.put("geometry", ROUTE);
        body.put("distanceKm", 1.3);
        body.put("durationMinutes", 16);
        body.put("safetyScore", 78.5);
        body.put("notifyContacts", notify);
        return body(perform(post("/api/trips"), session, body).andExpect(status().isCreated()));
    }

    private JsonNode ping(long tripId, double lat, double lon) throws Exception {
        return body(perform(post("/api/trips/" + tripId + "/location"), session,
                Map.of("latitude", lat, "longitude", lon, "accuracy", 10, "battery", 64)).andExpect(status().isOk()));
    }

    private Trip trip(long id) {
        return tripRepository.findById(id).orElseThrow();
    }

    @Test
    void startingATripNotifiesContactsWithAFollowLink() throws Exception {
        JsonNode trip = startTrip(true);
        assertThat(trip.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(trip.path("trackUrl").asText()).contains("/track/");
        assertThat(pushes).containsExactly(contactTopic + "|🧭 Priya started a Safe Trip");
    }

    @Test
    void pingsTrackProgressAndArrivalNotifiesContacts() throws Exception {
        long id = startTrip(true).path("id").asLong();
        JsonNode halfway = ping(id, 12.9300, 77.6260);
        assertThat(halfway.path("progress").asDouble()).isBetween(0.45, 0.55);
        assertThat(halfway.path("remainingKm").asDouble()).isBetween(0.55, 0.75);
        assertThat(halfway.path("offRoute").asBoolean()).isFalse();

        JsonNode arrived = ping(id, 12.93002, 77.63195);
        assertThat(arrived.path("status").asText()).isEqualTo("ARRIVED");
        assertThat(pushes).last().asString().contains("arrived safely");
    }

    @Test
    void leavingTheRouteLeadsToACheckInThenAnAlertIfUnanswered() throws Exception {
        long id = startTrip(false).path("id").asLong();
        JsonNode off = ping(id, 12.9340, 77.6260); // ~440 m north of the route
        assertThat(off.path("offRoute").asBoolean()).isTrue();
        assertThat(off.path("deviationMeters").asDouble()).isGreaterThan(400);

        tripService.runChecks(Instant.now().plusSeconds(50));
        JsonNode needsCheckIn = body(perform(get("/api/trips/" + id), session, null));
        assertThat(needsCheckIn.path("status").asText()).isEqualTo("CHECK_IN_REQUIRED");
        assertThat(needsCheckIn.path("checkIn").path("required").asBoolean()).isTrue();
        assertThat(needsCheckIn.path("checkIn").path("reason").asText()).contains("left your planned route");

        tripService.runChecks(trip(id).getCheckInDeadline().plusSeconds(1));
        assertThat(trip(id).getStatus().name()).isEqualTo("ALERTED");
        assertThat(pushes).anyMatch(p -> p.startsWith(contactTopic) && p.contains("missed a safety check-in"));
    }

    @Test
    void answeringTheCheckInClearsIt() throws Exception {
        long id = startTrip(false).path("id").asLong();
        ping(id, 12.9340, 77.6260);
        tripService.runChecks(Instant.now().plusSeconds(50));
        perform(post("/api/trips/" + id + "/checkin"), session, Map.of("ok", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.checkIn.required").value(false));
        assertThat(pushes).isEmpty();
    }

    @Test
    void askingForHelpInACheckInRaisesAnSos() throws Exception {
        long id = startTrip(false).path("id").asLong();
        perform(post("/api/trips/" + id + "/checkin"), session, Map.of("ok", false))
                .andExpect(jsonPath("$.status").value("ALERTED"))
                .andExpect(jsonPath("$.alertId").isNumber());
        assertThat(pushes).anyMatch(p -> p.contains("SOS from Priya"));
    }

    @Test
    void aSilentPhoneAlertsContacts() throws Exception {
        long id = startTrip(false).path("id").asLong();
        tripService.runChecks(Instant.now().plusSeconds(400));
        assertThat(trip(id).getStatus().name()).isEqualTo("ALERTED");
        assertThat(pushes).anyMatch(p -> p.contains("Lost contact with Priya"));
    }

    @Test
    void contactsCanFollowTheTripWithoutSigningIn() throws Exception {
        JsonNode trip = startTrip(false);
        ping(trip.path("id").asLong(), 12.9300, 77.6230);
        String token = trip.path("trackUrl").asText().replaceAll(".*/track/", "");

        perform(get("/api/track/" + token), new MockHttpSession(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TRIP"))
                .andExpect(jsonPath("$.name").value("Priya"))
                .andExpect(jsonPath("$.destinationName").value("HSR Layout"))
                .andExpect(jsonPath("$.trail.length()").value(1))
                .andExpect(jsonPath("$.battery").value(64));
        perform(get("/api/track/not-a-real-token"), new MockHttpSession(), null).andExpect(status().isNotFound());
    }

    @Test
    void trackingLinksExpireAfterTheTripEnds() throws Exception {
        JsonNode trip = startTrip(false);
        long id = trip.path("id").asLong();
        perform(post("/api/trips/" + id + "/end"), session, null).andExpect(jsonPath("$.status").value("ENDED"));
        Trip ended = trip(id);
        ended.setEndedAt(Instant.now().minusSeconds(3 * 3600));
        tripRepository.save(ended);
        String token = trip.path("trackUrl").asText().replaceAll(".*/track/", "");
        perform(get("/api/track/" + token), new MockHttpSession(), null).andExpect(status().isGone());
    }

    @Test
    void sosReachesEveryContactAndASecondPressReusesTheSameAlert() throws Exception {
        JsonNode sos = body(perform(post("/api/alerts/sos"), session,
                Map.of("latitude", 12.9716, "longitude", 77.5946, "accuracy", 12, "battery", 40))
                .andExpect(status().isCreated()));
        assertThat(sos.path("type").asText()).isEqualTo("SOS");
        assertThat(sos.path("status").asText()).isEqualTo("ACTIVE");
        JsonNode delivered = body(perform(get("/api/alerts/" + sos.path("id").asLong()), session, null));
        assertThat(delivered.path("deliveries").toString()).contains("\"channel\":\"PUSH\",\"status\":\"SENT\"");
        assertThat(delivered.path("deliveryInProgress").asBoolean()).isFalse();
        assertThat(pushes).containsExactly(contactTopic + "|🚨 SOS from Priya");

        JsonNode again = body(perform(post("/api/alerts/sos"), session, Map.of("latitude", 12.9717, "longitude", 77.5947)));
        assertThat(again.path("id").asLong()).isEqualTo(sos.path("id").asLong());
        assertThat(pushes).hasSize(1);

        String token = sos.path("trackUrl").asText().replaceAll(".*/track/", "");
        perform(get("/api/track/" + token), new MockHttpSession(), null)
                .andExpect(jsonPath("$.kind").value("SOS"))
                .andExpect(jsonPath("$.status").value("SOS"))
                .andExpect(jsonPath("$.latitude").value(12.9717));
    }

    @Test
    void sosWorksEvenWithoutLocation() throws Exception {
        perform(post("/api/alerts/sos"), session, null).andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("SOS"));
    }

    @Test
    void resolvingAnSosSendsAnAllClear() throws Exception {
        long id = body(perform(post("/api/alerts/sos"), session, Map.of("latitude", 12.97, "longitude", 77.59))).path("id").asLong();
        perform(post("/api/alerts/" + id + "/resolve"), session, null)
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(pushes).last().asString().contains("Priya is safe");

        perform(get("/api/alerts"), session, null)
                .andExpect(jsonPath("$[0].type").value("ALL_CLEAR"))
                .andExpect(jsonPath("$[1].type").value("SOS"));
    }

    @Test
    void otherUsersCannotTouchMyTripsOrAlerts() throws Exception {
        long tripId = startTrip(false).path("id").asLong();
        MockHttpSession stranger = signUp("Stranger");
        perform(get("/api/trips/" + tripId), stranger, null).andExpect(status().isNotFound());
        perform(post("/api/trips/" + tripId + "/end"), stranger, null).andExpect(status().isNotFound());
    }
}
