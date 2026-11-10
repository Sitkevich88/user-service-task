package de.tonline.usersservicetask.service;

import de.tonline.usersservicetask.api.SummaryResponse;
import de.tonline.usersservicetask.client.PostsClient;
import de.tonline.usersservicetask.client.PostsClient.UpstreamPost;
import de.tonline.usersservicetask.client.UpstreamException;
import de.tonline.usersservicetask.client.UsersClient;
import de.tonline.usersservicetask.client.UsersClient.UpstreamUser;
import de.tonline.usersservicetask.config.UpstreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

/**
 * Fetches user and posts concurrently and merges them.
 * <ul>
 *   <li>User is required: any failure is propagated.</li>
 *   <li>Posts are optional: on error or when the deadline is hit we answer with an empty list
 *       and {@code postsAvailable=false}.</li>
 * </ul>
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final UsersClient usersClient;
    private final PostsClient postsClient;
    private final ExecutorService executor;
    private final UpstreamProperties props;

    public SummaryService(UsersClient usersClient, PostsClient postsClient,
                          ExecutorService upstreamExecutor, UpstreamProperties props) {
        this.usersClient = usersClient;
        this.postsClient = postsClient;
        this.executor = upstreamExecutor;
        this.props = props;
    }

    public SummaryResponse getSummary(long id) {
        long start = System.nanoTime();

        CompletableFuture<UpstreamUser> userFuture =
                CompletableFuture.supplyAsync(() -> usersClient.fetch(id), executor);
        CompletableFuture<List<UpstreamPost>> postsFuture =
                CompletableFuture.supplyAsync(() -> fetchPostsWithRetry(id, start), executor);

        UpstreamUser user;
        try {
            user = userFuture.join();
        } catch (java.util.concurrent.CompletionException e) {
            postsFuture.cancel(true); // no point waiting for posts without a user
            throw unwrap(e);
        }

        List<UpstreamPost> posts = List.of();
        boolean available = true;
        try {
            posts = postsFuture.get(remainingNanos(start), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            postsFuture.cancel(true);
            available = false;
            log.warn("Posts for user {} not available: deadline of {} exceeded", id, props.overallDeadline());
        } catch (ExecutionException e) {
            available = false;
            log.warn("Posts for user {} not available: {}", id, e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("interrupted while waiting for posts", e, false, false);
        } catch (CancellationException e) {
            available = false;
        }

        return merge(user, posts, available);
    }

    private List<UpstreamPost> fetchPostsWithRetry(long userId, long startNanos) {
        int attempt = 0;
        while (true) {
            try {
                return postsClient.fetchByUser(userId);
            } catch (UpstreamException e) {
                boolean canRetry = e.isRetryable() && !e.isTimeout()
                        && attempt < props.postsRetries()
                        && remainingNanos(startNanos) > 0;
                if (!canRetry) {
                    throw e;
                }
                attempt++;
                log.info("Retrying posts for user {} (attempt {}): {}", userId, attempt + 1, e.getMessage());
            }
        }
    }

    private long remainingNanos(long startNanos) {
        return Math.max(0, props.overallDeadline().toNanos() - (System.nanoTime() - startNanos));
    }

    private static RuntimeException unwrap(java.util.concurrent.CompletionException e) {
        return e.getCause() instanceof RuntimeException re ? re : e;
    }

    private static SummaryResponse merge(UpstreamUser u, List<UpstreamPost> posts, boolean available) {
        return new SummaryResponse(
                new SummaryResponse.User(u.id(), u.name(), u.username(), u.email(),
                        u.company() == null ? null : u.company().name()),
                posts.stream().map(p -> new SummaryResponse.Post(p.id(), p.title(), p.body())).toList(),
                available);
    }
}
