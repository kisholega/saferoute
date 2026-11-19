package com.saferoute.ai.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /** Shared HTTP client for quick calls (geocoding, routing, push, SMS). */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder, SafeRouteProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.http().connectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.http().readTimeoutMs()))
                .defaultHeader(HttpHeaders.USER_AGENT, properties.http().userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .build();
    }

    /** Overpass queries can take tens of seconds, so they get their own longer timeout. */
    @Bean
    @Qualifier("overpassRestTemplate")
    public RestTemplate overpassRestTemplate(RestTemplateBuilder builder, SafeRouteProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.http().connectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.overpass().timeoutMs()))
                .defaultHeader(HttpHeaders.USER_AGENT, properties.http().userAgent())
                .build();
    }

    /** Sends alerts in the background so an SOS request returns immediately. Tests run it synchronously. */
    @Bean
    @Qualifier("alertExecutor")
    public Executor alertExecutor(SafeRouteProperties properties) {
        if (properties.alerts().synchronous()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("alert-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Background jobs (trip watchdog, SSE heartbeats). Switched off in tests, which call them directly. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "saferoute.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
