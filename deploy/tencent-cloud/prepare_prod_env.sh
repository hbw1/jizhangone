#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-/opt/jizhangone}"
ENV_FILE="${PROJECT_DIR}/backend/.env"
EXAMPLE_FILE="${PROJECT_DIR}/backend/.env.production.example"

if [[ ! -f "${EXAMPLE_FILE}" ]]; then
  echo "找不到 ${EXAMPLE_FILE}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${EXAMPLE_FILE}" "${ENV_FILE}"
fi

DB_PASSWORD="${DB_PASSWORD:-$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(24))
PY
)}"

SECRET_KEY_VALUE="${SECRET_KEY_VALUE:-$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(64))
PY
)}"

TAILSCALE_HOSTNAME="${TAILSCALE_HOSTNAME:-your-server-name.your-tailnet.ts.net}"

python3 - "${ENV_FILE}" "${DB_PASSWORD}" "${SECRET_KEY_VALUE}" "${TAILSCALE_HOSTNAME}" <<'PY'
from pathlib import Path
import sys

env_path = Path(sys.argv[1])
db_password = sys.argv[2]
secret_key = sys.argv[3]
tailscale_hostname = sys.argv[4]

lines = env_path.read_text(encoding="utf-8").splitlines()
updated = []
for line in lines:
    if line.startswith("DATABASE_URL="):
        updated.append(
            f"DATABASE_URL=postgresql+asyncpg://hongyun:{db_password}@postgres:5432/hongyun"
        )
    elif line.startswith("SECRET_KEY="):
        updated.append(f"SECRET_KEY={secret_key}")
    elif line.startswith("CORS_ORIGINS="):
        updated.append(f"CORS_ORIGINS=https://{tailscale_hostname}")
    else:
        updated.append(line)

env_path.write_text("\n".join(updated) + "\n", encoding="utf-8")
PY

echo "生产环境变量已准备完成：${ENV_FILE}"
echo "数据库密码：${DB_PASSWORD}"
echo "SECRET_KEY：${SECRET_KEY_VALUE}"
echo "Tailscale 地址：https://${TAILSCALE_HOSTNAME}/"
echo "请把上面三项妥善保存。"
