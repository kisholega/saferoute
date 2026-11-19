package com.saferoute.ai.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.exception.ExternalServiceException;

/**
 * Runs Overpass QL queries against OpenStreetMap. Public Overpass servers are sometimes busy
 * (HTTP 429/502/504), so each configured server is tried in turn, with one retry after a short pause.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private final RestTemplate restTemplate;
    private final List<String> urls;

    public OverpassClient(@Qualifier("overpassRestTemplate") RestTemplate restTemplate, SafeRouteProperties properties) {
        this.restTemplate = restTemplate;
        this.urls = properties.overpass().urls();
    }

    /** @return the "elements" array of the Overpass JSON response */
    public JsonNode query(String overpassQl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>(overpassQl, headers);

        RuntimeException last = null;
        for (String url : urls) {
            for (int attempt = 1; attempt <= 2; attempt++) { // one quick retry when the server is busy
                try {
                    JsonNode root = restTemplate.postForObject(url, request, JsonNode.class);
                    if (root != null && root.has("elements")) {
                        return root.path("elements");
                    }
                    last = new IllegalStateException("Unexpected Overpass response");
                } catch (HttpStatusCodeException ex) {
                    last = ex;
                    int status = ex.getStatusCode().value();
                    log.debug("Overpass {} answered {} (attempt {})", url, status, attempt);
                    if (status != 429 && status != 502 && status != 503 && status != 504) {
                        break; // a real error in the query: retrying will not help
                    }
                    pause(1000);
                } catch (RestClientException ex) {
                    last = ex;
                    log.debug("Overpass {} unreachable (attempt {}): {}", url, attempt, ex.getMessage());
                    break; // timeout or connection problem: try the next server
                }
            }
        }
        throw new ExternalServiceException("Street data from OpenStreetMap is unavailable right now.", last);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
