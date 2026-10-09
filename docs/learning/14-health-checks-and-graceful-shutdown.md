# 14 — Health Checks & Graceful Shutdown

The theme: **who decides an instance is dead, who decides it gets traffic, and how does it leave without dropping users?** This is the background for P1-T10 (`spring-boot-starter-actuator`, probes, `server.shutdown=graceful`).

Facts about Spring Boot and AWS behaviour below were checked against the Spring Boot 3.5 reference and the AWS ECS/ELB docs (2026-10-09).

---

## 1. Why actuator, and why only `health`

**The concept.** A load balancer has to answer one question about each task, over and over: *"can I send requests here?"* It does this by calling a URL on a timer. `spring-boot-starter-actuator` gives you that URL (`/actuator/health/...`) and connects it to Spring's own lifecycle. Spring knows when it is still starting, and when it is shutting down, so the URL answers correctly without you writing any code.

Actuator also ships many other endpoints (`env`, `beans`, `heapdump`, `loggers`, `metrics`...). Some of them leak secrets or memory contents. You don't need them, so expose only `health`:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true    # auto-enabled only on Kubernetes, so set it here
management.endpoint.health.show-details=never      # don't tell the internet which DB you use or that it's down
```

That gives you three URLs:

| URL | What it checks by default |
|---|---|
| `/actuator/health` | Everything Spring found: DB, disk space, and so on |
| `/actuator/health/liveness` | Only `LivenessState`, the app's internal "am I broken?" flag |
| `/actuator/health/readiness` | Only `ReadinessState`, the app's internal "am I accepting traffic?" flag |

**In your code.** `SecurityConfig.java` uses an allowlist where every public path has a comment with its reason. The health paths must join it, or the ALB gets a 401 and marks every task unhealthy:

```java
"/actuator/health/**", // the ALB calls this without a token
```

---

## 2. Liveness vs readiness: same instance, different reaction

**The concept.** Both probes ask about **one instance**: one JVM, one ECS task. Neither is about "the whole service". The difference is **what the platform does when the answer is "no"**:

| | Liveness | Readiness |
|---|---|---|
| Question | "Are you broken inside?" | "Should you get requests right now?" |
| On "no" | **Kill** the instance and start a new one | **Stop routing** to it, keep it alive, ask again later |
| Typical "no" | Deadlock, all threads stuck, corrupted state | Still starting up, or shutting down |

The one-line rule to remember:

> **"Will a restart fix it?" → that's liveness. "Will waiting fix it?" → that's readiness.**

---

## 3. Keep liveness free of external checks

This is the part that is easy to read and hard to *feel*. Here it is three ways.

### 3.1 The restaurant

A restaurant has **3 cooks**. The manager has one rule:

> Every 10 seconds, ask each cook "can you cook?". If a cook says no three times in a row, **fire them and hire a new one**.

That's a liveness probe. The manager is ECS, the cooks are your tasks.

Now the **gas company cuts the gas** for 1 minute. That's your database going down.

- All 3 cooks are asked "can you cook?". Honestly, no: there's no gas.
- The manager fires all 3 cooks. Every customer eating at the tables is told to leave. Those customers are your open WebSocket connections.
- 3 new cooks are hired. They need 30 seconds to put on uniforms and learn the kitchen (JVM startup). Then they're asked "can you cook?". Still no gas, so they're fired too.
- The gas comes back. **There is no cook in the kitchen.** The restaurant stays closed until the next batch of cooks is ready.
- When they're ready, every customer who was thrown out rushes back at the same moment.

**Firing the cook never brings the gas back.** The problem was outside the cook. The only thing the rule achieved was emptying the kitchen and the dining room.

The correct rule: fire a cook only if *the cook* is the problem (they fainted, they're frozen). If the gas is out, the cooks wait, and the gas comes back on its own.

### 3.2 The same thing, in DevCool

3 `api` tasks behind the ALB. Aurora does a failover, which is normal during patching or an AZ problem, and is unreachable for 60 seconds.

**❌ Liveness includes the DB check** (`management.endpoint.health.group.liveness.include=livenessState,db`):

| Time | What happens |
|---|---|
| 0s | Aurora becomes unreachable. |
| 0–30s | Each task's liveness calls the DB → `DOWN`. **All 3 at once**, because they all share the same DB. |
| ~30s | The platform sees 3 dead tasks and kills all 3. **Every connected chat user is disconnected.** |
| ~30–60s | 3 new tasks start. Startup itself needs the DB (Hibernate `ddl-auto=validate`). They fail or come up unhealthy, and get killed again. This is a **crash loop**. |
| 60s | Aurora is back. **But no task is running**, so the outage continues. |
| ~90s+ | New tasks finish starting, all at once. Each opens a full HikariCP pool against a DB that just recovered, and thousands of clients reconnect in the same second. |

A 60-second DB blip became 90+ seconds of **total** outage, a lost session for every user, and a load spike on a fragile DB.

**✅ Liveness checks only the JVM itself** (Spring Boot's default):

| Time | What happens |
|---|---|
| 0s | Aurora becomes unreachable. |
| 0–60s | Liveness stays `UP`, because the JVM is fine. Requests that need the DB fail with 5xx. WebSocket connections **stay open**. |
| 60s | Aurora is back. HikariCP reconnects on its own. Everything works again immediately. |

60 seconds of partial errors, then nothing. No restarts, no reconnect storm.

### 3.3 Which failures a restart actually fixes

| Failure | Inside the process? | Does a restart fix it? | So it belongs in... |
|---|---|---|---|
| Deadlock, every request thread stuck | Yes | **Yes** | liveness |
| Leaked memory, constant GC | Yes | **Yes** (for a while) | liveness |
| Still starting up | Yes, but temporary | No, just wait | readiness |
| Shutting down | Yes, intentionally | No, it's leaving | readiness |
| Aurora down | **No** | **No** | neither |
| Valkey down | **No** | **No** | neither |
| S3 or Bedrock slow | **No** | **No** | neither |

The pattern: **a shared external dependency fails for all instances at once.** Any probe that checks it fails for all instances at once, and the platform's reaction then hits all instances at once.

---

## 4. On ECS, the ALB check behaves like liveness too

This is where ECS differs from Kubernetes, and it changes the advice.

**The concept.** Kubernetes has two separate probes with two separate reactions. ECS with an ALB has **one** health check, the ALB target group check, and ECS reacts to it in **both** ways:

1. The ALB stops routing to the unhealthy task. That's the readiness reaction.
2. The ECS service scheduler **stops and replaces** the task the ALB reports unhealthy, once `healthCheckGracePeriodSeconds` has passed. That's the liveness reaction.

So on ECS, whatever URL the ALB checks gets **liveness consequences**. If you point the ALB at `/actuator/health/readiness` and add the DB to the readiness group, you get exactly the restaurant from §3. That's why the rule for this project is:

> **Keep both groups at Spring Boot's defaults. Don't add `db`, `redis` or anything external to either one.**

Spring Boot's defaults already do this. The reference docs say *"By default, Spring Boot does not add other health indicators to these groups"*, and warn that a liveness probe depending on an external system *"might restart all application instances and create cascading failures."* The safe config is the one you don't write.

Two more ECS details:

- **`healthCheckGracePeriodSeconds`** must be longer than the app's startup time. The default is 0, which means a slow-starting task gets killed before it is ready.
- **The ALB "fails open".** If every target is unhealthy, it routes to all of them anyway. A DB check in the ALB path therefore doesn't even protect users; it only triggers the replacements.

> Correction to an earlier chat answer: I said putting the DB in readiness is "mostly pointless behind an ALB". On ECS it's worse than pointless, because of point 2 above.

---

## 5. Graceful shutdown

**The concept.** Every deploy stops the old tasks. Without graceful shutdown, the JVM stops immediately: requests in flight get 5xx, and WebSocket users are cut off mid-message.

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

With this, when Spring receives SIGTERM it:

1. Sets readiness to `REFUSING_TRAFFIC`, and the web server rejects new requests.
2. Waits up to 30 s for in-flight requests to finish.
3. Closes the context: the HikariCP pool, the Valkey client, and so on.

**On ECS, the order of events is** (from the ECS task lifecycle docs):

| ECS state | What happens |
|---|---|
| `DEACTIVATING` | ECS **deregisters the task from the ALB target group** and waits out `deregistration_delay`. No new requests arrive, and existing connections continue. |
| `STOPPING` | ECS **sends SIGTERM**. Spring's graceful shutdown runs. After `stopTimeout` (default 30 s), ECS sends SIGKILL. |

So on ECS, **the ALB has already stopped sending traffic before Spring hears anything.** The readiness flip in step 1 matters less here than on Kubernetes. The part that does matter is step 2: finishing what's in flight before SIGKILL.

`stopTimeout` must be **≥ `timeout-per-shutdown-phase`**. Otherwise ECS kills the JVM in the middle of its graceful wait.

### Open question for P2: the WebSocket drain order

`01-system-architecture.md` §4.5 says:

> 2. ECS sends SIGTERM to an old task. Spring's graceful shutdown flips readiness to `REFUSING_TRAFFIC`, so the ALB stops sending new upgrades.
> 3. The task sends every local socket `RECONNECT {afterMs: random(0..10000)}` and closes it with code 1012.

Given the ECS order above, steps 2 and 3 happen **after** the `deregistration_delay` has already passed, not before it. The doc should describe that order. It also needs to answer one question: does the ALB keep the open WebSocket connections alive through the whole delay, or cut them when it ends? If it cuts them, the `RECONNECT` with jitter would arrive too late, and every client would drop at the same moment. Settle this when the ALB and ECS Terraform is written in P2, and fix §4.5 to match.

---

## Where else this applies

The general idea: **an automatic reaction to a shared dependency failing hits every instance at the same moment.**

- **Retries.** If every client retries a failed call immediately, the recovering service gets hit by all of them at once. That's why `RECONNECT` carries a random `afterMs`, and why retries need jittered backoff.
- **Circuit breakers** are the opposite tool. They make an instance *stop* calling a broken dependency, instead of restarting the instance.
- **The `worker` service (ADR-0001)** has no ALB. If it ever gets an ECS container health check, the same rule applies: check the JVM, not SQS or Aurora.
- **Startup.** A task that needs the DB to *start* (Hibernate `ddl-auto=validate`) can't come up while the DB is down. That is fine, as long as no probe turns the "can't start yet" into a kill loop. This is what `healthCheckGracePeriodSeconds` is for.
