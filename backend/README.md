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
- `POST /v1/nlp/parse-natural-language`

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

## MiniMax 智能解析

如果你要给“一句话记账”接云端大模型解析，需要在环境变量里配置：

```env
MINIMAX_API_KEY=your-key
MINIMAX_BASE_URL=https://api.minimax.io/v1
MINIMAX_MODEL=MiniMax-M1
MINIMAX_TIMEOUT_SECONDS=30
```

接口：

- `POST /v1/nlp/parse-natural-language`

请求体示例：

```json
{
  "book_id": "your-book-id",
  "raw_text": "今天和妈妈吃火锅花了300元，昨天爸爸买菜花了86元",
  "today": "2026-03-17",
  "timezone": "Asia/Shanghai"
}
```

这个接口会：

- 校验当前用户是否能访问该账本
- 自动读取账本下的成员、账户、收入分类、支出分类作为上下文
- 调用 MiniMax 做结构化解析
- 返回候选账单列表，供客户端确认后再入账

注意：

- 当前实现依赖 MiniMax 的 OpenAI 兼容 `/chat/completions` 接口
- 服务端只返回“解析建议”，不会直接入账

## 错误返回格式

现在服务端错误统一返回：

```json
{
  "error": {
    "code": "auth_invalid_credentials",
    "message": "账号或密码错误",
    "request_id": "0f6f5e0d-1f54-46cc-8f1f-8dfb8c9f2d83",
    "details": {
      "reason": "Invalid username or password"
    }
  }
}
```

说明：

- `code`：给客户端做稳定判断
- `message`：直接给用户看
- `request_id`：排查线上问题时对日志
- `details`：开发和联调用的补充信息
