# 鸿运账本

一个面向家庭内部使用的安卓记账应用，当前采用 `本地优先` 架构，并正在演进为 `可联网同步` 的版本。

## 当前状态

- 安卓本地版已可用
- 支持多账本、多成员、多账户、多分类
- 支持新增、编辑、删除交易
- 支持统计分析、一句话记账、本地备份导入导出
- 已补齐联网版后端基础骨架
- 已有同步接口第一版：`auth + bootstrap + sync push/pull`

## 仓库结构

- [app](app)：安卓端代码，`Kotlin + Jetpack Compose + Room`
- [backend](backend)：后端代码，`FastAPI + PostgreSQL`
- [docs](docs)：产品和技术文档
- [docker-compose.yml](docker-compose.yml)：本地或服务器部署入口

## 主要能力

- 本地记账
- 家庭共享账本模型
- 月度统计与周期筛选
- 一句话记账
- 本地解析候选编辑
- 本地 JSON 备份
- 联网后端基础认证
- 增量同步接口骨架

## 安卓端技术栈

- `Kotlin`
- `Jetpack Compose`
- `Room`
- `ViewModel`
- `WorkManager`
- `Retrofit`

## 后端技术栈

- `FastAPI`
- `PostgreSQL`
- `SQLAlchemy`
- `Docker Compose`

## 文档入口

- [产品需求文档 PRD](docs/PRD.md)
- [初始任务拆解](docs/TASKS.md)
- [联网版完整技术方案](docs/NETWORK_PLAN.md)
- [文档索引](docs/README.md)
- [后端说明](backend/README.md)

## 本地启动

### 安卓端

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
```

### 后端

```bash
cp backend/.env.example backend/.env
docker compose up --build
```

接口文档：

```text
http://localhost:8000/docs
```

## 联网版方向

当前规划是：

1. 安卓端继续保持本地优先
2. 服务器保存主数据
3. 支持多设备同步
4. Notion 作为第二备份
5. COS JSON 快照作为真正灾备
6. MiniMax 作为一句话记账的智能解析增强

## 当前下一步

- 安卓端补登录态
- Room 增加同步字段
- 接入 `sync push/pull`
- 接入 Notion 备份
- 接入 MiniMax 智能解析
