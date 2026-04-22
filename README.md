# EasyAgent Backend

`EasyAgent` 是一个面向智能体编排与运营的后端服务，支持：

- 多种 Agent 工作流（`sequential / parallel / loop / supervisor`）
- 动态 Agent 配置中心（创建、发布、下线、回滚、分页检索）
- Agent 广场（我的 Agent / 广场 Agent 隔离）
- 用户注册登录与账号落库
- 广场 Agent 订阅能力（订阅/取消订阅/我的订阅）
- `chat_stream` 流式事件输出（SSE）

---

## 1. 项目结构（当前）

```text
easy-agent-backend
├── easy-agent-api              # API 接口定义与 DTO
├── easy-agent-types            # 通用枚举/异常/常量
├── easy-agent-domain           # 领域服务与编排逻辑
├── easy-agent-infrastructure   # 持久层（MyBatis-Plus）
├── easy-agent-trigger          # HTTP 触发层 Controller
└── easy-agent-app              # Spring Boot 启动模块
```

说明：本项目已从早期 `agent-scaffold-lite-*` 命名演进到 `easy-agent-*` 模块命名，本文档以当前项目结构为准。

---

## 2. 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.x
- Node.js 20+（前端在 `easy-agent-front`）

---

## 3. 数据库初始化

按顺序执行以下 SQL：

1. `docs/dev-ops/mysql/sql/agent-config-v1.1.sql`
2. `docs/dev-ops/mysql/sql/agent-config-v1.2-plaza.sql`
3. `docs/dev-ops/mysql/sql/agent-config-v1.3-user-auth.sql`
4. `docs/dev-ops/mysql/sql/agent-config-v1.4-subscribe.sql`

新增表说明：

- `ai_user_account`：用户账号（登录凭据、状态、最后登录时间）
- `ai_agent_subscribe`：用户与广场 Agent 的订阅关系

---

## 4. 启动方式

### 4.1 启动后端

在 `easy-agent-backend` 目录执行：

```bash
mvn -pl easy-agent-app -am spring-boot:run
```

或先编译：

```bash
mvn -pl easy-agent-app -am -DskipTests compile
```

### 4.2 启动前端

在 `easy-agent-front` 目录执行：

```bash
npm install
npm run dev
```

默认访问：`http://localhost:3000`  
默认后端地址：`http://127.0.0.1:8091`（可通过前端环境变量覆盖）

---

## 5. 关键接口

### 5.1 用户认证

- `POST /api/v1/user_register`：注册
- `POST /api/v1/user_login`：登录

### 5.2 Agent 配置中心

- `POST /api/v1/agent_config_create`
- `POST /api/v1/agent_config_update`
- `POST /api/v1/agent_config_delete`
- `GET  /api/v1/agent_config_detail`
- `GET  /api/v1/agent_config_list`
- `GET  /api/v1/agent_config_my_list`
- `POST /api/v1/agent_config_publish`
- `POST /api/v1/agent_config_offline`
- `POST /api/v1/agent_config_rollback`

### 5.3 Agent 广场与订阅

- `GET  /api/v1/agent_config_plaza_list`
- `POST /api/v1/agent_config_plaza_publish`
- `POST /api/v1/agent_config_plaza_offline`
- `GET  /api/v1/agent_config_my_subscribe_list`
- `POST /api/v1/agent_config_subscribe`
- `POST /api/v1/agent_config_unsubscribe`

### 5.4 会话能力

- `POST /api/v1/create_session`
- `POST /api/v1/chat`
- `POST /api/v1/chat_stream`

---

## 6. 前端页面能力（对应当前实现）

- 登录页：支持注册/登录，成功后写入会话 cookie
- 首页：
- `我的Agent`
- `我的订阅`
- `Agent广场`
- 广场卡片右下角爱心订阅按钮：
- 未订阅：空心
- 已订阅：实心

---

## 7. 版本日志

- [v1.1](./docs/changelog/v1.1.md)：Supervisor 动态路由与流式事件标准化
- [v1.2](./docs/changelog/v1.2.md)：配置中心、广场能力、前端创建体验增强
- [v1.3](./docs/changelog/v1.3.md)：用户认证落库 + 广场订阅体系

---

## 8. License

请以仓库中的 `LICENSE` 文件为准。

