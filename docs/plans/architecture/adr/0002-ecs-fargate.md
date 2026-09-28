# 0002 — ECS Fargate for compute

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P2

## Context
The app is containerized (`Dockerfile`), there's an `ecs` Spring profile, and the README describes a manually managed ECS service. It needs horizontal scaling, long-lived WebSocket connections, and the ability to scale to zero when idle.

## Decision drivers
- WebSocket support behind a load balancer.
- Low ops burden for one person.
- Autoscaling on custom metrics.
- Scale to zero when idle.

## Options considered

### A — ECS on Fargate
- Pros: no nodes or control plane. ALB handles WS natively. Target tracking on CPU and custom CloudWatch metrics. `desiredCount = 0` for idle.
- Cons: AWS-specific. Fewer scheduling knobs than Kubernetes. Per-vCPU price is higher than EC2.

### B — EKS
- Pros: Kubernetes is the most portable skill. HPA/KEDA, a rich ecosystem.
- Cons: control plane ~$73/month before any pod, plus add-ons, upgrades and ingress controllers. Weeks of work that don't produce chat features.

### C — App Runner
- Pros: simplest possible. Scales to near zero.
- Cons: limited networking and WebSocket control. No sidecars (no OTel collector). A weak story for a chat system.

### D — Lambda + API Gateway WebSocket API
- Pros: true scale-to-zero, per-message pricing.
- Cons: a completely different programming model (connection ids in DynamoDB, `postToConnection`). Throws away the existing handler. Spring cold starts.

## Decision
**A, ECS Fargate.** Two services (`api`, `worker`) in private subnets behind an ALB, with an OTel collector sidecar per task.

## Consequences
- ALB idle timeout, deregistration delay and ECS `stopTimeout` must fit the WS heartbeat and drain (see [03 §11](../03-chat-system-design.md#11-deploys-failure-and-scaling)).
- Deployment circuit breaker + rollback on. ECS native blue/green is an upgrade path.
- Spring Boot's graceful shutdown plus readiness probes are required.

## Revisit when
- You need Kubernetes experience specifically, or multi-cloud.
- Fargate cost dominates the bill under steady load (move to ECS on EC2 capacity providers, not EKS).
