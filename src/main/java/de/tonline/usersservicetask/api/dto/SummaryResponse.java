package de.tonline.usersservicetask.api.dto;

import java.util.List;

/**
 * @param postsAvailable false when the posts service failed or was too slow; {@code posts} is then empty
 *                       and must not be read as "the user has no posts"
 */
public record SummaryResponse(User user, List<Post> posts, boolean postsAvailable) {
}
