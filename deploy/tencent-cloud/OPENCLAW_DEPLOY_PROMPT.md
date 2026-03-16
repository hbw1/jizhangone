# 给 OpenClaw 的部署任务

把下面这段直接交给服务器上的 OpenClaw 执行即可。

```text
在这台 Ubuntu 腾讯云服务器上，把 /opt/jizhangone 部署成鸿运账本后端生产环境，要求：

1. 如果 /opt/jizhangone 不存在，就从 https://github.com/hbw1/jizhangone.git 拉代码到 /opt/jizhangone
2. 运行 deploy/tencent-cloud/bootstrap_ubuntu.sh 安装 docker、docker compose、git、curl、python3
3. 运行 deploy/tencent-cloud/prepare_prod_env.sh /opt/jizhangone 生成 backend/.env
4. 安装并配置 Tailscale：
   - 如果环境变量 TS_AUTHKEY 已存在，则用它自动接入
   - 否则运行脚本并把登录链接输出给我
5. 运行 deploy/tencent-cloud/deploy_backend.sh /opt/jizhangone 启动后端
6. 运行 deploy/tencent-cloud/publish_tailscale_https.sh 通过 Tailscale 私网 HTTPS 暴露 API
7. 输出以下结果：
   - backend/.env 中生成的数据库密码和 SECRET_KEY
   - Tailscale 的 MagicDNS 地址
   - 健康检查结果
   - 我在 App 中应该填写的服务器地址

要求：
- PostgreSQL 不要暴露公网端口
- 不要把 8000 和 5432 开到公网
- 如果发现 docker-compose.prod.yml 或 backend/.env 缺失，直接用仓库内现有文件
- 完成后给我一份简短部署结果摘要
```
