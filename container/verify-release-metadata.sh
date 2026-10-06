#!/usr/bin/env bash

set -euo pipefail

project_root="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/.." &&
    pwd
)"

cd "$project_root"

expected_version="1.0.0"
expected_image="localhost/aulaflow:1.0.0"

project_version="$(
    sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml \
        | head -n 1
)"

if [[ "$project_version" != "$expected_version" ]]; then
    printf 'Versión Maven inesperada: %s (esperada %s).\n' \
        "$project_version" "$expected_version" >&2
    exit 1
fi

grep -Fq \
    'COPY target/aulaflow-1.0.0.jar /opt/aulaflow/aulaflow.jar' \
    Containerfile

grep -Fq \
    '!target/aulaflow-1.0.0.jar' \
    .containerignore

release_files=(
    pom.xml
    Containerfile
    .containerignore
    compose.yaml
    container/build-image.sh
    container/backup.sh
    container/restore.sh
)

if grep -nE '0\.1\.0-SNAPSHOT|localhost/aulaflow:2\.1-dev' \
    "${release_files[@]}"; then
    printf 'Se han encontrado metadatos de desarrollo en archivos de release.\n' >&2
    exit 1
fi

for path in \
    compose.yaml \
    container/build-image.sh \
    container/backup.sh \
    container/restore.sh; do
    if ! grep -Fq "$expected_image" "$path"; then
        printf 'La imagen estable %s no aparece en %s.\n' \
            "$expected_image" "$path" >&2
        exit 1
    fi
done

tracked_runtime_data="$(
    git ls-files \
        | grep -E '(^|/)(data|backups)/|(^|/)\.env$|\.(db|sqlite|sqlite3)$' \
        || true
)"

if [[ -n "$tracked_runtime_data" ]]; then
    printf 'Se han encontrado datos o configuración local versionados:\n%s\n' \
        "$tracked_runtime_data" >&2
    exit 1
fi

printf 'Metadatos de release coherentes: AulaFlow %s / %s\n' \
    "$expected_version" "$expected_image"
printf 'No hay .env, bases SQLite ni directorios data/backups versionados.\n'
