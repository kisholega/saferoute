package com.saferoute.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getHistory_withoutUserId_usesDefaultUser() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/routes/history", String.class);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
    }

    @Test
    void searchLocations_returnsRealGeocodingResults() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/locations/search?query=Central%20Park", String.class);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().contains("latitude"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().contains("longitude"));
    }
}
