# InvestorBook

A Spring Cloud microservices demo — service discovery (Eureka), an OAuth2/JWT authorization
server, an API gateway, and a member-profile service with S3-backed picture upload — modeling a
social network for investors. Five independently deployable services plus a shared library, no
parent POM, each built and run on its own. See [`CLAUDE.md`](CLAUDE.md) for the full architecture,
security model, and per-module quirks.

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

## What's covered, and what honestly isn't

- **Tested**: `member-service` only — unit tests, a real Postgres integration test, a real S3
  (LocalStack) integration test, and a full HTTP+security end-to-end test. 21 tests, all green.
- **Not tested**: `auth-service`, `api-gateway`, `resource-service`, `eureka-server` — untouched
  from their original generated state, still with no coverage beyond the default
  `contextLoads()` smoke test (or none at all, for `auth-service`).
- **Security scan**: SpotBugs/FindSecBugs is wired into `member-service`'s `mvn verify` and clean.
  OWASP Dependency-Check is declared but has never actually completed a run in this environment —
  without an NVD API key, its first sync ran for the better part of half an hour, started hitting
  rate-limit retries, and was still under 15% through the feed when I stopped it. It's a
  documented follow-up (run it with `NVD_API_KEY` set, or from CI), not a finished result.
- **No CI yet.** Nothing here has run anywhere but this machine.

## Running it

See [`CLAUDE.md`](CLAUDE.md) for build commands, the service startup order, and what each module
actually does.
