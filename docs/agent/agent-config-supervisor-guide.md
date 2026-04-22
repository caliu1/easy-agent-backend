# Agent 配置通用教程（覆盖常见场景，重点 Supervisor）

## 1. 这份教程解决什么问题

这份文档用于快速落地平台 Agent 配置，覆盖以下目标：

- 会写可运行的 Agent 配置（YML / configJson）。
- 知道什么时候用 `single / sequential / parallel / loop / supervisor`。
- 掌握 Supervisor 的核心协议与避坑方式。
- 能从创建、发布、联调一路跑通。

---

## 2. 配置入口与命名规则

### 2.1 两种配置入口

- 系统静态配置（YML）  
  路径示例：`easy-agent-app/src/main/resources/agent/*.yml`
- 动态配置中心（DB）  
  通过 `agent_config_create` / `agent_config_update` 写入 `configJson`

### 2.2 字段命名差异

- YML 常用 `kebab-case`，例如 `agent-workflows`、`router-agent`、`max-iterations`
- `configJson` 使用 `camelCase`，例如 `agentWorkflows`、`routerAgent`、`maxIterations`

---

## 3. 通用配置骨架（configJson）

```json
{
  "appName": "DemoApp",
  "agent": {
    "agentId": "100200",
    "agentName": "Demo Agent",
    "agentDesc": "示例智能体"
  },
  "module": {
    "aiApi": {
      "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/",
      "apiKey": "${DASHSCOPE_API_KEY}",
      "completionsPath": "v1/chat/completions",
      "embeddingsPath": "v1/embeddings"
    },
    "chatModel": {
      "model": "qwen-plus"
    },
    "agents": [
      {
        "name": "WorkerA",
        "description": "子智能体A",
        "instruction": "你是A专家",
        "outputKey": "worker_a_result"
      }
    ],
    "agentWorkflows": [
      {
        "type": "sequential",
        "name": "DemoPipeline",
        "description": "示例流程",
        "subAgents": ["WorkerA"]
      }
    ],
    "runner": {
      "agentName": "DemoPipeline",
      "pluginNameList": ["myTestPlugin", "myLogPlugin"]
    }
  }
}
```

---

## 4. 工作流选型速查

| 类型 | 适用场景 | 关键字段 | 重点注意 |
|---|---|---|---|
| single | 单轮问答、简单工具调用 | 不需要 `agentWorkflows` | `runner.agentName` 直接指向单 Agent |
| sequential | 固定流水线（A→B→C） | `type=sequential`、`subAgents` | 顺序强依赖，前一阶段输出可被后一阶段引用 |
| parallel | 多路并发收集信息 | `type=parallel`、`subAgents` | 常与后续 `sequential` 组合做汇总 |
| loop | 同一流程反复迭代优化 | `type=loop`、`maxIterations` | 必须设置合理迭代上限，防止过长循环 |
| supervisor | 动态路由专家、可中途收敛 | `type=supervisor`、`routerAgent`、`maxIterations` | Router 输出必须是严格 JSON 协议 |

---

## 5. Supervisor 专章（重点）

## 5.1 最重要的结构要求

- `agentWorkflows[].type` 必须是 `supervisor`。
- `subAgents` 必须包含 Router 和所有 Worker。
- `routerAgent` 必须能在 `subAgents` 中找到。
- `runner.agentName` 应指向这个 supervisor 工作流名。
- `maxIterations` 建议显式配置（通常 3~8）。

说明：
- 运行时如果 `routerAgent` 为空，会回退到 `subAgents` 第一个；建议不要依赖这个隐式行为，始终显式填写。

## 5.2 Router 输出协议（必须严格遵守）

Router Agent 的输出必须是单个 JSON 对象：

```json
{"thought":"简短分析","action":"route|final","nextAgent":"WorkerName","reply":"给用户的话"}
```

字段语义：

- `thought`：路由判断依据。
- `action`：只能是 `route` 或 `final`。
- `nextAgent`：`action=route` 时必须是已注册 Worker 名称。
- `reply`：
  - `route`：阶段性回复，可为空。
  - `final`：最终回复，面向用户直接输出。

## 5.3 运行机制与常见失败点

- 若 `action=route` 且 `nextAgent` 不存在，会报 `Unknown nextAgent` 并终止。
- 若 `action` 不是 `route/final`，会报 `Unsupported route action`。
- 若 Router 输出无法解析 JSON，会报 `Route decision parse failed`。
- 触发 `maxIterations` 后仍未 `final`，系统会给兜底 final（建议 Router 主动收敛，别依赖兜底）。

## 5.4 最小可用 Supervisor 示例（YML）

```yaml
ai:
  agent:
    config:
      tables:
        supportSupervisorAgent:
          app-name: SupportSupervisorApp
          agent:
            agent-id: "100210"
            agent-name: Support Supervisor
            agent-desc: 动态分发客服问题到不同专家
          module:
            ai-api:
              base-url: https://dashscope.aliyuncs.com/compatible-mode/
              api-key: ${DASHSCOPE_API_KEY}
              completions-path: v1/chat/completions
              embeddings-path: v1/embeddings
            chat-model:
              model: qwen-plus
            agents:
              - name: SupervisorRouter
                description: 负责动态路由与收敛
                instruction: |
                  你是主控路由器，只输出一个 JSON：
                  {"thought":"...","action":"route|final","nextAgent":"BillingExpert|TechExpert|GeneralExpert","reply":"..."}
              - name: BillingExpert
                description: 账单专家
                instruction: 你是账单专家，请直接解决账单问题。
              - name: TechExpert
                description: 技术专家
                instruction: 你是技术专家，请直接解决技术问题。
              - name: GeneralExpert
                description: 通用专家
                instruction: 你是通用专家，请直接给出答案。
            agent-workflows:
              - type: supervisor
                name: SupportSupervisorPipeline
                description: 主管路由专家并最终回复
                router-agent: SupervisorRouter
                max-iterations: 5
                sub-agents:
                  - SupervisorRouter
                  - BillingExpert
                  - TechExpert
                  - GeneralExpert
            runner:
              agent-name: SupportSupervisorPipeline
              plugin-name-list:
                - myTestPlugin
                - myLogPlugin
```

---

## 6. 各类场景模板（可直接改）

## 6.1 场景A：单 Agent 工具助手

适用：个人助理、轻量问答、工具调用。

- 不配 `agentWorkflows`
- `runner.agentName` 指向单 Agent

## 6.2 场景B：顺序流水线（生成 -> 审查 -> 修订）

适用：代码生成、文档生产、报告加工。

- `type=sequential`
- `subAgents` 按执行顺序填写

## 6.3 场景C：并行检索 + 顺序汇总

适用：多源研究、信息归并。

推荐组合：

1. 第一段 `parallel`（多个 Researcher 并发）
2. 第二段 `sequential`（Synthesis 汇总）

## 6.4 场景D：循环优化

适用：反复打磨文案、逐轮改写。

- `type=loop`
- 设置 `maxIterations`（如 3 或 5）

## 6.5 场景E：Supervisor 专家调度（推荐复杂任务）

适用：用户意图多变、需要动态决策路径。

- 使用 `type=supervisor`
- Router 负责决策、Worker 负责执行
- Router 最终用 `action=final` 收敛

---

## 7. 从配置到上线：标准流程

## 7.1 创建配置

调用 `POST /api/v1/agent_config_create`，核心字段：

- `agentId`
- `configJson`（完整 JSON）
- `operator`

建议同时传：

- `appName`、`agentName`、`agentDesc`（用于后台展示和校验）

## 7.2 发布生效

调用 `POST /api/v1/agent_config_publish`。

发布前系统会先做装配校验；校验通过才会激活运行时。

## 7.3 对话联调

1. `POST /api/v1/create_session`
2. `POST /api/v1/chat` 或 `POST /api/v1/chat_stream`

`chat_stream` 常见事件类型：

- `thinking`
- `route`
- `reply`
- `final`

---

## 8. 常见报错与排查

| 报错 | 常见原因 | 处理建议 |
|---|---|---|
| `configJson is blank` | 请求体没传配置 JSON | 补齐 `configJson` |
| `configJson parse failed` | JSON 非法、字段结构错误 | 用 JSON 校验器先校验 |
| `agentId mismatch between request and configJson` | 外层 `agentId` 与 JSON 内 `agent.agentId` 不一致 | 保持一致 |
| `runner.agentName is null` | `module.runner.agentName` 为空 | 填写可执行 Agent/Workflow 名称 |
| `supervisor workflow subAgents is empty` | Supervisor 未配置子 Agent | 填写 `subAgents` |
| `supervisor workflow routerAgent is empty` | 未配置 Router 且无可回退项 | 显式填写 `routerAgent` |
| `router agent not found` | `routerAgent` 名称不在 `subAgents` 中 | 名称与 `agents[].name` 对齐 |
| `Unknown nextAgent` | Router 决策了不存在的 Worker | 限定 Router 只输出枚举 Worker 名 |
| `Unsupported route action` | Router 输出了非法 action | 将 action 严格限制为 `route/final` |
| `Route decision parse failed` | Router 输出不是单一 JSON | Router 指令中强约束“只输出一个 JSON 对象” |

---

## 9. Supervisor 配置检查清单（上线前）

- Router 指令里是否强制“只输出一个 JSON”。
- `action` 是否严格限制为 `route/final`。
- `nextAgent` 是否有白名单约束。
- 是否有防循环规则（重复路由、最大轮次、信息不足时直接问用户）。
- `maxIterations` 是否符合业务复杂度。
- `reply` 是否区分阶段回复与最终回复。
- `runner.agentName` 是否指向 supervisor 工作流。

---

## 10. 参考示例

- `easy-agent-app/src/main/resources/agent/test-supervisor-agent.yml`
- `easy-agent-app/src/main/resources/agent/draw-io-agent.yml`
- `easy-agent-app/src/main/resources/agent/test-agent.yml`
- `easy-agent-app/src/main/resources/agent/parallel_research_app.yml`
- `easy-agent-app/src/main/resources/agent/test-single-agent.yml`

