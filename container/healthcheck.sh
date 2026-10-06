#!/bin/bash

set -euo pipefail

host="127.0.0.1"
port="${AULAFLOW_HTTP_PORT:-8080}"

exec 3<>"/dev/tcp/${host}/${port}"

printf 'GET /api/v1/health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' >&3

response="$(cat <&3)"

[[ "${response}" == HTTP/1.1\ 200* ]]
[[ "${response}" == *'"status": "UP"'* ]]
