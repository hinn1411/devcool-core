# docker/ecs — run the production image locally

Runs the same image `deploy.yml` pushes, with the `ecs` profile, against `docker/local`'s Postgres. Use it to catch `ecs`-only startup bugs (datasource env, Flyway off, read-only root, heap sizing) before a deploy. The real task env lives in [`infra/stacks/app/service.tf`](../../infra/stacks/app/service.tf); deploying is in [`infra/README.md`](../../infra/README.md).

## Run

```bash
# 1. Postgres (Compose project "local" → network local_default)
docker compose -f docker/local/compose.yaml up -d postgres

# 2. Task env: fill in the placeholders (task-env is gitignored)
cp docker/ecs/task-env.example docker/ecs/task-env

# 3. The production image
docker build -t devcool:local .

# 4. Migrate, as the one-off ecs,migrate task does before each deploy: exit 0 = migrated
docker run --rm --network local_default --env-file docker/ecs/task-env \
  -e SPRING_PROFILES_ACTIVE=ecs,migrate devcool:local; echo "exit $?"

# 5. The api task: Fargate's size (0.5 vCPU / 1 GB) and read-only root
docker run --rm --name devcool-ecs --network local_default --env-file docker/ecs/task-env \
  --cpus 0.5 --memory 1g --read-only -p 8080:8080 devcool:local

# 6. In another shell: the ALB's health check, then the CloudFront smoke path
curl -s localhost:8080/actuator/health/readiness   # {"status":"UP"}
curl -s localhost:8080/actuator/health             # {"status":"UP","groups":["liveness","readiness"]}
```

## Differences from ECS
- No TLS to the database, and no Aurora resume delay: the long pgjdbc timeouts only matter on ECS.
- No AWS credentials: the app starts (the S3 clients resolve credentials on first use), but media upload fails.
- Port 8080 clashes with `./mvnw spring-boot:run`; stop one of them, or map `-p 8081:8080`.
- Logs are ECS JSON, one object per line (`logging.structured.format.console=ecs`), as in CloudWatch.
