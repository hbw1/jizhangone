#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-/opt/jizhangone}"

if [[ ! -d "${PROJECT_DIR}" ]]; then
  echo "找不到项目目录：${PROJECT_DIR}"
  exit 1
fi

cd "${PROJECT_DIR}"

if [[ ! -f "backend/.env" ]]; then
  echo "缺少 backend/.env，请先运行 deploy/tencent-cloud/prepare_prod_env.sh"
  exit 1
fi

docker compose -f docker-compose.prod.yml pull || true
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps

echo "后端容器已启动。"
echo "本机健康检查：curl http://127.0.0.1:8000/health"
