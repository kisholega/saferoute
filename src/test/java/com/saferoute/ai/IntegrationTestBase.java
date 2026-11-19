package com.saferoute.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saferoute.ai.client.NominatimClient;
import com.saferoute.ai.client.NtfyClient;
import com.saferoute.ai.client.OsrmClient;
import com.saferoute.ai.client.OverpassClient;
import com.saferoute.ai.client.PhotonClient;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;

/**
 * Shared setup for tests that start the whole application. Every client that calls the internet is a
 * mock, so tests are fast, repeatable and work offline. All subclasses share the same mocks, so Spring
 * reuses one application context for the whole suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final AtomicInteger USERS = new AtomicInteger();

    @MockBean protected PhotonClient photonClient;
    @MockBean protected NominatimClient nominatimClient;
    @MockBean protected OsrmClient osrmClient;
    @MockBean protected OverpassClient overpassClient;
    @MockBean protected NtfyClient ntfyClient;

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    /** Push notifications "sent" during the test: topic + title. */
    protected final List<String> pushes = new ArrayList<>();

    @BeforeEach
    void defaultMocks() {
        pushes.clear();
        lenient().when(ntfyClient.publish(anyString(), anyString(), anyString(), anyInt(), anyList(), any(), anyList()))
                .thenAnswer(inv -> {
                    pushes.add(inv.getArgument(0) + "|" + inv.getArgument(1));
                    return "msg-" + pushes.size();
                });
        lenient().when(ntfyClient.baseUrl()).thenReturn("https://ntfy.sh");
        lenient().when(overpassClient.query(anyString())).thenThrow(new ExternalServiceException("offline in tests", null));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Registers a brand-new user and returns their signed-in session. */
    protected MockHttpSession signUp() throws Exception {
        return signUp("Priya Sharma");
    }

    protected MockHttpSession signUp(String name) throws Exception {
        int n = USERS.incrementAndGet();
        ResultActions result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", "user" + n + "-" + System.nanoTime() + "@test.local",
                                "phone", "98450" + String.format("%05d", n % 100000), "password", "correct-horse-1"))))
                .andExpect(status().isCreated());
        return sessionOf(result);
    }

    /** Signing in starts a fresh session (new id), just as the browser's cookie would be replaced. */
    protected MockHttpSession sessionOf(ResultActions result) {
        return (MockHttpSession) result.andReturn().getRequest().getSession(false);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, MockHttpSession session, Object body) throws Exception {
        request.session(session);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- fake OpenStreetMap data

    /** Builds an Overpass "elements" array. */
    protected class OsmBuilder {
        private final ArrayNode elements = objectMapper.createArrayNode();
        private long id = 1000;

        public OsmBuilder() {
        }

        public OsmBuilder node(GeoPoint p, String... tags) {
            ObjectNode node = elements.addObject();
            node.put("type", "node");
            node.put("id", id++);
            node.put("lat", p.lat());
            node.put("lon", p.lon());
            ObjectNode tagNode = node.putObject("tags");
            for (int i = 0; i + 1 < tags.length; i += 2) {
                tagNode.put(tags[i], tags[i + 1]);
            }
            return this;
        }

        /** n copies of the same kind of node spread along a line. */
        public OsmBuilder along(List<GeoPoint> line, int perSegment, String... tags) {
            for (int i = 1; i < line.size(); i++) {
                GeoPoint a = line.get(i - 1);
                GeoPoint b = line.get(i);
                for (int k = 0; k < perSegment; k++) {
                    double t = (k + 0.5) / perSegment;
                    node(new GeoPoint(a.lat() + (b.lat() - a.lat()) * t, a.lon() + (b.lon() - a.lon()) * t), tags);
                }
            }
            return this;
        }

        public JsonNode build() {
            return elements;
        }
    }
}
