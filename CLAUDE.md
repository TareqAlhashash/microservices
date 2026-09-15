# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

"InvestorBook" — a Spring Cloud microservices demo (originally Spring Boot 2.0.2, Spring Cloud
Finchley, Java 8) modeling a social network for investors. See [`README.md`](README.md) for the
portfolio-hardening narrative (what changed, why, and what's still honestly not done);
per-endpoint field docs for planned features live in `api-gateway/requirements-member.txt` and
`api-gateway/requierments-security.txt` (note the typo in the filename — don't "fix" it without
checking for other references).

Nine independently deployable Maven modules (the five original services plus `order-service`,
`payment-service`, `invoice-service`, and `notification-service` — a new event-driven purchase
flow, see "Event-driven purchase flow" below) plus one shared library, each with its own
`pom.xml`. There is no parent/reactor POM — modules are built and run separately, and every
module except `eureka-server` has test coverage (see below); `eureka-server` is still exactly as
originally generated.

## Commands

Prefer the global `mvn` over any module's `mvnw`/`mvnw.cmd` wrapper — `common` doesn't even have
one, and `member-service`'s wrapper is pinned to Maven 3.5.3, which is too old for the security
plugins wired into that module (they require 3.6.3+). There is no root build:

```bash
cd <service-dir> && mvn clean install     # build one service
cd <service-dir> && mvn test              # test one service
cd <service-dir> && mvn spring-boot:run   # run one service
```

`common` is a shared library (not a runnable service) consumed by `api-gateway` and `member-service`
via `com.investorbook:common:0.0.1-SNAPSHOT`. Build and `install` it into the local `.m2` repo before
building either of those, since there is no multi-module reactor to sequence it automatically:

```bash
cd common && mvn clean install
```

### Testing status per module (verified, not assumed)

Each module's real, checked test status on this machine (Windows, JDK 21, Docker Desktop
available). Don't assume `mvn test`/`mvn verify` works the same way across modules — it doesn't.

| Module | `mvn test` | `mvn verify` | Notes |
|---|---|---|---|
| `member-service` | **13 unit tests, no Docker** | +13 integration tests, **needs Docker** | See below |
| `auth-service` | **5 unit tests, no Docker** | +8 integration tests, **needs Docker** | See below |
| `api-gateway` | **2 unit tests, no Docker** | +5 integration tests, no Docker needed | See below |
| `eureka-server` | **fails on JDK 17+** | same failure | See "JDK 21 incompatibility" below |
| `resource-service` | **1 unit test, no Docker** | +4 integration tests, no Docker needed | See below |
| `common` | **5 unit tests, no Docker** | same | Library; see "Shared error handling" below |
| `order-service` | **9 unit tests, no Docker** | +4 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `payment-service` | **3 unit tests, no Docker** | +3 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `invoice-service` | **2 unit tests, no Docker** | +2 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `notification-service` | **3 unit tests, no Docker** | +2 integration tests, **needs Docker** | See "Event-driven purchase flow" below |

#### Shared error handling (common lib) — a real bug found while wiring it in

`common`'s `CustomizedResponseEntityExceptionHandler` (`@ControllerAdvice`, uniform
`{timestamp, message, details}` error body) was previously only active in `member-service` — the
other three services didn't scan `com.investorbook.common`, so its generic `Exception.class` →
500 and `@Valid` → 400 mapping simply didn't apply there. Each of `auth-service`,
`resource-service`, and `api-gateway`'s `*Application.java` now explicitly
`@Import(CustomizedResponseEntityExceptionHandler.class)` (`auth-service`/`resource-service`
needed the `common` dependency added for this — they didn't have it before). `api-gateway`'s
`LoginService.login` also gained `@Valid` on its `AuthRequest` parameter, so `AuthRequest`'s
existing `@NotNull`/`@Size` constraints are actually enforced now — previously they were declared
but silently ignored, since nothing bound the request with validation switched on.

Wiring the handler into `resource-service` immediately turned up a real, pre-existing bug (caught
by `ResourceServiceApiIT`'s wrong-role test going from 403 to 500): the handler's
`@ExceptionHandler(Exception.class)` catch-all was intercepting `AccessDeniedException` *inside*
the `DispatcherServlet`, before Spring Security's own `ExceptionTranslationFilter` ever got a
chance to turn it into a 403 — so every `@PreAuthorize` denial silently became a 500. Fixed by
adding narrower `@ExceptionHandler` methods for `AccessDeniedException` and
`AuthenticationException` that just rethrow, letting Spring's handler-resolution machinery prefer
the more specific match and the exception propagate to the security filter chain as normal. This
bug was **already live in `member-service` too** (inherited via `MemberResponseEntityExceptionHandler
extends CustomizedResponseEntityExceptionHandler`) — nothing there had ever tested a wrong-role
(as opposed to no-token) request against a `@PreAuthorize`'d endpoint. `MemberServiceApiIT` gained
`member_isForbidden_forAValidTokenWithoutTheMemberRole` to close that gap and prove the real fix.
`common` itself also has a `@Valid`/`BindException` override now (for `LoginService`'s implicit,
unannotated-parameter form binding, which fails with `BindException` rather than
`MethodArgumentNotValidException`), and got the same Boot 2.0.2 → 2.2.13/Hoxton.SR12 bump as the
other four modules specifically so this could be given real JUnit 5 test coverage
(`CustomizedResponseEntityExceptionHandlerTest`) for the first time.

#### member-service: unit vs. integration tests, and Docker

`member-service` splits tests by Maven's own naming convention rather than mixing everything into
Surefire: `*Test.java` (unit, mocked collaborators, no I/O) runs under Surefire during `mvn test`;
`*IT.java` (real Postgres via Testcontainers, real S3 via LocalStack, or a full HTTP+security
round trip) runs under Failsafe, bound to `integration-test`/`verify` only. This means **`mvn test`
never touches Docker**, on this module or any other — only `mvn verify` (or a direct
`mvn failsafe:integration-test`) does. If you add a new test class here, name it `*Test.java` for
a fast, mocked-collaborator test or `*IT.java` for one that needs a real Postgres/S3/HTTP round
trip — Surefire and Failsafe's default include patterns key off that suffix, so nothing else needs
touching to keep the split working.

```bash
cd member-service && mvn test                                     # 13 tests, seconds, no Docker
cd member-service && mvn verify                                   # +13 integration tests, needs Docker
cd member-service && mvn test -Dtest=MemberServiceControllerTest  # one unit test class
cd member-service && mvn failsafe:integration-test -Dit.test=MemberPersistenceIT  # one IT class
```

**Resilience**: `signUpMember`'s callback into api-gateway/auth-service (to mint the signup
response's token) goes through `AuthenticationServiceClient`, not the raw `AuthenticationServiceProxy`
Feign client directly — it wraps the call in a Resilience4j `@CircuitBreaker` (instance
`authenticationService`, config in `application.properties`: opens after a 50%+ failure rate over
a 4-call window) plus a Feign-level timeout (`feign.client.config.api-gateway.connectTimeout`/
`readTimeout`, 2s each). By the time this call runs the new member row is already committed, so
the fallback doesn't fail the whole request — it returns 202 with a token-less `AuthResponse`,
meaning "your account exists, log in separately." `AuthenticationServiceCircuitBreakerIT` proves
the circuit actually opens and short-circuits (not just "a failure returns a fallback"): it drives
4 failures (each still calling the real, mocked-failing proxy) then asserts a 5th call gets the
same graceful response *without* the proxy being invoked again. Needed an extra explicit
`resilience4j-spring:1.7.1` dependency alongside `resilience4j-spring-boot2:1.7.1` — Hoxton's
`spring-cloud-dependencies` BOM manages `resilience4j-spring` to an older `1.7.0`, and the
mismatch fails at context-startup time (`NoSuchMethodError` on `SpelResolverConfiguration.
spelResolver`), not at build time.

If Testcontainers fails with "Could not find a valid Docker environment" on a 400 rather than a
connection error, it's very likely the Docker-Engine-29+-vs-old-docker-java API version mismatch,
not a real Docker problem — see `member-service/src/test/resources/docker-java.properties`, which
already pins `api.version=1.44` for exactly this reason. Neither a `DOCKER_API_VERSION` env var nor
a Surefire-injected system property reaches this check; only that properties file does.

`mvn verify` also runs SpotBugs + FindSecBugs (bound to the `verify` phase, fast, fully offline —
it runs regardless of whether Docker is available). Findings are triaged in
`member-service/spotbugs-exclude.xml` with documented reasons — extend that file rather than
adding a bare `@SuppressFBWarnings` with no justification, and never suppress a new finding
without writing down why it's not a real issue. OWASP Dependency-Check is declared in the pom too
but deliberately **not** bound to a lifecycle phase: without an `NVD_API_KEY` its first NVD sync
can take hours under current rate limits. Run it explicitly when you have a key:

```bash
NVD_API_KEY=<key> mvn org.owasp:dependency-check-maven:check
```

#### auth-service: same Boot bump, a different JDK 21 fix that actually worked

`auth-service` got the same Spring Boot 2.0.2 → 2.2.13 / Hoxton.SR12 bump as member-service, for
the same reason (JUnit 5) plus a second one: it's the security core, so it's the highest-value
service to test. Same `*Test`/`*IT` split, same Docker API pin in
`auth-service/src/test/resources/docker-java.properties`.

```bash
cd auth-service && mvn test                                  # 5 tests, seconds, no Docker
cd auth-service && mvn verify                                 # +8 integration tests, needs Docker
```

Any `@SpringBootTest` here hits a JDK 21 failure too, but a different root cause from the one
below: `@EnableAuthorizationServer`'s `AuthorizationServerSecurityConfigurer` eagerly builds a
JAXB-based error message converter (unused — this app only speaks JSON), and jaxb-impl's
reflective accessor optimization needs `ClassLoader.defineClass` open on JDK 16+, otherwise
context startup fails with an NPE deep inside `jaxb-impl`'s `Injector`. Unlike the cglib case
below, `--add-opens java.base/java.lang=ALL-UNNAMED` **does** fix this one — it's wired into both
the Surefire and Failsafe plugin configs in `auth-service/pom.xml`, so `mvn test`/`mvn verify` just
work without passing it manually. `AuthServiceTokenIT` proves the real OAuth2 password-grant flow
end to end against a real Postgres member row: token issuance with a validly-signed, verifiable
JWT; `invalid_grant` (400) for a wrong password or unknown user; a 401 (no JSON body — Spring
Security's own Basic-auth entry point rejects an unrecognized client before OAuth2's error
rendering runs) for an unknown client; `token_key` being publicly readable; and `check_token`
requiring a trusted client's credentials.

#### resource-service: same Boot bump, no JDK 21 test workaround needed

`resource-service` got the same Boot 2.0.2 → 2.2.13 / Hoxton.SR12 bump, replacing its single
generated JUnit 4 `contextLoads()` smoke test (which hit the cglib failure documented below).
Unlike `auth-service`, it's a plain `@EnableResourceServer` (not an authorization server), so it
never builds the JAXB-based error converter and needs no `--add-opens` at all — `mvn test`/`mvn
verify` just work. One catch the bump surfaced: Hoxton's `spring-cloud-starter-security` no longer
pulls `spring-security-oauth2` transitively (member-service gets it via the `common` lib instead;
this module didn't depend on `common` at the time), so `spring-cloud-starter-oauth2` had to be
added directly, matching what `auth-service` already declares — without it, `JwtConvertor.java`'s
existing `OAuth2Authentication`/`DefaultAccessTokenConverter` usage doesn't even compile under
Hoxton. (`common` was added as a dependency later, for the shared error handler — see "Shared
error handling" above.)

`ResourceServiceApiIT` mints JWTs against a test signing key (no real dependency to exercise via
Testcontainers here) and proves the actual filter chain: no token → 401, a valid token without
`ROLE_MEMBER` → 403 (`@PreAuthorize` actually enforced, not just present in source), a valid token
with the role → 200. `JwtConvertorTest` covers the claims-map-as-details behaviour `JwtUtil.getEmail`
relies on downstream (see member-service's `MemberServiceController`).

```bash
cd resource-service && mvn test                                 # 1 test, seconds, no Docker
cd resource-service && mvn verify                                # +4 integration tests, no Docker needed
```

#### api-gateway: same Boot bump, plus a real (not just test-time) runtime bug found and fixed

`api-gateway` got the same Boot 2.0.2 → 2.2.13 / Hoxton.SR12 bump (it was on
`Finchley.BUILD-SNAPSHOT`, a snapshot train, before this). It's a resource server like
`resource-service`, so it hits the same JAXB/`jaxb-impl` issue as `auth-service` — the
`--add-opens` flag is wired into Surefire and Failsafe here too. `LoginService` was switched from
field to constructor injection while adding its test; its `@Bean JwtAuthenticationConfig` factory
method had to become `static` to avoid a circular self-dependency (the only bean definition for a
constructor parameter can't depend on an instance of the bean being constructed) — a static
`@Bean` method is invoked without needing an instance of its declaring class first.

Actually running the bumped app standalone (not just its tests) surfaced a genuine dependency
version mismatch that the test suite couldn't have caught, since both this module's and
member-service's tests `@MockBean` away the Feign client that would trigger it:
`spring-cloud-openfeign-core:2.2.9` (pulled in by Hoxton) transitively needs `feign-form-spring`
built against `feign-form:3.8.0`, but both modules explicitly pinned `feign-form:3.3.0` (correct
under the old Finchley/2.0.2 stack, stale after the bump) — Maven's nearest-wins mediation picked
the stale pin, and the first real Feign form-encode call failed with `NoSuchMethodError` on
`MultipartFormContentProcessor.addFirstWriter`. Fixed by bumping the pin to `3.8.0` in both
`api-gateway/pom.xml` and `member-service/pom.xml`. The same standalone run also confirmed
`mvn spring-boot:run` itself needs `--add-opens` on JDK 21 (not just the test JVMs) — added to
`spring-boot-maven-plugin`'s `jvmArguments` in `api-gateway/pom.xml` (and worth doing for
`auth-service` too if it's ever run directly rather than via an IDE/JDK-8 launch).

`LoginServiceTest` (unit, mocked `OauthServiceProxy`) proves the Basic-auth header assembly that
keeps the OAuth2 client secret off the wire to browser/mobile clients. `ApiGatewaySecurityIT`
(real HTTP + filter chain, `OauthServiceProxy` mocked since auth-service isn't running in the
test) proves default-deny (401 without a token), token acceptance (a valid bearer token is not
rejected by the gateway's own security layer), that `/login` bypasses that security layer
entirely so a client can obtain a token in the first place, and (once `@Valid` was added to
`LoginService.login`, see "Shared error handling" above) that a missing field gets a 400 in the
shared error shape. Deliberately does **not** test
`/uaa/oauth/token` or `/member-service/signup` the same way: both are pure Zuul-proxied routes
with no controller of their own in this app, and with Eureka disabled (as in every test here)
Zuul can't resolve them, forwarding internally to an error dispatch that produces a 401 for
reasons unrelated to the ignore-list — a real quirk of testing Zuul routes without a running
Eureka, not a gap in what `/login` already proves about the ignore-list mechanism.

```bash
cd api-gateway && mvn test                                      # 2 tests, seconds, no Docker
cd api-gateway && mvn verify                                     # +5 integration tests, no Docker needed
```

#### JDK 21 incompatibility in the untouched service

`eureka-server` still fails its single generated `contextLoads()` smoke test on this JDK with the
same root cause:

```
IllegalStateException: Cannot load configuration class: PropertySourceBootstrapConfiguration
Caused by: ExceptionInInitializerError
Caused by: CodeGenerationException: InaccessibleObjectException: Unable to make protected final
  java.lang.Class java.lang.ClassLoader.defineClass(...) accessible: module java.base does not
  "opens java.lang" to unnamed module
```

Spring 5.0.6 (pulled in by Boot 2.0.2) generates cglib proxies via reflection on
`ClassLoader.defineClass`, which the JPMS module system blocks from Java 16 onward without an
explicit `--add-opens`. A quick `-DargLine="--add-opens java.base/java.lang=ALL-UNNAMED"` did
**not** resolve it in a direct check — this needs either JDK 8/11 (what Boot 2.0.2 actually
targets and was never validated past) or the same kind of Boot version bump the other modules got
(see "Why member-service, auth-service, resource-service, api-gateway, and common are on a
different Boot version"). Confirmed by actually running its tests, not inferred from the version
number alone.

### Running the full system locally

Start order matters because services register with and discover each other through Eureka:

1. `eureka-server` (port **8761**) — naming server, dashboard at `http://localhost:8761/`
2. PostgreSQL reachable at `jdbc:postgresql://localhost/investorbook` (user `postgres` / pass `pass`)
   — required by `auth-service`, `member-service`, and all four purchase-flow services
   (`spring.jpa.hibernate.ddl-auto=update`, so schema is created/updated automatically, no
   migration tool)
3. `auth-service` (port **9100**, context path `/uaa`) — OAuth2/JWT authorization server
4. `member-service` (port **8100**)
5. `resource-service` (port **9200**) — currently just a `/hi` smoke-test endpoint
6. `api-gateway` (port **8765**) — Zuul edge router, the only service meant to be called externally
7. Kafka reachable at `localhost:9092` (`docker compose up -d` at the repo root) — required by the
   purchase-flow services: `order-service` (port **8200**), `payment-service` (port **8300**),
   `invoice-service` (port **8400**), `notification-service` (port **8500**); see "Event-driven
   purchase flow" below. Order doesn't matter between these four beyond Kafka/Postgres being up
   first - they only talk to each other over Kafka, never directly.

`api-gateway`, `eureka-server`, and `resource-service` still only have the default Spring Boot
`contextLoads()` smoke test generated by `start.spring.io`.

## Architecture

### Service responsibilities

- **eureka-server** — Netflix Eureka naming/discovery server. Nothing else registers with it as a
  peer (`eureka.client.register-with-eureka=false`).
- **auth-service** — Spring Cloud OAuth2 **authorization server** (`@EnableAuthorizationServer`).
  Issues JWTs signed with an RSA keypair hardcoded in `application.properties`. Authenticates
  against the `members` Postgres table via `MemberDetailsService` (implements Spring's
  `UserDetailsService`), using its own minimal `MemberEntity` (id/email/passwordHash only — a
  separate class from `member-service`'s richer entity of the same name, kept intentionally
  decoupled). Grants roles via `GrantedAuthorities` (`NORMAL_USER` → `ROLE_MEMBER`, `PREMIUM_USER`
  adds `ROLE_PREMIUMMEMBER`, `ADMIN` adds `ROLE_ADMIN`), though only `NORMAL_USER` is ever assigned
  today.
- **member-service** — the main business service: signup, profile get/update, and profile-picture
  upload/download (delegating storage to S3 via `common`'s `ProfilePictureStorage`). Owns
  `MemberEntity` (JPA, Postgres) with a `@OneToOne` `AddressEntity` sharing its primary key via
  `@MapsId`. `@EnableResourceServer` protects everything except `/signup` (opened via
  `WebSecurity.ignoring()`), with method-level `@PreAuthorize("hasRole('MEMBER')")` on the rest.
  **Signup is a two-hop flow**: `MemberServiceController.signUpMember` saves the new member (linking
  `address.setMember(member)` first — required for the `@MapsId` cascade to work at all), then calls
  back out to `api-gateway`'s `/login` through `AuthenticationServiceClient` (a circuit-breaker-
  and-timeout-wrapped `AuthenticationServiceProxy` Feign client — see "Resilience" under member-service's
  testing section) to obtain a token, so a client only calls member-service once and gets a JWT
  back. See "Why member-service, auth-service, resource-service, api-gateway, and common are on a
  different Boot version" below for why this and three other modules (plus `common`) are on
  Spring Boot 2.2.13 / JUnit 5 while `eureka-server` isn't.
- **resource-service** — skeletal `@EnableResourceServer` example service (single `/hi` endpoint) —
  a template for adding new protected microservices, not a real feature.
- **api-gateway** — Zuul (`@EnableZuulProxy`) reverse proxy and single external entry point.
  `SecurityConfiguration` requires authentication on every route except `/login`,
  `/uaa/oauth/token`, and `/member-service/signup` (matched via Zuul's routing prefix). Exposes
  `POST /login` (`LoginService`), which base64-encodes the `html5` OAuth client credentials and
  forwards the user's username/password to `auth-service`'s token endpoint via
  `OauthServiceProxy` (Feign) — so browser/mobile clients never see the OAuth client secret.
  `ZuulLoggingFilter` logs every proxied request (`pre` filter, order 1).
- **common** — shared library, not a service. Holds cross-cutting pieces every module reuses:
  - `dto/AuthRequest`, `dto/AuthResponse` — the OAuth2 password-grant request/response shape shared
    between api-gateway and auth-service.
  - `util/JwtUtil` — pulls the authenticated user's email (`user_name` claim) off the Spring Security
    `Authentication` via `OAuth2AuthenticationDetails`; this is how resource servers identify "the
    current user" from a decoded JWT without a separate lookup.
  - `util/CoreFeignConfiguration` — registers a form-encoding `Encoder` so Feign clients can POST
    `application/x-www-form-urlencoded` bodies (needed for the OAuth2 token endpoint).
  - `util/EncryptionUtil` — PBKDF2WithHmacSHA512 password hashing helper (currently unused in favor
    of `BCryptPasswordEncoder` in `auth-service`/`member-service` — check before assuming it's live).
  - `aws/ProfilePictureStorage` + `aws/S3Config` — profile-picture upload/presigned-URL helper,
    constructor-injected with an `AmazonS3` client (so it's testable against LocalStack) rather than
    the static, eagerly-initialized utility it used to be. `S3Config` is a plain `@Configuration`
    class living outside the consuming service's component-scan root, so it must be pulled in
    explicitly with `@Import(S3Config.class)` (member-service's `MemberServiceApplication` does
    this) — it won't be picked up by scanning alone.
  - `exception/CustomizedResponseEntityExceptionHandler` — a `@ControllerAdvice` mapping any
    uncaught exception to 500 and `MethodArgumentNotValidException`/`BindException` (the two
    shapes a failed `@Valid` can take) to 400, with a uniform `{timestamp, message, details}`
    body throughout. Explicitly rethrows `AccessDeniedException`/`AuthenticationException`
    rather than handling them — see "Shared error handling" for the real 403-became-500 bug that
    omission caused. Every service now `@Import`s this (`member-service` gets it transitively via
    its own `MemberResponseEntityExceptionHandler extends` it instead).

### Security model (the architectural throughline)

This is an OAuth2 **password grant + JWT** setup, not session-based auth:

1. A client sends username/password to `api-gateway`'s `POST /login`.
2. `api-gateway` adds the `html5` client's Basic-auth credentials and forwards to `auth-service`'s
   `/uaa/oauth/token` (Spring's standard OAuth2 token endpoint).
3. `auth-service` validates the user against Postgres, issues a JWT signed with its private RSA key
   (`investorbook.security.jwt.private.key`) containing the `user_name` claim and granted roles.
4. Every other service (`api-gateway`'s protected routes, `member-service`, `resource-service`) is
   an `@EnableResourceServer` that verifies the same JWT using the **public** key
   (`security.oauth2.resource.jwt.key-value` / `investorbook.security.jwt.public.key`) —
   the identical PEM string is duplicated across every `application.properties` file as the
   fallback of a `${JWT_PUBLIC_KEY:...}` placeholder (same pattern for the DB password
   `${DB_PASSWORD:...}` and the html5 client secret `${HTML5_CLIENT_SECRET:...}` /
   `${HTML5_CLIENT_SECRET_HASH:...}` — see "Config & secrets" below). Changing the keypair for
   real still means updating every service's env var in lockstep; the placeholder only removes
   the "it's a literal in source" problem, not the duplication itself.
5. All resource servers are `SessionCreationPolicy.STATELESS` — no server-side session state
   anywhere; authorization is entirely re-derived from the JWT on each request.

Each service's own `SecurityConfiguration` class governs its own routes; there is no shared/inherited
security config module, so a newly added service must supply its own (`resource-service` is the
template for this). `member-service`'s `MemberServiceApiIntegrationTest` proves this chain actually
works end-to-end (401 with no token, 404 with a valid token and no matching account, `@PreAuthorize`
enforced) by minting a JWT directly against a test signing key — see that test's class Javadoc for
why it doesn't depend on `auth-service` being up.

### Config & secrets

Every literal secret that used to sit directly in an `application.properties` value is now
`${ENV_VAR:same-literal-as-before}` — the fallback preserves today's behaviour exactly (no env
var set anywhere in dev/test), while a real deployment overrides it: `DB_USERNAME`/`DB_PASSWORD`
(`auth-service`, `member-service`), `JWT_PUBLIC_KEY` (all four resource-server-side services),
`JWT_PRIVATE_KEY` (`auth-service` only, since only it signs), `HTML5_CLIENT_SECRET`
(`api-gateway`'s plaintext copy) and `HTML5_CLIENT_SECRET_HASH` (`auth-service`'s BCrypt copy of
the *same* secret — two different env vars because one is a hash and one isn't). The comment
that used to sit right above `auth-service`'s BCrypt hash revealing its plaintext
(`#html5secretpass123`) is gone; that line existed purely to defeat the point of hashing it. No
`.env`/secrets-manager integration is wired in here — see the README's "Config & secrets" section
for what a real deployment would use instead.

### Observability

`spring-boot-starter-actuator` is on every service's classpath (transitively, via
`spring-cloud-starter-netflix-eureka-client`, which needs it for Eureka's own health-check
integration — nothing had to declare it explicitly). `management.endpoints.web.exposure.include=
health,info,metrics` is now set explicitly in every service (previously whatever Boot's own
default was), and each service's `SecurityConfiguration` adds `/actuator/**` to its
`WebSecurity.ignoring()` list — deliberately unauthenticated, since a health-check probe or
metrics scraper doesn't carry this app's own bearer token. In a real deployment these would sit
on a separate management port/network instead of the public one (`management.server.port`); not
done here to keep the demo's moving parts down. Every touched service's IT suite has a plain
`actuatorHealth_isReachableWithoutAToken` test proving the carve-out actually works, not just
that the property is set.

### Security scanning (Phase 3): SpotBugs + FindSecBugs + OWASP Dependency-Check on every module

Every module except `eureka-server` now has the same two static-analysis/CVE-scan plugins
member-service originally pioneered, bound the same way: SpotBugs+FindSecBugs bound to `verify`
(fast, offline, so it runs on every build), OWASP Dependency-Check declared but deliberately
**not** bound to a lifecycle phase (see member-service's "unit vs. integration tests" section
above for why — the first NVD sync without an API key is too slow to be a build-blocking
default). Each module has its own `spotbugs-exclude.xml`; every triage entry names the specific
class/method and states a reason, never a blanket suppression.

Running this across eight modules that had never been scanned before turned up a genuinely mixed
set of findings — some real bugs worth fixing, some deliberate design choices worth naming
instead of "fixing":

- **Real fixes made**: a missing `serialVersionUID` (`auth-service`'s `InvestorBookUser`); a
  `File.delete()` return value silently ignored, meaning a failed cleanup would leave a temp
  upload file on disk forever (`common`'s `ProfilePictureStorage`); `Date` fields returned/stored
  by reference in `common`'s `ExceptionResponse` (a shared value type touched by every service's
  error responses) — fixed with defensive copies rather than suppressed, since `Date` is
  genuinely mutable and the fix is two lines; `EncryptionUtil.hash` catching bare `Exception` when
  only `NoSuchAlgorithmException`/`InvalidKeySpecException` are actually possible; reliance on
  the JVM's default platform encoding in `api-gateway`'s `LoginService` (`String.getBytes()` with
  no explicit charset when Base64-encoding the OAuth2 client's Basic-auth header — a real
  portability footgun, fixed with an explicit `UTF_8`); a non-locale-aware `toUpperCase()` on a
  generated invoice number in `invoice-service` (fixed with `Locale.ROOT`, since it's uppercasing
  hex characters in an identifier, not user-facing text); and CRLF-log-injection findings on every
  Kafka listener that logged an event id or order id without sanitizing it first (`order-service`,
  `payment-service`, `invoice-service`, `notification-service` — the *fields themselves* are
  legitimately attacker-influenced if a producer were ever compromised, unlike the one
  false-positive case below) plus one in `api-gateway`'s `ZuulLoggingFilter` logging a raw request
  URI.
- **Triaged as deliberate, not suppressed blind**: `SPRING_CSRF_PROTECTION_DISABLED` on
  `auth-service` and `api-gateway`'s `SecurityConfiguration` — both are stateless, bearer-token
  APIs (`SessionCreationPolicy.STATELESS`); CSRF exploits rely on a browser automatically
  attaching a *cookie/session* to a forged cross-site request, and there is no cookie/session
  here for one to ride along on. `EI_EXPOSE_REP2` on every class that constructor-injects a
  `KafkaTemplate` (a Spring-managed connection/client object, not a value type — there is nothing
  meaningful to "defensively copy") and on `api-gateway`'s `LoginService` storing its
  `JwtAuthenticationConfig` (a `@Value`-populated config bean, populated once at startup and never
  mutated in this app's actual usage).
- **A genuine tool limitation, documented rather than worked around further**:
  `order-service`'s `OrderEventListener.transitionIfExpected` still trips `CRLF_INJECTION_LOGS` on
  its 3-arg `logger.info(String, Object...)` call *after* the exact same `sanitizeForLog(String)`
  fix that resolved the identical finding on the 2-arg `logger.warn` two lines above — confirmed
  by re-running with only that one fix in place. FindSecBugs' taint tracker doesn't verify custom
  sanitizer methods through the varargs logging overload specifically; the value passed is
  provably always the sanitized one, so this one is a suppressed false positive, not a live risk.

Confirmed by actually running `mvn verify` on all nine modules after every fix — including
re-running `member-service` (unchanged, but `common` moved under it) and reinstalling `common`
into the local `.m2` before re-verifying every consumer — not assumed from a clean-looking diff.

### Why member-service, auth-service, resource-service, api-gateway, and common are on a different Boot version

`member-service` was bumped from Spring Boot 2.0.2 to **2.2.13** (Spring Cloud `Hoxton.SR12`)
specifically to get JUnit 5 as the default in `spring-boot-starter-test`, before any test suite was
written for it — Boot 2.0.2's bundled Surefire (2.21.0) predates JUnit Platform support entirely.
`auth-service`, `resource-service`, `api-gateway`, and (later still, for the same reason, once it
got its first-ever tests) `common` all got the identical bump. Only `eureka-server` remains
deliberately untouched: none of these modules are runnable services it shares a JAR with, so
there's no cross-module coupling to the version bump — if it ever gets its own test suite, expect
to hit the same overrides. Two follow-on overrides were needed on every bumped module to make the
JDK on this machine (21) actually work with the upgraded test stack: `mockito.version` (Boot 2.2's
managed Mockito predates JDK 17+ bytecode support) and, less obviously, `byte-buddy.version`
(Boot's BOM otherwise still pins byte-buddy to a version too old for the overridden Mockito, which
fails at mock-creation time with `NoClassDefFoundError`, not at build time). `common` also lost
its `spring-snapshots` repository declaration in the bump — it depended on it only for
`Finchley.BUILD-SNAPSHOT`, a moving-target snapshot train that `Hoxton.SR12` (a real release)
doesn't need.

### Naming and package quirks to know about

- Two independent `com.investorbook.<x>.security.SecurityConfiguration` classes exist (in
  `member-service` and `resource-service`) plus a differently-named `JwtConvertor` in several
  modules (including `order-service`) — same intent, not shared code, don't assume editing one
  affects another.
- `resource-service`'s package is `com.investorbook.resourceservice.secuirty` (misspelled) —
  intentional-looking but easy to typo again when adding files there.
- `auth-service` and `member-service` each define their own `MemberEntity` mapped to the same
  `members` table with different column subsets — by design (auth only needs id/email/password
  hash), not a duplication bug to merge. `auth-service`'s `MemberEntity` has no `@GeneratedValue`
  on its `String id`, and until `AuthServiceTokenIT` needed to seed one, there was no way to
  construct a persistable instance at all — the 3-arg constructor `(id, email, passwordHash)` was
  added for that.

## Event-driven purchase flow (order/payment/invoice/notification-service)

Phase 2.5 (built after the core services were hardened, before the C4/ADR documentation phase):
a choreographed saga on top of the same Eureka/OAuth2 stack, demonstrating event-driven
architecture, eventual consistency, and idempotent consumers — the distributed-systems concepts
the rest of this repo doesn't touch. **All four saga services are built.**

### The flow

`order-service` exposes `POST /orders` (JWT-protected like every other write endpoint here,
`@PreAuthorize("hasRole('MEMBER')")`, `customerEmail` taken from the token, never the request
body) and `GET /orders/{id}`. Placing an order persists it as `PLACED` and publishes `OrderPlaced`
to Kafka. From there, the chain is choreographed, not orchestrated - no central saga
coordinator, each service reacts only to the event before it:

```
order-service --OrderPlaced--> payment-service --PaymentSucceeded--> invoice-service --InvoiceIssued--> notification-service --OrderCompleted--> order-service
                                       \--PaymentFailed--> order-service (compensating: PAYMENT_FAILED)
```

`order-service` also consumes the three terminal events (`PaymentSucceeded`/`PaymentFailed`,
`InvoiceIssued`, `OrderCompleted`) to advance its own order's status: `PLACED` → `PAID` →
`INVOICED` → `COMPLETED`, or `PLACED` → `PAYMENT_FAILED` on the compensating path. See
`OrderStatus` for the full state enum.

`payment-service` has no REST API of its own - it only reacts to Kafka. Its `PaymentEventListener`
consumes `OrderPlaced` and makes a **mocked, deterministic** decision: orders at or above
`PaymentEventListener.DECLINE_THRESHOLD` ($1000.00, simulating a simple risk/fraud threshold) get
`PaymentFailed`; everything else gets `PaymentSucceeded`. The point is the event-driven
orchestration and the saga's failure path, not a real payment integration.

`invoice-service` also has no REST API - `InvoiceEventListener` consumes `PaymentSucceeded`,
persists a real `Invoice` row (`invoiceNumber` is just `"INV-" + 8 random hex chars`, not a
real sequential numbering scheme - a demo simplification worth naming if asked), and publishes
`InvoiceIssued`.

`notification-service` also has no REST API - `NotificationEventListener` consumes
`InvoiceIssued`, sends a completion email via `NotificationEmailSender` (real `JavaMailSender`
code with a text-file invoice attachment built inline from the event's own fields, not fetched
from `invoice-service`), and publishes `OrderCompleted`. `spring.mail.host`/`port` default to a
harmless `localhost:2525` placeholder that nothing listens on in a normal local run - **no real
mail server is ever wired in, by design** (see the plan this was built from). The email send is
wrapped in a try/catch that only logs a warning on failure: it's a best-effort side channel, not
a gate on the saga completing, specifically so a normal local run (no SMTP server configured)
doesn't leave every order stuck at `INVOICED` forever. Tests point `spring.mail.port` at
GreenMail (a fake SMTP server) instead, so the real sending code is genuinely exercised, not
bypassed - see `NotificationServiceIT`.

### Shared pieces (in `common`)

- `com.investorbook.common.event`: the five event classes (`OrderPlaced`, `PaymentSucceeded`,
  `PaymentFailed`, `InvoiceIssued`, `OrderCompleted`) plus `Topics` (the topic-name constants,
  one topic per event type — `order.placed`, `payment.succeeded`, `payment.failed`,
  `invoice.issued`, `order.completed`). Every producer sends keyed by order id
  (`kafkaTemplate.send(topic, orderId, event)`), so all events for one order land in the same
  partition and keep their relative order — this saga needs per-order ordering, not a global one.
  Every field an event carries is deliberately denormalized (e.g. `customerEmail`/`amount` repeated
  in every event) so a consumer never has to call back to an earlier service to act.
- Uses Spring Kafka's default `JsonSerializer`/`JsonDeserializer` with type headers (the default) -
  since every service depends on the same `common` event classes, no per-listener type
  configuration is needed beyond `spring.kafka.consumer.properties.spring.json.trusted.packages=
  com.investorbook.common.event`.

### Idempotency: two different strategies, deliberately

- **`order-service`**: a state-machine guard, not a dedupe table. Each listener method
  (`OrderEventListener`) only applies its transition when the order's *current* status matches the
  expected predecessor (e.g. `PAID → INVOICED` only fires if the order is currently `PAID`); a
  redelivered event finds the order already past that point and is a silent no-op. This works
  because `order-service` already has rich state to guard on.
  Proven by `OrderServiceApiIT.aRedeliveredPaymentSucceeded_doesNotDoubleProcess`, which sends the
  same `PaymentSucceeded` twice and asserts the order settles on `PAID` and stays there.
- **`payment-service`, `invoice-service`, `notification-service`**: a dedicated
  `processed_events` dedupe table (one per service, since each keeps its own), keyed by event id.
  Unlike `order-service`, these have no other state to guard on. The table's entity
  (`ProcessedEvent`) implements Spring Data's `Persistable` with `isNew()` hardcoded to `true` -
  without that, `save()` on an entity with an already-populated `@Id` does a merge (update-if-
  exists) rather than an insert, which would silently succeed on a duplicate id instead of
  surfacing the constraint violation this table exists to catch. The insert is flushed immediately
  (`saveAndFlush`, not deferred to end-of-transaction) so the duplicate is caught *before* any
  Kafka send - Kafka isn't transactional with this database, so a message already sent can't be
  un-sent if the DB write is later found to conflict.
  Proven by `PaymentServiceIT.aRedeliveredOrderPlaced_resultsInOnlyOnePaymentSucceeded`,
  `InvoiceServiceIT.aRedeliveredPaymentSucceeded_resultsInOnlyOneInvoice`, and
  `NotificationServiceIT.aRedeliveredInvoiceIssued_sendsOnlyOneEmailAndPublishesOrderCompletedOnlyOnce`,
  each publishing the same event (same event id) twice and asserting only one downstream effect
  (one published event, one persisted row, one sent email) comes out - not just one, but exactly
  one, proving the redelivery was actually detected and skipped rather than coincidentally not
  happening.

### Local infra and testing

`docker-compose.yml` at the repo root runs Kafka locally (single-node KRaft mode, no Zookeeper) -
the one piece of new infrastructure this flow needs; everything else still runs the way the rest
of this repo documents (`mvn spring-boot:run` against a locally-installed Postgres). Tests never
depend on the compose file - they use Testcontainers Kafka instead.

**Testcontainers' `KafkaContainer` (the `org.testcontainers.containers` one, from the `kafka`
module) defaults to embedded-Zookeeper mode unless `.withKraft()` is called explicitly** - without
it, a `confluentinc/cp-kafka:7.5.0` image starts and accepts initial connections, but every
client (producers and consumers alike) simultaneously disconnects and can't reconnect about 10
seconds in. Always call `.withKraft()` when constructing one directly from a `DockerImageName`, to
match how the local docker-compose Kafka runs.

`OrderServiceApiIT` covers: the happy path all the way to `COMPLETED` (order-service's own
producer and consumer sides — `payment-service`/`invoice-service`/`notification-service` aren't
running in this test, so it publishes their events itself, standing in for them; each of those
services proves its own reaction to its trigger event in its own suite instead - `PaymentServiceIT`,
`InvoiceServiceIT`, and `NotificationServiceIT` - which together prove the same chain without one
fragile multi-service-in-one-JVM test), the compensating path
(`PaymentFailed` → `PAYMENT_FAILED`), idempotency (above), and that `OrderPlaced` is actually
published with the order id as the Kafka key. Two Kafka-test-API gotchas worth knowing if you
write another one of these: `KafkaTestUtils.getRecords(consumer, timeout)` takes a `long`
milliseconds, not a `Duration`; and its sibling `getSingleRecord` throws if more than one record
for the topic exists — fine for a topic only one test touches, wrong here since every test method
in this class calls the same `placeOrder()` helper, so `order.placed` legitimately accumulates
multiple records across the class's test run. Filter by key instead of assuming there's only one.
