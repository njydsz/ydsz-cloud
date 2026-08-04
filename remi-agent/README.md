# remi-agent

> AI Agent 智能体服务 — LLM 对话 / Agent 编排 / Tool Calling / RAG 知识增强 / 记忆管理 / 人工审批 / 调试器

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9008**（按构建顺序 9/10） |
| **服务名** | `remi-agent` |
| **构建顺序** | 9/10（Maven 构建最后一个部署单元） |
| **数据库** | PostgreSQL（`remi_agent_*` 表） |
| **依赖** | Nacos、PostgreSQL、Redis、LLM API |
| **公共依赖** | common-web / common-auth / common-redis / common-jdbc / common-queue |

## 核心职责

本模块是 REMI 的 **AI 智能体中心**，提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理、调试器全链路能力。

### 1. Agent 执行器（5 种）

| 执行器 | 实现类 | 适用场景 |
|---|---|---|
| **Simple Agent** | `SimpleAgentExecutor` | 单轮对话，无工具调用 |
| **ReAct Agent** | `ReActAgentExecutor` | 推理-行动循环，支持 Tool Calling |
| **Router Agent** | `RouterAgentExecutor` | 路由分发到子 Agent |
| **Plan-Execute Agent** | `PlanExecuteAgentExecutor` | 先规划后执行，复杂任务分解 |
| **RAG Agent** | `RagAgentExecutor` | 检索增强生成，结合知识库回答 |

### 2. 核心能力清单

| 能力 | 说明 | 关键类 |
|---|---|---|
| **LLM Provider 抽象** | `LlmClient` 统一接口 + OpenAI 兼容实现（覆盖 GPT/DeepSeek/Qwen/Moonshot/智谱） | `LlmClient` / `LlmClientRouter` |
| **同步对话** | 完整请求/响应 | `ChatService` |
| **流式对话（SSE）** | 逐 token 推送 | `ChatService` + `SseEmitter` |
| **对话管理** | `Conversation` 聚合 + `ChatMessage` 值对象 + Redis 滑动窗口记忆 | `Conversation` / `ConversationMemory` |
| **Prompt 模板** | `PromptTemplate` + `#{var}` 变量替换 + 模板管理 | `PromptManagementService` / `PromptTemplate` |
| **多模型路由** | Fallback 降级链 | `LlmClientRouter` |
| **Token 计量** | 每次 LLM 调用记录 prompt/completion/total tokens | `TokenUsage` |
| **DAG 编排** | DSL 解析 + DAG 执行器 | `DagDslParser` / `DagOrchestrationExecutor` |
| **Tool Calling** | `@Tool` 注解 + 工具注册中心 | `ToolRegistry` / `ToolExecutor` |
| **RAG 知识增强** | 文档摄入 Pipeline + 向量存储 + 检索 | `RagService` / `DocumentIngestionService` / `VectorStore` |
| **记忆管理** | Redis 滑动窗口 + TTL 过期 | `ConversationMemory` |
| **人工审批** | Agent 执行中暂停等待人工审批 | `HumanApprovalService` |
| **调试器** | 断点 / 单步 / 快照 / 恢复 | `AgentDebuggerService` |
| **成本分析** | LLM 调用成本统计与分析 | `CostAnalysisService` |
| **安全护栏** | 输入/输出 Guardrail + PII 脱敏 | `GuardrailService` / `InputGuardrail` / `OutputGuardrail` |
| **请求防护** | 限流 / 内容安全 / 越狱检测 | `AgentRequestGuard` |
| **健康检查** | LLM Provider + Memory + RAG 状态 | `AgentHealthIndicator` |
| **指标埋点** | 对话次数 / Token 用量 / 延迟 | `AgentMetrics` |
| **队列集成** | 异步任务 + 跨服务事件 | `AgentQueueChannels` |
| **跨模块监听** | 接收其他模块事件 | `CrossModuleEventListener` |

### 3. Web 层 Controller（8 个）

| Controller | 路径前缀 | 主要端点 |
|---|---|---|
| `ChatController` | `/agent/chat` | 同步对话 / 流式对话（SSE）/ 对话历史 / 清除历史 |
| `AgentController` | `/agent` | Agent CRUD / 启停 / 状态查询 |
| `AgentDefinitionController` | `/agent/definition` | Agent 定义 CRUD / 版本管理 / 启用/禁用 |
| `AgentMetadataController` | `/agent/metadata` | Agent 元数据 / 模型配置 / Token 用量 |
| `DagController` | `/agent/dag` | DAG 编排定义 / 触发 / 状态查询 |
| `DebugController` | `/agent/debug` | 调试器断点 / 单步 / 快照 / 恢复 |
| `HumanApprovalController` | `/agent/approval` | 人工审批提交 / 查询待审批 / 审批结果 |
| `RagController` | `/agent/rag` | 文档上传 / 检索 / 索引管理 |

## DDD 分层结构

```
remi-agent/
├── pom.xml
├── remi-agent-api/                    # API 层：Feign Client + DTO
├── remi-agent-domain/                 # 领域层：Entity + 领域模型 + Gateway
│   └── src/main/java/com/remisoft/agent/domain/
│       ├── agent/                     # Agent 聚合（AgentDefinition / AgentDag / AgentExecutor / ExecutionPlan / AgentExecutionRequest）
│       ├── conversation/              # 对话聚合（Conversation / ConversationMemory）
│       ├── entity/                    # 数据库实体
│       │   └── AgentDefinitionDO.java # ⚠️ 保留 DO 后缀（与同模块 AgentDefinition 同名，符合 entity-naming 例外）
│       ├── gateway/                   # LLM 网关（LlmClient / LlmException）
│       ├── guardrail/                 # 安全护栏（InputGuardrail / OutputGuardrail / GuardrailResult）
│       ├── model/                     # 模型对象（ChatMessage / ChatRequest / ChatResponse / TokenUsage / ToolCall / ToolDefinition / LlmModelConfig / MessageRole / ChatChunk）
│       ├── prompt/                    # Prompt 模板（PromptTemplate）
│       ├── rag/                       # RAG（EmbeddingClient / VectorStore / TextChunker / TextChunk）
│       ├── tool/                      # 工具调用（Tool / ToolExecutor / ToolParam / ToolRegistration / ToolRegistry）
│       └── trace/                     # 轨迹（TraceRecorder）
├── remi-agent-infra/                  # 基础设施层：LLM Provider + Redis 记忆 + 向量存储
├── remi-agent-server/                 # 应用层：Service + Config + Health + Metrics
│   └── src/main/java/com/remisoft/agent/server/
│       ├── agent/                     # Agent 服务（5 种执行器 + DAG 编排 + 人工审批 + 工厂）
│       ├── analytics/                 # 成本分析
│       ├── chat/                      # 对话服务 + 请求防护 + 护栏
│       ├── config/                    # AgentAutoConfiguration + AgentProperties
│       ├── debug/                     # 调试器
│       ├── health/                    # AgentHealthIndicator
│       ├── listener/                  # 跨模块事件监听
│       ├── metrics/                   # AgentMetrics（Prometheus）
│       ├── prompt/                    # Prompt 管理
│       ├── queue/                     # 队列通道定义
│       └── rag/                       # RAG 服务 + 文档摄入 + 搜索 Provider
└── remi-agent-web/                    # Web 层：Controller + 启动类
    └── src/main/java/com/remisoft/agent/web/
        ├── AgentApplication.java
        └── controller/                # 8 个 Controller
```

## 使用方式

### 1. 配置

```yaml
remi:
  agent:
    enabled: true
    llm:
      default-provider: ${LLM_PROVIDER:openai}      # openai / deepseek / qwen / ollama
      default-model: ${LLM_MODEL:gpt-4o-mini}
      api-key: ${LLM_API_KEY:}
      base-url: ${LLM_BASE_URL:https://api.openai.com/v1}
      temperature: ${LLM_TEMPERATURE:0.7}
      max-tokens: ${LLM_MAX_TOKENS:2048}
      timeout-seconds: ${LLM_TIMEOUT:60}
    memory:
      ttl-hours: 24
      max-messages: 20
    rag:
      enabled: ${RAG_ENABLED:false}
      vector-store: ${RAG_VECTOR_STORE:memory}      # memory / pgvector
      embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
      embedding-api-key: ${EMBEDDING_API_KEY:}
      embedding-base-url: ${EMBEDDING_BASE_URL:}
      dimension: ${EMBEDDING_DIMENSION:1536}
      chunk-size: 500
      chunk-overlap: 50
      top-k: 5
      min-score: 0.7
  # P0-1: 统一线程池配置（remi-common-thread）
  thread:
    enabled: true
    pools:
      agentDag:
        type: VIRTUAL                              # JDK 21+ 虚拟线程
        thread-name-prefix: agent-dag-
        await-termination-seconds: 30
      agentHeartbeat:
        core-size: 2
        max-size: 4
        queue-capacity: 100
        thread-name-prefix: agent-heartbeat-
        reject-policy: CALLER_RUNS
        await-termination-seconds: 10
```

### 2. 同步对话

```bash
curl -X POST http://localhost:9008/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下REMI系统"}'
```

### 3. 流式对话（SSE）

```bash
curl -N -X POST http://localhost:9008/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我分析项目进度"}'
```

### 4. 对话历史

```bash
# 获取历史
curl http://localhost:9008/agent/chat/history?conversationId=xxx

# 清除历史
curl -X DELETE http://localhost:9008/agent/chat/history?conversationId=xxx
```

## 启动

```bash
cd remi-cloud
mvn -pl remi-common -am install -DskipTests
mvn -pl remi-agent spring-boot:run
```

## 技术选型

| 决策 | 方案 | 理由 |
|---|---|---|
| LLM API | OpenAI 兼容 API | 事实标准，覆盖 90% 国产模型 |
| 流式输出 | SseEmitter + WebClient | 非 WebSocket，轻量级 |
| 记忆存储 | Redis List | 滑动窗口 + TTL 自动过期 |
| 向量存储 | 内存（默认）/ PostgreSQL pgvector | 复用现有 PG 基础设施 |
| 工具调用 | 自研 @Tool 注解 | 轻量级，无额外依赖 |
| DAG 编排 | 自研 DagDslParser | DSL 解析 + DAG 执行 |
| Agent 模式 | ReAct / Router / Plan-Execute / RAG / Simple | 覆盖主流 Agent 范式 |

## 常见问题

### Q1：LLM 调用超时

1. 检查 `remi.agent.llm.timeout-seconds`（默认 60 秒）
2. 检查 LLM API 网络连通性
3. 长文本生成建议使用流式对话（SSE）避免超时

### Q2：RAG 检索结果不准

1. 检查 `remi.agent.rag.min-score`（默认 0.7），可适当降低阈值
2. 检查文档分块参数 `chunk-size` / `chunk-overlap`
3. 检查 embedding 模型与维度是否匹配

### Q3：Agent DAG 执行卡住

1. 检查 DAG 节点依赖是否循环
2. 检查人工审批节点是否待审批（调用 `/agent/approval` 查询）
3. 检查 Agent 心跳线程池是否正常（`agent-heartbeat` 线程池）

### Q4：Token 用量统计不准

1. 部分 LLM Provider 不返回完整 Token 用量
2. 流式对话需累加每个 chunk 的 Token 数
3. 可通过 `/agent/metrics` 查询累计用量

---

> 本模块是 REMI AI 能力的核心入口，与 literule（规则引擎）、workflow（工作流）、message（消息引擎）三引擎深度融合。
> Agent 触发动作可联动 cronjob（定时任务）/ workflow（审批流）/ message（通知）。
