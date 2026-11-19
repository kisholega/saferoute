package com.saferoute.ai.service.alert;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.saferoute.ai.config.SafeRouteProperties;

/** Builds the links that go into alerts. */
@Component
public class TrackingLinks {

    private volatile String publicBaseUrl;

    public TrackingLinks(SafeRouteProperties properties) {
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    /** Switches links to a public address, e.g. the https URL of a Cloudflare tunnel discovered at runtime. */
    public void usePublicBaseUrl(String url) {
        this.publicBaseUrl = url.replaceAll("/+$", "");
    }

    public String trackUrl(String token) {
        return token == null ? null : publicBaseUrl + "/track/" + token;
    }

    public static String mapUrl(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return String.format(Locale.ROOT, "https://maps.google.com/?q=%.5f,%.5f", latitude, longitude);
    }

    /** Links to localhost only work on the computer running the server, not on a contact's phone. */
    public boolean isPublic() {
        String url = publicBaseUrl.toLowerCase(Locale.ROOT);
        return !(url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0"));
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }
}
