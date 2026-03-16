#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error http://127.0.0.1:8000/health
echo
docker compose -f docker-compose.prod.yml ps
