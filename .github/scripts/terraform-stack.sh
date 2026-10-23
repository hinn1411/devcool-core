#!/usr/bin/env bash
# init + plan one stack for the env's power state; apply/destroy apply the saved plan (P2-T08).
#   usage: terraform-stack.sh <plan|apply|destroy> <stack>
#   env:   ENVIRONMENT, STATE, IMAGE_TAG (app), TF_VAR_*, ALLOWED_CHANGES (optional address regex)
# plan writes plan.txt and changes=true|false to $GITHUB_OUTPUT.
set -euo pipefail

mode="$1"   # plan | apply | destroy
stack="$2"
environment="${ENVIRONMENT:-dev}"
power_state="${STATE:-awake}"
output_file="${GITHUB_OUTPUT:-/dev/null}"

stack_dir="infra/stacks/$stack"
ENVS_DIR="../../envs" # relative to the stack dir (terraform -chdir)
PLAN_FILE="tfplan"
LOCK_TIMEOUT="5m"     # wait for another run's state lock instead of failing
# `terraform plan -detailed-exitcode`: 0 = no changes, 1 = error, 2 = changes.
PLAN_FAILED=1
PLAN_HAS_CHANGES=2

tf_args=()

if [ "$stack" = "bootstrap" ]; then
  terraform -chdir="$stack_dir" init -input=false # full backend config in backend.tf
else
  terraform -chdir="$stack_dir" init -input=false \
    -backend-config="$ENVS_DIR/$environment.backend.hcl" -backend-config="key=devcool/$environment/$stack.tfstate"
  tf_args+=(-var-file="$ENVS_DIR/$environment.tfvars")
fi

# The power state wins over the tfvars, so applying never wakes the env (ADR-0013).
case "$stack:$power_state" in
  network:hibernated) tf_args+=(-var enable_nat=false -var enable_interface_endpoints=false) ;;
  # data:hibernated: P4-T05 adds -var enable_valkey=false here.
  app:asleep) tf_args+=(-var api_min_count=0) ;; # the autoscaling min is 0 while asleep (P2-T09)
esac
if [ "$stack" = "app" ]; then
  # The pipeline owns the tag (ADR-0003): keep the running one.
  [ -n "${IMAGE_TAG:-}" ] || { echo "::error::No running image tag (no ACTIVE devcool-$environment-api revision)"; exit 1; }
  tf_args+=(-var "image_tag=$IMAGE_TAG")
fi

[ "$mode" = "destroy" ] && tf_args+=(-destroy)

# env-power: fail on changes outside ALLOWED_CHANGES (ADR-0013).
assert_only_allowed_changes() {
  local unexpected
  unexpected=$(terraform -chdir="$stack_dir" show -json "$PLAN_FILE" | jq -r --arg allowed "$ALLOWED_CHANGES" '
    .resource_changes[]?
    | select(.change.actions != ["no-op"] and .change.actions != ["read"])
    | select(.address | test($allowed) | not)
    | "\(.change.actions | join("+")) \(.address)"')
  if [ -n "$unexpected" ]; then
    echo "::error::$stack: changes outside the allowlist ($ALLOWED_CHANGES); nothing applied"
    echo "$unexpected"
    exit 1
  fi
}

set +e
terraform -chdir="$stack_dir" plan -input=false -lock-timeout="$LOCK_TIMEOUT" -detailed-exitcode \
  -out="$PLAN_FILE" "${tf_args[@]}" 2>&1 | tee "$stack_dir/plan.log"
plan_exit_code=${PIPESTATUS[0]}
set -e
[ "$plan_exit_code" -eq "$PLAN_FAILED" ] && exit 1

if [ "$mode" = "plan" ]; then
  terraform -chdir="$stack_dir" show -no-color "$PLAN_FILE" >"$stack_dir/plan.txt"
  echo "changes=$([ "$plan_exit_code" -eq "$PLAN_HAS_CHANGES" ] && echo true || echo false)" >>"$output_file"
elif [ "$plan_exit_code" -eq "$PLAN_HAS_CHANGES" ]; then
  if [ -n "${ALLOWED_CHANGES+set}" ]; then assert_only_allowed_changes; fi
  terraform -chdir="$stack_dir" apply -input=false -lock-timeout="$LOCK_TIMEOUT" "$PLAN_FILE"
else
  echo "$stack: no changes"
fi
