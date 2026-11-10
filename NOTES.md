# NOTES

## Behaviour when the posts service is slow or failing

`GET /users/{id}/summary` treats the two upstreams differently:

| Upstream | Role | On failure |
|---|---|---|
| users | required | 404 (unknown user), 502 (error), 504 (timeout), as RFC 9457 `ProblemDetail` |
| posts | optional | **200** with `posts: []` and `postsAvailable: false` |

Why: posts fails ~10% of the time and takes up to 3 s. If it could fail the endpoint, our availability would be capped by a non-critical dependency, and our latency would follow the worst upstream. `postsAvailable` keeps "no posts" and "posts unknown" apart, so clients can retry or show a placeholder.

## Key decisions

- **Concurrency:** `CompletableFuture` on a virtual-thread executor; both calls start immediately. If users fails, the posts future is cancelled. `StructuredTaskScope` is still preview in Java 21, so I did not use it.
- **Timeouts:** connect 1 s, users read 2 s, posts read 1.5 s (deliberately below the 3 s worst case), overall deadline 3 s. All in `application.yaml`.
- **Retry:** Spring Framework 7's built-in `@Retryable` (enabled with `@EnableResilientMethods`) on `PostsClient.fetchByUser`, no extra dependency. One retry, 50 ms delay + jitter (all in `upstream.posts.retry.*`), and a `TransientFailurePredicate` that accepts only 5xx / IO errors. Not 4xx and **not timeouts** (a slow upstream is likely to be slow again, and the retry would eat the budget). With ~10% failures one retry brings the degraded rate to ~1%. Limitation: the overall deadline is enforced by the caller (`future.get`), the retry loop itself does not know about it. With one retry the extra work is bounded.
- **Logging:** inbound calls via Tomcat's access log, configured purely in `application.yaml` (`server.tomcat.accesslog.*`, written to `logs/`, includes duration). Outbound calls via a `ClientHttpRequestInterceptor` on both `RestClient`s (logger `upstream.calls`): one line per attempt, so retries and timeouts are visible. This one needs code: `logging.level.org.springframework.web.client=DEBUG` only prints "Reading to [...]", with no URL, status or timing. No bodies or headers are logged on purpose (PII, noise).
- **Cache:** Spring Cache + Caffeine, `@Cacheable` on both clients. Each upstream has its **own cache config** in `application.yaml` (`upstream.users.cache.*`, `upstream.posts.cache.*`: `max-size`, `ttl`; currently 500 entries / 1 s each). Spring Boot's `spring.cache.caffeine.spec` is shared by all caches, so a small `CacheManager` bean in `UpstreamConfig` builds one Caffeine cache per upstream instead. Caching sits at client level, so only successful upstream answers are cached: failures, 404s and degraded (empty, `postsAvailable=false`) results never are. Effect: a user and posts are fetched at most once per TTL per id. Not tested: the size limit itself (Caffeine's own behaviour); stampede protection (concurrent misses for one key each call upstream) is also not handled.
- **DTOs:** the summary contains all upstream data (full user with address, geo and company; posts without the redundant `userId`), so one set of records in `api/dto` is used both to read upstream responses and to build our response. No mapping layer. Trade-off: the upstream schema is now part of our API contract. `@JsonIgnoreProperties(ignoreUnknown = true)` keeps new upstream fields from breaking us, but they will not show up in the summary until the records are extended.
- **Tests use a tiny fake upstream on the JDK `HttpServer`** instead of WireMock. It can script delays and per-call failures, and needs no extra dependency (less risk with Spring Boot 4 compatibility).

## Deliberately left out

Correlation / trace ids in logs, circuit breaker / bulkhead, authentication, rate limiting, OpenAPI, Docker, CI, metrics and tracing, pagination of posts (jsonplaceholder returns ~10 per user).

## What I'd do next

1. Longer TTL for users (they change rarely), now possible per cache, and stampede protection.
2. Resilience4j circuit breaker around posts, so a longer outage stops costing 1.5 s per request.
3. Micrometer: degraded-response counter, upstream latency timers.
4. Header such as `X-Degraded: posts` next to the body flag.
5. A contract test against the real jsonplaceholder schema.

## Tests

`SummaryEndpointTest` (10 tests, ~10 s): happy path, real concurrency (timing), slow posts, failing posts with retry, retry recovery, user with zero posts (not degraded), unknown user, users 5xx, users timeout, invalid id. The timing assertions use generous margins but could be flaky on a very loaded machine.

## AI usage

Written with Claude Code.

- **Helped:** planning the failure policy, boilerplate (config, DTOs, clients), the fake upstream and the test scenarios.
- **Got wrong / needed fixing:**
  - First version of the controller used `@Validated` and `@Positive`, but the project has no validation starter, so it would not have worked. Replaced by an explicit check.
  - I first added a custom `OncePerRequestFilter` for inbound logging. The user pointed out that this is available through configuration; I checked and replaced the filter with the access log settings.
  - A first attempt to write the test files through a shell heredoc failed silently on quoting. Nothing was written, and I only noticed by checking the directory.
  - Spring Boot 4 moved packages and starters (e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`). I checked the real jar contents instead of trusting Boot 3 habits.
