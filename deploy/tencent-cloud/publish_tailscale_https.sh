#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请用 root 或 sudo 运行此脚本。"
  exit 1
fi

tailscale serve --bg --https=443 http://127.0.0.1:8000
tailscale serve status

echo "已通过 Tailscale 私网 HTTPS 暴露到 443。"
echo "请记录你的 MagicDNS 地址，并在 App 里填写 https://<magicdns>/"
