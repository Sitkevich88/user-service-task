package de.tonline.usersservicetask.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

public class PostsClient {

    private final RestClient restClient;

    public PostsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpstreamPost(long id, String title, String body) {
    }

    public List<UpstreamPost> fetchByUser(long userId) {
        return UpstreamCalls.execute("posts service", () -> {
            List<UpstreamPost> posts = restClient.get().uri("/posts?userId={id}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpstreamPost>>() {
                    });
            return posts == null ? List.<UpstreamPost>of() : posts;
        });
    }
}
