#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请用 root 或 sudo 运行此脚本。"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt update
apt install -y docker.io docker-compose-plugin git curl python3

systemctl enable docker
systemctl start docker

echo "基础环境安装完成：docker / docker compose / git / curl / python3"
