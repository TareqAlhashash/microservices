# ADR-009: Target AWS deployment architecture (Fargate, Multi-AZ, MSK, CloudFront+WAF)

## Status

Accepted. Not deployed; this describes the target infrastructure shape, see the diagram in
[`README.md`](../../README.md#target-aws-deployment).

## Context

Every service in this repo runs locally today: `mvn spring-boot:run` against a local Postgres and
a `docker-compose.yml` Kafka broker. Nothing is deployed to AWS except S3 (and LocalStack for that
in tests). The portfolio value of naming a target production architecture is answering the
interview question "how would this actually run in production," not building it, so this ADR
documents the shape and the reasoning without claiming any of it is live.

## Options considered

**Compute for the nine services:**

1. **EC2 instances, self-managed.** Full control, but the team now owns OS patching, capacity
   planning, and an AMI/deployment pipeline for nine simple services that don't need any of that
   control.
2. **EKS (Kubernetes).** Powerful, and the industry-default answer, but a real operational
   surface: a control plane to manage, node groups or Fargate profiles either way, and cluster
   upgrades to plan around. Nothing here needs Kubernetes' extension points (custom controllers,
   multi-tenant namespaces, a service mesh) to justify that surface.
3. **ECS Fargate** (the option taken). No cluster to patch or scale; a task definition per
   service and AWS runs the container. The same "match the tool to the problem" reasoning
   [ADR-001](001-service-discovery-and-gateway.md) already applied to Eureka/Zuul at this scale.

**Data layer:**

4. **RDS PostgreSQL, Multi-AZ, one instance** (the option taken), matching what
   [ADR-004](004-per-service-data-ownership.md) already decided: table-level ownership on a shared
   instance, not instance-level isolation. Multi-AZ adds an automatic standby failover without
   multiplying instances the way genuine per-service databases would.
5. **Amazon MSK for Kafka** (the option taken), removing broker provisioning, patching, and
   KRaft-cluster operations from the team. This doesn't change the honest trade-off
   [ADR-006](006-kafka-vs-queue.md) already named, Kafka is heavier than this four-service flow
   strictly needs; AWS managing the brokers doesn't make Kafka itself any more necessary here.

**Edge:**

6. **ALB only, no CDN or WAF.** Simplest, but leaves TLS termination and exploit filtering
   (SQLi, XSS rulesets) to happen no earlier than the load balancer, after traffic has already
   reached the VPC.
7. **CloudFront + AWS WAF in front of the ALB** (the option taken). `api-gateway` is the one
   externally-called service and the one carrying the raw OAuth2 password grant off the wire (see
   [ADR-002](002-oauth2-authentication.md)); filtering and TLS termination at the edge defends
   exactly that boundary before it's reached.

**Availability:**

8. **Single Availability Zone.** Simpler network layout, but an ALB can't even be created with
   subnets in only one AZ, that's an AWS hard requirement, so this option was never really
   available once an ALB is in the picture.
9. **Two Availability Zones** (the option taken): ECS tasks, the RDS standby, and a second MSK
   broker each placed in a second AZ. This is the minimum for "Multi-AZ" to mean anything real,
   a single-AZ deployment calling itself highly available isn't.

## Decision

ECS Fargate running all nine existing services as tasks, spread across two Availability Zones,
behind an Application Load Balancer, itself behind CloudFront with an attached AWS WAF Web ACL.
RDS PostgreSQL (Multi-AZ, one instance) and Amazon MSK sit in private subnets in both AZs.
Secrets Manager holds the JWT keypair, DB credentials, and OAuth2 client secret (replacing the
`${ENV_VAR:default}` placeholders described in `CLAUDE.md`'s "Config & secrets" section).
CloudWatch collects the structured logs and Actuator metrics every service already emits (see
`CLAUDE.md`'s "Observability" section), nothing new has to be added to the application for that
to work, only where the logs are shipped changes.

This is a lift of the existing nine services onto managed AWS equivalents of their current local
infrastructure, not a redesign, no service is split, merged, or given new responsibilities to fit
this deployment shape.

## Consequences

- **Good**: every piece of local infrastructure this repo already depends on (Postgres, Kafka, S3,
  env-var secrets, Actuator/logs) has a named AWS managed-service equivalent, so the "how would
  this run in production" question has a concrete, defensible answer rather than a hand-wave.
- **Good**: no new operational surface beyond what AWS itself manages, Fargate needs no cluster,
  MSK needs no broker patching, RDS Multi-AZ failover is automatic.
- **Bad, honestly**: this diagram has no auto-scaling policy, no multi-region failover, and no
  CI/CD pipeline into any of it, it names the target infrastructure shape, not a complete
  production runbook. A real rollout would still need to define scaling triggers, a deployment
  pipeline, and a disaster-recovery story beyond "the standby exists."
- **Bad, honestly**: Fargate's per-task pricing is generally more expensive than equivalent EC2
  capacity at steady, predictable load, the trade is paying more per unit of compute in exchange
  for not operating instances. At nine low-traffic demo services that trade is easy to justify;
  it's worth re-evaluating if traffic or service count grew enough that steady-state EC2 (or EKS
  with EC2 node groups) pricing became materially cheaper.
- **Bad, honestly**: CloudFront in front of an application (rather than static assets) adds a
  cache layer that has to be configured to not cache authenticated, per-user responses, done
  wrong, that's a real bug class (serving one member's cached response to another). This diagram
  doesn't specify those cache-behavior rules, a real rollout would need to get that right before
  it's safe to enable, not after.
