package de.tonline.usersservicetask.client;

import de.tonline.usersservicetask.api.dto.Post;
import de.tonline.usersservicetask.client.common.UpstreamCalls;
import de.tonline.usersservicetask.client.common.UpstreamException;
import de.tonline.usersservicetask.config.CacheNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;

import java.util.List;

public class PostsClient {

    private final RestClient restClient;

    public PostsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Protection layers, outermost first:
     * <ol>
     *   <li>{@code @Cacheable}: only successful results are cached ({@code upstream.posts.cache.*}).</li>
     *   <li>{@code @Retryable}: only 5xx / IO errors, see {@link TransientFailurePredicate}; never 4xx or
     *       timeouts. Count, delay and jitter come from {@code upstream.posts.retry.*}.</li>
     *   <li>{@code @CircuitBreaker}: configured under {@code resilience4j.circuitbreaker.instances.posts}.
     *       While open it throws {@code CallNotPermittedException} (not an {@link UpstreamException}), so it
     *       is neither retried nor cached, and {@code SummaryService} degrades the response immediately.</li>
     * </ol>
     */
    @Cacheable(CacheNames.POSTS)
    @Retryable(includes = UpstreamException.class, predicate = TransientFailurePredicate.class,
            maxRetriesString = "${upstream.posts.retry.max-retries}",
            delayString = "${upstream.posts.retry.delay-millis}",
            jitterString = "${upstream.posts.retry.jitter-millis}")
    @CircuitBreaker(name = "posts")
    public List<Post> fetchByUser(long userId) {
        return UpstreamCalls.execute("posts service", () -> {
            List<Post> posts = restClient.get().uri("/posts?userId={id}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Post>>() {
                    });
            return posts == null ? List.<Post>of() : posts;
        });
    }
}
