#!/usr/bin/env bash
# Roll the service onto the family's latest ACTIVE revision and wait (P2-T08). The service
# ignores task_definition (ADR-0003): Terraform's task-def changes go live only through this.
#   usage: roll-service-to-latest.sh <cluster> <service> <family>
set -euo pipefail

cluster="$1"
service="$2"
family="$3"

# Room for 3 failed tasks (120 s grace each) before the circuit breaker trips.
TIMEOUT_MINUTES=30
POLL_SECONDS=15
POLL_ATTEMPTS=$((TIMEOUT_MINUTES * 60 / POLL_SECONDS))

latest_revision=$(aws ecs describe-task-definition --task-definition "$family" \
  --query taskDefinition.taskDefinitionArn --output text)
running_revision=$(aws ecs describe-services --cluster "$cluster" --services "$service" \
  --query 'services[0].taskDefinition' --output text)
if [ "$latest_revision" = "$running_revision" ]; then
  echo "$service already runs $latest_revision"
  exit 0
fi

echo "Rolling $service: $running_revision -> $latest_revision"
deployment_id=$(aws ecs update-service --cluster "$cluster" --service "$service" --task-definition "$latest_revision" \
  --query "service.deployments[?status=='PRIMARY'].id | [0]" --output text)

# rolloutState, not services-stable: a rolled-back service is stable on the old revision.
for ((attempt = 1; attempt <= POLL_ATTEMPTS; attempt++)); do
  rollout_state=$(aws ecs describe-services --cluster "$cluster" --services "$service" \
    --query "services[0].deployments[?id=='$deployment_id'].rolloutState | [0]" --output text)
  case "$rollout_state" in
    COMPLETED) echo "$service runs $latest_revision"; exit 0 ;;
    FAILED | None) echo "::error::Rollout of $latest_revision failed or was rolled back (circuit breaker)"; exit 1 ;;
  esac
  sleep "$POLL_SECONDS"
done
echo "::error::Rollout of $latest_revision did not finish in $TIMEOUT_MINUTES min"
exit 1
