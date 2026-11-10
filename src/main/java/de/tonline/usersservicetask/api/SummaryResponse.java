package de.tonline.usersservicetask.api;

import java.util.List;

public record SummaryResponse(User user, List<Post> posts, boolean postsAvailable) {

    public record User(long id, String name, String username, String email, String company) {
    }

    public record Post(long id, String title, String body) {
    }
}
