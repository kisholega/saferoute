package com.saferoute.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SafeRouteAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeRouteAiApplication.class, args);
    }
}
