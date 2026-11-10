package de.tonline.usersservicetask.config;

import de.tonline.usersservicetask.client.PostsClient;
import de.tonline.usersservicetask.client.UsersClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(UpstreamProperties.class)
public class UpstreamConfig {

    @Bean
    UsersClient usersClient(UpstreamProperties props) {
        return new UsersClient(restClient(props, props.usersReadTimeout()));
    }

    @Bean
    PostsClient postsClient(UpstreamProperties props) {
        return new PostsClient(restClient(props, props.postsReadTimeout()));
    }

    /** Virtual threads: the work is blocking IO, so one cheap thread per call is the simplest model. */
    @Bean(destroyMethod = "close")
    ExecutorService upstreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private static RestClient restClient(UpstreamProperties props, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }
}
