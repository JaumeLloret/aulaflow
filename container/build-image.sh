#!/usr/bin/env bash

set -euo pipefail

project_root="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/.." &&
    pwd
)"
image="${AULAFLOW_IMAGE:-localhost/aulaflow:1.0.0}"

cd "$project_root"

./mvnw clean verify

podman build \
    --format docker \
    -t "$image" \
    .
