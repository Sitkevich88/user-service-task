package de.tonline.usersservicetask.client;

import de.tonline.usersservicetask.api.dto.Post;
import de.tonline.usersservicetask.client.common.UpstreamCalls;
import de.tonline.usersservicetask.client.common.UpstreamException;
import de.tonline.usersservicetask.config.CacheNames;
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
     * Retried by Spring's built-in {@code @Retryable} (enabled via {@code @EnableResilientMethods}):
     * only {@link UpstreamException}s that {@link TransientFailurePredicate} accepts, i.e. 5xx / IO errors,
     * never 4xx and never timeouts. Successful results are cached briefly (see {@code upstream.posts.cache.*}); failures never are. Retry count, delay and jitter come from {@code upstream.posts.retry.*}.
     */
    @Retryable(includes = UpstreamException.class, predicate = TransientFailurePredicate.class,
            maxRetriesString = "${upstream.posts.retry.max-retries}",
            delayString = "${upstream.posts.retry.delay-millis}",
            jitterString = "${upstream.posts.retry.jitter-millis}")
    @Cacheable(CacheNames.POSTS)
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
