# 07 — AWS compute, edge and networking

> **Used in DevCool:** [ADR-0002](../plans/architecture/adr/0002-ecs-fargate.md) (ECS Fargate) · [ADR-0011](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) (single CloudFront origin) · [01 §3, §4.5, §6](../plans/architecture/01-system-architecture.md#3-container-view) · [Phase 2](../plans/phases/phase-2-infra-cicd-walking-skeleton.md) · P4-T14–T15 (drain, autoscaling) · P9-T10 (cost)
> **Also see:** [infra/CLAUDE.md](../../infra/CLAUDE.md) and the `aws-architect` agent
> **Links checked:** 2026-09-29

## Concepts to own

- **ECS building blocks.** Cluster → service (desired count, deployment config, load balancer) → task definition (containers, CPU/memory, roles, secrets) → task. Fargate removes the EC2 hosts.
- **Task role vs execution role.** The *execution* role lets ECS pull the image, write logs and read the secrets it injects. The *task* role is what your code runs as (S3, SNS, Bedrock). Least privilege applies to both.
- **Rolling deploys and the circuit breaker.** New tasks must pass ALB health checks before old ones are drained; the deployment circuit breaker rolls back a deploy whose tasks keep failing.
- **Draining a WebSocket service.** ALB `deregistration_delay`, container `stopTimeout` (SIGTERM → SIGKILL window), and Spring's graceful shutdown must fit together with the app's `RECONNECT` + close 1012 ([01 §4.5](../plans/architecture/01-system-architecture.md#45-deploy-with-graceful-websocket-drain)).
- **ALB and WebSockets.** ALB supports WS natively; the connection idle timeout (default 60 s) closes quiet sockets, so the heartbeat must be shorter. Health checks hit `/actuator/health/readiness`.
- **Service auto scaling.** Target tracking on CPU and on a custom CloudWatch metric (`ws.connections.active` per task). Scale-in removes tasks, which means drained sockets.
- **CloudFront as a single origin.** Behaviours route `/*` to S3 (cached, OAC) and `/api/*` + `/ws` to the ALB with caching disabled and viewer headers, cookies and query strings forwarded. One origin means no CORS and a `SameSite=Strict` refresh cookie.
- **Certificates and DNS.** CloudFront certs must be in ACM `us-east-1`; Route 53 alias records point the apex/subdomain at CloudFront.
- **VPC layout.** Public subnets for the ALB and NAT, private subnets for tasks, Aurora and Valkey across 2 AZs. Gateway endpoints (S3) are free; interface endpoints (ECR, Logs, Secrets Manager, Bedrock) cost per hour but remove NAT traffic.
- **Security groups as the firewall graph.** ALB → api:8080; api/worker → Aurora:5432 and Valkey:6379; nothing inbound to tasks from the internet.
- **Secrets and config.** Secrets Manager for secrets (injected via the task definition `secrets` field), SSM Parameter Store for non-secret config (collector YAML).
- **Cost levers.** NAT gateways and ALBs bill hourly even when idle; Fargate bills per vCPU/GB-second; desired count 0 and "hibernate" workflows remove most idle cost.

## Read first

1. [Amazon ECS task definition parameters](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html) — *AWS docs* · Container definitions: `secrets`, `stopTimeout`, `essential`, `dependsOn`, health checks. Everything P2-T04 and P7-T06 set.
2. [Graceful shutdowns with ECS](https://aws.amazon.com/blogs/containers/graceful-shutdowns-with-ecs/) — *AWS Containers Blog* · SIGTERM, `stopTimeout`, and the ALB deregistration sequence, explained end to end.
3. [Target group attributes — deregistration delay](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/edit-target-group-attributes.html#deregistration-delay) — *AWS docs* · How long the ALB keeps an old target for in-flight connections.
4. [Using WebSockets with CloudFront distributions](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/distribution-working-with.websockets.html) — *AWS docs* · Requirements for WS through CloudFront (headers, origin protocol).
5. [Use managed cache policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html) and [managed origin request policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-origin-request-policies.html) — *AWS docs* · `CachingDisabled` and `AllViewerExceptHostHeader`, exactly as ADR-0011 specifies.

## Reference

### ECS and Fargate

- [AWS Fargate for Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html) — *AWS docs* · Platform versions, task sizes, networking mode `awsvpc`.
- [Deployment circuit breaker](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html) — *AWS docs* · Failure thresholds and automatic rollback.
- [Rolling update deployments](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-ecs.html) — *AWS docs* · `minimumHealthyPercent` / `maximumPercent`.
- [Task IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html) and [Task execution IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html) — *AWS docs* · The two roles, side by side.
- [Pass Secrets Manager secrets through environment variables](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html) — *AWS docs* · The `secrets` field and the execution-role permission it needs.
- [Target tracking scaling policies for ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-targettracking.html) — *AWS docs* · Scaling on CPU and custom metrics (P4-T15).
- [Amazon ECS best practices guide](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-best-practices.html) — *AWS docs* · Networking, security, scaling and deployment advice.

### Application Load Balancer

- [ALB listeners (WebSockets section)](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html) — *AWS docs* · WS support over HTTP/HTTPS listeners.
- [Load balancer attributes — connection idle timeout](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/edit-load-balancer-attributes.html#connection-idle-timeout) — *AWS docs* · The 60 s default the heartbeat must beat.
- [Target group health checks](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html) — *AWS docs* · Intervals and thresholds; pair with Spring's readiness probe.
- [Implementing health checks](https://aws.amazon.com/builders-library/implementing-health-checks/) — *Amazon Builders' Library* · Shallow vs deep health checks, and why "Valkey down" should not fail the check (03 §11).

### CloudFront, ACM, Route 53

- [Restrict access to an Amazon S3 origin (OAC)](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html) — *AWS docs* · The SPA bucket stays private.
- [Custom error responses](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/GeneratingCustomErrorResponses.html) — *AWS docs* · Serving `index.html` for SPA routes.
- [Invalidating files](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html) — *AWS docs* · The frontend deploy step (P5-T14).
- [Requirements for using SSL/TLS certificates with CloudFront](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-requirements.html) — *AWS docs* · Why the CloudFront cert must be requested in `us-east-1`.
- [Choosing between alias and non-alias records](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resource-record-sets-choosing-alias-non-alias.html) — *AWS docs* · Alias to CloudFront.

### VPC and networking

- [VPC with servers in private subnets and NAT](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html) — *AWS docs* · The reference layout the `network` stack builds.
- [NAT gateways](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html) — *AWS docs* · Per-AZ NAT, pricing, and the "toggle" in P2-T02.
- [Gateway endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/gateway-endpoints.html) and [interface endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/create-interface-endpoint.html) — *AWS docs* · Keeping S3/ECR/Secrets/Bedrock traffic off the NAT.
- [ECR interface VPC endpoints](https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html) — *AWS docs* · The exact endpoints Fargate needs to pull images without NAT.
- [Security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html) — *AWS docs* · Referencing security groups instead of CIDRs.

### Secrets, registry, storage

- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html) and [SSM Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html) — *AWS docs* · Which to use for what.
- [ECR image scanning](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning.html) and [lifecycle policies](https://docs.aws.amazon.com/AmazonECR/latest/userguide/LifecyclePolicies.html) — *AWS docs* · Scan on push, expire old images.
- [S3 presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) — *AWS docs* · Presigned PUT for browser uploads (P3-T14) and GET for media.

### Security, cost, architecture review

- [IAM security best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html) — *AWS docs* · Least privilege, roles over keys.
- [Managing your costs with AWS Budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html) — *AWS docs* · The budget alarm in P2-T13.
- [AWS Fargate pricing](https://aws.amazon.com/fargate/pricing/) — *AWS* · Numbers for the P9-T10 cost report.
- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html) — *AWS docs* · The six pillars; the `aws-architect` agent reviews against these.

## Self-check

- List the steps between "ECS decides to stop task X" and "task X's process exits". Which three timeouts must line up?
- What does the execution role need that the task role doesn't, and vice versa?
- Why does `/api/*` need `CachingDisabled` *and* an origin request policy? What breaks if you forward the `Host` header to the ALB?
- Why does the CloudFront certificate have to be in `us-east-1` when everything else is in `ap-southeast-1`?
- Which traffic still needs the NAT gateway after you add S3, ECR, Logs, Secrets Manager and Bedrock endpoints? (Hint: Grafana Cloud.)
- How does scaling on `ws.connections.active` differ from scaling on CPU for a WebSocket service?
- Which resources cost money when desired count is 0? Which ones does "hibernate" destroy?
