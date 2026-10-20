# syntax=docker/dockerfile:1
# Production image for ECS (api, worker, migrate: one image, the profile picks the role).
# Background: docs/learning/18-docker-and-ecs.md. Bases are pinned by digest; Dependabot bumps them.

# ---- build: compile the fat jar and split it into Spring Boot's layers ----
# eclipse-temurin:21-jdk-noble
FROM eclipse-temurin:24-jdk-noble@sha256:dacac8e9a0df0d2bd24e702b4431132875c249930b70555ebd7ca285b5bee684 AS build
WORKDIR /build

# Dependencies get their own layer, so a src-only change doesn't download them again (ch18 §2.3).
# go-offline misses a few (platform-specific Netty jars), so the package step stays online.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# CI already ran the tests and coverage; this stage only packages.
COPY src src
RUN ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- runtime: JRE only, non-root, one layer per Boot layer ----
# eclipse-temurin:21-jre-noble
FROM eclipse-temurin:24-jre-noble@sha256:b416d02335e702b0403ff280de9475a3348e29382285969c9d4e17862ce632e7

# Fixed numeric IDs: USER must be numeric for the platform to prove non-root (hadolint DL3066).
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /application

# Least to most volatile: a code change only replaces the last (small) layer.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Heap = 75 % of the container's memory limit (the JVM default is 25 %); leave the rest for
# metaspace, thread stacks and direct buffers. A task-def override replaces this whole value,
# so P7-T06 (OTel agent) must repeat these flags.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080
# Exec form: java is PID 1 and gets SIGTERM, so Spring's graceful shutdown runs (ch18 §3.3).
# No HEALTHCHECK: ECS ignores it; the ALB target group checks /actuator/health/readiness.
ENTRYPOINT ["java", "-jar", "application.jar"]
