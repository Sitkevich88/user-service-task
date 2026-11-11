package de.tonline.usersservicetask.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import de.tonline.usersservicetask.client.LoggingInterceptor;
import de.tonline.usersservicetask.client.PostsClient;
import de.tonline.usersservicetask.client.UsersClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableResilientMethods
@EnableCaching
@EnableConfigurationProperties(UpstreamProperties.class)
public class UpstreamConfig {

    private static final Logger log = LoggerFactory.getLogger(UpstreamConfig.class);

    @Bean
    UsersClient usersClient(UpstreamProperties props) {
        return new UsersClient(restClient("users", props.users()));
    }

    @Bean
    PostsClient postsClient(UpstreamProperties props) {
        return new PostsClient(restClient("posts", props.posts()));
    }

    /** Logs every circuit breaker state change. The breaker itself is configured in application.yaml. */
    @Bean
    RegistryEventConsumer<CircuitBreaker> circuitBreakerLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher().onStateTransition(e ->
                        log.warn("Circuit breaker '{}' state: {}", e.getCircuitBreakerName(), e.getStateTransition()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
    }

    /** One Caffeine cache per upstream, each with its own TTL and size limit from application.yaml. */
    @Bean
    CacheManager cacheManager(UpstreamProperties props) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache(CacheNames.USERS, props.users().cache()),
                caffeineCache(CacheNames.POSTS, props.posts().cache())));
        return manager;
    }

    private static CaffeineCache caffeineCache(String name, UpstreamProperties.Cache config) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(config.maxSize())
                .expireAfterWrite(config.ttl())
                .build());
    }

    /** Virtual threads: the work is blocking IO, so one cheap thread per call is the simplest model. */
    @Bean(destroyMethod = "close")
    ExecutorService upstreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private static RestClient restClient(String name, UpstreamProperties.Client config) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(config.readTimeout());
        return RestClient.builder().baseUrl(config.baseUrl()).requestFactory(factory)
                .requestInterceptor(new LoggingInterceptor(name)).build();
    }
}
