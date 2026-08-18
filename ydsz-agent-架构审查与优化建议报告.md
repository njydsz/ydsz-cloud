# ydsz-agent 模块架构审查与优化建议报告

> 审查范围：`ydsz-agent`（api / domain / infra / server / app / web 六子模块）
> 审查方法：全量精读核心源码 + 对标 LangGraph / AutoGen / Dify / LlamaIndex / Semantic Kernel 及阿里巴巴研发规范
> 优先级定义：**P0** 阻断性/数据丢失/资损 → 立即修复；**P1** 重要缺陷/性能/安全 → 下一迭代；**P2** 增强项 → 规划

---

## 一、模块现状概览

ydsz-agent 是一套自研 AI Agent 框架，定位对标 Dify 工作流 + LangGraph 图引擎。当前已实现：

| 能力域 | 实现情况 |
|--------|----------|
| LLM 对话 | OpenAI 兼容客户端（同步 RestClient + 流式 WebClient），支持重试/限流/超时 |
| Agent 范式 | ReAct / Plan-and-Execute / Supervisor / DAG 四种执行器 |
| DAG 编排 | CompletableFuture 拓扑排序 + 虚拟线程并行 + 条件/循环节点 |
| Tool Calling | `@Tool` 注解扫描 + 反射执行 + MCP(SSE) 适配 |
| RAG | pgvector / 内存向量库 + 全文检索 RRF 融合 + 分块 + rerank 抽象 |
| 安全护栏 | 输入/输出护栏 + PII 脱敏 + Prompt 注入检测 + 流式增量脱敏 |
| 对话记忆 | Redis 滑动窗口 + 摘要压缩 + Token 预算截断 |
| 可观测性 | DB 链路追踪 + Micrometer 指标 + 成本核算 + 调试/可观测面板 |
| 缓存 | "语义缓存"（实为精确哈希缓存） |

**优点**：DDD 五层分层规范、领域对象不可变性强、虚拟线程应用到位、多租户隔离已系统覆盖、Javadoc 详尽、降级兜底完善。

**核心短板**：DAG 引擎存在功能性 bug；"语义缓存"名不副实；MCP 协议覆盖度低；多处存在数据丢失/资损风险；缺乏持久化与断点恢复。

---

## 二、分维度分析与建议

### 维度 1：架构优化

#### A1. [P0] DAG 条件分支（CONDITION）实际不生效

**问题**：`DagOrchestrationExecutor.execute`（L105-130）对拓扑排序后的**所有**节点统一创建 `CompletableFuture`。`executeConditionNode`（L268-291）仅将选中分支 ID 写入 `results["__BRANCH__<id>"]`，但主循环并未据此跳过未选中分支的节点 future。结果是 **trueBranch 和 falseBranch 两路都会执行**，条件分支形同虚设。

**影响**：LLM 调用翻倍、成本翻倍、输出不确定性；语义上"条件"毫无作用。

**建议**：引入"动态跳过"机制——为每个节点附加 `skipPredicate`（基于上游 CONDITION 结果计算），或改为事件驱动调度（CONDITION 完成后显式调度选中分支）。最简改法：在 `executeNodeLogic` 入口检查该节点是否被某 CONDITION 节点排除，若被排除则直接 `return` 并标记 skipped。

#### A2. [P0] DAG 循环节点（LOOP）双重执行 + 竞态

**问题**：`executeLoopNode`（L304-343）在 future 图之外直接调用 `executeNodeLogic` 执行 body 节点；而这些 body 节点同时也在 `sortedNodeIds` 主循环里各自拥有 future。导致 **每个 body 节点被主图执行一次 + 循环内执行 N 次**，且并发写 `results` map 产生竞态。

**建议**：将 LOOP body 节点从主图调度中排除（拓扑排序时标记 LOOP body 为"由 LOOP 节点托管"），或主图按"逻辑节点"与"可调度节点"区分。同时为 `results`/`usages` 的写入加节点级保护。

#### A3. [P1] DAG 无持久化与断点恢复

**问题**：`executionId` 生成后从不落库，进程崩溃即丢失全部执行进度。对比 `HumanApprovalService` 已示范了 DB + 缓存 + 事件模式，DAG 未复用。

**对标**：LangGraph 内置 Checkpointer（内存/SQLite/Postgres），支持任意节点断点恢复；Dify 工作流全程持久化。

**建议**：实现 DAG 执行状态机持久化（节点状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED），重启后从断点恢复。复用已有的 `AgentTraceDO`/`AgentTraceStepDO` 表或新建 `agent_dag_execution` 表。

#### A4. [P1] DAG 节点 `agentType` 被忽略，子 Agent 能力失效

**问题**：`executeNodeLogic`（L181-217）所有节点统一走 `llmClient.chat`，`AgentDag.Node.agentType` 字段、`AgentFactory` 路由能力在 DAG 内完全失效——DAG 节点无法作为 ReAct/PlanExecute 子 Agent 执行。

**建议**：DAG 节点通过 `agentFactory.getExecutor(AgentDefinition)` 派发，使 DAG 节点可承载任意 Agent 范式。同时 `DagOrchestrationExecutor` 应实现 `AgentExecutor` 接口，纳入 `AgentFactory` 体系（当前割裂）。

#### A5. [P1] MCP 实现远未达到"完整支持"

**问题**：
- `AgentProperties.ServerInfo` 注释声称支持 `sse / stdio`，但全工程仅 `SseMcpClientProvider` 一个实现，**stdio / streamable-http 完全未实现**，配置 stdio 会被静默当 sse 处理；
- `McpClientProvider` 注释声称"基于 MCP Java SDK"，但 pom.xml 无该依赖（已 grep 确认），实为手搓 HTTP，注释误导；
- MCP 仅支持 tools，未实现 `resources/list`、`resources/read`、`prompts/list`、`prompts/get`——而 MCP 规范把 tools/resources/prompts 并列为三大能力；
- `sessionCache` 无失效与重连，MCP Server 会话过期后所有调用持续失败，无重连机制。

**对标**：Cursor / Claude Desktop 完整支持 MCP 三大能力 + 多传输。

**建议**：引入官方 `io.modelcontextprotocol:sdk` 替换手搓 HTTP；补 stdio/streamable-http 传输；session 加 TTL 或按 401 触发 reinit；补 resources/prompts 能力；修正误导性注释。

#### A6. [P0] MCP `requestId` 用 `System.currentTimeMillis()`

**问题**：`SseMcpClientProvider.sendRequest` 用 `System.currentTimeMillis()` 作为 JSON-RPC id，高并发同毫秒碰撞，违反 id 唯一性，可能导致响应错配。

**建议**：改用 `IdGenerator.nextIdStr()`（雪花 ID）。

---

### 维度 2：功能增强

#### F1. [P0] 摘要压缩非原子 + 摘要纯内存，重启丢历史

**问题**：`SummaryConversationMemory`
- 摘要缓存 `conversationSummaries`（L71）是纯内存 `ConcurrentHashMap`，**重启后摘要丢失**；
- `tryCompress`（L204-208）先 `conversationSummaries.put`（内存）→ `delegate.clear()`（删 Redis 旧消息）→ 逐条 `delegate.save` recentMessages。中途任一失败，**旧消息已删、新消息未写全、摘要也在内存随时可能丢，历史彻底丢失**。

**对标**：LangChain ConversationSummaryBufferMemory 摘要持久化；OpenAI Assistants Thread 服务端管理。

**建议**：摘要持久化到 Redis（独立 key `ydsz:agent:summary:{convId}`）；压缩改为"先写摘要+recent 到新临时 key，再用 RENAME 原子替换"或事务；至少保证 clear 与 save 的原子性。

#### F2. [P1] 无内容安全护栏（涉黄/涉政/暴力）

**问题**：`InputGuardrail`/`OutputGuardrail` 接口定义了内容分类，但无 `ContentSafetyGuardrail` 实现，未接入任何内容安全审核服务。

**建议**：实现 `ContentSafetyGuardrail`，接入腾讯云内容安全 / 阿里云内容审核 / 本地模型审核；护栏配置化（可按租户/场景开关）。

#### F3. [P1] Prompt 注入检测纯正则，易绕过且误杀

**问题**：`PromptInjectionGuardrail`（L31-49）15 条正则，同义词替换/编码/多语言即可绕过；`you are now a` 会误杀正常 system prompt。

**建议**：补充 LLM-based 注入检测（用小模型判断意图）作为增强；正则规则可配置化、支持白名单。

#### F4. [P1] ReAct 工具调用串行执行

**问题**：`ReActAgentExecutor.execute`（L148-165）`for (ToolCall...)` 顺序执行。LLM 一次返回多个 tool call 时应并发执行。

**对标**：LangChain / AutoGen 默认并行执行多 tool call。

**建议**：用 `CompletableFuture.allOf` 并发执行无依赖的 tool call。

#### F5. [P1] 工具无参数校验、无权限控制、无 per-tool 超时

**问题**：`DefaultToolRegistry.execute` 直接调 `executor.execute(args)`，未按 schema 校验 `required`/类型；`AgentExecutionRequest.enabledTools` 白名单在 registry 层未生效（LLM 幻觉调用工具名仍可执行）；全局 `defaultTimeoutSeconds`，MCP 工具与本地工具共用。

**建议**：registry 层加白名单校验 + JSON Schema 参数校验；`ToolRegistration` 携带 `timeoutSeconds`；工具结果用结构化 `ToolResult` 区分成败。

#### F6. [P2] 无流式工具调用、无结构化数据传递

**问题**：`ToolExecutor.execute` 返回 `String`，无法支持流式产出；`buildNodeInput`（L398-412）仅文本拼接上游结果，无 JSONPath/字段提取，长结果撑爆 context。

**建议**：新增 `executeStream(args, Consumer<String>)` 默认方法；支持 `inputFrom: node1.$.field` 结构化提取。

---

### 维度 3：性能提升

#### P1. [P0] 成本计算模型价格匹配错误（价差 16 倍）

**问题**：`CostAnalysisService.ModelPriceConfig.getPrice`（L265-275）用子串包含匹配 `lowerModel.contains(key)`。`gpt-4o-mini`.contains(`gpt-4o`) = true，若遍历时 `gpt-4o`（0.0025）排在 `gpt-4o-mini`（0.00015）前，会返回错误价格，**价差 16 倍**。默认构造用 `Map.of()`（无序），匹配结果不确定。

**影响**：成本面板数据严重失真，无法用于成本治理。

**建议**：改用精确匹配 + 前缀/后缀分层匹配（先精确，再按 `gpt-4o-mini` 长键优先于 `gpt-4o` 短键），或用 LinkedHashMap 保证"长键优先"顺序；价格配置外置到 Nacos。

#### P2. [P0] "语义缓存"实为精确哈希缓存

**问题**：`SemanticLlmCache.buildCacheKey`（L122-130）用 SHA-256 精确哈希做 key，`SemanticCacheConfig.DEFAULT_SIMILARITY_THRESHOLD=0.95` 定义后**全代码库未使用**。相同语义不同表述的 query 无法命中，缓存命中率远低于预期——名为"语义缓存"实为"精确缓存"。

**建议**：要么改名 `ExactLlmCache` 名实相符；要么真正引入 embedding 相似度检索（Redis 向量索引或独立向量表）做缓存查找，实现语义命中。

#### P3. [P1] RAG 摄入非原子 + 批量 embedding 后逐条写库

**问题**：
- `DocumentIngestionService.ingest` 先 `deleteByDocument` 再分块写入，无事务，中途失败旧索引已删、新索引不完整，**知识库数据丢失且无法自愈**；
- embedding 已批量化（EMBED_BATCH_SIZE=20），但写库逐条 `store`，未调用已实现的 `storeBatch()`，大文档摄入 RT 放大 20 倍。

**建议**：摄入加事务（或先写临时表再 RENAME）；写库改用 `storeBatch()`。

#### P4. [P1] 全文检索 ILIKE 全表扫描 + 排序逻辑矛盾

**问题**：`HybridRetriever.fullTextSearch` WHERE 用 `ILIKE '%query%'`（全表扫描），ORDER BY 又用 `ts_rank(to_tsvector, plainto_tsquery)`（BM25 排序）。WHERE 和 ORDER BY 两套匹配逻辑，ILIKE 命中的文档 ts_rank 可能为 0。

**建议**：统一用 `to_tsvector @@ plainto_tsquery` 操作符做 WHERE 过滤，配合 GIN 索引。

#### P5. [P1] 无 HNSW 索引，query embedding 无缓存

**问题**：`PgVectorStore` 用 ivfflat 未配 `probes`，高维(1536)召回率低；每次检索实时调 embedding API，相同 query 重复 embedding 浪费成本。

**建议**：DDL 改用 HNSW 索引（百万级以下精度速度均优）；query embedding 加短时缓存。

#### P6. [P1] Trace 逐条 insert 写放大 + recordUsage 同步阻塞

**问题**：`PgTraceRecorder.recordStep`（L116）每步同步单条 insert，ReAct 多轮迭代下高频写 DB（注释提到异步批量但未实现）；`CostAnalysisService.recordUsage`（L75）注释说"异步写入"实际同步阻塞主流程。

**建议**：Trace 步骤改异步批量写入（缓冲队列 + 定时刷盘）；recordUsage 改异步（消息队列或线程池）。

#### P7. [P1] 缓存击穿 + 命中不记成本 + 流式不缓存

**问题**：高并发同 key 未命中时所有请求打穿 LLM，无 Singleflight；缓存命中返回 `TokenUsage.zero()`，成本统计失真；流式不缓存（流式是主流场景，缓存覆盖面窄）。

**建议**：加互斥锁/Singleflight 防击穿；缓存命中记录 `cache_hit` 指标与估算节省量；评估流式首末缓存。

---

### 维度 4：体验改善（开发与运维体验）

#### E1. [P0] Trace 存储 input/output 未脱敏，PII 泄露到 DB

**问题**：`PgTraceRecorder.recordStep`（L102-103）将 input/output 直接 `toJsonString` 入库。ChatService 在 LLM 调用后、输出护栏前 `recordStep`，**output 是 LLM 原始输出未经 PII 脱敏**；trace 表成为 PII 泄露通道。

**建议**：recordStep 写入前对 input/output 调用 `SensitiveUtil` 脱敏；或仅存脱敏摘要 + 指向原始内容的引用。

#### E2. [P1] Trace 未接 OpenTelemetry/Langfuse，封闭体系

**问题**：全模块无 OTel/Langfuse 集成（已 grep 确认），自研 Trace 无法与外部 APM/Grafana 联动，跨服务链路断裂。

**对标**：Langfuse/LangSmith 是 LLM 应用可观测事实标准，提供 prompt 版本管理、A/B 测试、评估。

**建议**：接入 OpenTelemetry（W3C TraceContext 已在网关层支持）；评估接入 Langfuse 做 LLM 专属观测。

#### E3. [P1] DebugController.listTraces N+1 查询 + 面板字段硬编码 0

**问题**：`DebugController.listTraces` 对每条链路调 `getTrace()` 取 stepCount，N 条链路 N 次查询；`ObservabilityDashboardService.totalMessages`（L68）硬编码 `0L`，面板字段无数据；`activeConversations`（L65）强转 int 有溢出风险。

**建议**：listTraces 改 JOIN 聚合一次查询；补全面板字段真实统计；溢出风险用 long。

#### E4. [P1] DAG 同步阻塞 300s + 幂等 TTL 不匹配

**问题**：`DagController.execute` 同步阻塞返回（总超时 300s），HTTP 网关层易超时；`Idempotent` ttlSeconds=5 而 DAG 总超时 300s，5s 后幂等锁释放，相同请求可重复提交，**DAG 重复执行、LLM 成本翻倍**。

**建议**：DAG 改异步（提交任务返回 executionId，轮询/SSE 推送进度）；幂等 TTL ≥ DAG 总超时。

#### E5. [P2] 输出护栏拒绝文案硬编码、限流固定窗口

**问题**：`GuardrailService.applyOutputGuardrails` 返回固定文案"抱歉，我无法回答这个问题。"，不可配置；`AgentRequestGuard` 固定窗口限流有边界突刺，`MAX_REQUESTS_PER_MINUTE=10` 硬编码，仅按 userId 无 IP/tenant 维度。

**建议**：拒绝文案配置化、区分护栏类型；限流改滑动窗口/令牌桶，支持多维度可配。

---

### 维度 5：过度设计与设计不足

#### O1. [过度设计/半成品] "语义缓存"名实不符

`SemanticCacheConfig.DEFAULT_SIMILARITY_THRESHOLD=0.95` 定义后全代码库未使用；`maxCacheSize` 声明后从未使用（死代码）。这是"半成品"——做了语义缓存的配置骨架，但核心语义匹配未实现。**建议**：要么补全语义匹配，要么删除未使用配置避免误导。

#### O2. [过度设计] DagConditionEvaluator 文档与实现不符

类注释（L21）声明支持 `<, >, <=, >=` 数值比较，但 `evaluateAtomicCondition`（L129-174）**完全没有实现**这些运算符，遇到会走"纯变量引用"分支返回错误结果。**建议**：补全实现或修正文档。

#### O3. [设计不足] 缺乏 Checkpoint/恢复、Multi-Agent 协作、流式工具

对标 LangGraph 的 checkpointer、AutoGen 的多 Agent 对话、Dify 的可视化编排，本模块在断点恢复、多 Agent 协作、流式工具、结构化数据传递上均缺失。**整体判断：本模块以"设计不足"为主，过度设计较少。**

#### O4. [设计不足] 无 DB 持久化记忆、无多模态 embedding、reranker 默认空实现

`ConversationMemory` 接口注释提到"PostgreSQL 持久化"但无实现类，Redis 宕机历史丢失；`EmbeddingClient` 仅处理文本，图片/表格未覆盖；`IdentityReranker` 仅截断不重排，`rerankerEnabled` 默认 false，无 Cross-Encoder/BGE 实现。

---

## 三、问题清单（按优先级）

### P0 — 阻断性，必须立即修复（8 项）

| # | 问题 | 位置 | 类型 |
|---|------|------|------|
| 1 | DAG 条件分支两路都执行 | `DagOrchestrationExecutor` L105-130/268-291 | 架构缺陷 |
| 2 | DAG 循环节点双重执行 + 竞态 | `DagOrchestrationExecutor` L304-343 | 架构缺陷 |
| 3 | 摘要压缩丢历史（非原子+纯内存） | `SummaryConversationMemory` L71/204-208 | 数据丢失 |
| 4 | 成本价格子串匹配误命中（16倍价差） | `CostAnalysisService.ModelPriceConfig` L265-275 | 资损 |
| 5 | Trace input/output 未脱敏入库 | `PgTraceRecorder` L102-103 | 安全/合规 |
| 6 | RAG 摄入非原子，中途失败丢索引 | `DocumentIngestionService.ingest` L65 | 数据丢失 |
| 7 | MCP requestId 用 currentTimeMillis 碰撞 | `SseMcpClientProvider.sendRequest` L113 | 并发缺陷 |
| 8 | DAG 幂等 TTL(5s) << 总超时(300s) | `DagController.execute` L101 | 成本翻倍 |

### P1 — 重要，下一迭代修复（14 项）

DAG 持久化与恢复、DAG 节点接入 AgentFactory、MCP 协议补全(stdio/resources/prompts/SDK)、ReAct 工具并发、工具参数校验与白名单、内容安全护栏、Prompt 注入增强、批量写库、全文检索 ILIKE 优化、HNSW 索引、query embedding 缓存、Trace 异步批量+OTel 集成、缓存击穿防护+成本统计、摘要异步压缩、Redis 记忆续期问题、recordUsage 异步化、DebugController N+1。

### P2 — 增强项（9 项）

流式工具调用、结构化数据传递、reranker 真实实现、多模态 embedding、DB 持久化记忆、缓存命中率指标、护栏文案配置化、滑动窗口限流、DAG 异步执行+进度推送。

---

## 四、优化路线图

### 阶段一：紧急修复（1-2 周）
- 修 P0 #1-#2：DAG CONDITION/LOOP 调度逻辑重构
- 修 P0 #3：摘要压缩原子化 + 持久化
- 修 P0 #4：成本价格匹配算法
- 修 P0 #5：Trace PII 脱敏
- 修 P0 #6：RAG 摄入事务
- 修 P0 #7-#8：MCP requestId + 幂等 TTL

### 阶段二：架构补强（3-4 周）
- DAG 持久化 + 断点恢复
- DAG 节点接入 AgentFactory，DAG 实现 AgentExecutor
- MCP 引入官方 SDK + 补全协议
- Tool Calling 并发 + 校验 + 白名单
- 缓存击穿防护 + 成本统计修正
- Trace 异步批量 + OTel 集成

### 阶段三：能力增强（5-8 周）
- 内容安全护栏 + Prompt 注入增强
- RAG：HNSW + query 缓存 + reranker 实现
- 流式工具 + 结构化数据传递
- Langfuse 集成 + 评估体系
- DB 持久化记忆 + 多模态 embedding

---

## 五、对标差距总结

| 维度 | 本模块 | LangGraph | Dify | 差距 |
|------|--------|-----------|------|------|
| 条件分支 | **失效** | conditional edges | 条件节点 | 大 |
| 循环 | **双重执行 bug** | 自带循环 | 迭代节点 | 大 |
| Checkpoint/恢复 | **无** | checkpointer | 有 | 大 |
| Multi-Agent | 单 Agent 为主 | Graph+State | 可视化 | 中 |
| MCP | 仅 SSE+tools | 社区适配 | 完整 | 中 |
| 并行工具调用 | **串行** | 并行 | 并行 | 中 |
| HITL(人工审批) | 落库+事件，较好 | interrupt | 人工节点 | 小(优势) |
| 可观测 | 自研封闭 | OTel | Langfuse | 中 |
| RAG | 基础可用 | — | 完善 | 中 |

**差异化优势**：DDD 分层规范、领域模型不可变性、虚拟线程应用、HITL 已落库多实例可恢复、多租户隔离系统覆盖。

**结论**：ydsz-agent 在架构骨架和工程规范上达到中上水平，但 **DAG 引擎的功能性 bug 和多处数据丢失/资损风险是当前最大隐患**，建议优先修复 P0 后再推进能力增强。
