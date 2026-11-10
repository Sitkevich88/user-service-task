package de.tonline.usersservicetask.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration usersReadTimeout,
        Duration postsReadTimeout,
        int postsRetries,
        Duration overallDeadline) {
}
