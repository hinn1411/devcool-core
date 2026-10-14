# 18 — Docker & ECS

The theme: **what is actually inside the image you ship, and what does ECS do with it?** This is the background for P2-T06 (the production `Dockerfile`) and for the ECS service in P2-T04.

Facts about Spring Boot and AWS below were checked against the Spring Boot 3.5 reference and the AWS ECS docs (2026-10-15).

---

## 0. The ideas you already know

| You already know… | The Docker version |
|---|---|
| A **class** is a template; an **object** is a running instance with its own state | An **image** is a template; a **container** is a running instance with its own writable layer |
| A fat jar bundles your code with every dependency | An image bundles the jar *and* the JRE *and* an OS userland |
| Maven's `~/.m2` cache: an unchanged dependency is not downloaded again | Docker's **layer cache**: an unchanged build step is not run again |
| `pom.xml` declares how to build the jar | A `Dockerfile` declares how to build the image |
| Maven Central stores versioned artifacts | A **registry** (ECR, Docker Hub) stores versioned images |
| A `SNAPSHOT` version can change under you; a release can't | A **tag** (`21-jre`) can move; a **digest** (`@sha256:…`) can't |
| Flyway / Terraform: describe the target, a tool makes it real ([11](11-flyway-and-schema-management.md), [16](16-iac-fundamentals.md)) | A **task definition** describes the container; ECS keeps that many running |

The one sentence that carries the chapter: **an image is an immutable, layered artifact, and a container is just a Linux process with a restricted view of the machine.** Most Docker problems make sense once you take that literally.

---

## 1. The core parts

### 1.1 A container is a process, not a small VM

There is no guest kernel. `java` inside a container is an ordinary process on the host kernel, fenced in by two kernel features:

| Feature | What it limits | You see it when… |
|---|---|---|
| **Namespaces** (pid, net, mnt, uts, ipc, user) | What the process can **see**: its own PID 1, its own network interfaces, its own filesystem root, its own hostname | `ps` inside the container shows only your processes |
| **cgroups** | What the process can **use**: memory, CPU shares, PIDs | Exceed the memory limit and the kernel OOM-kills the process (exit code 137) |

Two consequences you'll meet in P2:

- **The JVM must read the cgroup limit, not the host's RAM.** Java 21 does this by default (`UseContainerSupport`), but its default heap is only **25 %** of that limit. That's why P2-T06 sets `-XX:MaxRAMPercentage=75` (§3.4).
- **Your app is PID 1.** PID 1 is the process that receives the stop signal. If it is a shell instead of `java`, the signal never reaches Spring (§3.3).

### 1.2 Image and layers

An image is an ordered stack of **read-only layers** plus a JSON config (entrypoint, env, user, exposed ports). Each filesystem-changing Dockerfile instruction (`RUN`, `COPY`, `ADD`) produces one layer: a tarball of what changed.

```
┌───────────────────────────────┐  container's writable layer (copy-on-write, lost on removal)
├───────────────────────────────┤
│ COPY application/   (~100 KB) │  changes every commit
│ COPY dependencies/  (~80 MB)  │  changes when pom.xml changes
│ JRE                           │  changes when the base image is bumped
│ Ubuntu userland               │
└───────────────────────────────┘  read-only, shared by every container from this image
```

The filesystem you see inside the container is these layers merged by **overlayfs**. Writing to a file copies it up into the container's writable layer. Delete the container and that layer is gone, which is why anything that must survive goes in a **volume** or, on AWS, in S3/Aurora.

Layers are content-addressed (by SHA-256) and shared. Two images on the same base store the base once; a push uploads only the layers the registry doesn't have yet.

### 1.3 Registry, tags and digests

| Name | Example | Mutable? | Use it for |
|---|---|---|---|
| Tag | `eclipse-temurin:21-jre` | **Yes.** The publisher moves it on every patch release | Reading, and humans |
| Digest | `eclipse-temurin:21-jre@sha256:3f1c…` | No. It is the hash of the manifest | Pinning what you build *from* and what you deploy |

`latest` is just a tag that nobody updates on purpose. For DevCool's own images, tag with the **git SHA** and turn on ECR **tag immutability**, so `devcool:abc1234` always means one exact build.

### 1.4 Engine and runtime

```
docker CLI ──► dockerd ──► containerd ──► runc ──► your process
 (client)     (API, build,   (image store,  (sets up namespaces
               networks)      lifecycle)     + cgroups, then exec)
```

The image format and runtime are open standards (**OCI**). That's why an image built with Docker runs unchanged on Fargate, which uses containerd and never runs `dockerd`. The practical consequence: **Docker-only features don't exist on ECS.** The Dockerfile `HEALTHCHECK` is one of them (§3.6).

### 1.5 Storage: volumes, bind mounts, tmpfs

| Kind | What it is | DevCool example |
|---|---|---|
| **Named volume** | Storage Docker manages, outlives the container | `postgres:/var/lib/postgresql/data` in `docker/local/compose.yaml`. `docker compose down` keeps your data; `down -v` deletes it |
| **Bind mount** | A host directory mounted in | Not used; handy for mounting `docker/local/seed` read-only |
| **tmpfs** | RAM-backed, gone on stop | Scratch space for a read-only root filesystem |

On Fargate, a task gets **20 GiB of ephemeral storage** by default (up to 200 GiB). Treat it as scratch: it disappears with the task.

### 1.6 Networking

| Concept | What it means | Where you see it |
|---|---|---|
| **User-defined bridge network** | Containers on it reach each other **by service name** through Docker's DNS | Compose puts `postgres` and `pgadmin` on one; pgAdmin's `servers.json` uses `"Host": "postgres"` |
| **Port publishing** `host:container` | Opens a host port and forwards it to the container | `"5432:5432"` is how the Spring app on your laptop reaches Postgres |
| `EXPOSE` | **Documentation only.** It publishes nothing | The current `Dockerfile`'s `EXPOSE 8080` |
| `localhost` inside a container | The container itself, **not** your laptop | The classic "connection refused to localhost:5432 from inside a container" |

On ECS with Fargate the model is different and simpler: every task gets its **own ENI and private IP** (`awsvpc` mode, §4.3).

---

## 2. Building images

### 2.1 The instructions that matter

| Instruction | What it does | Watch out for |
|---|---|---|
| `FROM image AS name` | Starts a stage from a base image | Tags move; pin by digest (§3.1) |
| `WORKDIR /app` | `cd`, creating the dir | Prefer it over `RUN cd …`, which doesn't persist |
| `COPY src dst` | Copies from the **build context** (or `--from=stage`) | `--chown=` to avoid a separate `chown` layer |
| `ADD` | `COPY` plus URL download and tar extraction | Use `COPY` unless you need the extras |
| `RUN cmd` | Runs at **build** time, saves the result as a layer | Files deleted in a later `RUN` still sit in the earlier layer |
| `ARG` | Build-time variable | Visible in `docker history`. **Never a secret** |
| `ENV` | Runtime env var baked into the image | Also visible. Task definitions override it |
| `USER` | Who later `RUN`s and the container run as | The default is root (§3.2) |
| `ENTRYPOINT` / `CMD` | What runs at start; `CMD` is default args, easy to override | Use the **exec form** `["java", …]` (§3.3) |
| `EXPOSE` | Metadata only | — |
| `HEALTHCHECK` | Docker's own probe | ECS ignores it (§3.6) |

### 2.2 The build context and `.dockerignore`

`docker build .` first sends **the whole directory** to the builder. Only then does the Dockerfile decide what to `COPY`.

DevCool has **no `.dockerignore`**, and the repo root holds `local.env` (real JWT secrets), `target/`, `.git/`, `frontend/` (with `node_modules/`) and `infra/`. Today nothing leaks, because the Dockerfile copies only `pom.xml` and `src`. But:

- Every build ships all of that to the builder, which makes builds slower than they need to be.
- The day someone writes `COPY . .`, `local.env` is baked into a layer and pushed to ECR. Deleting it in a later step doesn't help (§2.1, `RUN` row).

The fix is an allow-list `.dockerignore`. Exclude everything, then re-include what the build needs:

```
*
!pom.xml
!mvnw
!.mvn/
!src/
```

An allow-list fails safe: a new secrets file added next year is excluded without anyone remembering to add it.

### 2.3 How the layer cache decides

For each instruction, the builder reuses the cached layer if **(a)** every layer before it was reused, and **(b)** the instruction is identical and, for `COPY`/`ADD`, the copied files have the same checksums. The first miss invalidates **everything after it**.

So the rule is: **order instructions from least to most frequently changing.** Look at the current build stage with that rule:

```dockerfile
COPY pom.xml .
COPY src ./src                      # ← changes on every commit
RUN mvn -DskipTests clean package   # ← so this re-downloads every dependency, every build
```

Copying `pom.xml` first was the right instinct, but nothing uses it before `src` arrives. The cache-friendly order resolves dependencies in their own layer:

```dockerfile
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline          # cached until pom.xml changes
COPY src src
RUN ./mvnw -B -DskipTests package
```

`dependency:go-offline` doesn't catch every plugin dependency, so some downloads still happen in the last step. The stronger tool is a **BuildKit cache mount** (BuildKit is the default builder since Docker Engine 23):

```dockerfile
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package
```

The cache mount keeps `~/.m2` across builds **without** putting it in any layer. In CI, where every runner starts empty, you need `docker/build-push-action` with `cache-from/cache-to: type=gha` for either technique to pay off.

### 2.4 Multi-stage builds

A stage is a throwaway image. Only the **last** stage is what you ship. The current file already does this right: Maven and the JDK live in `build`, and only the jar is copied into the JRE image. The build tools never reach production, which means a smaller image and less to scan.

Two more uses of stages: an `extract` stage for the layered jar (§2.5), and a `test` stage CI can target with `--target`.

### 2.5 Spring Boot's layered jar

A fat jar is **one file**, so it is **one layer**. A one-line code change produces a new ~80 MB layer to push and store, even though 99 % of it is unchanged dependencies.

Spring Boot jars carry a `BOOT-INF/layers.idx` that splits the content into four layers, from least to most volatile: `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`. Since Boot 3.3 you extract them with the **`tools`** jar mode. The older `layertools` mode is deprecated, and most blog posts still show it:

```dockerfile
FROM eclipse-temurin:21-jre AS extract
WORKDIR /builder
COPY --from=build /app/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "application.jar"]
```

`application.jar` in the final stage is a thin jar whose manifest points at the extracted `lib/`. The layout is also what Class Data Sharing (CDS) needs if you later want faster startup.

**What it buys on Fargate, honestly:** less than you'd think at pull time. **Fargate caches no image layers.** Every task pulls the whole image, every time. The gains are elsewhere:

- CI pushes only the small `application` layer (seconds, not a full upload).
- ECR stores the dependency layer once across hundreds of commits.
- Local rebuilds and EC2-backed hosts (which do cache) reuse layers.

For Fargate start time, the levers are **image size** and an **ECR VPC endpoint** in the same Region. P2-T02 adds the endpoints.

### 2.6 What the current `Dockerfile` gets right and wrong

| Line | Verdict |
|---|---|
| `FROM maven:3.9-eclipse-temurin-21 AS build` | ✅ Multi-stage. ⚠️ Tag not pinned. ⚠️ Uses the image's `mvn`, not the repo's `./mvnw`, so the Maven version can differ from CI's |
| `COPY pom.xml .` then `COPY src ./src` | ⚠️ No dependency layer, so every build re-downloads everything (§2.3) |
| `RUN mvn -DskipTests clean package` | ✅ Skipping tests is right here; CI already ran them. `clean` is pointless in a fresh stage |
| `FROM eclipse-temurin:21-jre` | ✅ JRE, not JDK. ⚠️ Tag not pinned |
| `COPY --from=build /app/target/*.jar app.jar` | ⚠️ One fat-jar layer (§2.5) |
| *(no `USER`)* | ❌ Runs as root (§3.2) |
| *(no JVM flags)* | ❌ Heap capped at 25 % of the task's memory (§3.4) |
| `ENTRYPOINT ["java","-jar","app.jar"]` | ✅ Exec form, so `java` is PID 1 and receives SIGTERM (§3.3) |
| *(no `.dockerignore`)* | ❌ §2.2 |

---

## 3. Best practices and when to apply them

Each practice below exists to prevent a specific failure. Learn the failure and the rule follows.

| # | Practice | Prevents | Apply when |
|---|---|---|---|
| 3.1 | Pin base images by digest; let Dependabot/Renovate bump them | A rebuild silently pulls a new JRE patch; "same commit, different image" | Always, for anything deployed |
| 3.2 | Run as a non-root user | A container escape or file-write bug lands as UID 0 | Always. Fargate also lets you go further with `readonlyRootFilesystem` |
| 3.3 | Exec-form `ENTRYPOINT`, app as PID 1 | SIGTERM goes to `/bin/sh`, not Java; graceful shutdown never runs | Always. Add `initProcessEnabled` (tini) only if the app spawns child processes |
| 3.4 | JVM sized from the container limit | Heap too small (default 25 %), or too big and OOM-killed (exit 137) | Every JVM image |
| 3.5 | No secrets in the image | Credentials readable by anyone who can pull | Always. Inject at runtime (§4.5) |
| 3.6 | No Dockerfile `HEALTHCHECK` for an ALB service | A probe ECS ignores, plus `curl` in the image | ECS + ALB. Do use one in Compose (`depends_on: service_healthy`) |
| 3.7 | Minimal runtime image (JRE, slim, or distroless) | Large pulls, slow Fargate starts, a big CVE surface | Production. Distroless has no shell, which makes debugging harder |
| 3.8 | Lint (hadolint) and scan (Trivy / ECR scanning) in CI | The mistakes in this table, and known CVEs in the base | Every PR that touches the `Dockerfile`, and on a schedule |
| 3.9 | One process per container; sidecars for the rest | Mixing the OTel collector into the app's lifecycle | ECS tasks: the collector is its own container in the same task (ADR-0002, ADR-0008) |
| 3.10 | Build once, promote the same digest | "Works in dev" because prod got a different build | Deploys: dev and prod run the same `sha256` |
| 3.11 | Build for the target CPU architecture | `exec format error` on start | If tasks run on ARM64 (Graviton, cheaper on Fargate), build `--platform linux/arm64` or multi-arch |

### 3.2 Non-root, concretely

```dockerfile
RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app
```

Put `USER` after the `COPY` steps, and use `COPY --chown=app:app` if the app must write there. Hadolint's DL3002 ("last USER should not be root") enforces this.

### 3.3 PID 1 and the stop signal

| `ENTRYPOINT` | PID 1 | On `docker stop` / ECS stop |
|---|---|---|
| `["java", "-jar", "app.jar"]` (exec form) | `java` | SIGTERM reaches the JVM; Spring's graceful shutdown runs ([14 §5](14-health-checks-and-graceful-shutdown.md)) |
| `java -jar app.jar` (shell form) | `/bin/sh -c` | `sh` doesn't forward SIGTERM. Nothing happens until `stopTimeout`, then **SIGKILL**: in-flight requests cut, WebSockets dropped without `RECONNECT` |

If you ever need a wrapper script, end it with `exec java …` so the JVM replaces the shell.

### 3.4 Sizing the JVM

Set it with `JAVA_TOOL_OPTIONS`, which every JVM reads at start (it logs `Picked up JAVA_TOOL_OPTIONS`) and which the task definition can override without a rebuild:

```dockerfile
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
```

Why 75 and not 100: the heap is not the whole JVM. Metaspace, thread stacks (one per Tomcat thread), the code cache, GC structures and direct buffers live outside it. Leave about a quarter for them, or the **kernel** kills the process before the JVM ever reports `OutOfMemoryError`.

`ExitOnOutOfMemoryError` makes a heap OOM exit the process instead of limping on half-broken. ECS then replaces the task.

**The sidecar trap.** The percentage is taken of the limit the JVM *sees*. With an OTel collector in the same task (ADR-0008), give the app container its own `memory` in the task definition. Otherwise 75 % is computed from the **whole task**, and the app and the collector compete for the same memory.

### 3.6 Why no `HEALTHCHECK` here

ECS only runs health checks defined **in the task definition**; it ignores the one in the image. For the `api` service the ALB target-group check is already the one that matters ([14 §4](14-health-checks-and-graceful-shutdown.md)). Adding a Dockerfile check would mean installing `curl` into a JRE image only to get a probe that ECS never runs.

Compose is different: `docker/local/compose.yaml` uses a `healthcheck` on Postgres so pgAdmin waits with `depends_on: condition: service_healthy`. Same feature, a platform that uses it.

---

## 4. AWS ECS

### 4.1 The objects

| Object | What it is | Backend analogy |
|---|---|---|
| **Cluster** | A namespace for services and tasks. On Fargate, no machines to manage | A Spring application context: a container for beans |
| **Task definition** | Versioned, **immutable** JSON: image, CPU/memory, env, secrets, roles, ports, logging. Each change is a new **revision** | A class |
| **Task** | One running copy of a task definition: one or more containers sharing a network and lifecycle | An object |
| **Service** | Keeps N tasks of a revision running, replaces failures, registers them with the ALB, runs deploys | A connection pool that keeps N connections healthy |
| **Container definition** | One container inside the task definition, with `essential: true/false` | A field of the class |

A deploy is: push image `devcool:<sha>` → register a new task-definition revision pointing at it → `UpdateService` to that revision. Rolling back means pointing the service at the previous revision again.

### 4.2 Fargate vs EC2 launch type

| | Fargate (ADR-0002) | EC2 |
|---|---|---|
| Who runs the hosts | AWS. Each task gets its own single-use, single-tenant instance | You: AMIs, patching, scaling the fleet |
| Sizing | Fixed CPU/memory pairs per task, e.g. 0.25 vCPU with 0.5–2 GB, 1 vCPU with 2–8 GB | Bin-packing onto instances |
| Image cache | **None**. Full pull every task start | Layers cached on the host |
| Pricing | Per vCPU-second and GB-second while the task runs; `desiredCount = 0` costs nothing | Per instance, used or not |
| Spot | Fargate Spot: ~2-minute warning, then SIGTERM | EC2 Spot |

ADR-0002's "revisit when" says it: move to EC2 capacity providers only if Fargate cost dominates under steady load.

### 4.3 Networking: `awsvpc`

Fargate requires the `awsvpc` network mode. Each task gets **its own ENI and private IP** in your subnet, with its own security groups. That means:

- The ALB target group uses target type **`ip`** and registers task IPs directly, not instances.
- Security groups work per task: the `api` SG allows 8080 only from the ALB SG; Aurora's SG allows 5432 only from the `api` and `worker` SGs.
- Tasks in **private subnets** need a path to ECR, CloudWatch Logs and Secrets Manager. That's a NAT gateway, or VPC endpoints: `ecr.api`, `ecr.dkr`, `logs`, `secretsmanager`, plus the **S3 gateway endpoint**, because ECR stores layer blobs in S3. This is exactly P2-T02's endpoint list, and why the NAT can be toggled off.

### 4.4 Two IAM roles, and why it matters which is which

| Role | Used by | When | DevCool needs |
|---|---|---|---|
| **Task execution role** | The ECS/Fargate **agent** | **Before** and around your app: pulling the image, writing logs, fetching `secrets` | ECR pull, `logs:CreateLogStream/PutLogEvents`, `secretsmanager:GetSecretValue` + `kms:Decrypt` on the JWT and DB secrets only |
| **Task role** | **Your code** (AWS SDK picks it up automatically) | While the app runs | `s3:PutObject/GetObject` on the media bucket; later SNS/SQS for the `worker` |

The classic bug is the mirror image: putting S3 permissions on the execution role and getting `AccessDenied` from the app, or the reverse, the task stuck in `PENDING` with `ResourceInitializationError` because the *execution* role can't read a secret. Keep both least-privilege; each service (`api`, `worker`) gets its own task role.

### 4.5 Configuration and secrets

| Mechanism | How | Use for |
|---|---|---|
| `environment` | Plain key/values in the task definition | `SPRING_PROFILES_ACTIVE=ecs`, non-secret settings |
| `secrets` | `valueFrom` = a Secrets Manager or SSM Parameter Store ARN. The agent fetches it **at task start** and injects it as an env var | `DB_PASSWORD`, JWT secrets (P2-T03) |

Two consequences. First, a rotated secret reaches the app only on the **next task start**, so rotation needs a new deployment (`--force-new-deployment`). Second, the image stays secret-free and identical across environments, which is what makes §3.10 possible.

### 4.6 Logs

The `awslogs` driver sends stdout/stderr to a CloudWatch log group. So the app logs to **stdout**, never to files inside the container: they'd vanish with the writable layer and nobody would ship them. Later, the OTel collector sidecar takes over traces and metrics (ADR-0008).

### 4.7 Deploys, health and stopping

A rolling deploy with `minimumHealthyPercent=100` and `maximumPercent=200` starts new tasks first and stops old ones only once the new ones are healthy. On Fargate there is no fleet capacity to save, so that's the sensible setting.

| Setting | What it does | DevCool |
|---|---|---|
| ALB health check + `healthCheckGracePeriodSeconds` | Unhealthy tasks are pulled from the ALB **and** replaced; the grace period covers JVM start | [14 §4](14-health-checks-and-graceful-shutdown.md): check liveness, never the DB |
| **Deployment circuit breaker** with `rollback=true` | If new tasks keep failing, ECS stops the deploy and returns to the last good revision | On (ADR-0002) |
| `deregistration_delay` | Time the ALB keeps draining a task before ECS sends SIGTERM | Tied to the WebSocket drain ([14 §5](14-health-checks-and-graceful-shutdown.md)) |
| `stopTimeout` | SIGTERM → SIGKILL gap. Default 30 s, Fargate max 120 s | Must be ≥ `spring.lifecycle.timeout-per-shutdown-phase` (30 s in `application.properties`), with margin |

### 4.8 One image, three roles

ADR-0001 runs one image as different things. ECS makes that cheap: the image is the same, only the task definition differs.

| Role | ECS shape | What differs |
|---|---|---|
| `api` | Service behind the ALB, autoscaled | `SPRING_PROFILES_ACTIVE=ecs`, port mapping, target group |
| `worker` | Service, **no** load balancer | A `worker` profile (ADR-0001; the exact name is set when the worker is built). No ALB, so no web probe at all ([14](14-health-checks-and-graceful-shutdown.md), "Where else") |
| `migrate` | **One-off task** (`RunTask`), not a service, before each deploy | `SPRING_PROFILES_ACTIVE=ecs,migrate` (P2-T05). Runs Flyway, exits. The pipeline waits for exit code 0 before `UpdateService` (P2-T08) |

This only works if the image has **no role baked in**: no profile in `ENTRYPOINT`, nothing environment-specific in layers. Configuration comes from the task definition.

---

## 5. What P2-T06 needs from this chapter

| P2-T06 says | Section | Also worth doing in the same change |
|---|---|---|
| Non-root user | §3.2 | `COPY --chown` instead of a `chown` layer |
| `JAVA_TOOL_OPTIONS` with `-XX:MaxRAMPercentage=75` | §3.4 | `-XX:+ExitOnOutOfMemoryError`; set the app container's own `memory` in P2-T04 |
| Layered jar for caching | §2.5 | `-Djarmode=tools` (not `layertools`); a dependency layer in the build stage (§2.3) |
| No `HEALTHCHECK` | §3.6 | — |
| `docker` path filter + build + hadolint job in CI | §3.8 | An allow-list `.dockerignore` (§2.2); pinned base digests (§3.1); a GHA layer cache; Trivy image scan to match `ci-infra.yml` |

Not P2-T06's job: pushing to ECR and deploying. That's P2-T08.

---

## Interview talking points

- "A container is a process with namespaces and cgroups, not a VM. That's why the JVM has to be told to size itself from the cgroup limit, and why PID 1 matters for graceful shutdown."
- "Layer order is cache order: least volatile first. I split dependencies from code both in the Maven stage and with Spring Boot's layered jar."
- "On Fargate layers aren't cached between tasks, so image size and a same-Region ECR endpoint matter more for start time than layering does."
- "The execution role is for the agent before the app starts; the task role is for the app. Secrets are injected at start, so rotation needs a redeploy."
- "One immutable image, promoted by digest, run as `api`, `worker` and `migrate` by different task definitions."

## Where else this applies

- **Kubernetes:** the same image rules hold. `livenessProbe` replaces the ALB-as-liveness behaviour, and `terminationGracePeriodSeconds` replaces `stopTimeout`.
- **Testcontainers ITs** ([12](12-integration-testing-guide.md)): the same tag-vs-digest question for `pgvector/pgvector:pg16`, and the same "a container is a process" view when one is slow to start.
- **The frontend:** if the SPA ever gets a container instead of S3 + CloudFront, the same multi-stage pattern applies (Node in build, static files into a tiny image).
- **Local compose:** `pgadmin` uses `dpage/pgadmin4:latest`, which §1.3 argues against even for local tools.

## Check yourself

1. You change one controller. Which layers does the CI build rebuild and push with the current `Dockerfile`, and which with the layered version?
2. A task on 1 GB Fargate runs with no JVM flags. Roughly what is the max heap, and what goes wrong under load? What changes if you set 100 %?
3. The task is stuck in `PENDING` with `ResourceInitializationError: unable to pull secrets`. Which role do you fix, and what permission is missing?
4. Someone changes the entrypoint to `sh -c "java $JAVA_OPTS -jar app.jar"`. A deploy now cuts every WebSocket abruptly. Why, and what's the one-word fix?
5. The NAT gateway is off and the ECR interface endpoints exist, but image pulls still time out. What's missing?
6. Why can't you put `SPRING_PROFILES_ACTIVE=ecs` in the `Dockerfile`, given §4.8?
