package de.tonline.usersservicetask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Everything about the upstream REST clients lives under {@code upstream.*} in application.yaml. */
@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(Duration overallDeadline, Client users, Client posts) {

    /** Per-client HTTP settings plus the settings of that client's own cache. Retry and circuit breaker
     * settings are read by Spring / Resilience4j directly from application.yaml. */
    public record Client(String baseUrl, Duration connectTimeout, Duration readTimeout, Cache cache) {
    }

    /** Entries expire {@code ttl} after being written; at most {@code maxSize} entries are kept. */
    public record Cache(long maxSize, Duration ttl) {
    }
}
