package de.tonline.usersservicetask.config;

import de.tonline.usersservicetask.client.LoggingInterceptor;
import de.tonline.usersservicetask.client.PostsClient;
import de.tonline.usersservicetask.client.UsersClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableResilientMethods
@EnableConfigurationProperties(UpstreamProperties.class)
public class UpstreamConfig {

    @Bean
    UsersClient usersClient(UpstreamProperties props) {
        return new UsersClient(restClient("users", props.users()));
    }

    @Bean
    PostsClient postsClient(UpstreamProperties props) {
        return new PostsClient(restClient("posts", props.posts()));
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
