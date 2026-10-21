#!/usr/bin/env bash
# Power state of an environment and the values the AWS workflows need (P2-T08; env-power reuses it).
#   hibernated: /devcool/<env>/app/* is absent (app destroyed, ADR-0013)
#   asleep:     the api service runs 0 tasks
#   awake:      otherwise
# Writes key=value lines to $GITHUB_OUTPUT (stdout when run by hand). Needs read-only AWS access.
set -euo pipefail

env="${ENVIRONMENT:-dev}"
out="${GITHUB_OUTPUT:-/dev/stdout}"

# Known even while hibernated: app/main.tf names the family, app/service.tf the container.
family="devcool-$env-api"
container="api"

# The running tag: the family's last ACTIVE revision, which survives hibernate (skip_destroy).
image=$(aws ecs describe-task-definition --task-definition "$family" \
  --query "taskDefinition.containerDefinitions[?name=='$container'].image | [0]" --output text 2>/dev/null || true)
image_tag=""
[[ "$image" == *:* && "$image" != "None" ]] && image_tag="${image##*:}"

# app/ssm.tf: cluster_name, api_service_name, api_subnet_ids, api_security_group_ids, …
declare -A app=()
while IFS=$'\t' read -r name value; do
  [ -n "$name" ] && app["${name##*/}"]="$value"
done < <(aws ssm get-parameters-by-path --path "/devcool/$env/app" \
  --query 'Parameters[].[Name,Value]' --output text)

if [ "${#app[@]}" -eq 0 ]; then
  state=hibernated
else
  desired=$(aws ecs describe-services --cluster "${app[cluster_name]}" --services "${app[api_service_name]}" \
    --query 'services[0].desiredCount' --output text)
  if [ "$desired" = "0" ]; then state=asleep; else state=awake; fi
fi

domain=$(aws ssm get-parameter --name "/devcool/$env/edge/domain_name" --query Parameter.Value --output text)

{
  echo "state=$state"
  echo "image_tag=$image_tag"
  echo "family=$family"
  echo "container=$container"
  echo "domain=$domain"
  for k in "${!app[@]}"; do echo "$k=${app[$k]}"; done
} >>"$out"

echo "Environment $env is $state (running tag: ${image_tag:-none})" >&2
