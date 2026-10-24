#!/usr/bin/env bash
# Check what the Dockerfile promises: non-root user, heap sized from the container limit, Boot layers (P2-T06).
#   usage: check-image.sh <image>
set -euo pipefail

image="$1"

user=$(docker inspect -f '{{.Config.User}}' "$image")
uid=$(docker run --rm --entrypoint id "$image" -u)
echo "USER '$user', uid $uid"
if [ -z "$user" ] || [ "$uid" = "0" ]; then
  echo "::error::The image runs as root"; exit 1
fi

# 75 % of a 1 GiB limit is 768 MiB; the JVM default (25 %) would give 256 MiB.
heap=$(docker run --rm -m 1g --entrypoint java "$image" -XX:+PrintFlagsFinal -version 2>/dev/null \
  | awk '$2 == "MaxHeapSize" {print $4}')
echo "MaxHeapSize with -m 1g: $heap bytes"
if [ "$heap" -lt $((700 * 1024 * 1024)) ]; then
  echo "::error::Heap is not sized from the container limit (MaxRAMPercentage=75)"; exit 1
fi

for layer in dependencies spring-boot-loader snapshot-dependencies application; do
  docker history --no-trunc --format '{{.CreatedBy}}' "$image" | grep -q "extracted/$layer/" \
    || { echo "::error::Missing the '$layer' layer (layered jar)"; exit 1; }
done
