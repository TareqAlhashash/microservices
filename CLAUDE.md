# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

"InvestorBook" — a Spring Cloud microservices demo built around a purchase-order system. See
[`README.md`](README.md) for a short overview with the C4 diagrams (System Context, and Container
diagrams for the core services and the purchase flow), and [`docs/adr/`](docs/adr/) for the
architecture decision records behind the choices documented in this file (service
discovery/gateway, OAuth2, the `common` library, per-service data ownership, and the four
Kafka-saga decisions).

Seven independently deployable Maven modules (`eureka-server`, `auth-service`, `api-gateway`, and
the four event-driven purchase-flow services — `order-service`, `payment-service`,
`invoice-service`, `notification-service`, see "Event-driven purchase flow" below) plus one
shared library, each with its own `pom.xml`. There is no parent/reactor POM — modules are built
and run separately, and every module has test coverage (see below).

## Commands

Prefer the global `mvn` over any module's `mvnw`/`mvnw.cmd` wrapper — `common` doesn't even have
one. There is no root build:

```bash
cd <service-dir> && mvn clean install     # build one service
cd <service-dir> && mvn test              # test one service
cd <service-dir> && mvn spring-boot:run   # run one service
```

`common` is a shared library (not a runnable service) consumed by `auth-service` and the
purchase-flow services via `com.investorbook:common:0.0.1-SNAPSHOT` (`api-gateway` does not
depend on it — see "api-gateway" below for why). Build and `install` it into the
local `.m2` repo before building any of those, since there is no multi-module reactor to sequence
it automatically:

```bash
cd common && mvn clean install
```

### Testing status per module (verified, not assumed)

Each module's real, checked test status on this machine (Windows, JDK 21, Docker Desktop
available). Don't assume `mvn test`/`mvn verify` works the same way across modules — it doesn't.

| Module | `mvn test` | `mvn verify` | Notes |
|---|---|---|---|
| `auth-service` | **5 unit tests, no Docker** | +4 integration tests, **needs Docker** | See below |
| `api-gateway` | **10 unit tests, no Docker** | +6 integration tests, no Docker needed | See below |
| `eureka-server` | **1 unit test, no Docker** | same | See "eureka-server" below |
| `common` | **4 unit tests, no Docker** | same | Library; see "Shared error handling" below |
| `order-service` | **17 unit tests, no Docker** | +7 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `payment-service` | **7 unit tests, no Docker** | +6 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `invoice-service` | **8 unit tests, no Docker** | +5 integration tests, **needs Docker** | See "Event-driven purchase flow" below |
| `notification-service` | **10 unit tests, no Docker** | +3 integration tests, **needs Docker** | See "Event-driven purchase flow" below |

#### Shared error handling (common lib)

`common`'s `CustomizedResponseEntityExceptionHandler` (`@ControllerAdvice`) gives every service a
uniform `{timestamp, message, details}` error body: any uncaught exception maps to 500, and a
failed `@Valid` (`MethodArgumentNotValidException`) maps to 400. Every service that wants it
explicitly `@Import(CustomizedResponseEntityExceptionHandler.class)`s it in its `*Application.java`.

The one subtlety worth knowing if you touch this class: its `@ExceptionHandler(Exception.class)`
catch-all would otherwise intercept `AccessDeniedException` *inside* the `DispatcherServlet`,
before Spring Security's own `ExceptionTranslationFilter` ever gets a chance to turn it into a
403 — silently turning every `@PreAuthorize` denial into a 500 instead. The fix is narrower
`@ExceptionHandler` methods for `AccessDeniedException` and `AuthenticationException` that just
rethrow, letting Spring's handler-resolution machinery prefer the more specific match and the
exception propagate to the security filter chain as normal. Any service wiring this handler in
for the first time should have a test proving a wrong-role (not just no-token) request against a
`@PreAuthorize`'d endpoint actually gets a 403, not a 500 — that's the case this rethrow exists
to protect.

#### auth-service: a plain /login endpoint that signs its own JWT - not an OAuth2 provider

Same `*Test`/`*IT` split as every module in this repo, same Docker API pin in
`auth-service/src/test/resources/docker-java.properties`.

```bash
cd auth-service && mvn test                                  # 5 tests, seconds, no Docker
cd auth-service && mvn verify                                 # +4 integration tests, needs Docker
```

This service briefly went through two different OAuth2 rewrites - first onto Spring Authorization
Server (the successor to the removed `@EnableAuthorizationServer`), which needed a custom
grant-type extension since the framework deliberately doesn't implement the password grant this
system's login flow depends on (dropped in OAuth 2.1). That worked, but for a system with exactly
one first-party client (api-gateway, on the browser's behalf) and no third-party clients,
multi-tenant client registration, consent screens, or token introspection/revocation needs, an
OAuth2 *authorization server* framework is solving a much bigger problem than this one actually
has. **Replaced with the much smaller thing this system actually needs**: `LoginController`
authenticates the member (`DaoAuthenticationProvider`, same `MemberDetailsService`/BCrypt path
every version of this service has used) and `JwtIssuer` signs a JWT directly with Nimbus JOSE - a
`JWTClaimsSet.Builder` + `RSASSASigner`, no OAuth2 endpoint machinery, no client registry, no
grant types. The whole replacement is two small classes.

**Nothing downstream changed.** `JwtIssuer` reproduces the exact same `user_name`/`authorities`
claim shape every resource server in this repo (order-service, api-gateway) already reads, so
`NimbusJwtDecoder`-based JWT *verification* everywhere else in the system is untouched - this
was always purely an issuance-side simplification.

**Dropping OAuth2 also dropped the client-credentials concept entirely**, which cascaded into a
real simplification at api-gateway too: no more `html5` client id/secret, no more Basic-auth
header assembly, no more form-urlencoded request bodies for the internal auth-service call. See
"api-gateway" below for the other half of this.

`/login` accepts a plain JSON body (`{"username", "password"}`) and returns
`{"access_token": "..."}` - no `refresh_token`/`token_type`/`scope`/`jti` fields, since those are
OAuth2-token-response conventions this system has no other use for (the frontend only ever read
`access_token` from this response anyway). No refresh-token flow either: a token is valid for
`investorbook.security.jwt.expiration` seconds (24h by default), and re-authenticating (not
silently refreshing) is what happens after that - a deliberate simplification consistent with
this system's stateless, no-session-store design; see ADR-002's "Consequences" for the tradeoff.

`AuthServiceTokenIT` proves the real end-to-end flow against a real Postgres member row: token
issuance with a validly-signed, verifiable JWT (Nimbus JOSE, verified against the fixed public
key exactly the way a downstream resource server would); 401 for a wrong password or unknown
user (`DaoAuthenticationProvider` throws an `AuthenticationException`, which `common`'s
`CustomizedResponseEntityExceptionHandler` deliberately rethrows - see "Shared error handling"
above - letting `SecurityConfiguration`'s own `AuthenticationEntryPoint` turn it into a 401
rather than the generic handler flattening it to 500).

#### api-gateway

Spring Cloud Gateway (reactive, WebFlux/Netty), Spring Boot 4.1.1/Spring Cloud 2025.1.2 — the same
generation every module is on now (see "Boot/Spring Cloud versions across modules" below), though
api-gateway got there first, as its own dedicated migration. Because
Spring Cloud Gateway is WebFlux/Netty only, everything in this module is reactive: security,
every custom filter, and every outgoing HTTP call - there's no hybrid servlet/reactive mode.

This module does not depend on `common`: `common` pulls in `spring-boot-starter-web` and other
servlet-stack dependencies that conflict with a WebFlux app (risking Spring Boot detecting this
as a servlet app instead of reactive), so api-gateway owns small local equivalents instead
(`dto/AuthResponse`, `dto/LoginRequest` with `jakarta.validation` constraints).

**Routing**: `GatewayRoutesConfig` defines an explicit route for `order-service`
(`stripPrefix(1)`, so `/order-service/products` reaches order-service's own `/products` -
the downstream service knows nothing about its gateway route prefix).

**Security**: a reactive `SecurityWebFilterChain` (`ServerHttpSecurity`) verifies RSA-signed JWTs
via `NimbusReactiveJwtDecoder.withPublicKey(...)`, bound to `investorbook.security.jwt.public.key`
(matching every other resource-server-side service). No authorities/roles converter is configured:
api-gateway has no method-level `@PreAuthorize` of its own (unlike order-service) - it only needs
"is this a validly signed, unexpired JWT", with fine-grained role checks staying in the proxied
services.

**Filters**: `RequestLoggingFilter` (a Gateway `GlobalFilter`) logs every request actually routed
to a downstream service - a `GlobalFilter` only runs for requests Gateway proxies, never this
app's own locally-handled endpoints (`/login`, `/actuator/**`, `/dashboard/**`). `RateLimitFilter`
needs to cover those local endpoints too, so it's registered as a plain WebFlux `WebFilter`
instead (`config/RateLimitConfig.java`), ordered right after `CorsWebFilter`
(`Ordered.HIGHEST_PRECEDENCE + 1`, so a rejected request still carries CORS headers) and ahead of
Spring Security's reactive chain (so `permitAll()` routes are covered too). It caps requests per
client IP via a keyed resilience4j `RateLimiter`; `RateLimitFilterTest` (unit,
`MockServerWebExchange`) covers the limiter logic directly (allow, reject, per-client isolation);
`RateLimitFilterIT` proves a real HTTP burst gets 429s with a `Retry-After` header once over a
deterministically low configured limit - it swaps in a plain `SimpleClientHttpRequestFactory`
first, since `TestRestTemplate`'s default Apache HttpClient5 client automatically retries a 429
that carries a `Retry-After` header (correct real-client behaviour, but it means the test would
otherwise observe the *retried* 200, not the immediate 429 it's trying to prove). In-memory,
per-instance, keyed by remote address - see the class's own Javadoc for what that does and
doesn't cover.

**Error handling**: `WebFluxExceptionHandler` gives this module's own controllers the same
`{timestamp, message, details}` shape every other service uses. Its `ErrorResponseException`
handler preserves the exception's own status code rather than letting the generic
`@ExceptionHandler(Exception.class)` catch-all flatten it to 500 - the same "don't let a broad
handler mask a specific status" principle `common`'s shared handler follows for
`AccessDeniedException`/`AuthenticationException` (see "Shared error handling" above).

**Controllers are thin; `service/` holds the actual logic** - `LoginController`/`DashboardController`
just map HTTP to a `service.LoginService`/`service.DashboardService` call (matching this repo's
own Spring layering convention: "controller validates/translates the HTTP concern and delegates
to a @Service"). Both services share one `WebClient` (`internalWebClient` bean, `WebClientConfig`),
not a blocking HTTP client, since WebFlux needs non-blocking I/O throughout: `DashboardService`'s
health checks run concurrently (`Flux.merge`-style fan-out), so one slow or dead service doesn't
delay every check behind it. `LoginService` used to call `auth-service`'s OAuth2 token endpoint
through a Feign client (`OauthServiceProxy`), offloaded to `boundedElastic` since Feign is
fundamentally blocking - the only blocking call in an otherwise fully reactive app, and requiring
a Basic-auth header built from an OAuth2 client id/secret. Now that auth-service has dropped
OAuth2 entirely for a plain `/login` endpoint (see its own section above), this simplified twice
over: `ReactiveDiscoveryClient.getInstances("auth-service").next()` + a plain `WebClient` POST of
the *same* `LoginRequest` the controller received, as JSON, straight through - no Basic-auth
header, no client secret, no form-encoded body, no `boundedElastic` offload (`WebClient` is
non-blocking to begin with). Mirrors `DashboardService`'s own discovery-plus-WebClient pattern
instead of introducing a second one - no `@LoadBalanced WebClient.Builder`/`lb://` scheme, since
there's never more than one `auth-service` instance in this system and `.next()` on the
discovered instances is already the simplest thing that's actually needed. Removed
`spring-cloud-starter-openfeign`, `proxy/OauthServiceProxy`, `proxy/FormEncoderConfig`,
`config/Html5ClientProperties`, `@EnableFeignClients`, and (once nothing was left to bind)
`@ConfigurationPropertiesScan` entirely - nothing in this module uses Feign or knows about an
OAuth2 client secret now.

**Testing notes specific to this module's stack**: `TestRestTemplate` lives in its own module
(`spring-boot-resttestclient`, package `org.springframework.boot.resttestclient`), and a
`@SpringBootTest` that wants one injected needs an explicit `@AutoConfigureTestRestTemplate`.
Mockito test doubles use `@org.springframework.test.context.bean.override.mockito.MockitoBean`/
`MockitoSpyBean`. `ApiGatewaySecurityIT` mints its own RSA-signed test JWT via Nimbus JOSE
directly, since `NimbusReactiveJwtDecoder` only verifies asymmetric signatures - it proves
default-deny (401 without a token), token acceptance (a valid bearer token is not rejected by the
gateway's own security layer), that `/login` bypasses security entirely so a client can obtain a
token in the first place, and that a missing field gets a 400 in the shared error shape. `/login`
itself is proven with `auth-service` mocked at the two seams that actually reach outside this
process - a `@MockitoSpyBean ReactiveDiscoveryClient` (a spy, not a replacement bean: Spring
Cloud's own `reactiveCompositeDiscoveryClient` is already `@Primary`, so a second `@Primary` bean
of the same type is ambiguous rather than an override - a spy wraps the real bean in place
instead, so every *other* service's lookup, notably `order-service`'s, keeps resolving to nothing
exactly like production with Eureka disabled) stubbed to resolve `"auth-service"` to a fixed
instance, and a `@Primary`-registered stub `WebClient` (there's no real `@Primary` conflict here,
since `WebClientConfig`'s own bean isn't marked `@Primary`) that always answers with a canned
`{"access_token":"access"}` regardless of the URL, since the discovery stub doesn't point
anywhere real either. `service/LoginServiceTest` (unit, `ReactiveDiscoveryClient` mocked and
`WebClient` stubbed at the `ExchangeFunction` seam, same pattern as `DashboardServiceTest`) proves
the request targets the discovered instance's `/uaa/login` and the response is relayed back
unchanged. `service/DashboardServiceTest` covers the health-aggregation logic directly - see
"System dashboard" below.

Confirmed by actually running `mvn verify` (10 unit + 6 integration tests, SpotBugs clean) and by
driving a real standalone instance with curl: login validation, JWT-protected routes, the
dashboard, and the rate limiter's 429s all confirmed live, not just in tests.

```bash
cd api-gateway && mvn test                                      # 10 tests, seconds, no Docker
cd api-gateway && mvn verify                                     # +6 integration tests, no Docker needed
```

#### eureka-server: migrated off the JDK-8-era stack it was generated with

`eureka-server` used to be the one module left on the Boot 2.0.2/Spring 5.0.6 stack it was
originally generated with, and it failed its single generated `contextLoads()` smoke test on
JDK 17+ with:

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
**not** resolve it in a direct check — this needed either JDK 8/11 (what Boot 2.0.2 actually
targets) or moving to a newer Boot generation. **Fixed by migrating to Spring Boot 4.1.1/Spring
Cloud 2025.1.2**, the same generation every other module is now on (see "Boot/Spring Cloud
versions across modules" below) — this removes the cglib proxying path entirely, exactly as it
did for every other module's own migration. The only other changes this needed:
`spring-boot-starter-actuator` had to be declared explicitly (no longer pulled in transitively on
this Spring Cloud version, same gotcha the Eureka *client* starter already had) and
`management.endpoints.web.exposure.include=health,info,metrics` was added for consistency with
every other service. Confirmed by actually running its tests and then driving a real standalone
instance's registration API with curl, not inferred from the version number alone.

One Windows-specific gotcha this surfaced once eureka-server could actually run: on this machine
Eureka registered every client under its Windows network hostname (e.g.
`DESKTOP-XXXX.mshome.net`, from the Mobile Hotspot/Hyper-V default switch), which isn't
resolvable via DNS here. Eureka itself reported every instance as UP, but any server-to-server
call resolved through the registry - api-gateway calling auth-service to log in, the dashboard's
health aggregation - failed with an `UnknownHostException`/`WebClientRequestException`, and
silently rather than loudly (a login through the gateway came back `200` with an empty body,
since the reactive chain resolving `auth-service` just completed empty). Fixed with
`eureka.instance.prefer-ip-address=true` on every Eureka client (not just eureka-server itself),
which makes each one register its IP instead - what a container/VM deployment would already look
like, so this is purely a local-Windows-dev concern.

### Running the full system locally

Start order matters because services register with and discover each other through Eureka:

1. `eureka-server` (port **8761**) — naming server, dashboard at `http://localhost:8761/`
2. PostgreSQL reachable at `jdbc:postgresql://localhost/investorbook` (user `postgres` / pass `pass`)
   — required by `auth-service` and all four purchase-flow services
   (`spring.jpa.hibernate.ddl-auto=update`, so schema is created/updated automatically, no
   migration tool)
3. `auth-service` (port **9100**, context path `/uaa`) — authenticates members and issues JWTs
   (not an OAuth2 authorization server, see its own section below); seeds one demo member on
   first startup (`DemoMemberSeeder` - there's no signup flow, see "Accounts" in `README.md`)
4. `api-gateway` (port **8765**) — Spring Cloud Gateway edge router, the only service meant to be
   called externally
5. Kafka reachable at `localhost:9092` (`docker compose up -d` at the repo root) — required by the
   purchase-flow services: `order-service` (port **8200**), `payment-service` (port **8300**),
   `invoice-service` (port **8400**), `notification-service` (port **8500**); see "Event-driven
   purchase flow" below. Order doesn't matter between these four beyond Kafka/Postgres being up
   first - they only talk to each other over Kafka, never directly.

## Architecture

### Service responsibilities

- **eureka-server** — Netflix Eureka naming/discovery server. Nothing else registers with it as a
  peer (`eureka.client.register-with-eureka=false`).
- **auth-service** — authenticates members and issues JWTs; not an OAuth2 authorization server
  (see its own section above for why, after briefly being one). `LoginController` is a thin HTTP
  layer delegating to `service.LoginService`, which authenticates via `MemberDetailsService`/BCrypt
  and has `JwtIssuer` sign the token directly with Nimbus JOSE, using the RSA private key in
  `application.properties`. Authenticates against the `members` Postgres table via
  `MemberDetailsService` (implements Spring's `UserDetailsService`), using its own
  minimal `MemberEntity` (id/email/passwordHash only). Grants roles via `GrantedAuthorities`
  (`NORMAL_USER` → `ROLE_MEMBER`, `PREMIUM_USER` adds `ROLE_PREMIUMMEMBER`, `ADMIN` adds
  `ROLE_ADMIN`), though only `NORMAL_USER` is ever assigned today. `DemoMemberSeeder` seeds one
  fixed demo member on first startup (idempotent, same pattern as order-service's
  `ProductCatalogSeeder`) — the only account-provisioning mechanism this system has; there's no
  signup flow (see "Accounts" in `README.md`).
- **api-gateway** — Spring Cloud Gateway (reactive, WebFlux/Netty) reverse proxy and single
  external entry point — see "api-gateway" below for the full architecture.
  `SecurityConfiguration`'s reactive `SecurityWebFilterChain` requires a valid JWT on every route
  except `/login`, `/actuator/**`, `/order-service/products/**`, and `/dashboard/**`. Exposes
  `POST /login` (`LoginController`, delegating to `service.LoginService`), which resolves
  `auth-service` via `ReactiveDiscoveryClient` and forwards the user's username/password to its
  `/login` endpoint as plain JSON through a `WebClient` — no client secret involved on either
  side now (see "api-gateway" below).
  `RequestLoggingFilter` (a Gateway
  `GlobalFilter`) logs every request actually routed to a downstream service; `GatewayRoutesConfig`
  defines that route explicitly (`order-service`, `stripPrefix(1)`) rather than using Gateway's
  discovery locator, which needs its own predicate/filter SpEL config to auto-generate a route per
  Eureka-registered service.
- **common** — shared library, not a service. Holds cross-cutting pieces every remaining module
  reuses (`auth-service` and the four purchase-flow services — `api-gateway` has its own local
  equivalents instead, see "api-gateway" above):
  - `util/JwtUtil` — pulls the authenticated user's email (`user_name` claim) off the Spring Security
    `Authentication` via `OAuth2AuthenticationDetails`; this is how resource servers identify "the
    current user" from a decoded JWT without a separate lookup. Still live in `order-service`.
  - `util/EncryptionUtil` — PBKDF2WithHmacSHA512 password hashing helper (currently unused in favor
    of `BCryptPasswordEncoder` in `auth-service` — check before assuming it's live).
  - `exception/CustomizedResponseEntityExceptionHandler` — a `@ControllerAdvice` mapping any
    uncaught exception to 500 and `MethodArgumentNotValidException`/`BindException` (the two
    shapes a failed `@Valid` can take) to 400, with a uniform `{timestamp, message, details}`
    body throughout. Explicitly rethrows `AccessDeniedException`/`AuthenticationException`
    rather than handling them — see "Shared error handling" for the real 403-became-500 bug that
    omission caused. Every remaining service `@Import`s this.

### Security model (the architectural throughline)

Username/password + JWT, not session-based auth and not OAuth2 (see auth-service's own section
above for why this system doesn't use an OAuth2 authorization server):

1. A client sends username/password to `api-gateway`'s `POST /login`.
2. `api-gateway` resolves `auth-service` via Eureka and forwards the credentials to its `/login`
   as plain JSON - no client id/secret on either side, since there's no OAuth2 client to
   authenticate.
3. `auth-service` validates the user against Postgres (`service.LoginService`/`MemberDetailsService`),
   then signs a JWT directly with Nimbus JOSE (`JwtIssuer`) using its private RSA key
   (`investorbook.security.jwt.private.key`), containing the `user_name` claim and granted roles.
4. Every other resource server (`order-service`'s protected routes via a `JwtAuthenticationConverter`-
   based `SecurityFilterChain`, `api-gateway`'s reactive `SecurityWebFilterChain`) verifies the
   same JWT using the **public** key (`investorbook.security.jwt.public.key` everywhere except
   auth-service itself, which only ever needs the private half) — the identical PEM string is
   duplicated across every other `application.properties` file as the fallback of a
   `${JWT_PUBLIC_KEY:...}` placeholder (same pattern for the DB password `${DB_PASSWORD:...}` —
   see "Config & secrets" below). Changing the keypair for real still means updating every
   service's env var in lockstep; the placeholder only removes the "it's a literal in source"
   problem, not the duplication itself.
5. All resource servers are `SessionCreationPolicy.STATELESS` (or, for `api-gateway`, the reactive
   stack's stateless-by-default equivalent) — no server-side session state anywhere; authorization
   is entirely re-derived from the JWT on each request. There's no refresh-token flow either: a
   token is valid for `investorbook.security.jwt.expiration` seconds (24h by default), and
   re-authenticating is what happens after that - see ADR-002's "Consequences" for the tradeoff.

Each service's own `SecurityConfiguration` class governs its own routes; there is no
shared/inherited security config module, so a newly added service must supply its own —
`order-service`'s is a good starting template now.

### Config & secrets

Every literal secret that used to sit directly in an `application.properties` value is now
`${ENV_VAR:same-literal-as-before}` — the fallback preserves today's behaviour exactly (no env
var set anywhere in dev/test), while a real deployment overrides it: `DB_USERNAME`/`DB_PASSWORD`
(`auth-service`), `JWT_PUBLIC_KEY` (both resource-server-side services), and `JWT_PRIVATE_KEY`
(`auth-service` only, since only it signs). No client secret exists anywhere in this system -
dropping OAuth2 dropped that concept entirely (see auth-service's own section above). No
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

### Security scanning: SpotBugs + FindSecBugs + OWASP Dependency-Check on every module

Every module except `eureka-server` has the same two static-analysis/CVE-scan plugins, bound the
same way: SpotBugs+FindSecBugs bound to `verify` (fast, offline, so it runs on every build),
OWASP Dependency-Check declared but deliberately **not** bound to a lifecycle phase (the first
NVD sync without an API key is too slow to be a build-blocking default). Each module has its own
`spotbugs-exclude.xml`; every triage entry names the specific class/method and states a reason,
never a blanket suppression.

A genuinely mixed set of findings across modules — some real bugs worth fixing, some deliberate
design choices worth naming instead of "fixing":

- **Real fixes made**: a missing `serialVersionUID` (`auth-service`'s `InvestorBookUser`); `Date`
  fields returned/stored by reference in `common`'s `ExceptionResponse` (a shared value type
  touched by every service's error responses) — fixed with defensive copies rather than
  suppressed, since `Date` is genuinely mutable and the fix is two lines; `EncryptionUtil.hash`
  catching bare `Exception` when only `NoSuchAlgorithmException`/`InvalidKeySpecException` are
  actually possible; a non-locale-aware `toUpperCase()` on a generated invoice number in
  `invoice-service` (fixed with `Locale.ROOT`, since it's uppercasing hex characters in an
  identifier, not user-facing text); reliance on the JVM's default platform encoding
  (`String.getBytes()` with no explicit charset) in `api-gateway`'s `RateLimitFilter` — fixed with
  an explicit `UTF_8`; and CRLF-log-injection findings on every Kafka listener that logged an
  event id or order id
  without sanitizing it first (`order-service`, `payment-service`, `invoice-service`,
  `notification-service` — the *fields themselves* are legitimately attacker-influenced if a
  producer were ever compromised, unlike the one false-positive case below).
- **Triaged as deliberate, not suppressed blind**: `SPRING_CSRF_PROTECTION_DISABLED` on
  `auth-service` and `api-gateway`'s `SecurityConfiguration` — both are stateless, bearer-token
  APIs (`SessionCreationPolicy.STATELESS`); CSRF exploits rely on a browser automatically
  attaching a *cookie/session* to a forged cross-site request, and there is no cookie/session
  here for one to ride along on. `EI_EXPOSE_REP2` on every class that constructor-injects a
  `KafkaTemplate` or an HTTP client (`api-gateway`'s `DashboardService`/`LoginService` storing a
  `WebClient`) — a Spring-managed connection/client object, not a value type; there is nothing meaningful to
  "defensively copy".
- **A genuine tool limitation, documented rather than worked around further**:
  `order-service`'s `OrderEventListener.transitionIfExpected` still trips `CRLF_INJECTION_LOGS` on
  its 3-arg `logger.info(String, Object...)` call *after* the exact same `sanitizeForLog(String)`
  fix that resolved the identical finding on the 2-arg `logger.warn` two lines above — confirmed
  by re-running with only that one fix in place. FindSecBugs' taint tracker doesn't verify custom
  sanitizer methods through the varargs logging overload specifically; the value passed is
  provably always the sanitized one, so this one is a suppressed false positive, not a live risk.
- **Another taint-tracker quirk, this one worked around instead of suppressed**: FindSecBugs treats
  *every* value read off a Kafka event as tainted, `BigDecimal` included, so logging an event's
  `amount` in `payment-service`'s `refund` tripped `CRLF_INJECTION_LOGS` (a `BigDecimal` cannot
  hold a CR/LF; the finding is spurious). The fix is cheap and needs no suppression entry: log
  `sanitizeForLog(amount.toPlainString())` instead of the raw `BigDecimal`.

Confirmed by actually running `mvn verify` on every module after every fix — including
reinstalling `common` into the local `.m2` before re-verifying every consumer — not assumed from
a clean-looking diff.

### Boot/Spring Cloud versions across modules

Every module now runs Spring Boot 4.1.1/Spring Cloud 2025.1.2 on JDK 21 - `api-gateway` got there
first (its own dedicated Zuul→Gateway migration), `common` plus the five servlet-stack modules
(`auth-service`, `order-service`, `payment-service`, `invoice-service`, `notification-service`)
followed in one initiative, `auth-service` last since its `@EnableAuthorizationServer` needed a
real rewrite (see its own section above), not a version bump, and `eureka-server` - the one
module left on the JDK-8-era stack it was originally generated with - came last of all (see its
own section above for why that one specifically had been left untouched, and the JDK 21 failure
that finally forced it).

A few Boot4-migration gotchas worth knowing if you touch any of these modules again, found across
all seven migrations, not just one:
- **Kafka autoconfiguration relocated**: `KafkaTemplate` and friends moved out of
  `spring-boot-autoconfigure` into their own `spring-boot-starter-kafka`/`spring-boot-starter-kafka-test`
  artifacts - depending on plain `spring-kafka`/`spring-kafka-test` still compiles, but nothing
  autoconfigures the beans, and startup fails with `NoSuchBeanDefinitionException` on
  `KafkaTemplate`, not a compile error.
- **`TestRestTemplate` relocated** into its own `spring-boot-resttestclient` module
  (`org.springframework.boot.resttestclient.TestRestTemplate`, not `...test.web.client`), and its
  autoconfiguration (`@AutoConfigureTestRestTemplate`, now in
  `org.springframework.boot.resttestclient.autoconfigure`) needs `RestTemplateBuilder` on the
  classpath too (`spring-boot-starter-restclient`, also relocated out of `spring-boot-starter-web`)
  or context startup fails with `NoClassDefFoundError` on `RestTemplateBuilder` - only shows up on
  a servlet (non-reactive) app, which is why api-gateway's own migration didn't hit it.
- **`KafkaTestUtils.getRecords(consumer, timeout)` flipped from `long` millis to `Duration`**, the
  opposite of what an older revision of this file documented as a gotcha for the previous Kafka
  test library version - always check the actual overload rather than trusting a stale comment.
- **`spring-hateoas`'s `PagedModel` constructor became `protected`** - use the `PagedModel.of(...)`
  static factory instead of `new PagedModel<>(...)`.
- **Mockito/byte-buddy version overrides are gone.** Every Boot 2.2.13 module needed
  `mockito.version`/`byte-buddy.version` `pom.xml` overrides to work on JDK 21 (Boot 2.2's managed
  Mockito predates JDK 17+ bytecode support, and Boot's BOM otherwise pinned byte-buddy too old
  for the overridden Mockito). Boot 4.1.1 manages both at compatible versions already - removed
  from every migrated module's `pom.xml`, and no longer needed on a new one either.
- **`--add-opens java.base/java.lang=ALL-UNNAMED` is gone too.** Needed under the old stack for
  two unrelated reasons (`auth-service`/`api-gateway`'s JAXB-based error converter under
  `@EnableAuthorizationServer`/`@EnableResourceServer`; cglib proxying under Spring 5.0.6 for
  `eureka-server`, see its own section above) - Spring Security 7's removal of the legacy OAuth2
  support removed the JAXB path entirely, and Boot 4.1.1's newer Spring/cglib version doesn't need
  the reflective access the old one did.
- **`spring-milestones` repository declarations are gone** from every migrated module's `pom.xml` -
  they existed only for `Finchley.BUILD-SNAPSHOT`, a moving-target snapshot train Hoxton.SR12 (and
  now 2025.1.2) never needed.

### Naming and package quirks to know about

- Three independent `com.investorbook.<x>.security.SecurityConfiguration` classes exist
  (`auth-service`, `api-gateway`, `order-service`) — same intent, not shared code, don't assume
  editing one affects another. `order-service`'s JWT verification lives directly in its
  `SecurityConfiguration` now (a `JwtAuthenticationConverter` bean, `setAuthoritiesClaimName`/
  `setAuthorityPrefix("")` since auth-service's JWT already embeds `ROLE_`-prefixed authorities
  under a literal `authorities` claim) - the separate `JwtConvertor` class this used to delegate
  to was removed as part of the Boot4 migration, folded into `SecurityConfiguration` itself.
- `auth-service`'s `MemberEntity` (id/email/passwordHash only — just what authentication needs)
  has no `@GeneratedValue` on its `String id`; until `AuthServiceTokenIT` needed to seed one and
  `DemoMemberSeeder` needed to seed another, there was no way to construct a persistable instance
  at all — the 3-arg constructor `(id, email, passwordHash)` was added for that.

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
                                       \--PaymentFailed--> order-service (PAYMENT_FAILED, nothing was charged so nothing to undo)
```

`order-service` also consumes the terminal events (`PaymentSucceeded`/`PaymentFailed`,
`InvoiceIssued`, `OrderCompleted`, `PaymentRefunded`) to advance its own order's status: `PLACED`
→ `PAID` → `INVOICED` → `COMPLETED`, or `PLACED` → `PAYMENT_FAILED` when payment is declined, or
`PLACED`/`PAID`/`INVOICED` → `CANCELLED` once a refund has happened. See `OrderStatus` for the full
state enum.

### Compensation (what "reverting a step" means here)

Kafka events are immutable and Kafka isn't transactional with any of the databases, so nothing is
ever rolled back or un-published. A failing step publishes a failure event, and the service that
owns the effect to undo reacts to it, in reverse order of the original steps. Two chains cover
every step after payment (see [ADR-010](docs/adr/010-compensation-after-payment.md)):

```
invoice-service fails    InvoiceFailed -----------------------------> payment-service refunds --PaymentRefunded--> order CANCELLED
notification fails       NotificationFailed --> invoice-service voids --InvoiceVoided--> payment-service refunds --PaymentRefunded--> order CANCELLED
```

`payment-service` is the single refund point (`onInvoiceFailed`/`onInvoiceVoided` both end in
`PaymentEventListener.refund`); the refund itself is mocked, like the charge. The new topics are
`invoice.failed`, `notification.failed`, `invoice.voided`, `payment.refunded`. The failure
triggers are mocked and deterministic so each chain is demonstrable:

- **Invoice fails**: amount at or above `InvoiceEventListener.INVOICE_LIMIT` ($500.00, simulating a
  manual tax-review rule). Deliberately below payment's $1000.00 decline threshold, so an order in
  $500 to $999.99 is charged, fails to invoice, and gets refunded. No `Invoice` row is created.
- **Notification fails**: the customer's address can never receive mail (null, no `@`, or more
  than one address), which `NotificationEmailSender` reports as `UndeliverableRecipientException`.
  A mail *server* problem is still best-effort (logged, order still completes); only the
  never-retryable case is a saga failure.
- **Voiding an invoice** sets a nullable `voided_at` on the `Invoice` row (`isVoided()`); an
  already-voided invoice publishes nothing further, so a refund is never triggered twice.
- **`order-service` accepts `CANCELLED` from `PLACED`, `PAID`, or `INVOICED`**, not just the
  expected predecessor: the refund travels through several topics while the order's own
  `PaymentSucceeded`/`InvoiceIssued` arrive on others, with no cross-topic ordering, so the cancel
  can arrive first. A late `PaymentSucceeded`/`InvoiceIssued` afterwards finds `CANCELLED` and is
  ignored.
- **Not handled** (named in ADR-010): a compensation that itself fails has no retry or dead-letter
  topic; `CANCELLED` doesn't store the reason; the customer isn't told about a cancellation.

Every listener logs each stage in plain words: `saga:` on the forward path (for example
"payment captured, publishing PaymentSucceeded") and `compensation:` on the undo path ("payment of
600.00 for order X has to be refunded", "payment ... refunded, published PaymentRefunded", "invoice
... voided, void invoice sent"), so one order's whole story can be followed in the logs.

`payment-service` has no REST API of its own - it only reacts to Kafka. Its `PaymentEventListener`
consumes `OrderPlaced` and makes a **mocked, deterministic** decision: orders at or above
`PaymentEventListener.DECLINE_THRESHOLD` ($1000.00, simulating a simple risk/fraud threshold) get
`PaymentFailed`; everything else gets `PaymentSucceeded`. It also consumes `InvoiceFailed` and
`InvoiceVoided` and answers each with `PaymentRefunded`. The point is the event-driven
orchestration and the saga's failure paths, not a real payment integration.

`invoice-service` also has no REST API - `InvoiceEventListener` consumes `PaymentSucceeded`,
persists a real `Invoice` row (`invoiceNumber` is just `"INV-" + 8 random hex chars`, not a
real sequential numbering scheme - a demo simplification worth naming if asked), and publishes
`InvoiceIssued` (or `InvoiceFailed`, above the invoicing limit). It also consumes
`NotificationFailed`, voids the invoice, and publishes `InvoiceVoided`.

`notification-service` also has no REST API - `NotificationEventListener` consumes
`InvoiceIssued`, sends a completion email via `NotificationEmailSender` (real `JavaMailSender`
code with a text-file invoice attachment built inline from the event's own fields, not fetched
from `invoice-service`), and publishes `OrderCompleted`. `spring.mail.host`/`port` default to a
harmless `localhost:2525` placeholder that nothing listens on in a normal local run - **no real
mail server is ever wired in, by design** (see the plan this was built from). A send failure
other than an undeliverable address is caught and only logs a warning: it's a best-effort side
channel, not a gate on the saga completing, specifically so a normal local run (no SMTP server
configured) doesn't leave every order stuck at `INVOICED` forever. An undeliverable address is the
one exception and publishes `NotificationFailed` instead of `OrderCompleted` (see "Compensation"
above). Tests point `spring.mail.port` at GreenMail (a fake SMTP server) instead, so the real
sending code is genuinely exercised, not bypassed - see `NotificationServiceIT`.

### Shared pieces (in `common`)

- `com.investorbook.common.event`: the nine event classes (`OrderPlaced`, `PaymentSucceeded`,
  `PaymentFailed`, `InvoiceIssued`, `OrderCompleted`, plus the four compensation events
  `InvoiceFailed`, `NotificationFailed`, `InvoiceVoided`, `PaymentRefunded`) plus `Topics` (the topic-name constants,
  one topic per event type — `order.placed`, `payment.succeeded`, `payment.failed`,
  `invoice.issued`, `order.completed`, `invoice.failed`, `notification.failed`, `invoice.voided`,
  `payment.refunded`). Every producer sends keyed by order id
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
fragile multi-service-in-one-JVM test), the compensating paths
(`PaymentFailed` → `PAYMENT_FAILED`, and `PaymentRefunded` → `CANCELLED` from both `PAID` and
`INVOICED`, plus a redelivered refund), idempotency (above), and that `OrderPlaced` is actually
published with the order id as the Kafka key. Two Kafka-test-API gotchas worth knowing if you
write another one of these: `KafkaTestUtils.getRecords(consumer, timeout)` takes a `long`
milliseconds, not a `Duration`; and its sibling `getSingleRecord` throws if more than one record
for the topic exists — fine for a topic only one test touches, wrong here since every test method
in this class calls the same `placeOrder()` helper, so `order.placed` legitimately accumulates
multiple records across the class's test run. Filter by key instead of assuming there's only one.

One more gotcha in these four modules: a compile error in a *test* source file does not fail the
build. It surfaces as a runtime `java.lang.Error: Unresolved compilation problem` inside the one
test method that touches the bad line (for example a missing `throws` for a checked
`MessagingException`), so a "green" class with one odd error means read the Surefire/Failsafe
report under `target/` for that message before assuming the test logic is wrong.

## Storefront frontend (React)

`frontend/` is a React storefront UI (Vite + TypeScript + Tailwind CSS v4, React Router, plain
React Context for cart/auth state - no Redux/Zustand, no CSS-in-JS). It's **not a Maven module and
not registered with Eureka**: it's a browser client that calls `api-gateway` directly over CORS,
same as any other external client of this system, not something Gateway proxies requests through.
Design source: [Figma](https://www.figma.com/design/WskdCUCFg3FfpHQtq40uZu/InvestorBook-Store-UI)
(catalog grid, login, cart, order-status screens - indigo/slate palette, Inter). See
`frontend/README.md` for the stack, the exact endpoints it calls, and what's deliberately not
built (no admin/product-write UI, no token refresh).

Two backend changes were needed to support it, both minimal and both documented at their own call
sites rather than repeated here in full:

- **`order-service` gained a product catalog** (`ProductEntity`/`ProductRepository`/
  `ProductController`, `GET /products` and `GET /products/{id}`), seeded once on startup by
  `ProductCatalogSeeder` with a fixed, curated list (not regenerated per run, so restarts and
  tests stay deterministic). Deliberately unauthenticated (`SecurityConfiguration` ignores
  `/products/**`) - browsing a storefront doesn't require being logged in, only placing an order
  (`POST /orders`) does. The catalog was added to `order-service` rather than as a new service or
  module, since it already owns the purchasing domain and the Postgres/JPA wiring.
- **`api-gateway` gained CORS support** (`config/CorsConfig`) plus `/order-service/products/**`
  added to its own permitAll allowlist - the gateway's own auth layer, not just order-service's,
  has to let the public catalog route through. CORS is registered as a standalone `CorsWebFilter`
  bean at `Ordered.HIGHEST_PRECEDENCE`, **not** via the security DSL's own CORS support - a
  `permitAll()`/`WebSecurity.ignoring()`-style route bypasses Spring Security's whole filter
  chain, which would skip a security-DSL-registered CORS filter too and leave those exact routes
  without CORS headers even though they're the ones a browser calls without a token. Found by
  testing the real browser flow, not by
  inspection - see CorsConfig's Javadoc. Allowed origins are
  `investorbook.security.cors.allowed-origins` (`${CORS_ALLOWED_ORIGINS:...}`, defaulting to the
  Vite dev server's `http://localhost:5173`), following the same `${ENV_VAR:default}` pattern as
  this file's other externalised config. No other service needed CORS changes - `api-gateway` is
  the only one browsers ever call directly.

Checkout still goes through the existing, unmodified `POST /orders` (`amount` only - see "Event-
driven purchase flow" above); the frontend computes the cart total client-side and sends it as a
single opaque amount, same as any other caller of that endpoint. The order-status page polls
`GET /orders/{id}` every 3 seconds so the saga's state machine (`PLACED → PAID → INVOICED →
COMPLETED`, or a compensation path to `PAYMENT_FAILED`/`CANCELLED`) is visibly demonstrated live,
not just provable via the backend's own IT suites.

### System dashboard

A `/dashboard` page (behind a header link once logged in) shows live service health and every
order's real event timeline - see `frontend/README.md`'s "The dashboard" section for the frontend
side. Two backend additions support it:

- **`order-service` gained a persisted event audit trail**, not a scrape of console log text
  (which isn't queryable and wouldn't exist at all in a real deployment). `OrderEventLogEntity`/
  `OrderEventLogRepository` store one row per saga event (`orderId`, `eventType`, `message`,
  `occurredAt`); `OrderEventLogRecorder` writes them, called from `OrderEventPublisher` (for the
  `OrderPlaced` it publishes) and every handler in `OrderEventListener` (for what it consumes) -
  reusing each one's own existing log message text, not a separate copy. `OrderEventListener` also
  gained three new listeners, `onInvoiceFailed`/`onNotificationFailed`/`onInvoiceVoided`, purely to
  record those compensation-trigger events for the timeline - they don't transition this order's
  own status (payment-service/invoice-service already react to them for that), so they're
  deliberately not `@Transactional` the way the status-changing handlers are. New endpoints
  `GET /orders` (most recent 100, every customer - an ops view, not a "my orders" page, so
  deliberately not filtered by the caller's own email the way you might expect; a real deployment
  would gate this behind an ADMIN role, but every member is `NORMAL_USER` today, same as
  everywhere else in this repo) and `GET /orders/{id}/events` back the dashboard's table and its
  expandable rows. One easy-to-miss consequence of adding new `@KafkaListener` topics to an
  *existing* consumer group (`order-service`'s): Kafka replays a topic from the beginning the
  first time a consumer group subscribes to it (no prior committed offset), so the three new
  listeners backfilled audit rows for old orders' historical `InvoiceFailed`/`NotificationFailed`/
  `InvoiceVoided` events on first deploy, while the five pre-existing listeners' topics (already
  having committed offsets) did not retroactively backfill `OrderPlaced`/`PaymentSucceeded`/etc.
  for those same old orders - expected, not a bug, but confusing if you don't know to expect it.
- **`api-gateway` gained a health-aggregation endpoint**, `DashboardController`'s
  `GET /dashboard/services` (public, same allowlist reasoning as `/actuator/**` itself),
  delegating to `service.DashboardService` for the actual aggregation. A
  browser can't reach any of the seven services directly - different origins/ports, and only
  `api-gateway` has a CORS policy - so this app calls each one's own `/actuator/health`
  server-to-server instead, resolving addresses through Eureka's `ReactiveDiscoveryClient` rather
  than hardcoded ports (`eureka-server` is the one unavoidable exception: `eureka.client.register-
  with-eureka=false` means it can't be discovered through itself, so its `localhost:8761` address
  is hardcoded). Needed a short-timeout `WebClient` bean (`config/WebClientConfig`, 1s
  connect/response timeout, non-blocking since this module's stack is reactive throughout) so one
  dead service can't make the whole dashboard call hang. Two real bugs surfaced only by actually
  calling this live (not by inspection) and are worth knowing about if you touch this again:
  - `auth-service` is the only service with a non-root `server.servlet.context-path` (`/uaa`), so
    its actuator health lives at `/uaa/actuator/health`, not `/actuator/health` - the only other
    per-service override in `DashboardService` (`HEALTH_PATH_OVERRIDES`) besides eureka-server.
  - `notification-service` depends on `spring-boot-starter-mail`, which auto-configures a
    `MailHealthIndicator` that pings the configured SMTP host - the harmless `localhost:2525`
    placeholder nothing listens on in a normal local run (see "Event-driven purchase flow" above).
    That made its aggregate `/actuator/health` report `DOWN` even though the service was fully
    functional, contradicting this service's own documented design that an unreachable mail server
    is a best-effort side channel, not a gate on anything. Fixed with
    `management.health.mail.enabled=false` in its `application.properties`, not by suppressing the
    dashboard's read of the result - the health check itself was wrong, not the caller.
