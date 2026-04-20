# AI Agent 脚手架（agent-scaffold-lite）

一个基于 Java + Spring + DDD 的 AI Agent 开发脚手架，支持通过 YML 与数据库双通道装配 Agent、工作流编排、流式会话输出与前端管理平台。

## 项目能力

- Agent 配置化装配：支持 `YML` 系统 Agent + `DB` 动态 Agent 并存
- 多种编排模式：`sequential` / `parallel` / `loop` / `supervisor`
- 标准会话接口：`chat` / `chat_stream`（SSE 流式）
- 配置中心能力：Agent 配置 CRUD、发布、下线、回滚、分页检索
- Agent 广场能力：我的 Agent / 广场 Agent 隔离展示与发布控制
- 前端创建页增强：接入固定系统 Agent（Agent Config Writer）辅助生成配置

## v1.2 更新（简述）

v1.2 聚焦“动态装配 + 配置中心 + 广场化 + 创建体验增强”，核心变化如下：

- 新增 Agent 配置中心（DB 存储，支持 CRUD + 发布生命周期）
- 引入 MyBatis-Plus 纯 MP 化实现（BaseMapper + Wrapper + 分页插件）
- 新增 Agent 广场（官方 Agent + 用户发布 Agent），并与“我的 Agent”分区
- 新建 Agent 页面支持与 `AgentConfigWriterAgent` 对话并动态回填表单
- 流式输出链路继续增强，前后端按 SSE 事件稳定拼装

详细内容请查看：

- [v1.2 详细更新日志](./docs/changelog/v1.2.md)
- [v1.1 详细更新日志](./docs/changelog/v1.1.md)

## 目录建议

- `agent-scaffold-lite-app`：应用启动与系统配置
- `agent-scaffold-lite-domain`：领域逻辑与工作流编排
- `agent-scaffold-lite-trigger`：HTTP 触发层（含 SSE）
- `agent-scaffold-lite-infrastructure`：持久化与仓储实现
- `agent-scaffold-lite-api`：接口定义与 DTO
- `agent-scaffold-lite-types`：通用类型与常量

v1.1 重点引入 Supervisor 动态路由能力和标准化流式事件：

- 新增 `SupervisorRoutingAgent`，支持主 Agent 根据上下文动态选择子 Agent
- `chat_stream` 支持 `thinking / route / reply / final` 事件类型
- 支持主 Agent 阶段性回复与最终回复的流式输出

详细内容请查看：

- [v1.1 详细更新日志](./docs/changelog/v1.1.md)

## 目录建议

- `agent-scaffold-lite-app`：应用与配置
- `agent-scaffold-lite-domain`：领域与工作流编排
- `agent-scaffold-lite-trigger`：HTTP 触发层（含 SSE）
- `agent-scaffold-lite-api`：接口与 DTO
- `agent-scaffold-lite-types`：通用类型与常量

## 数据库脚本
- 基础建表（含配置中心核心表）：`docs/dev-ops/mysql/sql/agent-config-v1.1.sql`
- 广场扩展脚本：`docs/dev-ops/mysql/sql/agent-config-v1.2-plaza.sql`

## License

按项目实际 License 文件为准。