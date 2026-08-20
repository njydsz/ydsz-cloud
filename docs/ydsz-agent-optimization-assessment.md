# ydsz-agent 全面分析与优化完善建议

> **评估基线**：主分支最新代码（源码时间戳 2026-08-19 17:40+，target/classes 停留在 2026-08-19 11:23，即源码晚于最后一次成功构建）
> **规范依据**：《云顶编码规范》v2.23（2026-08-19 生效）
> **对标对象**：Spring AI 1.x、LangChain4j、LangGraph、Dify、Coze（扣子）及大厂 Agent 平台实践
> **代码规模**：196 个 Java 源文件 / 约 1.78 万行，5 个子模块（api / domain / infra / server / web）
> **评估方法**：全部基于源码逐文件核验，非文档口径

---

## 一、总体评价

ydsz-agent 是一个**能力清单相当完整、注释质量极高**的自研 Agent 框架：6 种执行器范式、Tool Calling、MCP、RAG（混合检索 + RRF + Rerank）、双层 LLM 缓存、多级记忆、Token 成本核算、链路调试器、租户配额、安全护栏（含流式 PII 脱敏）一应俱全，DDD 五层装配约定（server 不依赖 infra、web 层负责注入编排）执行到位，全链路使用 ydsz-common-json，合规意识强。

但当前状态存在一个**阻断性事实**和一批**"能力宣称与代码事实不符"**的问题：模块**当前不可编译**（详见 P0-1），且多模型路由、人工审批、Feign 客户端、MCP SDK、Text2SQL Schema 等 README 宣称的能力在代码层面未兑现或存在实质缺陷。以下按 P0 → P1 → P2 给出可执行待办。

### 值得保留的亮点（对标中不落下风的部分）

| 能力 | 代码事实 | 对标结论 |
|---|---|---|
| 流式 PII 脱敏 | `StreamingPiiMasker` 处理 token 边界截断 + 尾部 flush（ChatService.stream） | 优于多数开源竞品的细节处理 |
| 混合检索 | `HybridRetriever`：向量 + 全文两路召回、RRF(k=60) 融合、Reranker SPI、构造期建表探测 + 运行期降级 | 对标 Dify 检索策略，设计成熟 |
| Token 成本闭环 | 调用前估算（`estimateBeforeCall`）→ 配额预检 → 调用后实算（`calculateActual`）→ 落库（`CostAnalysisService`） | 超出 Spring AI / LangChain4j 内置能力 |
| 多租户隔离落地 | `PgVectorStore` / `HybridRetriever` / `RedisConversationMemory` 绕过 MyBatis 拦截器的路径均显式追加 tenant_id | 教科书级执行 |
| 幂等 + 限流 + 配额 + 护栏四重防线 | `AgentRequestGuard`（SETNX + 固定窗口）、`TenantQuotaService`（Redis INCR + 本地降级）、`GuardrailService` | 防线完整度对标大厂 |
| 工具并行执行 | `ReActAgentExecutor.executeToolsConcurrently`（虚拟线程 + 按 callId 回填保序） | 对齐 LangChain/AutoGen 默认行为 |
| 检查点续跑 | `DagOrchestrationExecutor` + `RedisDagCheckpointStore`，Redis 不可用时自动降级 | 对标 LangGraph checkpointer 雏形 |
| 装配纯净度 | server 层 pom 无 infra 依赖，infra 实现统一在 web 层 `AgentAutoConfiguration` 装配 | 符合模块自身 DDD 约定 |

---

## 二、P0：阻断性问题（必须最先修复）

### P0-1 模块当前无法编译（9 处注解/类缺失 import）

源码在 2026-08-19 17:40 后被改动，但最后成功编译停留在 11:23。以下注解/类在代码中使用却**没有对应 import**，javac 必然失败：

| 文件 | 缺失 import |
|---|---|
| `server/chat/ChatService.java:53` | `org.springframework.stereotype.Service` |
| `server/debug/AgentDebuggerService.java:35` | 同上 |
| `server/observability/ObservabilityDashboardService.java:28` | 同上 |
| `server/prompt/PromptEvaluationService.java:33` | 同上 |
| `server/prompt/PromptManagementService.java:43` | 同上 |
| `server/event/AgentEventPublisher.java:24` | `org.springframework.stereotype.Component` |
| `server/prompt/DatabasePromptTemplateProvider.java:19` | 同上 |
| `server/agent/AgentFactory.java:104` | `org.springframework.context.annotation.Lazy` |
| `server/chat/AgentRequestGuard.java:85` | `BusinessException`（common-core 包） |

这 8 个类均依赖 `AgentApplication` 的 `scanBasePackages` 组件扫描注册（不在 `AgentAutoConfiguration` 的 @Bean 列表中），import 修复后 Bean 才能生效。
**动作**：补齐 9 处 import → `mvn -pl ydsz-agent -am compile` 验证 → 将 `mvn checkstyle:check`（docs/checkstyle.xml v3.2）纳入提交前必跑项，本次断裂正是因为改动后未跑构建。

### P0-2 LlmClientRouter 多 Provider 路由形同虚设

`OpenAiCompatibleClient.supports()`（OpenAiCompatibleClient.java:298）对**任何非空 model 恒返回 true**，而 `LlmClientRouter.resolveClient()`（LlmClientRouter.java:178）按 `clients.values()` 迭代顺序取第一个 `supports` 命中者 → **永远路由到 ConcurrentHashMap 迭代序的第一个 Provider**。后果：

- 配置中 `providers.deepseek.models: [deepseek-chat]` 完全无效；
- `deepseek-chat` 模型可能打到 openai 的 baseUrl（反之亦然），报 MODEL_NOT_FOUND；
- Fallback 链的"主/备"语义实际是"随机主"。

**动作**：为 `OpenAiCompatibleClient` 注入该 Provider 的 `models` 列表（`AgentProperties.ProviderConfig.models` 已存在但从未传入），`supports()` 改为 `models.contains(modelId)`；`resolveClient` 在无精确命中时才回退 `defaultClient`。补单测：注册两个 Provider，断言 deepseek-chat 路由到 deepseek。

### P0-3 同步调用错误分类失效 → 错误请求最多重试 8 次

`OpenAiCompatibleClient.chat()` 的 catch 块捕获的是 `HttpClientErrorException` / `ResourceAccessException`（**RestTemplate 体系异常，WebClient 路径永远不会抛出**，死代码），导致：

- HTTP 401/403/404/400 全部落入泛型 `catch (Exception e)`，被当作可重试错误**重试 3 次**（退避 1+2+3s）；
- 包装为 `PROVIDER_ERROR` 后又触发 `LlmClientRouter` 的 Fallback 再打一次备用 Provider——**一个注定失败的请求最多消耗 4×2=8 次调用与约 6 秒空转**。

**动作**：删除两个死 catch 块，统一捕获 `WebClientResponseException` 并走 `mapHttpError()`（该方法已存在且逻辑正确）；`isRetryable` 仅放行 RATE_LIMITED/NETWORK_TIMEOUT/5xx。

### P0-4 流式取消检测永远不生效

`OpenAiCompatibleClient.stream()`（OpenAiCompatibleClient.java:263）在 `doOnNext` 内检查 `Thread.currentThread().isInterrupted()`——该 lambda 运行在 **Reactor Netty 事件循环线程**上，而中断发生在 SSE 执行虚拟线程上，**条件永假**。用户断开 SSE 后，上游 LLM 流不会被取消，token 持续计费。
**动作**：改用响应式取消信号：`bodyToFlux(...).takeUntil(c -> cancelled.get())` 配合 `emitter.onError/onCompletion` 回调置位，或改用 `Flux.usingWhen` + 订阅 `Disposable` 在 `SseExecutor.cleanup` 中 `dispose()`。

---

## 三、P1：功能缺陷与安全风险

### P1-1 用户级限流失效（所有用户共享一个桶）

`AgentController` 四处调用 `requestGuard.check(request.getRequestId(), null)`——userId 恒传 null，全部请求落入 `"anonymous"` 限流桶（默认 10 QPM）。**任何一个用户可以把全系统打满**。
**动作**：从认证上下文取当前用户（common-auth 已有），传入 `check(requestId, currentUserId)`。

### P1-2 多模态路径完全绕过输入护栏

`ChatService.chat(MessageContent)` / `stream(MessageContent)`（ChatService.java:282/584）没有调用 `applyInputGuardrails`——图片 alt 文本与文本段落可携带注入内容直达 LLM。
**动作**：与文本路径统一（见 P1-5 重构后自然解决）。

### P1-3 LLM 缓存 key 缺租户维度

`SemanticLlmCache.buildKey()` = SHA-256(model + systemPrompt + userMessage)，**不含 tenantId**：租户 A 定制的 system prompt 若与 B 相同（如都用默认模板），A 的答案会被 B 命中，且命中指标互相污染。RAG 注入的上下文同样不在 key 中。
**动作**：key 拼入 `TenantContextHolder.getTenantId()`；`extractCacheableContent` 若注入了 RAG 上下文则跳过缓存。

### P1-4 Text2SQL 三处实质缺陷（JdbcText2SQLService）

1. **Prompt 与执行互相矛盾**：`buildSystemPrompt()` 要求 LLM 写 `WHERE tenant_id = ?`（裸 `?` 经 Statement 直发 PG 必然语法错误），随后 `appendTenantCondition()` 又追加第二个 `tenant_id` 条件——LLM 服从指令则 SQL 报错，不服从则双重条件。**动作**：prompt 中删除租户要求，租户隔离完全由代码侧注入（当前方向正确）。
2. **appendTenantCondition 语法脆弱**：`replaceFirst("(?i)WHERE", ...)` 会命中子查询的第一个 WHERE；SQL 带 ORDER BY 时直接尾部拼接 `WHERE` 语法错误。**动作**：放弃文本改写，改为向 LLM 请求表名后用 `conn.setReadOnly(true)` + `SET LOCAL app.tenant_id` + 数据库侧 RLS（Row Level Security）策略，或至少在外层包一层 `SELECT * FROM (原SQL) sub WHERE tenant_id = ?`。
3. **"Schema-Aware" 名不副实**：类 Javadoc 宣称"提取目标表 Schema（列名、类型、注释）"，`buildSystemPrompt()` 实际不含任何 schema，LLM 只能幻觉列名。**动作**：从 `information_schema.columns` 按白名单表提取 schema 注入 prompt（表清单加配置项）。
4. 附带：`@Service` + `@Value` 装配方式与模块约定（infra Bean 统一在 web 层 AutoConfiguration 装配）不一致，且绕过了 `AgentProperties`。**动作**：改为 `AgentAutoConfiguration` 中 `@ConditionalOnProperty(ydsz.agent.text2sql.enabled)` 装配。

### P1-5 人工审批能力"空转"（宣称有、实际未接线）

`HumanApprovalService`（414 行，含 approve/reject/通知机制）+ `HumanApprovalController` 均存在，但**全模块没有任何执行器调用 `requestApproval()`**（Supervisor/DAG/ReAct 中 grep 无引用）。README 宣称"Agent 执行中暂停等待人工审批"、FAQ Q3 让用户"检查人工审批节点是否待审批"——DAG 引擎根本没有 APPROVAL 节点类型。
**动作**（择一）：
- 方案 A（推荐）：在 `DagOrchestrationExecutor` 增加 `nodeType: APPROVAL`，节点执行时调用 `requestApproval` 并阻塞（虚拟线程 + `CompletableFuture.awaitDone`，超时自动 REJECT），与检查点机制天然配合（审批前存 checkpoint，审批后续跑）；
- 方案 B：暂不接线，则删除 Service/Controller 及 README 相关段落，避免虚假能力宣称。

### P1-6 RagAgentExecutor 是 273 行孤儿代码

`AgentFactory.createExecutor("RAG")`（AgentFactory.java:162）创建的是 **ReActAgentExecutor**（注释自述"复用 ReAct 执行器"），`RagAgentExecutor` 类从未被任何路径实例化 = 死代码。
**动作**：删除 `RagAgentExecutor`，或让它走独立的"检索→必答"短链路（无工具循环、强制引用来源）与 ReAct+RAG 区分；二选一，不留双实现。

### P1-7 Feign 客户端缺失，跨模块联动是空中楼阁

README 结构图声称 api 层含 `feign/` + `fallback/`，实际**两个目录均为空**。README 尾部宣称"与 literule/workflow/message 三引擎深度融合"——但其他模块没有任何标准客户端可调。
**动作**：在 `ydsz-agent-api` 补 `AgentFeignClient`（chat / execute / dag/execute / rag/search 端点）+ Fallback 降级类，供 literule（AI 辅助规则生成）、workflow（AI 节点）、message（通知）消费。这是兑现"三引擎融合"的前置条件。

### P1-8 伪流式：ReAct / PlanExecute / DAG 的 executeStream 等待完整响应

三个执行器的流式入口内部仍调 `llmClient.chat()`（ReActAgentExecutor.java:220 等），用户在最终答案前的所有思考/工具轮次期间**无任何字节推送**（体验上等同同步 + 一次性吐出）。仅 Simple/Rag/Supervisor 用了真流式。
**动作**：最终轮（无 tool_calls 判定后）改走 `llmClient.stream()` 推 token；中间轮次至少推送 `[思考N] 开始` / `[工具调用] name` 进度帧（ReAct 已有此逻辑，改为逐 token 即可）。

### P1-9 ChatService / DagOrchestrationExecutor 大面积复制粘贴

- `ChatService` 805 行 = 文本/多模态 × 同步/流式 四个方法**两两 90% 相同**（估算约 400 行冗余），单方法超 130 行，违反规范 4.5（方法最大行数）；
- `DagOrchestrationExecutor.execute()` 与 `executeWithProgress()` 约 120 行调度逻辑重复。

**动作**（对标 Spring AI 的 Advisor 链，收敛横切逻辑）：
- ChatService 抽模板方法：`doChat(convId, 用户消息构造器, traceType)`，护栏/配额/成本/事件/指标全部只写一遍；
- 更进一步可引入 `ChatInterceptor` 链（guardrail → quota → metrics → cost → event 顺序），对标 Spring AI `ChatClient.Builder.defaultAdvisors()`，为后续 A/B、灰度、审计扩展留插槽；
- DAG：`execute()` 改为 `executeWithProgress(dag, userInput, resumeId, null)` 单行委托。

### P1-10 约 30 处重复 import（规范 5.3/5.5 红线）

全模块扫描结果（节选）：`ChatService`（TenantContextHolder ×2）、`AgentFactory`（RagService ×2）、`DagOrchestrationExecutor`（IdGenerator ×2）、`OpenAiCompatibleClient`（ObjectNode ×2）、`LlmClientRouter`（ChatResponse ×2）、`RedisConversationMemory`、`PgVectorStore`、`HybridRetriever`、`CostAnalysisService`、`RagService`、`TenantQuotaService`、`AgentDebuggerService`、`ObservabilityDashboardService` 等 20+ 文件。
**动作**：IDE "Optimize Imports" 全模块批量处理 + checkstyle `RedundantImport` 规则上线（checkstyle.xml v3.2 已声称可自动检查，显然未在 agent 模块跑过）。

### P1-11 infra pom 直接依赖 Caffeine（规范禁令）+ 冗余依赖

`ydsz-agent-infra/pom.xml` 显式声明 `com.github.ben-manes.caffeine:caffeine`，但源码**零处 import**（L1 缓存走 `YdszCache.newBuilder()` 包装）——既是违反"禁止直接依赖 Caffeine、必须使用 ydsz-common-cache"红线的死依赖，也让构建期纯净度校验形同虚设。`reactor-netty-http` 与 `spring-boot-starter-webflux` 重复（后者传递包含）。
**动作**：删除两个依赖 → 重跑纯净度校验。

---

## 四、P2：架构优化、性能、体验与过度设计

### 4.1 架构优化

| # | 建议 | 依据 |
|---|---|---|
| A1 | **收敛可观测四件套**：`AgentMetrics`(199 行) + `AgentRuntimeMetrics`(295 行) + `CostAnalysisService`(324 行) + `ObservabilityDashboardService` 职责交叠（都做"指标聚合"）。保留 Metrics（Prometheus）与 Cost（落库），RuntimeMetrics 并入 AgentMetrics，Dashboard 仅做查询聚合 | 规范 v2.23 第 35 章"指标精简与 AOP 化" |
| A2 | **domain 层 DTO/VO 双轨精简**：`domain/dto` 与 `domain/vo` 存在 7 组镜像类（AgentTraceDTO/AgentTraceVO 等）+ `AgentConverter`(321 行 MapStruct)。api 层已有对外 DTO，domain 层保留一套即可 | 规范"Repository 接口精简"精神 |
| A3 | **MCP 实现二选一**：当前是手写 JSON-RPC over JDK HttpClient（SseMcpClientProvider，无重连、无 stdio），README 却宣称"MCP Java SDK + stdio 支持"（双重 doc drift）。建议：短期改 README 如实描述；中期切 Spring AI 的 `spring-ai-starter-mcp-client`（与现有 WebClient 栈同源）获得重连/stdio/协议升级 | 对标 Spring AI 1.x 官方 MCP 集成 |
| A4 | **DAG 状态类型化**：`nodeResults` 是 `Map<String,String>`，节点间传参靠字符串拼接（`buildNodeInput`）。对标 LangGraph 的 typed State：定义 `DagState`（含 variables Map + typed accessors），为画布化（联动 ydsz-plane）打地基 | 对标 LangGraph StateGraph |
| A5 | **错误响应脱敏**：`SseExecutor.sendError` / ChatService 失败路径直接透出 `e.getMessage()`（含内部 URL、连接细节）。统一替换为错误码 + 通用文案，细节只进日志 | 规范第 15 章错误码 |

### 4.2 功能增强（对标差距）

| # | 建议 | 对标 |
|---|---|---|
| F1 | **真·多租户配额**：`resolveTenantQuota()` 恒用全局配置构造 `"default"` 配额——限额是全局的，"单租户每日 Token 限额"名不副实。落一张 `ydsz_agent_tenant_quota` 表 + Redis 计数（计数已按租户隔离，只差限额来源） | 大厂 Agent 平台标配 |
| F2 | **RAG 检索质量三件套**：查询改写（同义扩展/多查询 RAG-Fusion）、父子分块（parent-child chunk，检索子块返回父上下文）、检索命中率评估（prompt 评估已有，补检索侧）。现有 `reranker-enabled` 默认 Identity，可先接一个托管 rerank API | Dify 检索增强、LangChain4j EasyRAG |
| F3 | **文档摄入 pipeline 补解析层**：`DocumentIngestionService` 只接收纯文本。内网知识库需要 PDF/Office 解析——注意规范红线：**必须走自研 POI 组件**（common 层已有），不得直接依赖 POI | Dify 知识库 |
| F4 | **长期记忆**：现有滑动窗口 + 摘要压缩（SummaryConversationMemory 质量不错）。补跨会话记忆：会话结束时抽取事实/偏好入向量库，检索时并入上下文 | LangChain4j 长期记忆、Coze 记忆变量 |
| F5 | **Agent 定义热更新**：`AgentDefinitionService` 已有 CRUD，补"变更后踢出执行器缓存 + 灰度发布（按租户/百分比切新版本）" | Coze 发布机制 |
| F6 | **DAG 画布后端契约**：现有 `/dag/validate` + 检查点查询 + 进度事件已够画布用，补 `dagTemplate` CRUD + 版本管理，前端画布放到 ydsz-plane 对齐（呼应"前后端能力对齐"主线） | Dify/Coze 画布 |

### 4.3 性能提升

| # | 建议 | 依据 |
|---|---|---|
| P1 | **HybridRetriever 两路召回并行化**：Javadoc 宣称"并行召回"，代码是串行（先向量后全文），向量路含一次 embedding API 往返（数百 ms）。用 `CompletableFuture` + 虚拟线程并行两路 | 代码与注释不符 + 检索延迟直接收益 |
| P2 | **SemanticLlmCache 命中路径合并 Redis 命令**：L2 命中 = GET + ZADD 两次往返；容量淘汰逐条 DEL。用 pipeline 批量化 | 缓存路径 P99 优化 |
| P3 | **ReAct 系统提示词去重**：`buildSystemPrompt` 把全部工具名+描述拼进 system prompt，而 `ChatRequest.tools` 又传了一份（OpenAI function calling 协议）——**每轮迭代双倍 token**。删除 system prompt 中的工具清单段落 | 每轮迭代直接省 token 成本 |
| P4 | **pgvector 索引升级 HNSW**：DDL 注释推荐 ivfflat；数据量 >10 万 chunk 时 HNSW 召回/延迟更优，写入侧换 `hnsw (embedding vector_cosine_ops)` | pgvector 最佳实践 |
| P5 | **LLM 流式首包超时**：`responseTimeout` 覆盖整个流（60s），但 TTFT 超时应单独收紧（如 10s）——`HttpClient.responseTimeout` 无法区分，改用 `Mono.timeout` 首包 + 流式总超时双层 | 流式体验稳定性 |

### 4.4 体验改善

| # | 建议 |
|---|---|
| E1 | 批量对话 `/chat/batch` 返回结构已含单条失败隔离，补**部分失败时的聚合摘要**（成功 N/失败 M + 失败 itemId 列表），便于前端一次提示 |
| E2 | SSE 事件增加 `conversationId` 与 `traceId` 头帧，前端可即时展示"可解释"入口（debug 面板联动） |
| E3 | `/api/v1/agent/tools` 增加分组与启用状态（当前平铺列表），MCP/内置工具混合时前端无法区分来源 |
| E4 | 对话历史 GET `/history` 支持分页与时间段过滤（当前全量拉取 Redis 窗口） |

### 4.5 过度设计（做减法）

| # | 问题 | 处置建议 |
|---|---|---|
| O1 | `AgentProperties` 920 行 / 100+ 配置项，其中 MCP、Text2SQL、语义缓存、摘要记忆、reranker **默认全关**——大量"配置了但没人用"的能力面 | 冻结新配置项；对连续两个迭代无人启用的能力（reranker、summary 记忆）标注 @Deprecated 候选 |
| O2 | `SemanticLlmCache` 名为语义实为精确哈希（注释已坦白）。命名误导后来者按"语义缓存"预期排查 | 改名 `ExactMatchLlmCache`，或真正实现语义命中（复用已有 EmbeddingClient 做 key 向量化 + 相似度阈值） |
| O3 | 人工审批、RagAgentExecutor、feign/fallback 空目录——三个"半成品"同时存在，说明功能面铺得比交付快 | 本轮 P1-5/6/7 三选一处理（接通或删除），后续新能力必须"接线完成才算交付" |
| O4 | `DagOrchestrationExecutor` 的 `int[] completedCounter` + `synchronized(数组)` 反模式、`executeLoopNode` 中 `(Integer) config.get("maxIterations")` 强转（YAML 解析出 Long 会 CCE，同文件另一处已用 `instanceof Number` 正确处理） | 统一 `instanceof Number`；计数器换 `AtomicInteger` |
| O5 | 整图 300s 超时、心跳 15s、L1 200 条等魔法值散落（多数有注释但未常量化） | 提为常量并入 AgentProperties 可配（规范 3.1） |
| O6 | 测试覆盖：196 源文件仅 **2 个测试类**（AgentFactoryTest、RedisConversationMemoryTest）。P0-2/P0-3 这类路由与重试缺陷正是缺测试的直接后果 | 最低限度补：LlmClientRouter 路由/Fallback、OpenAiCompatibleClient 错误分类（MockWebServer）、DagOrchestrationExecutor 拓扑/循环/条件、GuardrailService 链式（规范第 14 章） |

### 4.6 README doc drift 清单（随代码一并修）

| README 宣称 | 代码事实 |
|---|---|
| "MCP 集成 \| MCP Java SDK" | 手写 JSON-RPC + JDK HttpClient，零 SDK 依赖 |
| "当前支持 SSE 和 stdio 两种传输方式"（FAQ Q5） | 仅 SSE（SseMcpClientProvider） |
| api 层结构含 `feign/` + `fallback/` | 两个空目录 |
| "人工审批：Agent 执行中暂停等待人工审批" | 无执行器接线 |
| 文档结构列出 `ydsz-agent-server/.../infra/tool/`（MCP 类路径） | 实际在 ydsz-agent-infra |
| HybridRetriever "并行召回" | 串行执行 |

---

## 五、云顶编码规范合规对照

| 规范条目 | 状态 | 证据 |
|---|---|---|
| 禁止第三方 JSON，必须 ydsz-common-json | ✅ 合规 | 全模块统一 `YdszJson`/`JsonMapper`，无 Jackson 直接依赖 |
| 禁止直接依赖 Caffeine | ❌ 违规 | infra pom 显式声明 caffeine（P1-11，死依赖） |
| 禁止直接依赖 POI | ✅ 合规 | 无 POI 依赖（RAG 文档解析尚未做，见 F3） |
| 5.3/5.5 import 排序与未使用 | ❌ 违规 | ~30 处重复 import（P1-10） |
| 编译可用性（一切的前提） | ❌ 断裂 | 9 处缺失 import（P0-1） |
| 4.5 方法最大行数 | ❌ 超限 | ChatService 四方法 90-180 行（P1-9） |
| 3.1 禁止魔法值 | ⚠️ 部分 | DAG 300s 超时等（O5） |
| 10.x 注释规范 | ✅ 优秀 | 全模块 Javadoc 覆盖率与质量显著高于平均水平 |
| 第 35 章过度设计防范（指标精简等） | ⚠️ 待落实 | 可观测四件套交叠（A1） |

---

## 六、建议执行顺序（迭代排期）

**第一迭代（修复断裂，恢复可信基线）**
P0-1 编译修复 + checkstyle 上线 → P0-2 路由修复（带单测）→ P0-3/P0-4 错误分类与流取消 → P1-1 限流传 userId → P1-11 删除违规依赖 → README doc drift 修正

**第二迭代（补齐"宣称已有"的能力）**
P1-5 人工审批接线（DAG APPROVAL 节点 + 检查点协同）→ P1-7 Feign 客户端（打通 literule/workflow 联动）→ P1-6 RagAgentExecutor 处置 → P1-8 伪流式改造 → P1-2/P1-3 护栏与缓存租户维度

**第三迭代（质量与重构）**
P1-9 ChatService 模板方法/Advisor 链重构 → P1-4 Text2SQL 修复 → P1-10 import 清理 → A1 可观测收敛 → O6 核心链路单测补齐

**第四迭代（竞品追赶）**
F1 租户级配额 → F2 RAG 三件套 → P1/P3 检索并行化与 token 去重 → F4 长期记忆 → F6 DAG 画布契约（联动 ydsz-plane）

---

## 附：本次分析覆盖的关键文件

- 执行链路：`DagOrchestrationExecutor`、`ReActAgentExecutor`、`AgentFactory`、`AbstractAgentExecutor`、`AgentAutoConfiguration`、`AgentApplication`
- LLM 层：`OpenAiCompatibleClient`、`LlmClientRouter`、`SemanticLlmCache`、`CachedLlmClient`
- 对话层：`ChatService`、`SseExecutor`、`AgentRequestGuard`
- 增强能力：`PgVectorStore`、`HybridRetriever`、`JdbcText2SQLService`、`TenantQuotaService`
- 构建与文档：5 个子模块 pom.xml、README.md、bootstrap.yml、docs/云顶编码规范.md（v2.23）
- 全模块自动化扫描：缺失/重复 import（114 项命中，人工甄别后 9 项编译级 + 30 项规范级成立）
