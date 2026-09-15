# ADR-001: Service discovery (Eureka) and an API gateway, not direct service-to-service calls

## Status

Accepted.

## Context

Five (now nine) services need to find each other's network locations, and a browser/mobile
client needs one predictable entry point rather than five different hosts/ports. Locations
change across restarts and environments, so hardcoding hostnames in config isn't viable.

## Options considered

1. **Hardcoded hostnames/ports in each service's config.** Simplest to build, but breaks the
   moment a service moves, and gives external clients no single entry point; they'd need to
   know every service's address and port directly.
2. **A service mesh (e.g. Istio/Linkerd).** Handles discovery, routing, retries, and mTLS
   uniformly at the infrastructure layer. Powerful, but a large operational surface (sidecar
   proxies, a control plane) to introduce for a demo-scale system with five to nine services.
3. **Netflix Eureka for discovery plus Zuul as an edge gateway** (the option taken). Services
   register themselves with Eureka on startup and discover each other by logical name; Zuul
   routes external traffic to the right service by path prefix and is the only service meant to
   be called from outside the cluster.

## Decision

Use Eureka for service discovery and Zuul as the single external-facing API gateway.
`eureka.client.service-url.default-zone` points every service at the naming server; Zuul's
routes are derived from service names it discovers there, not hardcoded hostnames.

## Consequences

- **Good**: a client only needs to know one address (`api-gateway`, port 8765). Services can
  scale, move, or restart without any config change elsewhere. Adding a new service means
  registering it with Eureka, with no gateway route table to hand-edit for the common case.
- **Bad, honestly**: both Eureka and Zuul (Netflix OSS) are in maintenance mode. Spring Cloud
  has been steering toward Kubernetes-native discovery and Spring Cloud Gateway for new projects.
  This repo keeps them because they're what the original code used and they still work correctly
  on this JDK/Spring Cloud version; a from-scratch system today would likely pick Spring Cloud
  Gateway plus Kubernetes DNS-based discovery instead.
- **Bad, honestly**: Eureka's registry is itself a single point of failure in this setup.
  `eureka.client.register-with-eureka=false` on the naming server itself means there's no
  peer-replication, matching the demo-scale, single-node nature of the whole system, not a
  masked production gap.
- The purchase-flow services (`order-service` and friends) still register with Eureka for their
  own inbound REST endpoints, but talk to *each other* over Kafka, never through Zuul or direct
  HTTP; see ADR-005 for why.
