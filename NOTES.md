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
- **Retry:** posts only, one retry, only for 5xx / IO errors. Not for 4xx and **not for timeouts** (a slow upstream is likely to be slow again, and the retry would eat the whole budget). With ~10% failures one retry brings the degraded rate to ~1%.
- **Own DTOs** with only the fields we expose, so upstream schema changes do not leak into our API.
- **Tests use a tiny fake upstream on the JDK `HttpServer`** instead of WireMock. It can script delays and per-call failures, and needs no extra dependency (less risk with Spring Boot 4 compatibility).

## Deliberately left out

Caching, circuit breaker / bulkhead, authentication, rate limiting, OpenAPI, Docker, CI, metrics and tracing, pagination of posts (jsonplaceholder returns ~10 per user).

## What I'd do next

1. Short-TTL cache (Caffeine) for users.
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
  - A first attempt to write the test files through a shell heredoc failed silently on quoting. Nothing was written, and I only noticed by checking the directory.
  - Spring Boot 4 moved packages and starters (e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`). I checked the real jar contents instead of trusting Boot 3 habits.
