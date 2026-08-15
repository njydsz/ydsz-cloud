# ydsz-agent 深度审查报告（2026 Q3）

> 分析基准：最新代码（2026-08-15）
> 模块形态：部署单元（端口 9008，构建序 9/10），DDD 五层（api / domain / infra / server / web）
> 覆盖范围：LLM 对话、5 类 Agent 执行器、Tool Calling、RAG、对话记忆、DAG 编排、人工审批、调试器、护栏、成本分析、可观测性
> 对标对象：LangChain / LlamaIndex、Dify、Coze、AutoGen、OpenAI Assistants、Spring AI，以及阿里/腾讯/美团 LLM 网关研发规范（`docs/云顶编码规范.md`）

---

## 0. 总评

模块的**骨架与抽象设计达到合格基线**：DDD 分层清晰、依赖方向正确（domain 定义 `LlmClient`/`VectorStore`/`ToolRegistry` 等 Gateway，infra 提供实现）、`LlmClientRouter` 具备重试/降级/并发限流、可观测性齐全（Micrometer 指标 + 链路追踪 + 成本核算 + 健康检查 + 幂等限流）。

但与 `ydsz-userinfo` 同源，短板不在"缺功能"，而在**三类收口**：

1. **声明与实现错位**：README 宣称的"断点/单步调试器""人工审批暂停恢复""多模型按模型路由"，代码层面或未实现、或未接线。
2. **重构半途而废**：`GuardrailService` 已创建以消除执行器重复代码，但零调用方；`SummaryConversationMemory` 已实现但从未注册——大量"写了但没用"的死代码。
3. **测试覆盖为零**：全模块无 `src/test`，安全护栏、DAG 编排、流式解析等关键链路完全依赖人工验证。

---

## 一、架构优化

### 1.1 🔴 执行器逻辑大量重复，护栏/埋点/记忆保存六处复制

`ReActAgentExecutor`、`SimpleAgentExecutor`、`RagAgentExecutor`、`PlanExecuteAgentExecutor` 各自复制了 `applyInputGuardrails` / `applyOutputGuardrails` / `memory.save` / `agentMetrics.recordLlmCall` / `costAnalysisService.recordUsage` / `traceRecorder.recordStep` 等逻辑；`ChatService` 又重复了一份（§1.3）。

而 `server/chat/GuardrailService.java` 的 Javadoc 明确写着"**消除 ChatService、ReActAgentExecutor、SimpleAgentExecutor、RagAgentExecutor 中重复的 applyInputGuardrails/applyOutputGuardrails 逻辑**"，却在全模块**零调用方**（仅 `AgentAutoConfiguration` 注册了该 Bean）——这是一次未完成的重构。

**建议**：抽取 `AbstractAgentExecutor`（模板方法：护栏 → 构建消息 → LLM 调用 → 埋点 → 记忆保存 → 响应），或统一 `ExecutionContext` 管道；`GuardrailService` 作为唯一护栏入口被所有执行器引用，删除各执行器内的私有护栏方法。

### 1.2 🔴 领域对象契约失效：AgentDefinition/AgentExecutionRequest 的关键字段被忽略

- `AgentDefinition` 声明了 `toolNames` / `modelId` / `temperature` / `maxTokens`，但 `AgentFactory.getExecutor()` 只读取 `definition.getType()`，执行器内部一律使用 `properties.getLlm().getDefaultModel()/getTemperature()/getMaxTokens()`，**每个 Agent 绑定的模型、工具、采样参数全部不生效**。
- `AgentExecutionRequest.enabledTools`（工具白名单）只在 `AgentController` 里被 `set` 进请求，`ReActAgentExecutor` 构建请求时 `toolRegistry.getToolDefinitions().stream().map(td -> td)...` 无条件返回全部工具，**白名单字段是死字段**。

**建议**：执行器按 `AgentDefinition` 解析模型/工具/温度/maxTokens，`AgentFactory` 缓存粒度从"按 type"细化为"按 type+modelId+toolNames"；`enabledTools` 在构建 tool 列表时做过滤。这是"前后端能力不匹配"在 Agent 侧的根源之一。

### 1.3 🟡 两条并行对话路径，边界模糊

`ChatService.chat()/stream()` 与 `AgentFactory` 的 `SimpleAgentExecutor` 功能几乎等价，各自维护一套"护栏 + 埋点 + 记忆"逻辑（§1.1）。`/agent/chat`（ChatService）与 `/agent/execute`（AgentFactory）是两套独立实现，任何护栏/埋点修复都要改两处。

**建议**：ChatService 收敛为"对话编排层"，统一委托给执行器（或反之），避免双实现漂移。

### 1.4 🟡 SummaryConversationMemory 死代码（未注册 Bean）

`infra/memory/SummaryConversationMemory.java` 实现了完整的"摘要压缩记忆"（对标 LangChain ConversationSummaryBufferMemory），但 `AgentAutoConfiguration` **从未装配该 Bean**，生产实际使用裸 `RedisConversationMemory`。能力存在但未启用。

**建议**：要么在配置中按 `memory.summary-enabled` 开关装配（并解决 §3.6 的同步阻塞与摘要跨重启丢失），要么删除并下沉到 P1 规划。

### 1.5 🟡 @ConditionalOnMissingBean 类型级误用，默认安全能力会被整体替换

`AgentAutoConfiguration` 中 `promptInjectionGuardrail()` 用 `@ConditionalOnMissingBean(InputGuardrail.class)`、`piiMaskingGuardrail()` 用 `@ConditionalOnMissingBean(OutputGuardrail.class)`。注释已自认：**业务侧只要自定义任意一个输入护栏，默认的 Prompt 注入防护就不再注册**；同理自定义任意输出护栏后，PII 脱敏整体失效。

**建议**：改为按具体实现类或命名 Bean 做条件装配，或让默认护栏无条件注册、业务护栏以 `List` 追加（Spring 已支持按类型注入全量实现，参见 `guardrailService` 的装配方式）。

### 1.6 🟡 DAG 执行器死依赖与死方法

`DagOrchestrationExecutor` 注入了 `AgentFactory agentFactory` 字段但**从未使用**（节点直接调 `llmClient.chat`，不走工厂）；`allDepsCompleted()` 私有方法零调用。属于重构残留。

---

## 二、功能增强

### 2.1 🔴 流式 + Tool Calling 断裂（parseChunk 不解析 delta.tool_calls）

`OpenAiCompatibleClient.parseChunk()` 只解析 `delta.content` 与 `finish_reason`，**未解析 `delta.tool_calls`**。因此 `ReActAgentExecutor.executeStream()` 在流式模式下永远拿不到工具调用，`hasToolCalls()` 恒为 false，流式 ReAct 实际退化为单轮对话。

**建议**：补齐 SSE 增量 tool_calls 的解析（含 index 合并、`arguments` 分片拼接），或在流式 ReAct 场景明确降级为"非流式内部执行 + 结果分块推送"。

### 2.2 🔴 流式输出护栏失效（PII 脱敏形同虚设）

`SimpleAgentExecutor` / `RagAgentExecutor` / `ChatService` 的流式路径中，LLM 回调内先 `chunkConsumer.accept(chunk)` 把**原始未脱敏内容**推给客户端，流结束后才对 `contentBuilder.toString()` 调 `applyOutputGuardrails`——**用户已经看到手机号/身份证等未掩码内容**。

**建议**：流式场景对输出护栏做"逐 chunk 增量脱敏"（对可掩码模式做流式匹配），或至少对含 PII 的流做缓冲后统一脱敏再下发；否则 PII 合规在流式接口是裸奔。

### 2.3 🔴 人工审批未接入执行流（HITL 是孤立服务）

`HumanApprovalService` 提供 `requestApproval/approve/reject`，并有 `HumanApprovalController`，但**全模块无任何执行器或 DAG 节点调用 `requestApproval`**。README 宣称"Agent 执行中暂停等待人工审批"，实际只是"一套审批记录的 CRUD"，无法暂停/恢复 Agent 执行。且审批态存于内存 `ConcurrentHashMap`（§3.1）。

**建议**：在 `ReActAgentExecutor` / DAG 中引入 `HumanInTheLoop` 节点（工具或节点类型），审批通过后回调恢复执行；审批态落 Redis/DB 以支持多实例与重启恢复。

### 2.4 🔴 多 Provider"按模型路由"失效，跨 Provider Fallback 会撞模型名

- `OpenAiCompatibleClient.supports(modelId)` 实现为 `modelId != null && !modelId.isBlank()`——**恒 true**。`LlmClientRouter.resolveClient()` 因此永远返回第一个注册的客户端（默认 Provider），"按模型路由"名存实亡。
- `AgentProperties.ProviderConfig.models` 列表全模块零消费（只有 getter）。
- `findFallback()` 切到备用 Provider 时仍传**原模型名**（如 `gpt-4o-mini`），DeepSeek/Qwen 会返回 `MODEL_NOT_FOUND`，降级失效。

**建议**：`supports()` 改为基于 `ProviderConfig.models` 前缀/精确匹配；路由到 Provider 时映射为 Provider 侧模型名（或配置 `model-mapping`）；Fallback 仅切换到支持该模型的 Provider。

### 2.5 🟡 调试器非"真调试器"（声明 vs 实现错位）

`AgentDebuggerService` 仅提供 `getTrace / replay / listTraces`，即**链路查询 + 重放**；README 宣称的"断点 / 单步 / 快照 / 恢复"完全不存在。这与其兄弟项目 literule 已识别的"断点非真断点"短板同源。

**建议**：要么实现真正的执行暂停/单步（需 Agent 执行循环支持断点标记与恢复上下文），要么诚实化 README 为"链路追踪 + 重放调试"，避免过度承诺。

### 2.6 🟡 文档摄入 Pipeline 是 TODO

`RagService.ingestByFileId()` 仅打日志，注释自认"TODO: 通过 Feign 调用 nextwiki 获取文件内容 → 文档解析 → 向量化"。跨模块的"上传即入库"链路实际未闭环。

### 2.7 🟡 DAG 条件/循环节点半成品

- `evaluateCondition()` 用 `indexOf/substring` 手撕 `.contains("...")/.equals("...")/.startsWith("...")` 字符串表达式，脆弱且不可扩展；
- `executeConditionNode()` 写入 `results.put("__BRANCH__"+nodeId, branchNodeId)`，但**该分支结果从未被消费**，未选中的分支节点仍会被主循环执行——"条件分支"语义未闭环；
- `executeLoopNode()` 直接调 `executeNodeLogic` 执行循环体节点，而这些节点同时也在拓扑排序的主循环中，**存在重复执行**。

**建议**：条件/循环求值复用项目已有的 `ydsz-literule` 规则引擎（表达式能力正是其强项），并实现真正的分支剪枝；循环体节点应从主图执行中排除。

### 2.8 🟡 记忆/摘要未支持多租户与持久化

`RedisConversationMemory` 的 key 为 `ydsz:agent:memory:{conversationId}`，无租户维度；`SummaryConversationMemory` 的摘要存本地 `ConcurrentHashMap`，重启即丢。多租户物理隔离（用户长期关注项）在 Agent 侧尚未体现。

---

## 三、性能提升

### 3.1 🔴 成本/链路/审批/摘要全部内存态，重启丢失、多实例不互通

- `CostAnalysisService` 用量存内存 `ConcurrentHashMap`（上限 1 万条，滚动淘汰）——成本数据生产不可持久化；
- `InMemoryTraceRecorder` 链路内存（注释自认"重启即丢失、多实例数据不互通"）；
- `HumanApprovalService` 审批态内存。

**建议**：Token 用量/成本落 PostgreSQL（或至少 Redis 持久化），链路追踪接入 APM/日志平台，审批态落 Redis。这是"成本分析"能否真正用于预算管控的前提。

### 3.2 🟡 PgVectorStore.storeBatch 逐条 insert（N+1）

`storeBatch()` 循环调用 `store()`，每次一条 `INSERT ... ON CONFLICT`。大文档分块灌库会产生 N 次网络往返。

**建议**：改用 `JdbcTemplate.batchUpdate` 或单条多 VALUES 的 `UNNEST` 批量 upsert。

### 3.3 🟡 HybridRetriever 全文检索无索引、每次全表计算 tsvector

`fullTextSearch()` 的 SQL 每次 `ORDER BY ts_rank(to_tsvector('simple', content), ...)`——`to_tsvector` 在查询时对每行重算，无 GIN 索引，大语料下全表扫描。注释称"BM25"实为 `ILIKE + ts_rank`。

**建议**：建 `GENERATED ... AS (to_tsvector('simple', content)) STORED` 列 + GIN 索引；或引入真正的 BM25（如 PG 全文 + `ts_rank_cd` / pg_bm25）。

### 3.4 🟡 InMemoryVectorStore 全量线性扫描

`searchByVector()` 遍历全部 chunk 算余弦相似度后排序，无 ANN 索引；`storeBatch` 逐块同步调 `embeddingClient.embed`。

**建议**：内存实现仅作降级/测试用即可，但需文档标注规模上限；生产必须走 pgvector（`ivfflat`/`hnsw`）。

### 3.5 🟡 ChatController 自建虚拟线程与心跳线程池，未复用统一线程池

`ChatController` 直接 `new ScheduledThreadPoolExecutor(2, Thread.ofVirtual()...)` 且 `Thread.startVirtualThread(...)`，绕过了项目统一 `common-thread` 线程池管理（README 宣称 DAG 已接入 `agentDagExecutor` 统一池，但对话流没有）。线程资源无法统一监控与优雅停机。

### 3.6 🟡 SummaryConversationMemory 同步 LLM 摘要阻塞请求

`tryCompress()` 在 `save()` 内同步调用 LLM 生成摘要，压缩发生在对话主线程上，长对话触发摘要时会显著增加响应延迟。

**建议**：摘要改为异步（`common-thread` 线程池 + 事件驱动），或改在读取时惰性压缩。

---

## 四、体验改善

### 4.1 🔴 @Idempotent 的 key 为常量，可能全局串行化所有对话请求

`ChatController` 的 `chat` / `chatStream` 分别标注 `@Idempotent(key = "ydsz:agent:ChatController:chat:lock", ttlSeconds = 5)`——key 是**静态字符串**，未绑定 `requestId` 或 `conversationId`。若 common-lock 的 key 为字面量（非 SpEL 绑定），则**同一 5 秒窗口内全局只允许一次对话请求**，其余全被幂等拦截。

**建议**：核对 `common-lock` 的 `@Idempotent` key 语义；key 必须动态绑定 `#requestId`（或 `#request.conversationId`）。此问题与 §4.2 的重复幂等叠加后风险更大。

### 4.2 🟡 限流与幂等三重冗余，且阈值互相矛盾

`ChatController` 同时叠加：`@RateLimit(threshold=50)`（common，50 QPS）+ `AgentRequestGuard.checkRateLimit`（Redis 固定窗口 **10 次/分钟**）+ `@Idempotent` + `AgentRequestGuard.checkIdempotent`。三套限流/幂等职责重叠，且 `AgentRequestGuard` 的 10 QPM 会先于 50 QPS 触发，用户实际感知的限流远严于注解声明。

### 4.3 🟡 限流 userId 恒为 anonymous

`requestGuard.check(request.getRequestId(), null)` 传入 `userId=null`，`AgentRequestGuard` 退化为 `"anonymous"`，**所有用户共享同一个限流桶**，无法按用户粒度限流。

### 4.4 🟡 流式 double-finish 与 finish 语义混乱

`OpenAiCompatibleClient.stream()` 在 `blockLast()` 后又主动 `chunkConsumer.accept(ChatChunk.finish("", ...))`，而 `parseChunk` 在遇到 `finish_reason` 时也已返回 `ChatChunk.finish(...)`——存在**重复 finish 事件**；且兜底 finish 的 `usage=null`，覆盖了真实的 usage。执行器侧的 `chunk.isFinished() && chunk.getUsage()!=null` 判断依赖该顺序，脆弱。

### 4.5 🟡 流式错误被吞（doOnError 只 log 不重抛）

`OpenAiCompatibleClient.stream()` 中 `.doOnError(e -> log.error(...))` 未重抛，异常依赖 `blockLast` 抛出，`LlmException` 类型在 Reactor 包装下丢失（执行器 catch 到的是原始 reactor 异常，错误类型映射失效）。同步 `chat()` 的重试使用 `Thread.sleep` 线性退避（非指数），且限流重试会阻塞请求线程。

---

## 五、过度设计 / 冗余

### 5.1 🟡 大量"写了但没用"的死代码

| 死代码 | 位置 | 说明 |
|---|---|---|
| `GuardrailService` | `server/chat/GuardrailService.java` | 已注册 Bean 但零调用（§1.1） |
| `SummaryConversationMemory` | `infra/memory/` | 从未注册 Bean（§1.4） |
| `AgentRuntimeMetrics` 多个 `recordXxx` | `server/metrics/AgentRuntimeMetrics.java` | `recordDagNode`/`recordApprovalWaiting`/`recordRagRetrieval`/`recordToolCall`/`reconcileActiveConversations`/`markConversationInactive` 全模块零调用 |
| `DagOrchestrationExecutor.agentFactory` | `server/agent/DagOrchestrationExecutor.java` | 注入未使用 |
| `DagOrchestrationExecutor.allDepsCompleted` | 同上 | 零调用 |
| `ChatMessage.appendContent` | `domain/model/ChatMessage.java` | 流式路径改用 StringBuilder，此方法未使用 |
| `AgentProperties.ProviderConfig.models` | `server/config/AgentProperties.java` | 只有 getter，零消费（§2.4） |
| `AgentExecutionRequest.variables` | `domain/agent/AgentExecutionRequest.java` | 声明"Prompt 模板渲染"，执行器零消费 |

### 5.2 🟡 CostAnalysisService 全内存 + 淘汰 = 半成品

投入了完整的价格表、日期范围统计、兜底单价等，但底层存储是上限 1 万条的内存 Map，数据滚动丢失。作为"成本分析/预算管控"不可用，作为"调试观测"又过重——定位尴尬。

### 5.3 🟡 DAG 条件/循环节点"过度设计但未闭环"

实现了 CONDITION/LOOP 节点 + 字符串表达式求值 + 拓扑排序 + 并行执行，但分支剪枝语义缺失、循环体重复执行（§2.7），复杂度投入与可运行能力不匹配。

### 5.4 🟢 AgentExecutionRequest 的 Builder + 全 final + Map.copyOf

领域对象防御做得扎实（符合规范），但 `variables` 字段无消费方，属"为未使用能力预留的过度封装"，建议随 §5.1 一并盘点。

---

## 六、落地路线图

### P0（本迭代，正确性/安全/契约收口）

| # | 事项 | 证据位置 | 验证方式 |
|---|---|---|---|
| 1 | 流式输出护栏改逐 chunk 脱敏（或缓冲后统一下发） | `SimpleAgentExecutor/RagAgentExecutor/ChatService#executeStream` | 流式返回手机号/身份证已掩码 |
| 2 | parseChunk 补齐 delta.tool_calls 解析 | `OpenAiCompatibleClient#parseChunk` | 流式 ReAct 能触发工具调用 |
| 3 | `@Idempotent` key 改为动态绑定 requestId | `ChatController#chat/#chatStream` | 并发 2 请求不同 requestId 不被误拦 |
| 4 | 执行器收敛护栏逻辑，接入 GuardrailService | 各 `*AgentExecutor` | 删除执行器内私有护栏方法 |
| 5 | `supports()` 改为按 ProviderConfig.models 匹配 + Fallback 模型映射 | `OpenAiCompatibleClient#supports`、`LlmClientRouter#findFallback` | 多 Provider 按模型正确路由与降级 |
| 6 | AgentDefinition/AgentExecutionRequest 的 toolNames/modelId/enabledTools 生效 | `AgentFactory`、`ReActAgentExecutor` | 指定白名单后仅暴露白名单工具 |

### P1（下个迭代，能力补齐与质量）

| # | 事项 |
|---|---|
| 1 | 人工审批接入执行流（ReAct 工具 / DAG 节点），审批态落 Redis |
| 2 | 修复 DAG 条件分支剪枝 + 循环体去重执行，条件求值复用 literule |
| 3 | 文档摄入 Pipeline 闭环（Feign 拉取 nextwiki → 解析 → 分块 → 向量化） |
| 4 | 调试器能力诚实化或实现真断点/单步 |
| 5 | ChatService 与执行器双路径收敛为单一入口 |
| 6 | 核心链路单测：护栏、parseChunk、parsePlan、DAG 拓扑/条件、Router（当前 0 覆盖） |
| 7 | 成本/链路/审批态持久化（Redis/DB），多实例互通 |
| 8 | 流式 double-finish 收敛，doOnError 正确重抛 LlmException |

### P2（性能/体验/长期治理）

| # | 事项 |
|---|---|
| 1 | PgVectorStore 批量 upsert；HybridRetriever 建 GIN 索引 |
| 2 | ChatController 流式/心跳线程接入统一 common-thread 池 |
| 3 | SummaryConversationMemory 异步摘要 + 摘要持久化（或按开关启用） |
| 4 | 限流/幂等去冗余（收敛为单一入口 + 动态 key + 按 userId 限流） |
| 5 | 多租户维度（记忆/RAG/审批 key 增加 tenant 维度，对标物理隔离） |
| 6 | 死代码清理（§5.1 清单）与成本分析定位澄清（落库或降级为观测） |

---

## 七、关键证据位置

| 发现 | 文件位置 |
|---|---|
| 流式 Tool Calling 断裂 | `infra/llm/OpenAiCompatibleClient.java#parseChunk` |
| 流式输出护栏失效 | `server/agent/SimpleAgentExecutor.java#executeStream`、`RagAgentExecutor.java#executeStream`、`server/chat/ChatService.java#stream` |
| 护栏逻辑六处重复 + GuardrailService 死代码 | 各 `*AgentExecutor#applyInputGuardrails/#applyOutputGuardrails`、`server/chat/GuardrailService.java` |
| 领域契约失效 | `server/agent/AgentFactory.java#getExecutor`、`ReActAgentExecutor#execute`（toolRegistry 全量） |
| 多 Provider 路由失效 | `infra/llm/OpenAiCompatibleClient.java#supports`、`infra/llm/LlmClientRouter.java#resolveClient/#findFallback` |
| 人工审批孤立 | `server/agent/HumanApprovalService.java#requestApproval`（零调用） |
| 调试器声明不符 | `server/debug/AgentDebuggerService.java`、`README.md`（断点/单步） |
| DAG 条件/循环半成品 | `server/agent/DagOrchestrationExecutor.java#evaluateCondition/#executeConditionNode/#executeLoopNode` |
| 成本/链路/审批内存态 | `server/analytics/CostAnalysisService.java`、`infra/trace/InMemoryTraceRecorder.java`、`HumanApprovalService.java` |
| 幂等 key 常量 | `web/controller/ChatController.java#chat/#chatStream` |
| 死代码清单 | `SummaryConversationMemory`、`AgentRuntimeMetrics#recordDagNode 等`、`AgentProperties.ProviderConfig#getModels`、`ChatMessage#appendContent` |
| 测试 0 覆盖 | 全模块无 `src/test` |

---

## 八、总结

`ydsz-agent` 在**抽象设计、可观测性、安全基线**上具备与主流 Agent 框架对齐的骨架（Gateway 抽象 + 路由器降级 + 护栏 + 指标 + 成本 + 追踪），优于多数同阶段自研项目。

当前应优先解决的不是"缺功能"，而是**三类收口**：

1. **安全与正确性硬伤**：流式输出护栏裸奔（PII 合规风险）、流式 Tool Calling 断裂、幂等 key 常量可能全局串行化——这三项直接造成生产事故或合规风险，应进 P0。
2. **声明与实现对齐**：人工审批、真调试器、多 Provider 路由、文档摄入、DAG 条件分支——与用户长期关注的"前后端能力不匹配"同源，须先"能力诚实化"再谈增强。
3. **死代码与半成品清理**：GuardrailService、SummaryConversationMemory、AgentRuntimeMetrics 大量方法、CostAnalysisService 内存态等，要么接线、要么删除，避免误导后续维护。

按 P0 → P1 → P2 推进后，`ydsz-agent` 可对齐 Dify/Coze 的 Agent 编排能力分层，并与 `ydsz-literule`（条件/循环求值、风控）、`ydsz-workflow`（审批流）、`ydsz-message`（通知）真正联动，形成"规则 + 编排 + 审批 + 通知"的闭环，向多租户物理隔离延伸。
