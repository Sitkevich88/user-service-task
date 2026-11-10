package de.tonline.usersservicetask.client;

import de.tonline.usersservicetask.api.dto.User;
import de.tonline.usersservicetask.client.common.UpstreamCalls;
import org.springframework.web.client.RestClient;

public class UsersClient {

    private final RestClient restClient;

    public UsersClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public User fetch(long id) {
        return UpstreamCalls.execute("users service", () ->
                restClient.get().uri("/users/{id}", id)
                        .retrieve()
                        .onStatus(s -> s.value() == 404, (req, res) -> {
                            throw new UserNotFoundException(id);
                        })
                        .body(User.class));
    }
}
