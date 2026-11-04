#!/usr/bin/env bash
# Para Hack4Edu (los datos se conservan en el volumen de Docker).
cd "$(dirname "$0")"
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
docker compose down && echo "✓ Hack4Edu parado. Los datos se conservan."
read -r -p "Pulsa Intro para cerrar…"
