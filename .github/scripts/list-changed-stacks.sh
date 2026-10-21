#!/usr/bin/env bash
# Stacks a change touches, in apply order, as a JSON array (P2-T08).
#   usage: list-changed-stacks.sh <plan|apply> <base-sha> <head-sha>   or   list-changed-stacks.sh <plan|apply> all
# plan includes bootstrap; apply never does (bootstrap is applied by hand, ADR-0003).
set -euo pipefail

mode="$1"
order=(bootstrap network data edge app)
declare -A hit=()

if [ "${2:-all}" = "all" ] || [[ "${2:-}" =~ ^0+$ ]]; then
  for s in "${order[@]}"; do hit[$s]=1; done
else
  while read -r path; do
    case "$path" in
      infra/stacks/*/*)
        s="${path#infra/stacks/}"
        hit[${s%%/*}]=1
        ;;
      infra/modules/*/*)
        # Every stack that sources the module.
        m="${path#infra/modules/}"
        m="${m%%/*}"
        while read -r f; do
          s="${f#infra/stacks/}"
          hit[${s%%/*}]=1
        done < <(grep -l "modules/$m\"" infra/stacks/*/*.tf)
        ;;
      infra/envs/*)
        for s in network data edge app; do hit[$s]=1; done
        ;;
      .github/scripts/* | .github/workflows/infra-plan.yml | .github/workflows/infra-apply.yml)
        for s in "${order[@]}"; do hit[$s]=1; done
        ;;
    esac
  done < <(git diff --name-only "$2...$3")
fi

if [ "$mode" = "apply" ] && [ -n "${hit[bootstrap]:-}" ]; then
  [ "${2:-all}" = "all" ] || echo "::warning::bootstrap changed: apply it by hand (infra/README.md)" >&2
  unset 'hit[bootstrap]'
fi

stacks=()
for s in "${order[@]}"; do
  [ -n "${hit[$s]:-}" ] && [ -d "infra/stacks/$s" ] && stacks+=("$s")
done
if [ "${#stacks[@]}" -eq 0 ]; then echo '[]'; else printf '%s\n' "${stacks[@]}" | jq -cRn '[inputs]'; fi
