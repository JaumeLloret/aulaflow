#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    printf 'Uso: bash container/restore.sh <archivo.db>\n' >&2
    exit 2
fi

backup_file="$1"
image="${AULAFLOW_IMAGE:-localhost/aulaflow:1.0.0}"
data_volume="${AULAFLOW_VOLUME_NAME:-aulaflow-data}"
backup_volume="${AULAFLOW_BACKUP_VOLUME_NAME:-aulaflow-backups}"

if ! podman volume inspect "$data_volume" >/dev/null 2>&1; then
    printf 'No existe el volumen de datos %s.\n' "$data_volume" >&2
    exit 1
fi

if ! podman volume inspect "$backup_volume" >/dev/null 2>&1; then
    printf 'No existe el volumen de backups %s.\n' "$backup_volume" >&2
    exit 1
fi

running_with_data="$({
    podman ps \
        --filter "volume=$data_volume" \
        --format '{{.ID}}'
} 2>/dev/null || true)"

if [[ -n "$running_with_data" ]]; then
    printf 'Restauración rechazada: hay un contenedor usando el volumen %s.\n' \
        "$data_volume" >&2
    printf 'Detén AulaFlow antes de restaurar.\n' >&2
    exit 1
fi

podman run --rm \
    --network none \
    -e AULAFLOW_DB_PATH=/var/lib/aulaflow/aulaflow.db \
    -e AULAFLOW_BACKUP_DIR=/var/lib/aulaflow-backups \
    -v "$data_volume:/var/lib/aulaflow" \
    -v "$backup_volume:/var/lib/aulaflow-backups:ro" \
    --entrypoint java \
    "$image" \
    --enable-native-access=ALL-UNNAMED \
    -cp /opt/aulaflow/aulaflow.jar \
    es.aulaflow.operations.AulaFlowMaintenance \
    restore "$backup_file"
