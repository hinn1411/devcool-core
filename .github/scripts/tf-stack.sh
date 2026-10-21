#!/usr/bin/env bash
# init + plan one stack for the env's power state; `apply` then applies the saved plan (P2-T08).
#   usage: tf-stack.sh <plan|apply> <stack>
#   env:   ENVIRONMENT, STATE (env-state.sh), IMAGE_TAG (app only), TF_VAR_* as needed
# plan writes infra/stacks/<stack>/plan.txt and `changes=true|false` to $GITHUB_OUTPUT.
set -euo pipefail

mode="$1"
stack="$2"
env="${ENVIRONMENT:-dev}"
dir="infra/stacks/$stack"
out="${GITHUB_OUTPUT:-/dev/null}"
args=()

if [ "$stack" = "bootstrap" ]; then
  terraform -chdir="$dir" init -input=false # full backend config in backend.tf
else
  terraform -chdir="$dir" init -input=false \
    -backend-config="../../envs/$env.backend.hcl" -backend-config="key=devcool/$env/$stack.tfstate"
  args+=(-var-file="../../envs/$env.tfvars")
fi

# The power state wins over the tfvars, so applying never wakes the env (ADR-0013).
case "$stack:${STATE:-awake}" in
  network:hibernated) args+=(-var enable_nat=false -var enable_interface_endpoints=false) ;;
  # data:hibernated: P4-T05 adds -var enable_valkey=false here.
  app:asleep) args+=(-var api_min_count=0) ;; # the autoscaling min is 0 while asleep (P2-T09)
esac
if [ "$stack" = "app" ]; then
  # The pipeline owns the tag (ADR-0003): keep the running one.
  [ -n "${IMAGE_TAG:-}" ] || { echo "::error::No running image tag (no ACTIVE devcool-$env-api revision)"; exit 1; }
  args+=(-var "image_tag=$IMAGE_TAG")
fi

set +e
terraform -chdir="$dir" plan -input=false -lock-timeout=5m -detailed-exitcode -out=tfplan "${args[@]}" 2>&1 \
  | tee "$dir/plan.log"
rc=${PIPESTATUS[0]}
set -e
[ "$rc" -eq 1 ] && exit 1

if [ "$mode" = "plan" ]; then
  terraform -chdir="$dir" show -no-color tfplan >"$dir/plan.txt"
  echo "changes=$([ "$rc" -eq 2 ] && echo true || echo false)" >>"$out"
elif [ "$rc" -eq 2 ]; then
  terraform -chdir="$dir" apply -input=false -lock-timeout=5m tfplan
else
  echo "$stack: no changes"
fi
