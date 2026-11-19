package com.saferoute.ai.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.exception.ExternalServiceException;

/**
 * Publishes push notifications through ntfy (https://ntfy.sh): free, open source, no account or API key.
 * Anyone subscribed to the topic in the ntfy app (Android / iOS) or the ntfy web app gets the notification
 * instantly. JSON publishing is used so titles and messages can contain any characters.
 */
@Component
public class NtfyClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NtfyClient(RestTemplate restTemplate, SafeRouteProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.alerts().ntfyUrl().replaceAll("/+$", "");
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** A button shown on the notification that opens a link. */
    public record Action(String label, String url) {
    }

    /**
     * @param priority 1 (min) to 5 (max: urgent, overrides do-not-disturb in the app when allowed)
     * @param tags     emoji short codes shown with the notification, e.g. "rotating_light"
     * @param click    link opened when the notification is tapped
     * @return the ntfy message id
     */
    public String publish(String topic, String title, String message, int priority, List<String> tags,
                          String click, List<Action> actions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("title", title);
        body.put("message", message);
        body.put("priority", priority);
        body.put("tags", tags);
        if (click != null) {
            body.put("click", click);
        }
        List<Map<String, Object>> actionList = new ArrayList<>();
        for (Action action : actions) {
            actionList.add(Map.of("action", "view", "label", action.label(), "url", action.url(), "clear", false));
        }
        if (!actionList.isEmpty()) {
            body.put("actions", actionList);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(baseUrl, body, Map.class);
            return response == null ? "" : String.valueOf(response.getOrDefault("id", ""));
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Push notification service is unreachable.", ex);
        }
    }
}
