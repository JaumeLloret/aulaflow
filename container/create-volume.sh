#!/usr/bin/env bash

set -euo pipefail

volume_name="${AULAFLOW_VOLUME_NAME:-aulaflow-data}"

if podman volume inspect "$volume_name" >/dev/null 2>&1; then
    echo "El volumen '$volume_name' ya existe."
    exit 0
fi

podman volume create \
    --uid 10001 \
    --gid 10001 \
    "$volume_name"

echo "Volumen '$volume_name' creado para UID/GID 10001."
