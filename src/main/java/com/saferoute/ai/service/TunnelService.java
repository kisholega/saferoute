package com.saferoute.ai.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.saferoute.ai.service.alert.TrackingLinks;

import jakarta.annotation.PreDestroy;

/**
 * Makes live-tracking links work on any phone or computer, not just this one.
 * <p>
 * When enabled (PUBLIC_TUNNEL=true), the app starts a free Cloudflare "quick tunnel"
 * ({@code cloudflared tunnel --url http://localhost:PORT}), reads the public https address it prints
 * (https://something.trycloudflare.com) and uses it for every tracking link in alerts. No account is needed.
 * The address changes each time the app starts. A fixed address can be set with PUBLIC_BASE_URL instead.
 */
@Service
public class TunnelService {

    private static final Logger log = LoggerFactory.getLogger(TunnelService.class);
    private static final Pattern URL = Pattern.compile("https://[a-z0-9-]+[.]trycloudflare[.]com");

    private final TrackingLinks links;
    private final boolean enabled;
    private final String configuredPath;
    private final int port;
    private volatile Process process;
    private volatile String publicUrl;
    private volatile String status = "off";

    public TunnelService(TrackingLinks links,
                         @Value("${saferoute.tunnel.enabled:false}") boolean enabled,
                         @Value("${saferoute.tunnel.cloudflared-path:}") String configuredPath,
                         @Value("${server.port:8080}") int port) {
        this.links = links;
        this.enabled = enabled;
        this.configuredPath = configuredPath;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        if (links.isPublic()) {
            status = "using PUBLIC_BASE_URL";
            log.info("PUBLIC_BASE_URL is set to {}; not starting a tunnel.", links.publicBaseUrl());
            return;
        }
        String binary = findCloudflared();
        if (binary == null) {
            status = "cloudflared not found";
            log.warn("PUBLIC_TUNNEL=true but cloudflared was not found. Install it (winget install Cloudflare.cloudflared) "
                    + "or set CLOUDFLARED_PATH. Tracking links stay on localhost.");
            return;
        }
        try {
            status = "starting";
            process = new ProcessBuilder(binary, "tunnel", "--no-autoupdate", "--url", "http://localhost:" + port)
                    .redirectErrorStream(true)
                    .start();
            Thread reader = new Thread(this::readOutput, "cloudflared-output");
            reader.setDaemon(true);
            reader.start();
            log.info("Starting a public Cloudflare tunnel for live-tracking links…");
        } catch (Exception ex) {
            status = "failed: " + ex.getMessage();
            log.warn("Could not start cloudflared: {}", ex.getMessage());
        }
    }

    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = URL.matcher(line);
                if (publicUrl == null && matcher.find()) {
                    publicUrl = matcher.group();
                    links.usePublicBaseUrl(publicUrl);
                    status = "on";
                    log.info("==> Public address: {}  (tracking links in alerts now work on any device)", publicUrl);
                }
            }
        } catch (Exception ex) {
            log.debug("cloudflared output closed: {}", ex.getMessage());
        }
        if (!"on".equals(status)) {
            status = "stopped";
        }
    }

    private String findCloudflared() {
        List<String> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(configuredPath);
        }
        candidates.add("tools/cloudflared.exe");
        candidates.add("C:/Program Files (x86)/cloudflared/cloudflared.exe");
        candidates.add("C:/Program Files/cloudflared/cloudflared.exe");
        candidates.add("/usr/local/bin/cloudflared");
        candidates.add("/usr/bin/cloudflared");
        candidates.add("/opt/homebrew/bin/cloudflared");
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                candidates.add(new File(dir, "cloudflared.exe").getPath());
                candidates.add(new File(dir, "cloudflared").getPath());
            }
        }
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /** "on", "starting", "off", "cloudflared not found", ... for the app-info endpoint. */
    public String status() {
        return status;
    }

    public String publicUrl() {
        return publicUrl;
    }

    @PreDestroy
    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }
}
