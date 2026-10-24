#!/usr/bin/env bash
# terraform validate every stack and module, without a backend (P2-T10).
#   usage: validate-stacks.sh
set -euo pipefail

for dir in infra/stacks/*/ infra/modules/*/; do
  [ -d "$dir" ] || continue # an unmatched glob stays literal
  echo "::group::$dir"
  terraform -chdir="$dir" init -backend=false -input=false
  terraform -chdir="$dir" validate
  echo "::endgroup::"
done
