#!/usr/bin/env bash
# The env's power state and the values the AWS workflows need, as key=value lines in $GITHUB_OUTPUT
# (stdout by hand; read-only AWS access is enough). P2-T08.
#   hibernated: no /devcool/<env>/app/* (ADR-0013) · asleep: api runs 0 tasks · awake: otherwise
set -euo pipefail

environment="${ENVIRONMENT:-dev}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"

APP_PARAMS_PATH="/devcool/$environment/app"             # app/ssm.tf
DOMAIN_PARAM="/devcool/$environment/edge/domain_name"   # edge stack

# Known even while hibernated (app/main.tf, app/service.tf).
family="devcool-$environment-api"
container="api"

# Running tag = the family's last ACTIVE revision; it survives hibernate (skip_destroy).
running_image=$(aws ecs describe-task-definition --task-definition "$family" \
  --query "taskDefinition.containerDefinitions[?name=='$container'].image | [0]" --output text 2>/dev/null || true)
image_tag=""
[[ "$running_image" == *:* && "$running_image" != "None" ]] && image_tag="${running_image##*:}"

# Keyed by the last path segment: cluster_name, api_service_name, … (app/ssm.tf).
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
