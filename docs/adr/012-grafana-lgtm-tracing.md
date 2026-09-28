# ADR-012: Grafana LGTM for distributed tracing and logs

## Status

Accepted. Builds on [ADR-011](011-prometheus-metrics.md).

## Context

Prometheus (ADR-011) says how a service is doing in aggregate. It cannot follow one order. A
purchase is five services and four Kafka hops, and the only ways to see it were the persisted
order audit trail (business events, one service) or reading each service's log and matching event
ids by hand. Production systems tag every request with a trace id at the edge and propagate it
through every hop, so one id shows the whole path and where the time went.

## Options considered

1. **Prometheus only.** Nothing to add, but no per-request view at all.
2. **Zipkin or Jaeger** for traces. Small and well known, but it is one more UI beside Prometheus
   and gives nothing for logs later.
3. **Elastic Stack.** Strong at full-text log search, heavy to run for a demo, and traces need
   APM Server on top.
4. **Grafana LGTM** (the option taken): Loki (logs), Grafana, Tempo (traces), Mimir or Prometheus
   (metrics), fed by an OpenTelemetry collector. The `grafana/otel-lgtm` image bundles all of it in
   one container, and Grafana already sits on top of Prometheus.

## Decision

- `docker-compose.yml` runs `grafana/otel-lgtm` (pinned to 0.34.0) with Grafana on port 3000 and
  the OTLP receiver on 4318. `monitoring/grafana-datasources.yaml` adds the standalone Prometheus
  as a datasource, so metrics and traces are in one Grafana. The image's own embedded Prometheus
  is left unused: metrics keep going through the scrape from ADR-011, and OTLP metrics export is
  switched off (`management.otlp.metrics.export.enabled=false`) so nothing is published twice.
- Every service except `eureka-server` depends on `spring-boot-starter-opentelemetry` and exports
  spans over OTLP HTTP. Boot 4 has no default endpoint, so
  `management.opentelemetry.tracing.export.otlp.endpoint` must be set. Without it no exporter is
  created and nothing is sent, with no warning.
- Sampling is 100% (`management.tracing.sampling.probability=1.0`). Spring's default is 10%, which
  makes a demo look broken because most orders have no trace.
- The trace continues across Kafka because the four saga services enable observation on the
  producer and the listener (`spring.kafka.template.observation-enabled`,
  `spring.kafka.listener.observation-enabled`). The reactive gateway needs
  `spring.reactor.context-propagation=auto`, or the trace breaks at the edge.
- Health checks, Prometheus scrapes and Eureka client traffic are excluded by an
  `ObservationPredicate` (`common`'s `InfrastructureObservationConfiguration`, imported by the five
  servlet services, and a local twin in `api-gateway`, which cannot depend on `common`). Boot 4 has
  no property for this. Spring Security's own per-request spans are switched off with
  `management.observations.enable.spring.security=false`, since they were 4 extra spans per hop.

- Logs go to Loki over the same OTLP endpoint (`management.opentelemetry.logging.export.otlp.endpoint`,
  again with no default in Boot 4). Boot autoconfigures the log exporter but not the bridge from
  Logback, so each service has a `logback-spring.xml` adding the OpenTelemetry Logback appender next
  to the console appender, and one bean hands the SDK to that appender once Spring has built it
  (`common`'s `OpenTelemetryLogAppenderConfiguration`, with a twin in `api-gateway`). Every log line
  then carries `trace_id` and `span_id`, and Grafana links a log line to its trace.
- The `logback-spring.xml` includes Boot's `defaults.xml` and `console-appender.xml` rather than
  `base.xml`, because `base.xml` also switches on a file appender that is off by default.

Result: one order is one trace of 13 spans across `api-gateway`, `order-service`,
`payment-service`, `invoice-service` and `notification-service`, including every Kafka send and
receive, and every service's log lines are searchable in Loki by service and by trace id.

## Consequences

- **Good**: "where did this order go and how long did each step take" is one search in Grafana
  instead of matching ids across five logs, for one dependency and about six properties per
  service.
- **Good**: from a slow or failed trace, one click shows the log lines of every service that took
  part, instead of grepping five consoles for an order id.
- **Good**: no service code changed for tracing itself. The only Java is the noise filter and the
  appender hand-over.
- **Bad, honestly**: the Logback appender is an alpha artifact (`opentelemetry-logback-appender-1.0`),
  absent from the BOMs Boot imports, so `common` and `api-gateway` import the alpha instrumentation
  BOM at a pinned version (2.28.1-alpha). Bumping Boot means checking that pin still matches.
- **Bad, honestly**: `logback-spring.xml` is copied into six services, and it replaces Boot's
  default logging setup, so `logging.file.name` no longer produces a log file.
- **Production mapping**: the services' OTLP configuration does not change. On AWS
  ([ADR-009](009-aws-deployment-architecture.md)) the ADOT collector sidecar in each task receives
  the same OTLP on `localhost:4318` and forwards traces to X-Ray and logs to CloudWatch Logs, with
  Amazon Managed Grafana on top. The one-click trace-to-log link seen locally would have to be set
  up again there, and has not been tried.
- **Bad, honestly**: the Kafka broker itself is not covered. Only the services' own producer and
  consumer spans and log lines are.
- **Bad, honestly**: the hand-over is proven by a unit test in `common` and `api-gateway`, and
  delivery to Loki was confirmed from the services' integration test runs, not from a manual
  end-to-end order through running services.
- **Bad, honestly**: the noise filter also removes actuator requests from the
  `http.server.requests` metric, so Prometheus no longer counts its own scrapes. That is what
  most people want, but it is a change from ADR-011's behavior.
- **Bad, honestly**: 100% sampling and an all-in-one container with no persistent volume are
  demo settings. A real deployment would sample, and would run Tempo, Loki and Mimir on object
  storage or use Grafana Cloud, with the same OTLP configuration in the services.
- **Bad, honestly**: there are no Grafana dashboards or alerts yet, and two Prometheus instances
  exist (the standalone one and the unused embedded one).
- **Bad, honestly**: updating `common` means restarting every service that uses it. On Windows the
  running JVMs hold the jar open, so `mvn install` on `common` fails until they are stopped.
