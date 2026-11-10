package de.tonline.usersservicetask.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

public class UsersClient {

    private final RestClient restClient;

    public UsersClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Only the fields we expose. Unknown upstream fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpstreamUser(long id, String name, String username, String email, Company company) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(String name) {
    }

    public UpstreamUser fetch(long id) {
        return UpstreamCalls.execute("users service", () ->
                restClient.get().uri("/users/{id}", id)
                        .retrieve()
                        .onStatus(s -> s.value() == 404, (req, res) -> {
                            throw new UserNotFoundException(id);
                        })
                        .body(UpstreamUser.class));
    }
}
