#!/usr/bin/env bash
# Point the service at the family's latest ACTIVE revision and wait for the rollout (P2-T08).
# The service ignores task_definition (ADR-0003), so a Terraform change to the task definition
# (env, CPU, sidecars) only goes live through this. The image stays the running one.
#   usage: ecs-roll-to-latest.sh <cluster> <service> <family>
set -euo pipefail

cluster="$1"
service="$2"
family="$3"

latest=$(aws ecs describe-task-definition --task-definition "$family" --query taskDefinition.taskDefinitionArn --output text)
running=$(aws ecs describe-services --cluster "$cluster" --services "$service" --query 'services[0].taskDefinition' --output text)
if [ "$latest" = "$running" ]; then
  echo "$service already runs $latest"
  exit 0
fi

echo "Rolling $service: $running -> $latest"
deployment=$(aws ecs update-service --cluster "$cluster" --service "$service" --task-definition "$latest" \
  --query "service.deployments[?status=='PRIMARY'].id | [0]" --output text)

# rolloutState, not services-stable: after a circuit-breaker rollback the service is stable
# again on the old revision. 30 min covers 3 failed tasks with the 120 s grace period.
for _ in $(seq 120); do
  state=$(aws ecs describe-services --cluster "$cluster" --services "$service" \
    --query "services[0].deployments[?id=='$deployment'].rolloutState | [0]" --output text)
  case "$state" in
    COMPLETED) echo "$service runs $latest"; exit 0 ;;
    FAILED | None) echo "::error::Rollout of $latest failed or was rolled back (circuit breaker)"; exit 1 ;;
  esac
  sleep 15
done
echo "::error::Rollout of $latest did not finish in 30 min"
exit 1
