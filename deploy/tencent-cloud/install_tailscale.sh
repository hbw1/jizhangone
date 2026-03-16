#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请用 root 或 sudo 运行此脚本。"
  exit 1
fi

curl -fsSL https://tailscale.com/install.sh | sh

if [[ -n "${TS_AUTHKEY:-}" ]]; then
  tailscale up --ssh --authkey="${TS_AUTHKEY}"
  echo "Tailscale 已通过 auth key 接入。"
else
  tailscale up --ssh
  echo "如果终端出现登录链接，请在浏览器里完成授权。"
fi
