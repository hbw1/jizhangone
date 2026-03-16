# 鸿运账本后端

当前目录提供联网版后端基础骨架，包含：

- `FastAPI`
- `PostgreSQL`
- 基础认证
- 默认账本初始化
- 健康检查
- Docker Compose 部署入口

## 本地启动

1. 复制环境变量：

```bash
cp .env.example .env
```

2. 启动服务：

```bash
docker compose up --build
```

3. 打开接口文档：

```text
http://localhost:8000/docs
```

## 当前接口

- `GET /health`
- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `GET /v1/bootstrap`
- `POST /v1/sync/push`
- `GET /v1/sync/pull`

## 当前说明

- 首版默认启用 `AUTO_CREATE_TABLES=true`，方便快速启动
- 正式环境建议切到 Alembic 迁移
- 注册用户时会自动创建：
  - 一个默认账本
  - 一个默认成员
  - 两个默认账户
  - 一组基础收入/支出分类
- 首次注册时，默认账本数据也会写入同步日志
- `sync` 当前支持这些实体：
  - `book`
  - `member`
  - `account`
  - `category`
  - `journal_entry`
  - `transaction`
