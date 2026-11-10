package de.tonline.usersservicetask.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything about the upstream REST clients lives under {@code upstream.*} in application.yaml. */
@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(Duration overallDeadline, Client users, Client posts) {

    /** Per-client HTTP settings. */
    public record Client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }
}
