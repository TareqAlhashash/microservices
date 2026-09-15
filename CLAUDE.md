# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

"InvestorBook" — a Spring Cloud microservices demo (originally Spring Boot 2.0.2, Spring Cloud
Finchley, Java 8) modeling a social network for investors. There is no top-level README yet;
per-endpoint field docs for planned features live in `api-gateway/requirements-member.txt` and
`api-gateway/requierments-security.txt` (note the typo in the filename — don't "fix" it without
checking for other references).

Five independently deployable Maven modules plus one shared library, each with its own `pom.xml`.
There is no parent/reactor POM — modules are built and run separately, and **`member-service`,
`auth-service`, `resource-service`, and `api-gateway` have been upgraded and given test
coverage** (see below); only `eureka-server` is still exactly as originally generated.

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
| `member-service` | **13 unit tests, no Docker** | +11 integration tests, **needs Docker** | See below |
| `auth-service` | **5 unit tests, no Docker** | +7 integration tests, **needs Docker** | See below |
| `api-gateway` | **2 unit tests, no Docker** | +3 integration tests, no Docker needed | See below |
| `eureka-server` | **fails on JDK 17+** | same failure | See "JDK 21 incompatibility" below |
| `resource-service` | **1 unit test, no Docker** | +3 integration tests, no Docker needed | See below |
| `common` | nothing to run | nothing to run | Library, no tests |

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
cd member-service && mvn verify                                   # +11 integration tests, needs Docker
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
cd auth-service && mvn verify                                 # +7 integration tests, needs Docker
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
this module doesn't depend on `common`), so `spring-cloud-starter-oauth2` had to be added directly,
matching what `auth-service` already declares — without it, `JwtConvertor.java`'s existing
`OAuth2Authentication`/`DefaultAccessTokenConverter` usage doesn't even compile under Hoxton.

`ResourceServiceApiIT` mints JWTs against a test signing key (no real dependency to exercise via
Testcontainers here) and proves the actual filter chain: no token → 401, a valid token without
`ROLE_MEMBER` → 403 (`@PreAuthorize` actually enforced, not just present in source), a valid token
with the role → 200. `JwtConvertorTest` covers the claims-map-as-details behaviour `JwtUtil.getEmail`
relies on downstream (see member-service's `MemberServiceController`).

```bash
cd resource-service && mvn test                                 # 1 test, seconds, no Docker
cd resource-service && mvn verify                                # +3 integration tests, no Docker needed
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
rejected by the gateway's own security layer), and that `/login` bypasses that security layer
entirely so a client can obtain a token in the first place. Deliberately does **not** test
`/uaa/oauth/token` or `/member-service/signup` the same way: both are pure Zuul-proxied routes
with no controller of their own in this app, and with Eureka disabled (as in every test here)
Zuul can't resolve them, forwarding internally to an error dispatch that produces a 401 for
reasons unrelated to the ignore-list — a real quirk of testing Zuul routes without a running
Eureka, not a gap in what `/login` already proves about the ignore-list mechanism.

```bash
cd api-gateway && mvn test                                      # 2 tests, seconds, no Docker
cd api-gateway && mvn verify                                     # +3 integration tests, no Docker needed
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
targets and was never validated past) or the same kind of Boot version bump the other four
modules got (see "Why member-service, auth-service, resource-service, and api-gateway are on a
different Boot version"). Confirmed by actually running its tests, not inferred from the version
number alone.

### Running the full system locally

Start order matters because services register with and discover each other through Eureka:

1. `eureka-server` (port **8761**) — naming server, dashboard at `http://localhost:8761/`
2. PostgreSQL reachable at `jdbc:postgresql://localhost/investorbook` (user `postgres` / pass `pass`)
   — required by `auth-service` and `member-service` (`spring.jpa.hibernate.ddl-auto=update`, so
   schema is created/updated automatically, no migration tool)
3. `auth-service` (port **9100**, context path `/uaa`) — OAuth2/JWT authorization server
4. `member-service` (port **8100**)
5. `resource-service` (port **9200**) — currently just a `/hi` smoke-test endpoint
6. `api-gateway` (port **8765**) — Zuul edge router, the only service meant to be called externally

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
  back out to `api-gateway`'s `/login` through `AuthenticationServiceProxy` (a Feign client) to
  obtain a token, so a client only calls member-service once and gets a JWT back. This is the only
  module on Spring Boot 2.2.13 / JUnit 5 — see "Why member-service is on a different Boot version"
  below.
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
    uncaught exception to 500 and `MethodArgumentNotValidException` to 400.

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
   the identical PEM string is duplicated across every `application.properties` file. Changing the
   keypair means updating it everywhere in lockstep.
5. All resource servers are `SessionCreationPolicy.STATELESS` — no server-side session state
   anywhere; authorization is entirely re-derived from the JWT on each request.

Each service's own `SecurityConfiguration` class governs its own routes; there is no shared/inherited
security config module, so a newly added service must supply its own (`resource-service` is the
template for this). `member-service`'s `MemberServiceApiIntegrationTest` proves this chain actually
works end-to-end (401 with no token, 404 with a valid token and no matching account, `@PreAuthorize`
enforced) by minting a JWT directly against a test signing key — see that test's class Javadoc for
why it doesn't depend on `auth-service` being up.

### Why member-service, auth-service, resource-service, and api-gateway are on a different Boot version

`member-service` was bumped from Spring Boot 2.0.2 to **2.2.13** (Spring Cloud `Hoxton.SR12`)
specifically to get JUnit 5 as the default in `spring-boot-starter-test`, before any test suite was
written for it — Boot 2.0.2's bundled Surefire (2.21.0) predates JUnit Platform support entirely.
`auth-service`, `resource-service`, and `api-gateway` later got the identical bump for the same
reason. Only `eureka-server` remains deliberately untouched: all five modules only interoperate
over REST/Eureka, never a shared JAR, so there's no cross-module coupling to the version bump — if
it ever gets its own test suite, expect to hit the same overrides. Two follow-on overrides were
needed on every bumped module to make the JDK on this machine (21) actually work with the upgraded
test stack: `mockito.version` (Boot 2.2's managed Mockito predates JDK 17+ bytecode support) and,
less obviously, `byte-buddy.version` (Boot's BOM otherwise still pins byte-buddy to a version too
old for the overridden Mockito, which fails at mock-creation time with `NoClassDefFoundError`, not
at build time).

### Naming and package quirks to know about

- Two independent `com.investorbook.<x>.security.SecurityConfiguration` classes exist (in
  `member-service` and `resource-service`) plus a differently-named `JwtConvertor` in three modules
  — same intent, not shared code, don't assume editing one affects another.
- `resource-service`'s package is `com.investorbook.resourceservice.secuirty` (misspelled) —
  intentional-looking but easy to typo again when adding files there.
- `auth-service` and `member-service` each define their own `MemberEntity` mapped to the same
  `members` table with different column subsets — by design (auth only needs id/email/password
  hash), not a duplication bug to merge. `auth-service`'s `MemberEntity` has no `@GeneratedValue`
  on its `String id`, and until `AuthServiceTokenIT` needed to seed one, there was no way to
  construct a persistable instance at all — the 3-arg constructor `(id, email, passwordHash)` was
  added for that.
