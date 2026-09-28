# ADR-011: Prometheus for service metrics

## Status

Accepted.

## Context

Until now the only live view of the system was hand-built: `api-gateway`'s `/dashboard/services`
polls each service's `/actuator/health`, and `order-service` keeps a persisted event audit trail.
Both answer "is it up right now?" and "what happened to this order?". Neither keeps history, so
nothing can answer "how did latency change after the last deploy?", "is a Kafka consumer falling
behind?" or "has this service been flapping?". Those are the questions a production system is
monitored with.

## Options considered

1. **Keep the hand-built dashboard only.** Cheap, but it has no history, no per-request latency
   and no JVM or Kafka numbers, and every new question means new application code.
2. **Push metrics over OpenTelemetry (OTLP)** to a collector. Vendor-neutral, and it would share
   one pipeline with traces and logs later, but it needs a collector in the picture and is less
   familiar than the scrape model.
3. **Prometheus scraping Micrometer** (the option taken): each service exposes
   `/actuator/prometheus` and one Prometheus container pulls from all of them.

## Decision

- Every module depends on `micrometer-registry-prometheus` (version managed by Spring Boot's BOM)
  and adds `prometheus` to `management.endpoints.web.exposure.include`. No application code
  changed for this, and no custom metrics were added: the JVM, HTTP server, datasource and Kafka
  client metrics Micrometer provides out of the box are the whole set.
- `docker-compose.yml` runs Prometheus on port 9090, configured by `monitoring/prometheus.yml`
  with one static scrape job per service, every 15 seconds. `http://localhost:9090/targets` shows
  which services are up.
- Static targets, not Eureka service discovery. Every service has a fixed port, and Eureka
  discovery would add configuration for nothing here. `auth-service` is the one job with a
  different `metrics_path` (`/uaa/actuator/prometheus`) because of its context path. Prometheus
  runs in Docker while the services run on the host, so targets use `host.docker.internal`.
- `payment-service`, `invoice-service` and `notification-service` gained a small
  `SecurityConfiguration` each. They have no REST API and never needed one, but Spring Security
  arrives through `common`, and Boot's default only leaves `health` and `info` open, so their
  `/actuator/prometheus` returned 401 until the endpoint was explicitly permitted. The rest of
  their paths are denied.

## Consequences

- **Good**: history, per-service request rate and latency, JVM and Kafka consumer lag, and a
  "service is down" signal (`up == 0`), for one dependency line and one compose service per
  module.
- **Good**: the hand-built dashboard and the order audit trail are unchanged. They serve a
  different purpose (a demo-facing view and a business history), so this adds to them rather than
  replacing them.
- **Bad, honestly**: metrics only. There is no Grafana dashboard and no alerting (Prometheus
  records `up`, but nothing pages anyone). Tracing was added afterwards, see
  [ADR-012](012-grafana-lgtm-tracing.md), along with logs in Loki.
- **Bad, honestly**: the endpoint is unauthenticated, for the same reason `/actuator/health` is: a
  scraper carries no bearer token. A real deployment would put it on a separate management
  port or network.
- **Bad, honestly**: Prometheus has no persistent volume in the compose file, so its history is
  lost when the container is removed. Fine for a local demo, wrong for anything else.
- **Bad, honestly**: static targets mean a new service must be added to `prometheus.yml` by hand,
  and a second instance of a service would not be scraped.
- **Production mapping**: nothing in the services changes. On AWS (see
  [ADR-009](009-aws-deployment-architecture.md)) the ADOT collector sidecar in each task scrapes
  the same endpoint and remote-writes it to Amazon Managed Service for Prometheus, shown in the
  AWS diagram.
