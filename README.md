# InvestorBook

A Spring Cloud microservices demo — service discovery (Eureka), an OAuth2/JWT authorization
server, an API gateway, a member-profile service with S3-backed picture upload, and a
choreographed, event-driven purchase saga over Kafka — modeling a social network for investors.
Nine independently deployable modules plus a shared library, no parent POM, each built and run on
its own. See [`CLAUDE.md`](CLAUDE.md) for the full architecture, security model, and per-module
quirks.

This was a working prototype with no tests. This README is about what changed and how.

## The state I started from

`member-service` — the service with the actual business logic (signup, profile, S3 picture
upload) — had zero tests and didn't even compile from a clean checkout: a dead import
(`javax.ws.rs.Produces`, unused, and no dependency on the classpath provided it) broke the build
before anything else could run. Its S3 integration was a fully static utility class with an
`AmazonS3` client built eagerly in a static field, which made it structurally impossible to test
against anything but a real AWS account.

## How I used AI here

I ran this as an actual agentic session in Claude Code, directing it through the same workflow I
use day to day: reproduce with a failing test before touching production code, drive a full suite
to green without ever weakening what a test checks, run a security scan before calling anything
done. Three of my own Claude Code skills encode that workflow as agent instructions, checked into
this repo at [`.claude/skills/`](.claude/skills/) so they're readable, not just described:

- **[`bugfix-workflow`](.claude/skills/bugfix-workflow/SKILL.md)** — reproduce a bug with a
  failing test first, fix the root cause, code-review the fix, drive the suite green, extend
  coverage where the bug revealed a gap, run the security scan, only then call it done.
- **[`integration-test-loop`](.claude/skills/integration-test-loop/SKILL.md)** — runs the suite;
  on failure, diagnoses whether the code or the test is wrong, fixes it, and re-runs the *whole*
  suite, up to a bounded number of iterations. Never reaches green by disabling or weakening a
  test. Stops and reports if it can't fix something legitimately.
- **[`owasp-java-check`](.claude/skills/owasp-java-check/SKILL.md)** — OWASP Dependency-Check
  (CVEs) plus SpotBugs/FindSecBugs (static analysis). Reports by severity; never suppresses a
  finding without a recorded reason.

To be plain about what these are: agent instructions, not deterministic guarantees. They reduce
the odds of an agent reaching a dishonest "done" — they don't eliminate it. The judgment calls
below were mine, made by actually reading what the agent produced, not by trusting a green
checkmark.

## What actually happened, in order

1. **Fixed the dead import.** Nothing could be tested until the module compiled.
2. **Bumped `member-service` to Spring Boot 2.2.13**, specifically to get JUnit 5 as the default —
   its original Boot 2.0.2 shipped JUnit 4 with a Surefire version that predates JUnit Platform
   support entirely. The other four services were left untouched; they only talk to each other
   over REST/Eureka, so there was no reason to touch their dependency graphs.
3. **Replaced the static `S3Utils`** with a constructor-injected `ProfilePictureStorage`, so it
   could be pointed at a real S3 API (LocalStack) in tests instead of a real AWS account. Two more
   defects came out of that refactor for free: a presigned URL was being generated against the
   full stored S3 URL instead of the object key (would never have resolved to a real object), and
   the upload path built its temp filename directly from the client-supplied original filename
   with no sanitization.
4. **Built the test suite in layers**, from fastest/narrowest to slowest/broadest — unit tests for
   the controller and exception handler, then a real Postgres integration test (Testcontainers),
   then a real S3 integration test (LocalStack), then a full HTTP + Spring Security integration
   test that mints its own JWT and hits real endpoints.
5. **Wired in the security scan** — SpotBugs/FindSecBugs bound to `mvn verify` (fast, offline);
   OWASP Dependency-Check declared but deliberately not bound to a lifecycle phase (see below).

## Failure modes worth naming

Some of these are bugs the tests caught in the *existing* code; some are friction the agent hit
in its own tooling choices. Worth telling apart:

**A real production bug the unit tests structurally could not have found.** `signUpMember` never
linked a new member's address back to the member before saving — `updateMember` did this, signup
never did. `AddressEntity` shares its primary key with `MemberEntity` via `@MapsId`, which
Hibernate derives from that link. Every unit test mocks the repository, so this path was never
exercised; every signup that included an address has always crashed with a 500
(`IdentifierGenerationException: attempted to assign id from null one-to-one property`) in the
original code, completely invisible until the full end-to-end integration test — real Postgres,
real cascade behaviour — actually ran it. This is the single strongest argument in this repo for
why mocked-repository unit tests and a real integration test are both necessary, not either/or.

**Tooling friction that took real diagnosis, not a first-try success:**
- Bumping to Boot 2.2.13 got JUnit 5, but Mockito immediately failed with
  `NoClassDefFoundError: GraalImageCode` on every mock creation — Boot 2.2's managed Mockito
  predates JDK 17+ bytecode support (this machine runs JDK 21). Overriding `mockito.version`
  wasn't enough by itself: Boot's BOM still pinned `byte-buddy` to a version too old for the newer
  Mockito, so both had to be overridden together.
- Testcontainers failed outright with "Could not find a valid Docker environment" against Docker
  Engine 29 — actually a version-negotiation mismatch (docker-java defaulting to API 1.32, which
  Engine 29 rejects outright, min 1.40), not a real Docker problem. Neither a `DOCKER_API_VERSION`
  environment variable nor a Surefire-injected system property reached the check that mattered;
  only a `docker-java.properties` file on the test classpath did. Confirmed via a live web search,
  not guessed.
- The end-to-end test's first two failures were a self-inflicted test-authoring bug, not a product
  bug: `Member.email` and `Member.password` are `@JsonProperty(access = WRITE_ONLY)` so they never
  leak into API responses — but that annotation also strips them when a test HTTP client
  serializes a `Member` instance as an *outgoing* request body, since Jackson applies the same
  rule regardless of direction. The fix was sending a plain `Map` as the signup payload instead —
  what an actual JSON client's request body looks like.

**A finding that led nowhere, on purpose.** SpotBugs also flagged nine `EI_EXPOSE_REP`/
`EI_EXPOSE_REP2` warnings on the JPA entities — "exposes internal representation" for returning
an association by reference. "Fixing" these by defensively copying would have broken Hibernate's
lazy-loading and dirty-checking, which depend on identity, not value equality. Documented and
suppressed in `member-service/spotbugs-exclude.xml` rather than silently ignored or blindly
"fixed" into a worse state.

## Phase 2: hardening the four tested services

Once every service that could reasonably be tested had a real suite, the next pass made the
system look like something that could actually run in production, not just pass tests:

- **A circuit breaker on a real failure mode.** `member-service`'s signup flow calls back into
  `api-gateway`/`auth-service` to mint the new member's token. That call now goes through
  `AuthenticationServiceClient` — a Resilience4j `@CircuitBreaker` plus a Feign-level timeout —
  instead of the raw Feign client. The member row is already committed by the time this runs, so
  the fallback returns 202 with no token ("your account exists, log in separately") rather than a
  500 that would wrongly imply signup itself failed. The integration test proves the circuit
  actually *opens and short-circuits*, not just that one failure returns a fallback.
- **A shared error handler that turned out to have a real, hidden bug.** `common`'s
  `CustomizedResponseEntityExceptionHandler` was silently only active in `member-service` — the
  other three services never scanned `com.investorbook.common`, so it did nothing there. Wiring it
  in surfaced a genuine, pre-existing defect: its `Exception.class` catch-all was intercepting
  `AccessDeniedException` *before* Spring Security's own filter could turn it into a 403, so every
  `@PreAuthorize` denial was silently reported as a 500 — in every service, `member-service`
  included, just never caught there because nothing had tested a wrong-role request. Fixed with
  narrower handlers that rethrow security exceptions instead of swallowing them.
- **Validation that was declared but never enforced.** `api-gateway`'s `/login` took an
  `AuthRequest` with `@NotNull`/`@Size` constraints already on the DTO, but the controller method
  never had `@Valid` — so they were silently ignored. Now it does, and a missing field gets a
  proper 400 in the same shared error shape as everything else.

## Observability

Every service now exposes `/actuator/health`, `/actuator/info`, and `/actuator/metrics` without
requiring the app's own JWT — a health-check probe or metrics scraper doesn't carry one. In a real
deployment these would live on a separate management port/network rather than the public one;
that split wasn't worth the added complexity for a demo-scale system. To wire this into an actual
observability stack: point Prometheus at each service's `/actuator/prometheus` (needs the
`micrometer-registry-prometheus` dependency added — not done here) on a scrape interval, and ship
each service's stdout/stderr through Filebeat or a Fluent Bit sidecar into an ELK stack, tagging
by `spring.application.name` so logs from all five services land in one searchable index.
`api-gateway` and `member-service` already emit Sleuth-correlated trace/span IDs in every log
line (`[api-gateway,traceId,spanId,exportable]`), which is what makes a single request traceable
across service boundaries in that same ELK index — extending that to `auth-service` and
`resource-service` would just mean adding `spring-cloud-starter-sleuth` there too.

## Config & secrets

Every secret that used to sit as a literal in `application.properties` — the Postgres password,
the RSA JWT keypair, the OAuth2 client secret (plaintext in `api-gateway`, its BCrypt hash in
`auth-service`) — is now `${ENV_VAR:same-value-as-before}`, so nothing's behavior changed by
default, but every one of them is overridable without touching a file. In a real deployment, none
of these would have a literal fallback at all: the keypair and client secret would come from a
secrets manager (AWS Secrets Manager, HashiCorp Vault, or a Kubernetes `Secret` mounted as an
env var), rotated independently of a deploy, and the RSA keypair itself would be generated per
environment rather than the same demo pair checked into five `application.properties` files.

## Event-driven purchase flow

The next addition after hardening the four core services: a choreographed saga (order-service →
payment-service → invoice-service → notification-service, each reacting only to the event before
it, no central coordinator) over Kafka, demonstrating the distributed-systems concepts the rest of
the repo doesn't touch:

- **A real compensating action, not just a happy-path chain.** A declined (mocked, deterministic)
  payment publishes `PaymentFailed`, and `order-service` cancels the order rather than leaving it
  stuck. That's the difference between an event chain and an actual saga — a reviewer will ask
  "what happens when payment fails after the order is placed," and there's a real, tested answer.
- **Idempotent consumers, two different ways.** `order-service` already has an order's status to
  guard on, so a redelivered event that would repeat an already-applied transition is just a
  no-op. The other three services don't have that state, so each keeps its own `processed_events`
  table keyed by event id instead. Both are proven against a real *duplicate delivery* in tests,
  not asserted from the design alone.
- **Eventual consistency, stated plainly.** There's a real window where an order is `PLACED` but
  not yet `PAID` — that's correct for this domain, not a bug to hide.
- Payment and email are both deliberately mocked (a threshold-based decision, and `GreenMail` — a
  fake SMTP server — in tests instead of a real mail relay) so the thing being demonstrated is the
  event-driven orchestration itself, not a payment or email integration.

See `CLAUDE.md`'s "Event-driven purchase flow" section for the full design (topics, event
payloads, the `Persistable`-with-`isNew()`-true trick the dedupe tables need, and a real
Testcontainers gotcha — `KafkaContainer` defaults to embedded-Zookeeper mode and needs
`.withKraft()` called explicitly, or every client disconnects about 10 seconds in).

## What's covered, and what honestly isn't

- **Tested**: every module except `eureka-server` — `member-service`, `auth-service`,
  `resource-service`, `api-gateway`, the shared `common` library, and all four purchase-flow
  services (`order-service`, `payment-service`, `invoice-service`, `notification-service`). Unit
  tests, real Postgres/S3/Kafka (Testcontainers/LocalStack) integration tests, full HTTP+security
  end-to-end tests, a circuit-breaker integration test (`member-service`), and a real-SMTP-via-
  GreenMail integration test (`notification-service`). `mvn verify` is green on all nine.
- **Not tested**: `eureka-server` only — it's the naming server with no custom logic of its own,
  so there's nothing here to write a meaningful test against.
- **Security scan**: SpotBugs/FindSecBugs is wired into every module's `mvn verify` and clean —
  member-service had it from the start; wiring it into the other eight in one pass turned up a
  real, if small, batch of genuine bugs (a mutable `Date` returned by reference from the shared
  error-response DTO every service uses, `String.getBytes()` relying on the JVM's default
  platform encoding to Base64-encode an OAuth2 client secret, a silently-ignored `File.delete()`
  failure, a couple of CRLF-log-injection gaps on values that genuinely originate from Kafka
  message payloads) alongside a few deliberate design choices (CSRF disabled on stateless
  bearer-token APIs, `KafkaTemplate` stored by reference) that got documented with a reason
  instead of "fixed" into something worse. See `CLAUDE.md`'s "Security scanning" section for the
  full list, including the one genuine FindSecBugs tool limitation found along the way (it
  doesn't verify a custom sanitizer method through a varargs logging overload — confirmed by
  re-running with only that one fix in place before concluding it wasn't a real gap). OWASP
  Dependency-Check is declared but has never actually completed a run in this environment —
  without an NVD API key, its first sync ran for the better part of half an hour, started hitting
  rate-limit retries, and was still under 15% through the feed when I stopped it. It's a
  documented follow-up (run it with `NVD_API_KEY` set, or from CI), not a finished result.
- **No CI yet.** Nothing here has run anywhere but this machine.
- **Demo-scale, not production-scale, on purpose**: one Postgres instance shared by every service
  that needs one (no per-service database isolation), a single hardcoded demo RSA keypair
  (env-overridable, but the same pair ships as everyone's fallback), no real payment or email
  integration (both mocked, by design — see above), and no C4 diagrams or ADRs yet (deliberately
  next, now that there's a system worth documenting rather than a thin demo).

## Running it

See [`CLAUDE.md`](CLAUDE.md) for build commands, the service startup order, and what each module
actually does.
