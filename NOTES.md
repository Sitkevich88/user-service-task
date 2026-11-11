# NOTES

## Run

```
./mvnw spring-boot:run          # http://localhost:8080
curl localhost:8080/users/1/summary
./mvnw test                     # 15 tests, ~15 s
```

Requires Java 21+. The upstream URLs and all timeouts, retry, cache and circuit breaker settings are in `src/main/resources/application.yaml`.

## Behaviour when the posts service is slow or failing

`GET /users/{id}/summary` treats the two upstreams differently:

| Upstream | Role | On failure |
|---|---|---|
| users | required | 404 (unknown user), 502 (error), 504 (timeout), as RFC 9457 `ProblemDetail` |
| posts | optional | **200** with `posts: []` and `postsAvailable: false` |

Why: posts fails ~10% of the time and takes up to 3 s. If it could fail the endpoint, our availability would be capped by a non-critical dependency, and our latency would follow the worst upstream. `postsAvailable` keeps "no posts" and "posts unknown" apart, so clients can retry or show a placeholder.

## Key decisions

- **Concurrency:** `CompletableFuture` on a virtual-thread executor; both calls start immediately. If users fails, the posts future is cancelled (this only completes the future; a call already in flight ends at its own read timeout). `StructuredTaskScope` is still preview in Java 21, so I did not use it.
- **Timeouts:** connect 2 s, users read 2 s, posts read 1.5 s (deliberately below the 3 s worst case), overall deadline 3 s. The deadline covers both waits: if users is not there by then the answer is 504, if posts is not there by then it degrades. All in `application.yaml`.
- **Retry:** Spring Framework 7's built-in `@Retryable` (enabled with `@EnableResilientMethods`) on `PostsClient.fetchByUser`, no extra dependency. One retry, 50 ms delay + jitter (all in `upstream.posts.retry.*`), and a `TransientFailurePredicate` that accepts only 5xx / IO errors. Not 4xx and **not timeouts** (a slow upstream is likely to be slow again, and the retry would eat the budget). With ~10% failures one retry brings the degraded rate to ~1%. Limitation: the overall deadline is enforced by the caller (`future.get`), the retry loop itself does not know about it. With one retry the extra work is bounded.
- **Logging:** inbound calls via Tomcat's access log, configured purely in `application.yaml` (`server.tomcat.accesslog.*`, written to `logs/`, includes duration). Outbound calls via a `ClientHttpRequestInterceptor` on both `RestClient`s (logger `upstream.calls`): one line per attempt, so retries and timeouts are visible. This one needs code: `logging.level.org.springframework.web.client=DEBUG` only prints "Reading to [...]", with no URL, status or timing. No bodies or headers are logged on purpose (PII, noise).
- **Circuit breaker:** neither Spring Framework 7 nor Boot 4 has one built in (only `@Retryable` and `@ConcurrencyLimit`; I checked the jar), so it is Resilience4j through `resilience4j-spring-boot3` + `spring-boot-starter-aspectj`: `@CircuitBreaker(name = "posts")` on `PostsClient.fetchByUser`, configured under `resilience4j.circuitbreaker.instances.posts` in `application.yaml` (window 10, min 5 calls, 50% failure rate, 10 s open, 2 half-open trial calls). The starter is built for Boot 3, but it starts and binds the yaml on Boot 4.1; the tests prove it, but this is the dependency I would watch on upgrades. `CircuitBreakerFailurePredicate` (referenced from the yaml) counts 5xx, IO errors and timeouts, not 4xx. While open, the call is rejected immediately, so the endpoint answers with `postsAvailable=false` in milliseconds instead of waiting 1.5 s on every request. Order is cache -> retry -> breaker -> HTTP (confirmed by a test: one request, attempt + retry, already opens a breaker with minimum 2 calls), so an open breaker is not retried or cached, and cached posts are still served while it is open. State changes are logged. Users has no breaker: it is required, so failing fast would not change the outcome (still 502/504).
- **Cache:** Spring Cache + Caffeine, `@Cacheable` on both clients. Each upstream has its **own cache config** in `application.yaml` (`upstream.users.cache.*`, `upstream.posts.cache.*`: `max-size`, `ttl`; currently 500 entries / 1 s each). Spring Boot's `spring.cache.caffeine.spec` is shared by all caches, so a small `CacheManager` bean in `UpstreamConfig` builds one Caffeine cache per upstream instead. Caching sits at client level, so only successful upstream answers are cached: failures, 404s and degraded (empty, `postsAvailable=false`) results never are. Effect: a user and posts are fetched at most once per TTL per id. Not tested: the size limit itself (Caffeine's own behaviour); stampede protection (concurrent misses for one key each call upstream) is also not handled.
- **DTOs:** the summary contains all upstream data (full user with address, geo and company; posts without the redundant `userId`), so one set of records in `api/dto` is used both to read upstream responses and to build our response. No mapping layer. Trade-off: the upstream schema is now part of our API contract. `@JsonIgnoreProperties(ignoreUnknown = true)` keeps new upstream fields from breaking us, but they will not show up in the summary until the records are extended.
- **Tests use a tiny fake upstream on the JDK `HttpServer`** instead of WireMock. It can script delays and per-call failures, and needs no extra dependency (less risk with Spring Boot 4 compatibility).

## Deliberately left out

Known limitation: the first request after startup pays for DNS, TLS and class loading (1.8 s+ measured against jsonplaceholder) and can hit a timeout; later requests take 0.2-0.4 s. Correlation / trace ids in logs, bulkhead, a breaker for users, authentication, rate limiting, OpenAPI, Docker, CI, metrics and tracing, pagination of posts (jsonplaceholder returns ~10 per user).

## What I'd do next

1. **Docker:** a multi-stage `Dockerfile` and a `docker-compose.yml`, with upstream URLs and timeouts overridable through environment variables.
2. **Metrics:** Spring Boot Actuator + Micrometer: circuit breaker state, a counter of degraded responses (`postsAvailable=false`), upstream latency timers and cache hit rate, exposed for Prometheus.
3. **Cache:** a longer TTL for users (they rarely change) and protection against concurrent misses for the same key.

## Tests

`SummaryEndpointTest` (15 tests, ~15 s) runs the whole app against a scripted fake upstream (`FakeUpstream`, JDK `HttpServer`):
- **Core:** all user and post data merged, real concurrency (timing), user with zero posts (not degraded), invalid id.
- **Posts failing:** slow posts, 500 with retry, retry recovery, circuit breaker opens / skips calls / recovers, 4xx does not open it.
- **Users failing:** unknown user 404, 5xx 502, timeout 504.
- **Cache:** hit within 1 s, expiry after 1 s, failures not cached.

Caches and the breaker are reset before each test. Not covered: the cache size limit (Caffeine's own behaviour), the overall deadline for users (the per-call read timeout fires first with the test settings). The timing assertions use generous margins but could be flaky on a very loaded machine.

## AI usage

I wrote the first plan myself (`plan.md`) and then improved the solution in small iterations with Claude Code: retries, logging, DTOs, cache, circuit breaker, config in `application.yaml`. I reviewed each step and decided what to keep.

- **Helped:** boilerplate, the fake upstream for tests, the test scenarios, comparing retry options.
- **Got wrong:**
  - The first DTOs were incomplete: only a few fields of the user and posts. I gave it the real JSON examples and asked for all data in the summary.
  - Proposed `@Validated` / `@Positive`, but the project has no validation starter. Replaced by a simple check.
  - Added a custom request logging filter, although this works through `application.yaml`. I pointed it out and it was removed.
  - The generic advice on a "Boot 4 circuit breaker" missed that Boot 4 has none built in and that the Resilience4j starter targets Boot 3. Checked by hand, works with tests.
