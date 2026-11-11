# Plan: `GET /users/{id}/summary`

> **Status: this is the original plan, kept for history.** The implementation went further and differs in places.
> See `NOTES.md` for what was actually built. Main deviations:
> - Tests use a small fake upstream on the JDK `HttpServer`, not WireMock.
> - Retry uses Spring 7's built-in `@Retryable`, not a hand-written loop.
> - A circuit breaker (Resilience4j), a per-upstream Caffeine cache, request logging and a startup warm-up were added.
> - The response contains **all** upstream data (full user, posts without `userId`) in shared DTOs, not the reduced shape in section 2.
> - Config lives in `application.yaml` per client (`upstream.users.*`, `upstream.posts.*`, `resilience4j.*`).

Timebox: 2 hours. Stack already in place: Java 21, Spring Boot 4.1.2, `RestClient`, Lombok, Maven.

## 1. Key design decision (the "what happens when posts is slow or fails" question)

The two upstreams are not equally important, so they get different failure handling.

| Upstream | Role | On timeout / error |
|---|---|---|
| `/users/{id}` | **Required.** There is no summary without a user. | 404 if upstream returns 404. 502 if upstream fails, 504 if it times out. |
| `/posts?userId={id}` | **Optional enrichment.** | **Degrade gracefully:** return 200 with the user, `posts: []` and `postsAvailable: false`. |

Why:
- The posts service fails about 10% of the time and takes up to 3 s. If a failed posts call failed the whole endpoint, our availability would be capped at about 90% because of a non-critical dependency.
- Waiting 3 s for posts makes our own latency as bad as the worst upstream. A client would rather get the user quickly with an explicit "posts unavailable" flag.
- The flag matters: an empty list must not look like "this user has no posts". The client can tell the two cases apart and retry or show a placeholder.

Resilience details for the posts call:
- **Timeout** on the posts call of about 1.5–2 s, configurable. Below the 3 s worst case, on purpose. Users call has a separate, similar timeout.
- **One retry** on connection error or 5xx only, and only if time remains in the overall budget. A 10% failure rate means one retry cuts the effective failure rate to about 1%. No retry on 4xx.
- **Overall deadline** for the endpoint, so retries can never push the response past the budget.
- Log degraded responses at WARN with the user id and the reason, and expose a counter metric if time allows.

## 2. Response shape

```json
{
  "user":  { "id": 1, "name": "...", "username": "...", "email": "...", "company": "..." },
  "posts": [ { "id": 1, "title": "...", "body": "..." } ],
  "postsAvailable": true
}
```

- Own DTOs (Java records) instead of passing upstream JSON through. Only the fields we need, `@JsonIgnoreProperties(ignoreUnknown = true)` on upstream models. This keeps us decoupled from the upstream schema.
- Option: also return `postCount`, which is cheap and useful.

## 3. Architecture (small, three layers)

```
api/       SummaryController, DTOs, exception handler (@RestControllerAdvice -> ProblemDetail)
service/   SummaryService   - orchestration, concurrency, degradation policy
client/    UsersClient, PostsClient - thin RestClient wrappers, upstream DTOs
config/    RestClient beans, timeouts via @ConfigurationProperties (application.yaml)
```

- **Concurrency:** `CompletableFuture.supplyAsync` on a virtual-thread executor (Java 21), started for both calls at the same time, then joined. Plain and easy to explain live. `StructuredTaskScope` is still preview in 21, so I'll mention it in NOTES.md and not use it.
- **Fail fast:** if the user call fails, don't wait for posts.
- **Errors:** `@RestControllerAdvice` maps domain exceptions (`UserNotFoundException`, `UpstreamUnavailableException`, `UpstreamTimeoutException`) to RFC 9457 `ProblemDetail`.
- **Validation:** `id` must be a positive integer. Return 400 otherwise.
- Config in `application.yaml`: base URLs, connect and read timeouts, retry count, overall deadline.

## 4. Steps and commits (commit after each step)

1. **Skeleton and config.** Clean the initial commit state, add config properties, `RestClient` beans with timeouts. Commit.
2. **Clients.** `UsersClient` and `PostsClient` with upstream DTOs and error translation. Commit.
3. **Service.** Parallel fetch, merge, degradation policy, retry. Commit.
4. **Controller and error handling.** Endpoint, validation, `ProblemDetail` mapping. Commit.
5. **Tests.** See section 5. Commit, possibly in two parts (service tests, then HTTP-level tests).
6. **NOTES.md.** Decisions, what was left out, next steps, where the AI helped or was wrong. Commit.
7. **Final check.** `./mvnw verify` from a clean state, run the app once against the real jsonplaceholder, then push to GitHub.

Rough time budget: setup 15 min, clients 20 min, service 25 min, controller and errors 15 min, tests 30 min, notes and polish 15 min. That is about 2 h with a small buffer.

## 5. Tests (important paths only)

Use WireMock (or `MockWebServer`) to stand in for the upstreams. `MockRestServiceServer` cannot simulate delays, which I need for the timeout test.

Service and integration level:
1. **Happy path:** both upstreams succeed, merged JSON is correct.
2. **Concurrency:** both upstreams delayed by about 500 ms, total time is below the sum of the two (about 500 ms, not 1000 ms).
3. **Posts times out:** 200 with `posts: []` and `postsAvailable: false`, and the response comes back within the budget.
4. **Posts returns 500:** same degradation. With one transient 500 followed by a success, the retry recovers and `postsAvailable: true`.
5. **User not found (upstream 404):** our 404.
6. **User upstream 5xx or timeout:** 502 or 504, as a `ProblemDetail`.
7. **User has zero posts:** `posts: []` and `postsAvailable: true`. This is different from case 3, and it is the reason for the flag.
8. **Invalid id (0, negative, not a number):** 400.

Left out of tests on purpose: JSON field-by-field mapping beyond the happy path, and load testing.

## 6. Deliberately out of scope (to be written down in NOTES.md)

- Caching (users change rarely, so a short TTL cache would be the first improvement).
- Circuit breaker (Resilience4j). With a 10% failure rate, timeout plus one retry is enough for now. A breaker pays off when the upstream is down for longer periods.
- Authentication, rate limiting, API docs (OpenAPI), Docker, CI.
- Pagination of posts. jsonplaceholder returns about 10 per user.
- Metrics and tracing beyond basic logging.

## 7. What I'd do next (for NOTES.md and the live extension)

- Add a Caffeine cache for users, with a short TTL.
- Add Resilience4j circuit breaker and bulkhead around posts.
- Micrometer metrics: degraded-response counter, upstream latency timers.
- Contract tests against the real jsonplaceholder schema.
- Make "partial" more explicit, for example an `X-Degraded: posts` header.

## 8. How I'll use AI, and what I'll record

- Use it for boilerplate (DTOs, `RestClient` config, test scaffolding) and as a reviewer of the degradation policy.
- Check every generated piece myself: Spring Boot 4 changed several starters and package names (`spring-boot-starter-restclient`, `spring-boot-starter-webmvc-test`), so AI suggestions based on Boot 3 APIs are likely to be wrong.
- Keep a running list while working of where the AI helped and where it was wrong, so the NOTES.md section is concrete and not written from memory at the end.
