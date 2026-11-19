package com.saferoute.ai.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.exception.ExternalServiceException;

/** Sends SMS through the Twilio REST API (no SDK needed). Enabled only when credentials are configured. */
@Component
public class TwilioClient {

    private static final String API = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final RestTemplate restTemplate;
    private final SafeRouteProperties.Alerts config;

    public TwilioClient(RestTemplate restTemplate, SafeRouteProperties properties) {
        this.restTemplate = restTemplate;
        this.config = properties.alerts();
    }

    public boolean isConfigured() {
        return !config.twilioAccountSid().isBlank() && !config.twilioAuthToken().isBlank()
                && !config.twilioFromNumber().isBlank();
    }

    /** @return the Twilio message SID */
    public String send(String toE164, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String credentials = config.twilioAccountSid() + ":" + config.twilioAuthToken();
        headers.set(HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", config.twilioFromNumber());
        form.add("Body", body);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(String.format(API, config.twilioAccountSid()),
                    new HttpEntity<>(form, headers), Map.class);
            return response == null ? "" : String.valueOf(response.getOrDefault("sid", ""));
        } catch (HttpStatusCodeException ex) {
            // Twilio explains the problem in the body, e.g. "unverified number" on trial accounts.
            String reason = ex.getResponseBodyAsString();
            int at = reason.indexOf("\"message\"");
            String message = at >= 0 ? reason.substring(at + 10, Math.min(reason.length(), at + 200)).replaceAll("[\"{}]", "").trim() : ex.getStatusText();
            throw new ExternalServiceException("SMS rejected: " + message, ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("SMS service is unreachable.", ex);
        }
    }
}
