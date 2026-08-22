# ydsz-agent

> AI Agent 智能体服务 — LLM 对话 / Agent 编排 / Tool Calling / RAG 知识增强 / 记忆管理 / 人工审批 / 调试器 / MCP / Text2SQL

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动），父 POM 聚合 6 个子模块 |
| **端口** | **9008**（按构建顺序 9/10） |
| **服务名** | `ydsz-agent` |
| **构建顺序** | 9/10（Maven 构建最后一个部署单元） |
| **数据库** | PostgreSQL（`ydsz_agent_*` 表 + `ydsz_prompt_*` 表） |
| **依赖** | Nacos、PostgreSQL、Redis、LLM API、MCP Server（可选） |
| **公共依赖** | common-web / common-auth / common-redis / common-jdbc / common-queue / common-safe / common-thread / common-event / common-docs / common-sentry / common-search / common-tenant |
| **子模块** | `ydsz-agent-api` / `ydsz-agent-domain` / `ydsz-agent-infra` / `ydsz-agent-server` / `ydsz-agent-app` / `ydsz-agent-web` |

## 核心职责

本模块是 YDSZ 的 **AI 智能体中心**，提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理、调试器、MCP 工具发现、Text2SQL 自然语言查询全链路能力。

### 1. Agent 执行器（6 种）

| 执行器 | 实现类 | 适用场景 |
|---|---|---|
| **Simple Agent** | `SimpleAgentExecutor` | 单轮对话，无工具调用 |
| **ReAct Agent** | `ReActAgentExecutor` | 推理-行动循环，支持 Tool Calling |
| **Supervisor Agent** | `SupervisorAgentExecutor` | 主管-子 Agent 协同（路由/委派） |
| **Plan-Execute Agent** | `PlanExecuteAgentExecutor` | 先规划后执行，复杂任务分解 |
| **RAG Agent** | `ReActAgentExecutor`（注入 ragService） | 检索增强生成，结合知识库回答（工厂将 RAG 类型路由到 ReAct 执行器并注入 `ragService`） |
| **DAG Agent** | `DagOrchestrationExecutor` | DAG 编排执行（Node + Edge + State 图引擎） |

> **说明**：P2-1 重构后 `RouterAgentExecutor` 已删除，多节点编排统一由 `DagOrchestrationExecutor` 承担。未知类型默认回退到 `ReActAgentExecutor`。

### 2. 核心能力清单

| 能力 | 说明 | 关键类 |
|---|---|---|
| **LLM Provider 抽象** | `LlmClient` 统一接口 + OpenAI 兼容实现（覆盖 GPT/DeepSeek/Qwen/Moonshot/智谱） | `LlmClient` / `LlmClientRouter` |
| **Agent 应用门面** | 解耦 Controller 与内部服务（ChatService / AgentFactory / AgentDefinitionService） | `AgentFacade` / `AgentFacadeImpl` |
| **同步对话** | 完整请求/响应 | `ChatService` |
| **流式对话（SSE）** | 逐 token 推送，心跳保活 + 断连检测 | `ChatService` + `SseExecutor` |
| **多模态对话** | 支持文本+图片多模态输入（Vision 模型），同步/流式均支持 | `AgentFacade.chat(MessageContent)` / `AgentFacade.stream(MessageContent)` |
| **同步执行 Agent** | 等待完整响应后返回（ReAct / RAG / Plan-Execute / Supervisor / DAG） | `AgentFacade.execute` |
| **流式执行 Agent（SSE）** | 逐 chunk 推送 LLM 响应，支持 DAG 节点级进度事件回调 | `AgentFacade.executeStream(request, chunkConsumer, progressConsumer)` |
| **批量对话** | JDK 21 结构化并发并行处理多条对话，单条失败不影响其他条目 | `AgentFacade.batchChat` |
| **对话管理** | `Conversation` 聚合 + `ChatMessage` 值对象 + Redis 滑动窗口记忆 | `Conversation` / `ConversationMemory` |
| **Prompt 模板** | `PromptTemplate` + `#{var}` 变量替换 + 数据库管理 + 评估对比 | `PromptManagementService` / `PromptTemplate` / `PromptEvaluationService` |
| **多模型路由** | Fallback 降级链 + 多 Provider 配置 | `LlmClientRouter` |
| **Token 计量** | 每次 LLM 调用记录 prompt/completion/total tokens | `TokenUsage` |
| **DAG 编排** | YAML DSL 解析 + DAG 执行器（拓扑排序 + 节点派发 + 检查点续跑） | `DagDslParser` / `DagOrchestrationExecutor` |
| **Tool Calling** | `@Tool` 注解 + 工具注册中心 + 注解扫描器 | `ToolRegistry` / `ToolExecutor` / `ToolAnnotationScanner` |
| **MCP 工具发现** | MCP Server 自动发现并注册工具到 ToolRegistry | `McpClientProvider` / `McpToolAdapter` / `SseMcpClientProvider` |
| **RAG 知识增强** | 文档摄入 Pipeline + 混合检索（向量 + 全文 RRF 融合）+ Reranker 精排 | `RagService` / `DocumentIngestionService` / `HybridRetriever` / `VectorStore` / `Reranker` |
| **记忆管理** | Redis 滑动窗口 + TTL 过期 + 摘要压缩记忆 | `ConversationMemory` / `SummaryConversationMemory` |
| **人工审批** | Agent 执行中暂停等待人工审批 | `HumanApprovalService` |
| **调试器** | 链路查询 / 链路详情 / 链路重放 | `AgentDebuggerService` |
| **成本分析** | LLM 调用成本统计与分析 | `CostAnalysisService` |
| **可观测性面板** | 面板概览（今日成本、活跃会话）+ 模型使用分布 | `ObservabilityDashboardService` |
| **安全护栏** | 输入/输出 Guardrail + PII 脱敏 + Prompt 注入检测（可选） | `GuardrailService` / `InputGuardrail` / `OutputGuardrail` / `PiiMaskingGuardrail` / `PromptInjectionGuardrail` |
| **请求防护** | 限流 / 内容安全 / 越狱检测 | `AgentRequestGuard` |
| **LLM 语义缓存** | 双层缓存（L1 YdszCache + L2 Redis），精确哈希匹配，节省成本与延迟 | `CachedLlmClient` / `SemanticLlmCache` |
| **Text2SQL** | 自然语言转 SQL 查询，多重安全护栏（仅 SELECT、SQL 注入检测、结果行数限制） | `Text2SQLService` / `JdbcText2SQLService` / `Text2SQLTool` |
| **租户配额** | 单租户每日 Token 限额 + 月度预算 + 告警阈值 | `TenantQuotaService` |
| **健康检查** | LLM Provider + Memory + RAG + Trace + Cost 多维健康检查 | `AgentHealthIndicator` / `AgentAppHealthIndicator` |
| **指标埋点** | 对话次数 / Token 用量 / 延迟 / 运行时指标 | `AgentMetrics` / `AgentRuntimeMetrics`（Prometheus） |
| **API 文档** | Swagger UI + OpenAPI 3.0 自动生成 | `springdoc-openapi-starter-webmvc-ui` |
| **队列集成** | 异步任务 + 跨服务事件 | `AgentQueueChannels` |
| **跨模块监听** | 接收其他模块事件 | `CrossModuleEventListener` |
| **应用事件** | Agent 生命周期事件发布 | `AgentEventPublisher` |

### 3. Web 层 Controller（9 个，基路径 `/api/v1/agent`）

| Controller | 路径前缀 | 主要端点 |
|---|---|---|
| `AgentController` | `/api/v1/agent` | 同步执行 `/execute`、流式执行（SSE）`/execute/stream`、同步对话 `/chat`、流式对话（SSE）`/chat/stream`、批量对话 `/chat/batch`、对话历史 `/history`（GET/DELETE）。支持多模态（Vision）输入 |
| `AgentDefinitionController` | `/api/v1/agent/definitions` | Agent 定义 CRUD（列表 / 详情 / 按 code 查询 / 创建 / 更新 / 删除） |
| `AgentMetadataController` | `/api/v1/agent` | Agent 元数据（可用模型 `/models`、已注册工具 `/tools`）。与 `AgentController` 共享基路径 |
| `DagController` | `/api/v1/agent/dag` | DAG 编排执行 `/execute`、DSL 验证 `/validate`、检查点查询 `/checkpoint/{executionId}` |
| `DebugController` | `/api/v1/agent/debug` | 调试器链路列表 `/traces`、链路详情 `/trace/{traceId}`、链路重放 `/trace/{traceId}/replay` |
| `HumanApprovalController` | `/api/v1/agent/approvals` | 人工审批待审批列表 `/pending`、审批详情 `/{id}`、审批通过 `/{id}/approve`、审批拒绝 `/{id}/reject` |
| `RagController` | `/api/v1/agent/rag` | 文档摄入 `/ingest`、向量检索 `/search`、删除文档 `/documents/{documentId}`、统计 `/stats` |
| `ObservabilityController` | `/api/v1/agent/observability` | 可观测性概览 `/overview`、模型用量 `/model-usage` |
| `PromptController` | `/api/v1/agent/prompt` | Prompt 模板评估 `/evaluate`、对比评估 `/compare` |

## DDD 分层结构

```
ydsz-agent/
├── pom.xml                            # 父 POM（6 个子模块）
├── README.md
├── ydsz-agent-api/                    # API 层：Feign Client + DTO
│   └── src/main/java/com/njydsz/agent/api/
│       ├── dto/                       # 数据传输对象（ChatRequestDTO / ChatResponseDTO / BatchChatRequestDTO / BatchChatResponseDTO / AgentExecutionRequestDTO / DagExecutionDTO / DocumentIngestDTO / RagQueryDTO / PromptTemplateDTO / AgentTraceDetailDTO / AgentTraceListDTO）
│       ├── fallback/                  # Feign 降级
│       └── feign/                     # Feign Client 接口
├── ydsz-agent-domain/                 # 领域层：Entity + 领域模型 + Gateway + Repository + 枚举 + 值对象
│   └── src/main/java/com/njydsz/agent/domain/
│       ├── agent/                     # Agent 聚合（AgentDefinition / AgentDag / AgentExecutionContext / AgentExecutionRequest / AgentExecutor / DagCheckpoint / DagProgressEvent / ExecutionPlan）
│       ├── conversation/              # 对话聚合（Conversation / ConversationMemory）
│       ├── dto/                       # 领域 DTO（AgentApprovalDTO / AgentDefinitionDTO / AgentTraceDTO / AgentTraceStepDTO / PromptTemplateDTO / PromptVersionDTO / TokenUsageRecordDTO）
│       ├── enums/                     # 枚举（AgentExceptionCode / AgentStatusEnum）
│       ├── event/                     # 领域事件（AgentDomainEvent）
│       ├── gateway/                   # 网关接口（LlmClient / LlmException / CacheMetricsRecorder / DagCheckpointStore / PromptTemplateProvider / Text2SQLService）
│       ├── guardrail/                 # 安全护栏抽象（InputGuardrail / OutputGuardrail / GuardrailResult）
│       ├── json/                      # JSON 序列化模块（AgentJsonModule / ChatMessageSerializer / ChatRequestSerializer / TokenUsageDeserializer/Serializer / ToolCallDeserializer/Serializer / ToolDefinitionSerializer）
│       ├── model/                     # 模型值对象（ChatMessage / ChatRequest / ChatResponse / TokenUsage / ToolCall / ToolDefinition / LlmModelConfig / MessageRole / ChatChunk / MessageContent / BatchChatResult / CostEstimate / SseEvent / TenantQuota）
│       ├── prompt/                    # Prompt 模板（PromptTemplate — #{var} 变量替换）
│       ├── rag/                       # RAG 抽象（EmbeddingClient / VectorStore / TextChunker / TextChunk / Reranker / Retriever）
│       ├── repository/                # 仓储接口（AgentApprovalRepository / AgentDefinitionRepository / AgentTraceRepository / AgentTraceStepRepository / PromptTemplateRepository / PromptVersionRepository / TokenUsageRecordRepository）
│       ├── tool/                      # 工具调用抽象（Tool / ToolExecutor / ToolParam / ToolRegistration / ToolRegistry）
│       ├── trace/                     # 轨迹抽象（TraceMeta / TraceRecorder）
│       └── vo/                        # 值对象（AgentApprovalVO / AgentDefinitionVO / AgentTraceStepVO / AgentTraceVO / PromptTemplateVO / PromptVersionVO / TokenUsageRecordVO）
├── ydsz-agent-infra/                  # 基础设施层：LLM Provider + Redis 记忆 + 向量存储 + MCP + Text2SQL + 护栏实现
│   └── src/main/java/com/njydsz/agent/infra/
│       ├── checkpoint/                # 检查点存储（RedisDagCheckpointStore）
│       ├── converter/                 # 对象转换器（AgentConverter — MapStruct）
│       ├── entity/                    # 数据库实体（AgentApprovalDO / AgentDefinitionDO / AgentTraceDO / AgentTraceStepDO / PromptTemplateDO / PromptVersionDO / TokenUsageRecordDO）
│       ├── guardrail/                 # 护栏实现（PiiMaskingGuardrail / PromptInjectionGuardrail）
│       ├── llm/                       # LLM 客户端（CachedLlmClient / LlmClientRouter / OpenAiCompatibleClient / SemanticCacheConfig / SemanticLlmCache）
│       ├── mapper/                    # MyBatis Mapper（7 个 Mapper 接口）
│       ├── memory/                    # 记忆实现（RedisConversationMemory / SummaryConversationMemory）
│       ├── rag/                       # RAG 实现（HybridRetriever / IdentityReranker / InMemoryVectorStore / OpenAiEmbeddingClient / PgVectorStore / SimpleTextChunker）
│       ├── repository/                # 仓储实现（7 个 Repository 实现类）
│       ├── text2sql/                  # Text2SQL 实现（JdbcText2SQLService）
│       ├── tool/                      # 工具实现（DefaultToolRegistry / McpClientProvider / McpToolAdapter / SseMcpClientProvider / Text2SQLTool / ToolAnnotationScanner）
│       └── trace/                     # 轨迹实现（InMemoryTraceRecorder / PgTraceRecorder）
│   └── src/main/resources/db/         # SQL 迁移脚本
│       ├── V1__prompt_template.sql    # Prompt 模板主表 + 版本历史表
│       └── V2__trace_step_cost.sql    # 链路步骤 cost 字段
├── ydsz-agent-server/                 # 应用层：Service + Config + Health + Metrics + Event
│   └── src/main/java/com/njydsz/agent/server/
│       ├── agent/                     # Agent 服务（6 种执行器 + DAG 编排 + 人工审批 + 工厂 + 门面 + DSL 解析器 + 条件评估器）
│       ├── analytics/                 # 成本分析（CostAnalysisService）
│       ├── chat/                      # 对话服务 + 请求防护 + 护栏 + SSE 执行器 + 流式 PII 脱敏 + Token 成本计算
│       ├── config/                    # AgentProperties（prefix: ydsz.agent）
│       ├── debug/                     # 调试器（AgentDebuggerService）
│       ├── event/                     # 应用事件发布（AgentEventPublisher）
│       ├── health/                    # 健康检查（AgentHealthIndicator）
│       ├── listener/                  # 跨模块事件监听（CrossModuleEventListener）
│       ├── metrics/                   # 指标埋点（AgentMetrics + AgentRuntimeMetrics — Prometheus）
│       ├── observability/             # 可观测性面板（ObservabilityDashboardService）
│       ├── prompt/                    # Prompt 管理 + 评估（DatabasePromptTemplateProvider / PromptEvaluationService / PromptManagementService）
│       ├── queue/                     # 队列通道定义（AgentQueueChannels）
│       ├── quota/                     # 租户配额（TenantQuotaService）
│       └── rag/                       # RAG 服务（RagService / DocumentIngestionService / TokenEstimator / AgentDefinitionSearchProvider）
├── ydsz-agent-app/                    # App 端基座：仅在 ydsz.platform.mode=app 时激活
│   └── src/main/java/com/njydsz/agent/app/
│       ├── config/                    # AgentAppAutoConfiguration
│       ├── health/                    # AgentAppHealthIndicator
│       └── openapi/                   # AgentAppOpenApiConfiguration
├── ydsz-agent-web/                    # Web 层：Controller + 启动类 + 自动配置
│   └── src/main/java/com/njydsz/agent/web/
│       ├── AgentApplication.java      # 启动类（@SpringBootApplication / @EnableDiscoveryClient / @EnableYdszAuth / @EnableYdszSafe / @EnableYdszAudit / @EnableYdszFeign / @MapperScan）
│       ├── config/                    # AgentAutoConfiguration（Bean 依赖注入编排）
│       └── controller/                # 9 个 Controller
│   └── src/main/resources/            # 配置文件
│       └── bootstrap.yml              # 端口 9008 / Nacos / Agent 默认配置
```

## 使用方式

> 启动后访问 Swagger UI：`http://localhost:9008/swagger-ui.html`（OpenAPI JSON：`/v3/api-docs`）

### 1. 配置

```yaml
ydsz:
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
      # 多 Provider 配置（key = provider 名称）
      providers:
        openai:
          name: openai
          api-key: ${OPENAI_API_KEY:}
          base-url: https://api.openai.com/v1
          models: [gpt-4o-mini, gpt-4o]
          enabled: true
        deepseek:
          name: deepseek
          api-key: ${DEEPSEEK_API_KEY:}
          base-url: https://api.deepseek.com/v1
          models: [deepseek-chat]
          enabled: true
      # 模型价格配置（key = 模型名前缀，value = 每千 token 价格 USD）
      model-prices:
        gpt-4o-mini: 0.00015
        gpt-4o: 0.005
    memory:
      ttl-hours: 24
      max-messages: 20
      summary-enabled: false                    # 是否启用摘要压缩记忆
      summary-threshold: 20                     # 触发摘要压缩的消息条数阈值
      summary-keep-recent: 10                   # 压缩后保留的最近原始消息条数
      token-budget: 4000                        # 上下文 Token 预算
      token-char-ratio: 2.5                     # Token 估算字符系数
    rag:
      enabled: ${RAG_ENABLED:false}
      vector-store: ${RAG_VECTOR_STORE:memory}  # memory / pgvector
      embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
      embedding-api-key: ${EMBEDDING_API_KEY:}
      embedding-base-url: ${EMBEDDING_BASE_URL:}
      dimension: ${EMBEDDING_DIMENSION:1536}
      chunk-size: 500
      chunk-overlap: 50
      top-k: 5
      min-score: 0.7
      context-token-budget: 3000                # RAG 上下文 Token 预算
      reranker-enabled: false                   # 是否启用 Reranker 精排
      tenant-isolation: true                    # 是否启用多租户隔离
    mcp:
      enabled: false                            # 是否启用 MCP 工具发现
      servers:
        - name: my-mcp-server
          transport: sse                       # sse / stdio
          url: http://localhost:3000/sse
          timeout-seconds: 30
          enabled: true
    text2sql:
      enabled: false                            # 是否启用 Text2SQL
    cache:
      enabled: false                            # 是否启用 LLM 语义缓存
      ttl-minutes: 60                           # 缓存 TTL（分钟）
      max-size: 500                             # 最大缓存条目数
    prompt-template:
      default-system-code: DEFAULT_SYSTEM       # 默认系统 Prompt 模板编码
      react-system-code: REACT_SYSTEM           # ReAct 模式 Prompt 模板编码
      plan-system-code: PLAN_SYSTEM             # Plan-Execute 模式 Prompt 模板编码
      plan-execute-plan-code: PLAN_EXECUTE_PLAN
      plan-execute-plan-system-code: PLAN_EXECUTE_PLAN_SYSTEM
      plan-execute-replan-code: PLAN_EXECUTE_REPLAN
      supervisor-plan-code: SUPERVISOR_PLAN
      supervisor-plan-system-code: SUPERVISOR_PLAN_SYSTEM
    guardrail:
      prompt-injection-enabled: false           # Prompt 注入检测（默认关闭）
      max-requests-per-minute: 10               # 单用户每分钟请求上限
      rejection-message: "抱歉，我无法回答这个问题."
    tool:
      timeout-seconds: 30                       # 工具执行超时（秒）
    quota:
      enabled: true                             # 是否启用配额校验
      daily-token-limit: 1000000                # 每日 Token 限额（0 = 不限制）
      monthly-budget-usd: 1000.0                # 月度预算（USD，0 = 不限制）
      alert-threshold: 0.8                      # 告警阈值（0.0-1.0）
  # P0-1: 统一线程池配置（ydsz-common-thread）
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
  # JDBC 安全加固（ydsz-common-jdbc）
  jdbc:
    sql-firewall:
      enabled: true
      block-drop-table: true
      block-truncate: true
      block-delete-without-where: true
      block-update-without-where: true
      block-multi-statement: true
      block-permission-ops: true
    pagination:
      max-limit: 500
    slow-sql:
      enabled: true
      threshold-millis: 500
      alert-threshold-millis: 2000
    sql-audit:
      enabled: true
      audit-select: false
      audit-insert: true
      audit-update: true
      audit-delete: true
      log-parameters: true
      max-parameter-length: 500
```

> **注意**：以上配置为 `bootstrap.yml` 默认值 + `AgentProperties.java` 完整属性定义。生产环境建议通过 Nacos 配置中心覆盖敏感配置（如 `llm.api-key`）。

### 2. 同步执行 Agent

```bash
curl -X POST http://localhost:9008/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"agentCode": "order-analysis", "userInput": "帮我分析项目进度"}'
```

### 3. 流式执行 Agent（SSE）

```bash
curl -N -X POST http://localhost:9008/api/v1/agent/execute/stream \
  -H "Content-Type: application/json" \
  -d '{"agentCode": "order-analysis", "userInput": "帮我分析项目进度"}'
```

### 4. 同步对话

```bash
curl -X POST http://localhost:9008/api/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下YDSZ系统"}'
```

### 5. 流式对话（SSE）

```bash
curl -N -X POST http://localhost:9008/api/v1/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我分析项目进度"}'
```

### 5.1 多模态对话（Vision 模型）

```bash
# 同步多模态对话
curl -X POST http://localhost:9008/api/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"multimodalContent": [{"type": "text", "text": "这张图片是什么？"}, {"type": "image_url", "imageUrl": "https://example.com/image.jpg"}]}'
```

### 6. 批量对话

```bash
curl -X POST http://localhost:9008/api/v1/agent/chat/batch \
  -H "Content-Type: application/json" \
  -d '{"items": [{"itemId": "1", "message": "问题1"}, {"itemId": "2", "message": "问题2"}]}'
```

### 7. 对话历史

```bash
# 获取历史
curl http://localhost:9008/api/v1/agent/history?conversationId=xxx

# 清除历史
curl -X DELETE http://localhost:9008/api/v1/agent/history?conversationId=xxx
```

### 8. DAG 编排

```bash
# 执行 DAG
curl -X POST http://localhost:9008/api/v1/agent/dag/execute \
  -H "Content-Type: application/json" \
  -d '{"dsl": "name: order-analysis\nnodes:\n  - id: fetch\n    agent: order-fetcher\n  - id: analyze\n    agent: order-analyzer\n    dependsOn: [fetch]", "userInput": "分析订单"}'

# 验证 DSL
curl -X POST http://localhost:9008/api/v1/agent/dag/validate \
  -H "Content-Type: application/json" \
  -d '{"dsl": "name: test\nnodes:\n  - id: step1\n    agent: test-agent"}'
```

### 9. Prompt 评估

```bash
# 评估单个 Prompt
curl -X POST http://localhost:9008/api/v1/agent/prompt/evaluate \
  -H "Content-Type: application/json" \
  -d '{"templateCode": "REACT_SYSTEM", "userMessage": "测试消息", "model": "gpt-4o-mini"}'

# 对比评估两个 Prompt
curl -X POST http://localhost:9008/api/v1/agent/prompt/compare \
  -H "Content-Type: application/json" \
  -d '{"templateCodeA": "REACT_SYSTEM", "templateCodeB": "PLAN_SYSTEM", "userMessage": "测试消息"}'
```

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-agent spring-boot:run
```

> **首次启动前**请确保 PostgreSQL 数据库已创建。脚本 `V1__prompt_template.sql`（Prompt 模板表）及 `V2__*.sql` 等 **需手动执行初始化**——项目规范禁止 Flyway / Liquibase，不存在自动迁移。DDL 表结构（`ydsz_agent_*`）由手动 SQL 脚本（`src/main/resources/db/`）补齐，未启用 MyBatis Plus 自动建表。

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_agent_definition` | Agent 定义主表（类型、配置、工具列表） |
| `ydsz_agent_trace` | Agent 执行链路主表 |
| `ydsz_agent_trace_step` | 链路步骤表（含 cost 字段 — V2 迁移添加） |
| `ydsz_agent_approval` | 人工审批请求表 |
| `ydsz_prompt_template` | Prompt 模板主表（支持 #{var} 变量替换） |
| `ydsz_prompt_version` | Prompt 模板版本历史表 |
| `ydsz_token_usage_record` | Token 用量记录表 |

## 技术选型

| 决策 | 方案 | 理由 |
|---|---|---|
| LLM API | OpenAI 兼容 API | 事实标准，覆盖 90% 国产模型 |
| 流式输出 | SseEmitter + WebClient | 非 WebSocket，轻量级 |
| 多模态输入 | Vision 模型（文本+图片段落） | `MessageContent` / `ContentPart` 封装 |
| 记忆存储 | Redis List | 滑动窗口 + TTL 自动过期 |
| 向量存储 | 内存（默认）/ PostgreSQL pgvector | 复用现有 PG 基础设施 |
| 检索策略 | 混合检索（向量 + 全文 RRF 融合） | `HybridRetriever` 双路召回 + Reranker 精排 |
| 工具调用 | 自研 @Tool 注解 | 轻量级，无额外依赖 |
| MCP 集成 | MCP Java SDK（SseMcpClientProvider） | 标准协议，自动发现外部工具 |
| DAG 编排 | 自研 DagDslParser | DSL 解析 + DAG 执行 + 检查点续跑 |
| Agent 模式 | ReAct / Supervisor / Plan-Execute / RAG / Simple / DAG | 覆盖主流 Agent 范式 |
| LLM 缓存 | 双层缓存（L1 YdszCache + L2 Redis） | 精确哈希匹配，节省成本与延迟 |
| Text2SQL | LLM 生成 + 安全护栏 | 自然语言查询，多重安全防护 |
| API 文档 | Springdoc OpenAPI 3.0 | Swagger UI 自动生成 |
| 对象映射 | MapStruct | 编译期生成类型安全转换器 |
| 服务注册 | Nacos Discovery | 服务发现 + 配置中心 |
| JDBC 安全 | SQL 防火墙 + 慢 SQL + 审计 | 防注入、防全表扫描、操作留痕 |
| 可观测性 | Prometheus + Micrometer | 指标采集 + Grafana 看板 |
| 多租户 | TenantContextHolder | RAG / 记忆按租户隔离 |

## 常见问题

### Q1：LLM 调用超时

1. 检查 `ydsz.agent.llm.timeout-seconds`（默认 60 秒）
2. 检查 LLM API 网络连通性
3. 长文本生成建议使用流式对话（SSE）避免超时

### Q2：RAG 检索结果不准

1. 检查 `ydsz.agent.rag.min-score`（默认 0.7），可适当降低阈值
2. 检查文档分块参数 `chunk-size` / `chunk-overlap`
3. 检查 embedding 模型与维度是否匹配
4. 可启用 Reranker 精排（`ydsz.agent.rag.reranker-enabled=true`）提升 Top-K 精确度

### Q3：Agent DAG 执行卡住

1. 检查 DAG 节点依赖是否循环
2. 检查人工审批节点是否待审批（调用 `/api/v1/agent/approvals/pending` 查询）
3. 检查 Agent 心跳线程池是否正常（`agent-heartbeat` 线程池）
4. 检查检查点存储（Redis）是否正常，异常时降级为无续跑能力

### Q4：Token 用量统计不准

1. 部分 LLM Provider 不返回完整 Token 用量
2. 流式对话需累加每个 chunk 的 Token 数
3. 可通过 `/api/v1/agent/observability/model-usage` 查询模型用量统计

### Q5：MCP 工具未注册

1. 检查 `ydsz.agent.mcp.enabled=true`
2. 检查 MCP Server URL 是否可达
3. 检查 MCP Server 是否暴露了工具（调用 `/api/v1/agent/tools` 查询已注册工具列表）
4. 当前支持 SSE 和 stdio 两种传输方式

### Q6：LLM 语义缓存命中率低

1. 缓存仅对 `temperature=0` 的确定性请求生效
2. 缓存采用精确哈希匹配（非语义相似），相同输入才可命中
3. 可通过 `ydsz.agent.cache.ttl-minutes` 调整缓存有效期

### Q7：多模态（图片）对话不生效

1. 确认请求体中 `multimodalContent` 字段非空（非空时优先于 `message` 字段）
2. 确认 `ContentPartDTO.type` 为 `text` 或 `image_url`
3. 确认使用的 LLM 模型支持 Vision 能力（如 `gpt-4o`、`gpt-4o-mini`）

### Q8：Agent 类型路由规则

| Agent 类型 | 路由目标 |
|---|---|
| `REACT` / `REACT_AGENT` | `ReActAgentExecutor` |
| `RAG` | `ReActAgentExecutor`（注入 ragService） |
| `CHAT` | `SimpleAgentExecutor` |
| `PLAN_EXECUTE` / `WORKFLOW` | `PlanExecuteAgentExecutor` |
| `SUPERVISOR` | `SupervisorAgentExecutor` |
| `DAG` | `DagOrchestrationExecutor` |
| 未知类型 | `ReActAgentExecutor`（兜底） |

### Q9：DAG 执行进度事件推送

流式执行 DAG 类型 Agent 时，`AgentFacade.executeStream` 的 `progressConsumer` 回调会收到节点级进度事件（`eventType` / `nodeId` / `completedCount` / `totalCount`），前端通过 SSE `progress` 事件名接收。

### Q10：Prompt 模板变量替换

Prompt 模板使用 `#{var}` 语法声明变量，运行时由 `PromptTemplate` 进行替换。模板存储在 `ydsz_prompt_template` 表，支持版本管理（`ydsz_prompt_version`）。

---

> 本模块是 YDSZ AI 能力的核心入口，与 literule（规则引擎）、workflow（工作流）、message（消息引擎）三引擎深度融合。
> Agent 触发动作可联动 cronjob（定时任务）/ workflow（审批流）/ message（通知）。
