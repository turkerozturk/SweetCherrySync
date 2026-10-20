#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f application.yml ]; then
    echo "[ERROR] Copy application.example.yml to application.yml and configure it first." >&2
    exit 1
fi
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java 17 or newer is required and must be on PATH." >&2
    exit 1
fi
exec java -jar sweetcherrysync-1.1.0-alpha.1.jar --spring.config.additional-location=file:./application.yml
