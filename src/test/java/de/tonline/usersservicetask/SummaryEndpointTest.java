package de.tonline.usersservicetask;

import static de.tonline.usersservicetask.FakeUpstream.Reply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests of GET /users/{id}/summary against a scripted fake upstream.
 * Timeouts are short so the suite stays fast.
 */
@SpringBootTest(properties = {
        "upstream.users.connect-timeout=500ms",
        "upstream.users.read-timeout=600ms",
        "upstream.posts.connect-timeout=500ms",
        "upstream.posts.read-timeout=400ms",
        "upstream.posts.retry.max-retries=1",
        "upstream.overall-deadline=1500ms"
})
@AutoConfigureMockMvc
class SummaryEndpointTest {

    private static final FakeUpstream UPSTREAM = new FakeUpstream();

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("upstream.users.base-url", UPSTREAM::baseUrl);
        registry.add("upstream.posts.base-url", UPSTREAM::baseUrl);
    }

    @AfterAll
    static void stop() {
        UPSTREAM.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void resetUpstream() {
        UPSTREAM.reset();
        // caches live across tests in the shared Spring context
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void repeatedCallsWithinOneSecondAreServedFromCache() throws Exception {
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk());
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.posts.length()").value(2));

        assertThat(UPSTREAM.userCalls.get()).isEqualTo(1);
        assertThat(UPSTREAM.postCalls.get()).isEqualTo(1);
    }

    @Test
    void cacheEntriesExpireAfterOneSecond() throws Exception {
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk());
        Thread.sleep(1200);
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk());

        assertThat(UPSTREAM.userCalls.get()).isEqualTo(2);
        assertThat(UPSTREAM.postCalls.get()).isEqualTo(2);
    }

    @Test
    void failuresAreNotCached() throws Exception {
        UPSTREAM.users = n -> n == 1 ? Reply.status(500) : Reply.ok(FakeUpstream.USER_JSON);

        mvc.perform(get("/users/1/summary")).andExpect(status().isBadGateway());
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk());
    }

    @Test
    void mergesAllUserAndPostData() throws Exception {
        mvc.perform(get("/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").value("Leanne Graham"))
                .andExpect(jsonPath("$.user.username").value("Bret"))
                .andExpect(jsonPath("$.user.email").value("Sincere@april.biz"))
                .andExpect(jsonPath("$.user.address.street").value("Kulas Light"))
                .andExpect(jsonPath("$.user.address.suite").value("Apt. 556"))
                .andExpect(jsonPath("$.user.address.city").value("Gwenborough"))
                .andExpect(jsonPath("$.user.address.zipcode").value("92998-3874"))
                .andExpect(jsonPath("$.user.address.geo.lat").value("-37.3159"))
                .andExpect(jsonPath("$.user.address.geo.lng").value("81.1496"))
                .andExpect(jsonPath("$.user.phone").value("1-770-736-8031 x56442"))
                .andExpect(jsonPath("$.user.website").value("hildegard.org"))
                .andExpect(jsonPath("$.user.company.name").value("Romaguera-Crona"))
                .andExpect(jsonPath("$.user.company.catchPhrase").value("Multi-layered client-server neural-net"))
                .andExpect(jsonPath("$.user.company.bs").value("harness real-time e-markets"))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].userId").doesNotExist())
                .andExpect(jsonPath("$.posts[0].id").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("sunt aut facere"))
                .andExpect(jsonPath("$.posts[0].body").value("quia et suscipit\nsuscipit recusandae"))
                .andExpect(jsonPath("$.posts[1].title").value("qui est esse"))
                .andExpect(jsonPath("$.postsAvailable").value(true));
    }

    @Test
    void callsUpstreamsConcurrently() throws Exception {
        UPSTREAM.users = n -> Reply.ok(FakeUpstream.USER_JSON, 300);
        UPSTREAM.posts = n -> Reply.ok(FakeUpstream.POSTS_JSON, 300);

        long start = System.nanoTime();
        mvc.perform(get("/users/1/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.postsAvailable").value(true));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // sequential execution would take at least 600 ms
        assertThat(elapsedMs).isLessThan(580);
    }

    @Test
    void slowPostsDegradeToEmptyListWithoutFailingTheRequest() throws Exception {
        UPSTREAM.posts = n -> Reply.ok(FakeUpstream.POSTS_JSON, 3000);

        long start = System.nanoTime();
        mvc.perform(get("/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.postsAvailable").value(false));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("must not wait for the 3s upstream").isLessThan(1500);
        assertThat(UPSTREAM.postCalls.get()).as("timeouts are not retried").isEqualTo(1);
    }

    @Test
    void failingPostsDegradeAfterRetry() throws Exception {
        UPSTREAM.posts = n -> Reply.status(500);

        mvc.perform(get("/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.postsAvailable").value(false));

        assertThat(UPSTREAM.postCalls.get()).as("first attempt + one retry").isEqualTo(2);
    }

    @Test
    void transientPostsFailureIsRecoveredByRetry() throws Exception {
        UPSTREAM.posts = n -> n == 1 ? Reply.status(503) : Reply.ok(FakeUpstream.POSTS_JSON);

        mvc.perform(get("/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.postsAvailable").value(true));
    }

    @Test
    void userWithoutPostsIsNotTreatedAsDegraded() throws Exception {
        UPSTREAM.posts = n -> Reply.ok("[]");

        mvc.perform(get("/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.postsAvailable").value(true));
    }

    @Test
    void unknownUserIs404() throws Exception {
        UPSTREAM.users = n -> Reply.status(404);

        mvc.perform(get("/users/999/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void usersUpstreamErrorIs502() throws Exception {
        UPSTREAM.users = n -> Reply.status(500);

        mvc.perform(get("/users/1/summary")).andExpect(status().isBadGateway());
    }

    @Test
    void usersUpstreamTimeoutIs504() throws Exception {
        UPSTREAM.users = n -> Reply.ok(FakeUpstream.USER_JSON, 3000);

        mvc.perform(get("/users/1/summary")).andExpect(status().isGatewayTimeout());
    }

    @Test
    void invalidIdIs400() throws Exception {
        mvc.perform(get("/users/0/summary")).andExpect(status().isBadRequest());
        mvc.perform(get("/users/-5/summary")).andExpect(status().isBadRequest());
        mvc.perform(get("/users/abc/summary")).andExpect(status().isBadRequest());
        assertThat(UPSTREAM.userCalls.get()).isZero();
    }
}
