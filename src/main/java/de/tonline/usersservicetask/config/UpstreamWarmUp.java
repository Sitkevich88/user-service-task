package de.tonline.usersservicetask.config;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.tonline.usersservicetask.client.PostsClient;
import de.tonline.usersservicetask.client.UsersClient;

/**
 * Makes one real call per upstream right after startup, in the background. The first call pays for DNS,
 * the TLS handshake and class loading (measured: ~1.8 s against jsonplaceholder), which would otherwise
 * hit the first real request and could trip its timeouts. Failures are ignored on purpose.
 */
@Component
@ConditionalOnProperty(name = "upstream.warm-up-enabled", havingValue = "true")
class UpstreamWarmUp {

    private static final Logger log = LoggerFactory.getLogger(UpstreamWarmUp.class);

    private final UsersClient usersClient;
    private final PostsClient postsClient;
    private final ExecutorService executor;

    UpstreamWarmUp(UsersClient usersClient, PostsClient postsClient, ExecutorService upstreamExecutor) {
        this.usersClient = usersClient;
        this.postsClient = postsClient;
        this.executor = upstreamExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        executor.execute(() -> run("users", () -> usersClient.fetch(1)));
        executor.execute(() -> run("posts", () -> postsClient.fetchByUser(1)));
    }

    private static void run(String name, Runnable call) {
        try {
            call.run();
            log.info("Warm-up of {} client done", name);
        } catch (RuntimeException e) {
            log.info("Warm-up of {} client failed (ignored): {}", name, e.getMessage());
        }
    }
}
