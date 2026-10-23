#!/usr/bin/env bash
# Run ecs,migrate on the latest revision; exit 0 = migrated (P2-T09).
# One retry: a cold Aurora outlasts pgjdbc's timeouts.
#   usage: run-migrate-task.sh <cluster> <family> <container> <subnet-ids> <security-group-ids>   (ids comma-separated)
set -euo pipefail

cluster="$1"
family="$2"
container="$3"
subnet_ids="$4"
security_group_ids="$5"

MAX_ATTEMPTS=2
RETRY_DELAY_SECONDS=60 # Aurora resume time

# Called in an if (set -e off): return on each failure.
run_migrate_once() {
  local task_arn exit_code stopped_reason
  task_arn=$(aws ecs run-task --cluster "$cluster" --task-definition "$family" --launch-type FARGATE \
    --started-by env-power-migrate \
    --network-configuration "awsvpcConfiguration={subnets=[$subnet_ids],securityGroups=[$security_group_ids],assignPublicIp=DISABLED}" \
    --overrides "{\"containerOverrides\":[{\"name\":\"$container\",\"environment\":[{\"name\":\"SPRING_PROFILES_ACTIVE\",\"value\":\"ecs,migrate\"}]}]}" \
    --query 'tasks[0].taskArn' --output text) || return 1
  if [ -z "$task_arn" ] || [ "$task_arn" = "None" ]; then
    echo "::warning::run-task placed no task"
    return 1
  fi
  echo "Migrate task: $task_arn"
  aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task_arn" || return 1
  exit_code=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
    --query "tasks[0].containers[?name=='$container'].exitCode | [0]" --output text) || return 1
  stopped_reason=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
    --query 'tasks[0].stoppedReason' --output text) || return 1
  echo "Exit code $exit_code ($stopped_reason); log stream api/$container/${task_arn##*/}"
  [ "$exit_code" = "0" ]
}

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  if run_migrate_once; then
    echo "Migrated"
    exit 0
  fi
  if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
    echo "::warning::Migrate attempt $attempt failed; retrying in ${RETRY_DELAY_SECONDS}s (cold Aurora?)"
    sleep "$RETRY_DELAY_SECONDS"
  fi
done
echo "::error::Migrate failed $MAX_ATTEMPTS times; the service was not scaled up"
exit 1
