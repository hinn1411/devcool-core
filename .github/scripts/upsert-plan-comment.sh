#!/usr/bin/env bash
# Post one stack's plan to the PR, updating its earlier comment in place (P2-T08).
#   usage: upsert-plan-comment.sh <stack>
#   env:   STATE, OUTCOME (the plan step's), CHANGES, PR, HEAD_SHA, ENVIRONMENT, GH_TOKEN
set -euo pipefail

stack="$1"

stack_dir="infra/stacks/$stack"
MARKER="<!-- infra-plan:$stack -->" # finds this stack's comment on the next run
COMMENT_FILE="comment.md"
# A comment holds 65,536 characters; keep the end of a failure, the start of a plan.
MAX_BODY_CHARS=60000

plan_output="$stack_dir/plan.txt"
if [ "$OUTCOME" != "success" ]; then
  headline="❌ **plan failed**"
  plan_output="$stack_dir/plan.log"
elif [ "$stack:$STATE" = "app:hibernated" ]; then
  # A full create, which infra-apply skips: don't headline it as what the merge does.
  resources_to_add=$(sed -nE 's/^Plan: ([0-9]+) to add.*/\1/p' "$plan_output")
  headline="**not applied: hibernated** (resume creates ${resources_to_add:-0})"
elif [ "$CHANGES" = "true" ]; then
  headline="$(grep -E '^Plan:' "$plan_output" || echo 'Changes')"
else
  headline="No changes"
fi

note=""
case "$stack:$STATE" in
  bootstrap:*)        note="Applied by hand, never by CI (infra/README.md)." ;;
  app:hibernated)     note="\`app\` is destroyed while hibernated, so this plan only checks the config; resume (P2-T09) applies it." ;;
  network:hibernated) note="The env is hibernated: planned with the NAT and interface endpoints off, as infra-apply applies it." ;;
esac

if [ "$OUTCOME" != "success" ]; then
  body=$(tail -c "$MAX_BODY_CHARS" "$plan_output" 2>/dev/null)
else
  body=$(head -c "$MAX_BODY_CHARS" "$plan_output")
fi

{
  echo "$MARKER"
  echo "### \`$stack\` · $headline"
  echo "Env \`$ENVIRONMENT\` is **$STATE** · commit $HEAD_SHA · [run]($GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID)"
  [ -n "$note" ] && echo "> $note"
  echo; echo "<details><summary>Plan output</summary>"; echo
  echo '```'; echo "$body"; echo '```'
  echo "</details>"
} >"$COMMENT_FILE"

comments_api="repos/$GITHUB_REPOSITORY/issues/$PR/comments"
comment_id=$(gh api "$comments_api" --paginate \
  --jq ".[] | select(.body | startswith(\"$MARKER\")) | .id" | head -1)
if [ -n "$comment_id" ]; then
  gh api -X PATCH "repos/$GITHUB_REPOSITORY/issues/comments/$comment_id" -F body=@"$COMMENT_FILE" >/dev/null
else
  gh api "$comments_api" -F body=@"$COMMENT_FILE" >/dev/null
fi
