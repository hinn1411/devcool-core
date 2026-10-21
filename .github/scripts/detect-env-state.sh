#!/usr/bin/env bash
# Power state of an environment and the values the AWS workflows need (P2-T08; env-power reuses it).
#   hibernated: /devcool/<env>/app/* is absent (app destroyed, ADR-0013)
#   asleep:     the api service runs 0 tasks
#   awake:      otherwise
# Writes key=value lines to $GITHUB_OUTPUT (stdout when run by hand). Needs read-only AWS access.
set -euo pipefail

environment="${ENVIRONMENT:-dev}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"

APP_PARAMS_PATH="/devcool/$environment/app"             # app/ssm.tf
DOMAIN_PARAM="/devcool/$environment/edge/domain_name"   # edge stack

# Known even while hibernated: app/main.tf names the family, app/service.tf the container.
family="devcool-$environment-api"
container="api"

# The running tag: the family's last ACTIVE revision, which survives hibernate (skip_destroy).
running_image=$(aws ecs describe-task-definition --task-definition "$family" \
  --query "taskDefinition.containerDefinitions[?name=='$container'].image | [0]" --output text 2>/dev/null || true)
image_tag=""
[[ "$running_image" == *:* && "$running_image" != "None" ]] && image_tag="${running_image##*:}"

# cluster_name, api_service_name, api_subnet_ids, api_security_group_ids, … keyed by the last path segment.
declare -A app_params=()
while IFS=$'\t' read -r param_name param_value; do
  [ -n "$param_name" ] && app_params["${param_name##*/}"]="$param_value"
done < <(aws ssm get-parameters-by-path --path "$APP_PARAMS_PATH" \
  --query 'Parameters[].[Name,Value]' --output text)

if [ "${#app_params[@]}" -eq 0 ]; then
  state=hibernated
else
  desired_count=$(aws ecs describe-services --cluster "${app_params[cluster_name]}" \
    --services "${app_params[api_service_name]}" --query 'services[0].desiredCount' --output text)
  if [ "$desired_count" = "0" ]; then state=asleep; else state=awake; fi
fi

domain=$(aws ssm get-parameter --name "$DOMAIN_PARAM" --query Parameter.Value --output text)

{
  echo "state=$state"
  echo "image_tag=$image_tag"
  echo "family=$family"
  echo "container=$container"
  echo "domain=$domain"
  for key in "${!app_params[@]}"; do echo "$key=${app_params[$key]}"; done
} >>"$output_file"

echo "Environment $environment is $state (running tag: ${image_tag:-none})" >&2
