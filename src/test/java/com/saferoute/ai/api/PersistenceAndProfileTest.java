package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

class PersistenceAndProfileTest extends IntegrationTestBase {

    private Cookie registerAndGetRememberCookie(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Meera Iyer", "email", email, "phone", "9845098450", "password", "long-password-1"))))
                .andExpect(status().isCreated()).andReturn();
        Cookie cookie = result.getResponse().getCookie("SAFEROUTE_REMEMBER");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isGreaterThan(29 * 24 * 3600);
        return cookie;
    }

    @Test
    void rememberCookieKeepsYouSignedInWhenTheSessionIsGoneLikeAfterARestart() throws Exception {
        Cookie remember = registerAndGetRememberCookie("restart@example.test");
        // A brand-new session (no attributes) stands in for a server restart, which loses all sessions.
        mockMvc.perform(get("/api/auth/me").session(new MockHttpSession()).cookie(remember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("restart@example.test"));
        mockMvc.perform(get("/api/emergency-contacts").session(new MockHttpSession()).cookie(remember))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRememberCookie() throws Exception {
        Cookie remember = registerAndGetRememberCookie("logout@example.test");
        mockMvc.perform(post("/api/auth/logout").session(new MockHttpSession()).cookie(remember)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(new MockHttpSession()).cookie(remember)).andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedRememberCookieIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("SAFEROUTE_REMEMBER", "made-up-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bestTimeToLeaveScoresEveryHourAndDaytimeBeatsLateNight() throws Exception {
        MockHttpSession session = signUp();
        List<List<Double>> route = List.of(List.of(12.9300, 77.6200), List.of(12.9300, 77.6320), List.of(12.9420, 77.6320));
        JsonNode profile = body(perform(post("/api/routes/time-profile"), session, Map.of("geometry", route, "startHour", 20, "hours", 24))
                .andExpect(status().isOk()));
        assertThat(profile.size()).isEqualTo(24);
        assertThat(profile.get(0).path("hour").asInt()).isEqualTo(20);
        double lateNight = profile.get(4).path("safetyScore").asDouble();   // 00:00
        double midday = profile.get(16).path("safetyScore").asDouble();     // 12:00
        assertThat(midday).isGreaterThan(lateNight);
    }

    @Test
    void bestTimeToLeaveValidatesInput() throws Exception {
        MockHttpSession session = signUp();
        perform(post("/api/routes/time-profile"), session, Map.of("geometry", List.of(), "startHour", 30))
                .andExpect(status().isBadRequest());
        perform(post("/api/routes/time-profile"), new MockHttpSession(), Map.of("geometry", List.of(List.of(1.0, 2.0), List.of(1.1, 2.1))))
                .andExpect(status().isUnauthorized());
    }
}
