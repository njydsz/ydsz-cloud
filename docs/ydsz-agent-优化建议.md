# ydsz-agent 模块代码审计与优化建议

> 审计范围：`ydsz-agent`（api / domain / infra / server / web 五层，136 个 Java 文件）
> 对标对象：LangChain / LangGraph、LlamaIndex、Dify、Coze、OpenAI Assistants API、国内大厂（字节扣子、阿里百炼、腾讯智能体平台）的 Agent 研发规范
> 审计方式：逐文件通读核心实现，以最新代码事实为准

---

## 一、总体评估

**成熟度：骨架完整（约 70%），细节有坑。**

已具备的能力链路相当完整：DDD 分层、7 种执行器（ReAct / Simple / RAG / PlanExecute / Router / Supervisor / DAG）、Provider 路由 + 降级、语义缓存、混合检索（向量 + 全文 RRF 融合 + Reranker）、输入/输出护栏（Prompt 注入检测 + PII 脱敏）、全链路 Trace、Micrometer 指标 + 成本核算、HITL 人工审批、MCP / Text2SQL 工具、SSE 流式 + 心跳 + 断连检测。这一骨架已经对齐了主流 Agent 框架的通用能力。

但存在三类问题：

1. **正确性/安全/编译问题（P0）**——必须立即修复，见第二节；
2. **「宣称能力 ≠ 实际实现」的半成品**——Supervisor 计划解析、HITL 闭环、伪流式、语义缓存容量等；
3. **重构不彻底导致的死代码/重复逻辑**——`GuardrailService` 未在 `ChatService` 落地、`AgentExecutorFactory` 接口未使用等。

---

## 二、P0 级问题（正确性 / 安全 / 编译，需立即修复）

### 2.1 语义缓存 key 恒定，导致「串流」返回错误答案 ⚠️ 最高优先

- 位置：`infra/llm/SemanticLlmCache.java` + `CachedLlmClient.java`
- 事实：`extractCacheableContent(List<?> messages)` 用 `instanceof Map` 判断 role/content；但 `ChatRequest.getMessages()` 返回的是 `List<ChatMessage>`（领域对象，非 Map）。因此该方法对任何请求都返回 `Map.entry("", "")`。
- 后果：缓存 key = `SHA256(model + sep + "" + sep + "")`，对**所有** temperature≈0 且无工具的请求**完全相同**。不同用户、不同问题会命中同一条缓存，返回完全错误的答案。
- 修复：直接遍历 `ChatMessage`，取 `role==SYSTEM` 的最后一条 system 内容与最后一条 `role==USER` 的内容构造 key；并补一个「缓存 key 必须含 user 内容」的防御断言。

### 2.2 缓存写入条件括号/逻辑错误

- 位置：`CachedLlmClient.chat()` 第 66–67 行
- 事实：`SemanticLlmCache.isCacheable(request.getTemperature(), hasTools(request) && response.getContent() != null)`——第二个参数本应是 `hasTools(request)`，实际传入了 `hasTools(request) && response.getContent()!=null`。当 content 为 null 时短路失效，仍会以 null 内容写缓存。
- 修复：改为 `isCacheable(temp, hasTools(request)) && response.getContent() != null`。

### 2.3 模块内 API 漂移（存在编译风险）

- 位置：`server/observability/ObservabilityDashboardService.java`
- 事实：该服务引用 `CostAnalysisService.ModelCostStats`（该类不存在）与 `getStatsByModel(LocalDateTime, LocalDateTime)`（无此重载）。实际 `CostAnalysisService` 只提供 `getStatsByModel(LocalDate, LocalDate)` 返回 `ModelUsageStats`。
- 判断：这是一次 `CostAnalysisService` 重构后未同步调用方的漂移，会导致该模块编译失败或运行期 `NoSuchMethodError`。
- 修复：统一成本统计的返回结构（`ModelUsageStats`），或补 `ModelCostStats` 按模型分组的聚合方法并同步所有调用方。

### 2.4 Text2SQL 的并发污染 + 注入面

- 位置：`infra/tool/Text2SqlTool.java`
- 事实：
  1. `jdbcTemplate.setQueryTimeout(10)` 修改的是**共享** `JdbcTemplate` 的超时，会污染其他并发查询的超时设置（线程安全 bug）；
  2. 声称「参数化占位符」，实际把 LLM 生成的 SQL 字符串直接 `queryForList(sql)` 执行，靠关键字黑名单兜底——LLM 可被 Prompt 注入诱导生成危险 SELECT（`SELECT pg_read_file(...)`、`information_schema`、UNION 读敏感表）；
  3. 黑名单 `contains("DELETE")` 会误伤含 `deleted` 字段的合法查询（`DELETED` 前 6 字符即 `DELETE`）。
- 修复：只读 DB 账号 + Row-Level Security + 表/Schema 白名单 + 引入 JSqlParser 做 AST 级校验（仅允许单条 SELECT 且表在白名单内）+ 用完恢复超时或改用一次性 `JdbcTemplate`。

### 2.5 流式场景 PII 脱敏失效（合规风险）

- 位置：`ChatService.stream()` / `SimpleAgentExecutor.executeStream()`
- 事实：流式过程中 token 已逐个推送给客户端，PII 脱敏在 `contentBuilder` 拼接完成后才执行，且只作用于写入 memory 的内容。已发出的 token 中若含手机号/身份证，脱敏形同虚设。
- 修复：流式路径做**增量脱敏**（对每个 delta 或按缓冲窗口做 `SensitiveUtil.scanAndMask`），或在 `OpenAiCompatibleClient.stream` 回调层统一加输出护栏，保证「先脱敏、后推送」。

---

## 三、架构优化建议

| 编号 | 现状问题 | 改进方案 | 优先级 |
|---|---|---|---|
| A1 | 流式架构不统一：`ReAct/PlanExecute/Supervisor` 是「伪流式」（先 `chat()` 拿完整响应再切 chunk 推），只有 `Simple/ChatService` 走真 `llmClient.stream()`；工具调用链路完全无真流式 | 统一走 `LlmClient.stream`；流式 `tool_calls` 增量拼接（`OpenAiCompatibleClient.parseDeltaToolCalls` 已预留 args 拼接注释，但 ReAct 未用上），工具结果流式回填 | P0 |
| A2 | `GuardrailService` 重构不彻底：`ChatService` 仍保留私有 `applyInputGuardrails/applyOutputGuardrails`，与 `GuardrailService` 逻辑重复 | `ChatService` 注入 `GuardrailService`，删除私有重复方法；护栏只保留一个驱动入口 | P1 |
| A3 | `SupervisorAgentExecutor.parsePlanResponse` 是「假解析」：调用 `YdszJson.parseMap` 后丢弃结果，仅 `json.contains(type.name())` 判类型，`description` 固定为「执行XXX任务」，`depends_on` 依赖完全忽略（顺序执行） | 真 JSON 反序列化为 `SubTask` 列表 + 按 `depends_on` 拓扑调度（可复用 DAG 拓扑排序） | P1 |
| A4 | `HumanApprovalService` 未接入执行闭环：`pendingApprovals` 是纯内存 `ConcurrentHashMap`，无持久化、多实例不共享、无「暂停-恢复」机制——没有任何执行器真正调用 `requestApproval` 来暂停执行 | 接入执行器（工具调用前检查审批点）、持久化到 DB、审批后通过事件/回调恢复执行，超时自动失效并通知 | P1 |
| A5 | 多租户隔离不完整：RAG 的 `PgVectorStore`/`HybridRetriever` 走 `JdbcTemplate` 原生 SQL，**绕过 MyBatis tenant 拦截器**，`ydsz_agent_document_chunk` 无 tenant 维度；对话记忆 Redis key 只有 conversationId；工具注册表全局 | RAG 表加 `tenant_id` 并在检索 SQL 显式过滤；记忆 key 加租户前缀；工具按租户注册/授权 | P0 |
| A6 | 各执行器 `execute()` 流程高度重复（护栏→构建消息→调用→埋点→护栏→保存），`ReAct/Simple/Rag` 三份几乎相同 | 抽 `BaseAgentExecutor` 模板方法（`doExecute` 钩子），收敛护栏/埋点/记忆保存为统一骨架 | P1 |

---

## 四、功能增强建议

| 编号 | 建议 | 对标 | 优先级 |
|---|---|---|---|
| F1 | 真流式 Function Calling：流式 `tool_calls` 增量拼接、流式工具结果反馈（当前流式下 ReAct 实际退化为非流式 `chat`） | OpenAI Streaming Function Calling | P0 |
| F2 | RAG 中文分词：当前全文检索 `to_tsvector('simple', content)` + `ILIKE %query%` 对中文无效，召回差 | pg_jieba / zhparser / ngram | P1 |
| F3 | RAG 评测闭环：补 RAGAS 评测集（召回率/忠实度/相关性），否则 RRF/阈值无从调优 | LlamaIndex / RAGAS | P2 |
| F4 | 结构化输出：`PlanExecute` 用正则解析编号列表、`Supervisor` 用字符串 contains，脆弱；改用 JSON Schema / function-calling 结构化输出 | OpenAI Structured Outputs | P1 |
| F5 | 模型智能路由：按成本/延迟/成功率路由，多 Provider 熔断状态可观测（当前 fallback 是一次性 try，无熔断记忆） | 扣子 / 阿里百炼 | P2 |
| F6 | MCP 补齐：stdio 传输、工具 schema 校验、动态热加载（当前仅 SSE 且启动期一次性发现） | MCP 规范 | P2 |
| F7 | 记忆摘要默认装配：`SummaryConversationMemory` 已实现但**未在 `AgentAutoConfiguration` 中装配**（`conversationMemory` 直接返回 `RedisConversationMemory`），长对话无压缩 | LangChain SummaryBufferMemory | P1 |

---

## 五、性能提升建议

| 编号 | 现状问题 | 改进方案 | 优先级 |
|---|---|---|---|
| P1 | 语义缓存 `maxSize` 字段声明但未使用，Redis 无容量/LRU 淘汰 | 修复 2.1 后补 LRU（Redis 内存策略或 ZSET 访问时间戳） | P1 |
| P2 | `PgTraceRecorder.recordStep` 每次执行 `SELECT MAX(stepIndex)`，N 步 N 次额外查询，并发下 stepIndex 可能冲突 | 内存 `AtomicInteger` 计数 + 异步批量写入（注释自己也提示了 AsyncWriter） | P1 |
| P3 | `endTrace` 用 `sum(各步 durationMs)` 当总耗时，DAG 并行节点会虚高 | 记录墙钟时间（startTrace 时间戳 → endTrace） | P1 |
| P4 | `PgVectorStore.storeBatch` 逐条 INSERT，无 JDBC batch | 改 batchUpdate（`DocumentIngestionService` 已 embedBatch，但 store 未批） | P1 |
| P5 | `InMemoryVectorStore.search` 全量线性扫描 O(N·D) | 生产建议换 pgvector ivfflat（已支持）/ Milvus / Qdrant；内存版至少按 docId 分桶 | P2 |
| P6 | `ReAct` 每轮 `toolDefinitions.stream().map(td->td).collect(...)` 重复收集，`.map(td->td)` 无意义 | 执行器构造时缓存 `List<ToolDefinition>` | P2 |
| P7 | `AgentMetrics` 每次 `Timer.builder(...).register()` | 实例缓存 Timer（Micrometer 会按 tag 复用，但避免每次构建） | P2 |
| P8 | `HybridRetriever` 构造期探测全文表是否可用并缓存，启动后补表不恢复 | 改为运行期惰性探测或定时刷新 | P2 |

---

## 六、体验改善建议

| 编号 | 现状问题 | 改进方案 | 优先级 |
|---|---|---|---|
| E1 | 伪流式导致首字延迟（TTFT）差；TTFT 已埋点但仅 `ChatService` 路径生效 | 随 A1/F1 真流式改造后自然改善，并给 ReAct/PlanExecute 路径补 TTFT 埋点 | P0 |
| E2 | 断连取消未必真正中断：`OpenAiCompatibleClient.stream` 用 `blockLast()` 阻塞，`virtualThread.interrupt()` 不保证中断 Reactor 请求 | 用 `Disposable`/可取消订阅，或在 chunk 回调检查中断标志后主动 `dispose()` | P1 |
| E3 | 错误码未统一映射：`LlmException.ErrorType` 直接 `throw e`，前端拿到的是原始异常 | 统一异常处理器，映射友好错误码 + 可重试提示 | P1 |
| E4 | 缺 Token 用量/成本实时展示与限额提示 | SSE 附加 usage 事件 + 成本预估 | P2 |
| E5 | 流式缺增量 PII 脱敏（同 2.5） | 增量脱敏 | P0 |

---

## 七、过度设计（建议裁剪/收敛）

| 编号 | 位置 | 说明 |
|---|---|---|
| O1 | `AgentFactory.AgentExecutorFactory` | `@FunctionalInterface` 定义后**从未使用**，`createExecutor` 实际用 switch；注释宣称「消除 if-else 路由链」与实现不符。删除或落地。 |
| O2 | `DagOrchestrationExecutor.allDepsCompleted` | 定义了但从未调用（`executeNodeLogic` 只检查 `failed`）。删除。 |
| O3 | `DagOrchestrationExecutor.shutdown()` | `@PreDestroy` 方法体刻意留空，只为注释声明「不负责关闭线程池」。可删，用注释/字段说明替代。 |
| O4 | 各执行器 `inputGuardrails/outputGuardrails` 字段 | `ReAct/Simple/Rag/PlanExecute/Router/Supervisor` 构造器均接收并赋值这两个 List，但实际全部走 `guardrailService`，字段是死字段。移除构造参数，统一只注入 `GuardrailService`。 |
| O5 | `LlmClientRouter` 运行时 `register/unregister` | 当前配置驱动下基本用不到运行时热注册，属预留能力。保留可，但建议明确其生命周期约定（当前 `defaultClient` 非 volatile，仅启动期安全）。 |
| O6 | `MessageContent` 多模态 | 建模完整（`userWithContent`）但无执行器消费，聊天入口未接 Vision 调用。要么补齐 Vision 路径，要么暂标记为 TODO 避免误导。 |

---

## 八、落地顺序建议（按 P0 → P1 → P2）

1. **P0（本迭代必须）**：修复语义缓存串流（2.1/2.2）→ 修复 ObservabilityDashboard API 漂移（2.3）→ Text2SQL 并发污染与注入面（2.4）→ 流式增量 PII 脱敏（2.5）→ 真流式统一（A1/F1）→ RAG 租户隔离（A5）。
2. **P1（下一迭代）**：护栏单一入口（A2）、Supervisor 真解析（A3）、HITL 闭环（A4）、执行器模板方法（A6）、Trace 异步批量 + stepIndex 原子化（P2/P3）、VectorStore 批量写（P4）、记忆摘要装配（F7）、结构化输出（F4）、中文分词（F2）。
3. **P2（持续优化）**：RAG 评测（F3）、智能路由 + 熔断可观测（F5）、MCP 补齐（F6）、内存向量索引（P5）、指标缓存（P7）、体验项（E2/E3/E4）、过度设计清理（O1–O6）。

---

## 九、值得保留的亮点（不折腾）

- `LlmClientRouter` 的 Fallback 错误分类（只对可恢复错误降级，AUTH/MODEL_NOT_FOUND/INVALID_RESPONSE 不降级）思路正确；
- `HybridRetriever` 的 RRF 融合 + 构造期降级 + 运行期异常兜底，设计干净；
- `ChatMessage` 不可变值对象 + 防御拷贝，线程安全语义清晰；
- SSE 心跳 + 断连检测 + 虚拟线程承载，工程质量在线；
- `AgentProperties` 的多 Provider 灰度开关、Embedding 凭据回落到 LLM 配置，运维友好。
