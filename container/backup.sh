#!/usr/bin/env bash

set -euo pipefail

image="${AULAFLOW_IMAGE:-localhost/aulaflow:1.0.0}"
data_volume="${AULAFLOW_VOLUME_NAME:-aulaflow-data}"
backup_volume="${AULAFLOW_BACKUP_VOLUME_NAME:-aulaflow-backups}"

if ! podman volume inspect "$data_volume" >/dev/null 2>&1; then
    printf 'No existe el volumen de datos %s.\n' "$data_volume" >&2
    exit 1
fi

if ! podman volume inspect "$backup_volume" >/dev/null 2>&1; then
    podman volume create \
        --uid 10001 \
        --gid 10001 \
        "$backup_volume" >/dev/null
fi

podman run --rm \
    --network none \
    -e AULAFLOW_DB_PATH=/var/lib/aulaflow/aulaflow.db \
    -e AULAFLOW_BACKUP_DIR=/var/lib/aulaflow-backups \
    -v "$data_volume:/var/lib/aulaflow" \
    -v "$backup_volume:/var/lib/aulaflow-backups" \
    --entrypoint java \
    "$image" \
    --enable-native-access=ALL-UNNAMED \
    -cp /opt/aulaflow/aulaflow.jar \
    es.aulaflow.operations.AulaFlowMaintenance \
    backup
