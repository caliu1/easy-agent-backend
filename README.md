# EasyAgent Backend

`EasyAgent` 是一个面向多 Agent 编排与运行的后端服务，支持：

- 多种 Agent 工作流（`sequential / parallel / loop / supervisor`）
- 动态 Agent 配置中心（创建、更新、发布、下线、回滚、分页查询）
- Agent 广场与订阅（广场发布/下架、订阅/取消订阅、我的订阅）
- 用户注册登录与账户落库
- `chat_stream` 流式事件输出（SSE）

---

## v1.4 重点更新

- 完成 API / APP / DOMAIN / INFRA / TRIGGER / TYPES 六层职责收敛
- 领域按业务上下文拆分为 `agent`、`user`、`session`
- 配置查询入口统一：保留分页查询，移除冗余列表查询接口
- 引入 `UserId` 值对象，统一用户 ID 规范化与校验
- 清理遗留 VO（弃用模型移除），降低领域噪声
- API DTO 按业务域重组到 `agent/*`、`user/*`

---

## 1. 项目结构（当前）

```text
easy-agent-backend
├── easy-agent-api              # API 契约层（对外接口 + DTO + application 契约）
├── easy-agent-types            # 通用枚举/异常/常量
├── easy-agent-domain           # 领域层（agent/user/session）
├── easy-agent-infrastructure   # 基础设施层（MyBatis-Plus 持久化实现）
├── easy-agent-trigger          # 触发层（HTTP Controller，实现 api 契约）
└── easy-agent-app              # 应用层与启动模块（编排用例、装配 Bean）
```

说明：

- `trigger` 实现 `api` 接口
- `trigger` 调用 `api.application` 契约
- `app` 实现 `api.application`，并调用 `domain`
- `infrastructure` 实现 `domain.repository`

---

## 2. 领域划分（v1.4）

- `cn.caliu.agent.domain.agent`：Agent 配置、发布、回滚、广场与运行时装配
- `cn.caliu.agent.domain.user`：用户账户认证、订阅关系
- `cn.caliu.agent.domain.session`：会话创建与会话绑定

---

## 3. 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.x
- Node.js 20+（前端在 `easy-agent-front`）

---

## 4. 数据库初始化

按顺序执行：

1. `docs/dev-ops/mysql/sql/agent-config-v1.1.sql`
2. `docs/dev-ops/mysql/sql/agent-config-v1.2-plaza.sql`
3. `docs/dev-ops/mysql/sql/agent-config-v1.3-user-auth.sql`
4. `docs/dev-ops/mysql/sql/agent-config-v1.4-subscribe.sql`

---

## 5. 启动方式

### 5.1 启动后端

在 `easy-agent-backend` 目录执行：

```bash
mvn -pl easy-agent-app -am spring-boot:run
```

或先编译：

```bash
mvn -pl easy-agent-app -am -DskipTests compile
```

### 5.2 启动前端

在 `easy-agent-front` 目录执行：

```bash
npm install
npm run dev
```

默认访问：`http://localhost:3000`  
默认后端地址：`http://127.0.0.1:8091`

---

## 6. 关键接口（当前）

### 6.1 用户认证

- `POST /api/v1/user_register`
- `POST /api/v1/user_login`

### 6.2 Agent 配置中心

- `POST /api/v1/agent_config_create`
- `POST /api/v1/agent_config_update`
- `POST /api/v1/agent_config_delete`
- `GET  /api/v1/agent_config_detail`
- `POST /api/v1/agent_config_page_query`
- `POST /api/v1/agent_config_publish`
- `POST /api/v1/agent_config_offline`
- `POST /api/v1/agent_config_rollback`

### 6.3 广场与订阅

- `GET  /api/v1/agent_config_plaza_list`
- `POST /api/v1/agent_config_plaza_publish`
- `POST /api/v1/agent_config_plaza_offline`
- `GET  /api/v1/agent_config_my_subscribe_list`
- `POST /api/v1/agent_config_subscribe`
- `POST /api/v1/agent_config_unsubscribe`

### 6.4 聊天与会话

- `GET  /api/v1/query_ai_agent_config_list`
- `POST /api/v1/create_session`
- `POST /api/v1/chat`
- `POST /api/v1/chat_stream`

---

## 7. Docker 部署

- 部署脚本与编排文件参考：`docs/dev-ops/docker-server`
- 详细说明见：`docs/dev-ops/docker-server/README.md`

---

## 8. 版本日志

- [v1.1](./docs/changelog/v1.1.md)
- [v1.2](./docs/changelog/v1.2.md)
- [v1.3](./docs/changelog/v1.3.md)
- [v1.4](./docs/changelog/v1.4.md)

---

## 9. License

请以仓库中的 `LICENSE` 文件为准。

