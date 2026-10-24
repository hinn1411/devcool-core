#!/usr/bin/env bash
# Changed stacks in apply order, as a JSON array (P2-T08). apply never lists bootstrap (ADR-0003).
#   usage: list-stacks.sh <plan|apply> <base-sha> <head-sha> | <plan|apply> all
set -euo pipefail

mode="$1"            # plan | apply
base="${2:-all}"     # base commit, or "all"
head="${3:-}"        # head commit

STACKS_IN_APPLY_ORDER=(bootstrap network data edge app)
STACKS_USING_ENVS=(network data edge app) # the stacks that read infra/envs/*.tfvars
# A branch's first push has an all-zero github.event.before: nothing to diff against.
NULL_SHA_RE='^0+$'

declare -A changed_stacks=() # a set

mark_changed() {
  local stack
  for stack in "$@"; do changed_stacks[$stack]=1; done
}

# infra/stacks/<stack>/... → <stack>
stack_from_path() {
  local rest="${1#infra/stacks/}"
  echo "${rest%%/*}"
}

diff_everything=false
if [ "$base" = "all" ] || [[ "$base" =~ $NULL_SHA_RE ]]; then diff_everything=true; fi

if [ "$diff_everything" = true ]; then
  mark_changed "${STACKS_IN_APPLY_ORDER[@]}"
else
  while read -r changed_path; do
    case "$changed_path" in
      infra/stacks/*/*)
        mark_changed "$(stack_from_path "$changed_path")"
        ;;
      infra/modules/*/*)
        # Every stack that sources the module.
        module="${changed_path#infra/modules/}"
        module="${module%%/*}"
        while read -r stack_tf_file; do
          mark_changed "$(stack_from_path "$stack_tf_file")"
        done < <(grep -l "modules/$module\"" infra/stacks/*/*.tf)
        ;;
      infra/envs/*)
        mark_changed "${STACKS_USING_ENVS[@]}"
        ;;
      .github/scripts/* | .github/workflows/infra-plan.yml | .github/workflows/infra-apply.yml)
        mark_changed "${STACKS_IN_APPLY_ORDER[@]}"
        ;;
    esac
  done < <(git diff --name-only "$base...$head")
fi

if [ "$mode" = "apply" ] && [ -n "${changed_stacks[bootstrap]:-}" ]; then
  # Warn unless "all": a diff or a null SHA may hold a real bootstrap change.
  [ "$base" = "all" ] || echo "::warning::bootstrap changed: apply it by hand (infra/README.md)" >&2
  unset 'changed_stacks[bootstrap]'
fi

ordered=()
for stack in "${STACKS_IN_APPLY_ORDER[@]}"; do
  [ -n "${changed_stacks[$stack]:-}" ] && [ -d "infra/stacks/$stack" ] && ordered+=("$stack")
done
if [ "${#ordered[@]}" -eq 0 ]; then echo '[]'; else printf '%s\n' "${ordered[@]}" | jq -cRn '[inputs]'; fi
