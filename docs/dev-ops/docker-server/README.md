# EasyAgent 服务器 Docker 部署

部署组件：

- `mysql`：MySQL 8
- `backend`：EasyAgent 后端（Spring Boot）
- `frontend`：EasyAgent 前端（Next.js）
- `gateway`：Nginx 反向代理（统一入口）

## 1. 前置条件

- 服务器已安装 Docker
- 已安装 Docker Compose 插件（`docker compose`）
- 服务器上同时有 `easy-agent-backend` 与 `easy-agent-front` 目录，且目录结构与当前工程一致

## 2. 配置环境变量

进入目录：

```bash
cd easy-agent-backend/docs/dev-ops/docker-server
cp .env.example .env
```

编辑 `.env`，重点修改：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `PUBLIC_BASE_URL`（浏览器可访问地址）
- `DASHSCOPE_API_KEY`

## 3. 启动

```bash
docker compose --env-file .env up -d --build
```

查看状态：

```bash
docker compose ps
docker compose logs -f
```

## 4. 访问地址

- 平台入口：`http://<服务器IP或域名>:<GATEWAY_PORT>`
- 后端直连（可选）：`http://<服务器IP或域名>:<BACKEND_PORT>`
- MySQL（可选）：`<服务器IP或域名>:<MYSQL_PORT>`

## 5. 停止与重启

停止：

```bash
docker compose down
```

更新后重建：

```bash
docker compose --env-file .env up -d --build
```

## 6. 数据与初始化说明

- MySQL 数据在 volume：`mysql_data`
- 后端日志在 volume：`backend_log`
- 首次启动 MySQL 会自动执行：
  - `agent-config-v1.1.sql`
  - `agent-config-v1.2-plaza.sql`
  - `agent-config-v1.3-user-auth.sql`
  - `agent-config-v1.4-subscribe.sql`

如果 `mysql_data` 已存在，初始化 SQL 不会重复执行。

