package de.tonline.usersservicetask.config;

/** Cache names, shared between {@code @Cacheable} annotations and the cache manager setup. */
public final class CacheNames {

    public static final String USERS = "users";
    public static final String POSTS = "posts";

    private CacheNames() {
    }
}
