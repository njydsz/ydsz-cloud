# ydsz-agent 对标 AgentScope Java 2.0 借鉴分析

> 日期：2026-09-14
> 分析对象：`ydsz-agent`（v26.09.x 工作树）vs AgentScope Java 2.0（v2.0.0 GA 2026-07-10 / 当前 2.0.1）
> 事实来源：AgentScope 官方 Release Notes（java.agentscope.io）+ 本仓库源码逐文件核验
>
> ⚠️ **存放位置说明（已解决）**：本报告在 `docs/` 下曾反复被外部清理进程删除（本日 3 次，且**已 `git add` 入索引仍被删**——
> 仅 `HEAD` 中的已提交内容免疫）。现已**正式 commit 入 `docs/`**，并在 `.workbuddy/deliverables/`（`.gitignore:71` 忽略，免疫清扫）留备份。
>
> 另附一条本日发现：排查期间 `docs/` 下 **24 个已跟踪文件曾整体被删**（含 `云顶编码规范.md` / `云顶版本规范.md` / `云顶接口清单.md`），
> 系未提交的工作区误删，已用 `git restore --source=HEAD --worktree -- docs/` 全量恢复，无内容损失。

---

## 0. 评判框架声明

依据《云顶编码规范》§33.7（commit c001a18c5）：基础能力模块不以业务引用数论价值；**零引用的能力储备判定为「裸奔资产」，处置方式为接线 + 补测试 + 生命周期标注，而非删除**。本报告为对标借鉴分析，非过度设计审查；所有差距结论均以源码事实为据（文件路径可复查），拒绝纸面推断。

结论分级：

- **P0**：已有资产收口 / 生产体验阻塞，应立即处理
- **P1**：可靠性提升，近期（1-2 个迭代）落地
- **P2**：按需储备，**明确不提前建设**（防过度设计）

---

## 1. AgentScope Java 2.0 核心设计速览

阿里通义实验室出品，2025-09 v0.1 → 2026-07 v2.0.0 GA（JDK 17 起步）。2.0 的主题是「让 Agent 在企业环境中可靠运行」，核心机制：

| # | 机制 | 要点 |
|---|---|---|
| 1 | **双层 Agent 架构** | ReActAgent = 无状态推理核（per-call 状态经 Reactor Context 传播，单实例安全并发服务多个 (userId, sessionId)）；HarnessAgent = 经 Middleware + Toolkit 两通道叠加 workspace / memory / sandbox / subagents / skills / plan mode，核心推理循环保持不变 |
| 2 | **统一消息 + 事件流** | ContentBlock 消息模型（Text / Data / ToolUse / ToolResult / HintBlock）+ `streamEvents()` 28 种 typed AgentEvent；事件携带 `source` 路径（如 `main/researcher`）供前端对子代理事件解复用；ToolResult 错误结构化 |
| 3 | **中间件五阶段** | onAgent / onReasoning / **onActing** / onModelCall / onSystemPrompt；`order()` 控制洋葱层级 |
| 4 | **Toolkit 默认并行** | 2.0.1 起工具执行默认并行 |
| 5 | **Workspace/Sandbox 抽象** | Local / Docker / K8s / E2B 统一接口 + 预热池；实现拆独立扩展模块，核心只留抽象 |
| 6 | **声明式子代理 + 管控继承** | YAML/Markdown 声明，`agent_spawn`/`agent_send`；子代理**强制继承父级 DENY 规则与 Plan Mode**；静态注册表按 runtime context 隔离防多租户串扰 |
| 7 | **Skills 闭环** | Classpath/FileSystem/Nacos/Marketplace 四层来源 + propose → curate → promote 自学习闭环 + SkillFilter |
| 8 | **DistributedBackend 统一门面** | 一行配置接入全部分布式组件；AgentStateStore 按 (userId, sessionId) 分区，跨副本会话恢复；Redis/MySQL/PG/OSS 后端 |
| 9 | **生产细节** | close() 解绑 state-saver 防 OOM；SSE 绝对超时截断修复；HITL 用 RequireUserConfirmEvent + UserConfirmResultEvent(replyId) 事件对（非轮询） |

---

## 2. ydsz-agent 现状核验（代码事实，改造前基线）

| 能力 | 现状 | 证据 |
|---|---|---|
| 中间件链 | **已落地**：优先级排序 + onModelCall 洋葱模型，6 钩子（onAgentStart/onSystemPrompt/onReasoning/onModelCall/onObservation/onAgentEnd），4 个中间件（Input/OutputGuardrail、AgentMetrics、AgentTrace），AgentFactory 对全部执行器注入 | `server/middleware/MiddlewareChainImpl.java`、`web/config/MiddlewareAutoConfiguration.java` |
| AgentHarness | ⚠️ **零调用方**：26.09.13 新增，注释自称「对标 AgentScope HarnessAgent」，但无任何执行器/配置引用，未注册 Bean，**连 execute() 方法都没有**——纯配置持有器 | `server/harness/AgentHarness.java`；全模块 grep 仅命中自身文件 |
| 执行器无状态 | ✅ 全部依赖 final 注入，无可变实例字段 | `server/agent/AbstractAgentExecutor.java` |
| 并行工具调用 | ✅ 已实现 | `ReActAgentExecutor#executeToolsConcurrently` |
| 沙箱 | ✅ 雏形：Local + Docker 代码执行两实现 | `infra/code/DockerSandboxCodeExecutionService.java`、`LocalSandboxCodeExecutionService.java` |
| 工作区 | ✅ 抽象 + Redis 实现 | `domain/workspace/AgentWorkspaceStore.java`、`infra/workspace/RedisAgentWorkspaceStore.java` |
| SSE 事件协议 | ⚠️ 仅 7 种，**无 source 字段**，无 HITL 确认事件、无 result / progress 通用扩展事件 | `domain/model/SseEvent.java` |
| 人工审批（HITL） | ⚠️ 轮询拉取式，且 `requestApproval` **全仓零调用方** | `server/agent/HumanApprovalService.java`、`HumanApprovalController` |
| 分布式状态 | ❌ 无 AgentStateStore / DistributedBackend 等价物；Redis 状态三处分散，无跨副本会话恢复语义 | 全模块 grep `Distributed\|AgentStateStore` 仅命中无关类 |
| 子代理管控 | ⚠️ Supervisor 委派存在，但子请求不设工具白名单 = **子 Agent 工具面宽于父级** | `server/agent/SupervisorAgentExecutor.java` |
| Skills | ⚠️ 仅记录侧（SkillLesson / SkillLessonRecorder），无闭环与 SkillFilter | `domain/skill/`、`server/skill/` |
| 团队多代理 | ✅ TeamRun 编排（本地特色，AgentScope 无直接对应物） | `server/teamrun/TeamRunOrchestrationService.java` |

---

## 3. 差距矩阵

| AgentScope 2.0 机制 | ydsz-agent | 判定 |
|---|---|---|
| 无状态 ReActAgent + Context 传播 | 执行器全 final 依赖注入 | **已对齐** |
| Toolkit 并行执行 | executeToolsConcurrently | **已对齐** |
| 中间件五阶段（含 onActing） | 6 钩子，缺工具执行阶段独立钩子 | 部分 → **已补齐** |
| Harness 分层 | AgentHarness 零接线裸奔资产 | 部分 → **已接线** |
| 28 种 typed event + source | 7 种事件、无 source | 缺失 → **已扩展** |
| HITL 事件对（replyId 关联） | 轮询式审批 | 缺失 → **已事件化** |
| 子代理管控继承（DENY/Plan Mode） | 无 | 缺失 → **已实现工具面继承** |
| DistributedBackend / 状态分区 | 三处 Redis 分散 | 缺失 → **已统一门面** |
| Workspace/Sandbox 抽象 | 已有雏形 | 部分 |
| Skills 闭环 | 仅记录侧 | 缺失（P2 储备） |
| 消息模型 ContentBlock | MessageContent/ContentPart | 大体对齐 |

---

## 4. 借鉴建议（原始清单）

### P0 — 资产收口与生产体验（立即）

**P0-1 AgentHarness 接线或降级。** 三条路按序选：① **接线**（推荐）② 最低限度收口（补 `execute()` + 测试 + `@status` 标注）③ 移除。依据 §33.7 不推荐静默保留现状。

**P0-2 SSE 事件协议扩展 + source 字段。** 新增 `approval_required` / `tool_call_delta` / `progress` / `result`；全部事件加 `source`。**TeamRun 多代理并行输出共用一条 SSE 流时，前端没有 source 无法区分消息归属**——真实缺陷。兼容策略：不可变值对象 + 工厂方法，source 放入 data 载荷，无协议破坏。

**P0-3 HITL 事件化。** 借鉴 `RequireUserConfirmEvent` + `UserConfirmResultEvent(replyId)`：审批请求直接作为 `approval_required` 推入当前流，前端展示卡片、以 replyId 回填。轮询端点保留作补偿。

### P1 — 可靠性提升

**P1-1 中间件补 onActing 钩子。** 工具级限流 / 审计 / 结果驱逐可插件化。注意 AgentScope 教训：**结果必须先持久化再驱逐**。

**P1-2 分布式状态统一门面。** 收敛为按 `(tenantId, userId, conversationId)` 分区的 `AgentStateStore`。**前置条件**：单实例部署收益有限，与多副本计划绑定排期。

**P1-3 子代理安全继承。** 委派子 Agent 时强制继承父级工具白名单与管控策略。

### P2 — 按需储备（明确不提前建设）

- **P2-1 Skills 闭环**：等出现真实跨会话复用需求再建。
- **P2-2 沙箱后端抽象收敛**：K8s / E2B 云沙箱 **不做**。
- **P2-3 AG-UI 协议对齐**：纯内部前端无此诉求。

---

## 5. 不建议照搬清单（防过度设计）

| AgentScope 能力 | 不照搬理由 |
|---|---|
| GraalVM native image | 启动速度非本系统瓶颈 |
| K8s sandbox CRDs/controllers + 预热池 | RL rollout / 大规模并行场景，当前规模用不上 |
| Marketplace / Nacos 四层技能来源 | 单组织内部系统无市场分发诉求 |
| PostgreSQL/MySQL/Redis/OSS 全后端矩阵 | 已有明确技术选型（Redis + PG），一种后端足够 |
| Reactor Context 全响应式传播 | Servlet + SseEmitter 栈；执行器无状态已达成同等目标 |

**核心原则**：借鉴其**分层思想**（推理核极简、工程能力经通道叠加）与**事件协议设计**（typed event + source + replyId），而非复制其分布式全家桶。

---

## 6. 实施进展（2026-09-14 落地，P0 + P1 全部完成）

验收方式：Corretto JDK21 + njydsz-maven 编译 4 个 agent 模块（175 / 70 / 69 / 19 源文件）
→ **BUILD SUCCESS**；`mvn -fae … checkstyle:check` → **4 模块 SUCCESS / 0 violations**。
改动已由工作区自动提交流程入库（对应 commit：`85f1ef782c` / `774768df99` / `b09bf2c5bf` / `77ca3a337e` / `c25eedd4c1`），
`git status ydsz-agent/` 为空可复核。

### 6.1 P0-1 AgentHarness 收口 ✅（路线 1：接线）

| 改动 | 文件 | 说明 |
|---|---|---|
| 新增 `execute(request, executor)` / `executeStream(request, executor, …)` | `server/harness/AgentHarness.java` | 三层能力：上下文预算守门 → 溢出自动重试 → 工作区生命周期 |
| 上下文预算守门 | 同上 | 执行前 `loadWithTokenBudget` + 压缩，经 `withContextMessages` 注入请求 |
| 溢出重试 | 同上 | 捕获 `ContextOverflowException`，预算折半重试（`maxRetryOnOverflow` 次） |
| 请求承载预置上下文 | `domain/agent/AgentExecutionRequest.java` | 新增 `contextMessages` + `withContextMessages()` + Builder 字段 |
| 执行器消费预置上下文 | `AbstractAgentExecutor#loadHistory()` | 6 处 `memory.load()` 统一改走该 helper（ReAct×2 / Simple×2 / Rag×2） |
| 注册 Bean | `web/config/AgentAutoConfiguration#agentHarness` | 可选依赖用 `ObjectProvider` 注入，缺件自动降级 |
| 接入执行链 | `AgentFacadeImpl` | 抽出 `resolveExecutor()`，`execute` / `executeStream` 统一经 Harness |

移除 Harness 的 `middlewareChain` 字段——中间件由执行器通过 `AbstractAgentExecutor` 驱动，
Harness 重复持有会导致 onAgent 钩子二次触发。

**顺带修复的真实缺陷**：`ContextCompressor.compress(messages, maxSize)` 第二个参数语义分裂——
`ContextOverflowHandler` 传 **Token 预算**，`SlidingWindowCompressor` 却当 **消息条数** 比较
（预算 12000 ≫ 条数 → 永远短路），整条压缩链路实为**静默空转**。现统一口径为 Token 预算：
契约写入接口文档、`DEFAULT_TOKEN_CHAR_RATIO` 收敛到 `ContextCompressor`（domain 单点定义）、
滑动窗口改为「从最新消息向前累加至预算耗尽」并保留 System 消息 + 至少 1 条最近对话。

### 6.2 P0-2 SSE 事件协议扩展 + source ✅

- `SseEvent` 事件类型 **7 → 12**：新增 `tool_call_delta` / `approval_required` / `approval_resolved` / `progress` / `result`；
- 新增 `source` 字段 + `withSource()` / `hasSource()` / `toPayload()`（source 放入 data 载荷，无协议破坏）；
- `ChatChunk` 增加 `source` + `SOURCE_MAIN` + `withSource()`；
- `AgentExecutor` 增加四参 `executeStream`（带 `Consumer<SseEvent>`），默认实现委托三参 → **既有实现零改动**；
- ReAct 流式路径实际发射 `tool_call_started` / `tool_call_completed` / `reasoning` / `result`，均携带 `source=main`；
- **Sub-agent source 落地**：Supervisor 转发子代理片段时打标 `source=supervisor/{子任务号}`
  （`ChatChunk.withSource`），即差距矩阵中「前端可按 source 区分」的验收点；
- 新增 `AgentFacade.executeStream` 四参默认方法 + `AgentFacadeImpl` 全参透传 +
  `AgentController#sendSseEvent` 将事件转为独立 SSE 事件帧（`event:` = 事件类型，`data:` = 载荷）。

### 6.3 P0-3 HITL 事件化 ✅

- `HumanApprovalService` 三个重载接收 `Consumer<SseEvent>`：`requestApproval` 推 `approval_required`
  （载荷含 `replyId`）、`approve` / `reject` 推 `approval_resolved`；推送失败仅告警，不影响主流程；
- `MiddlewareContext` 增加 `eventConsumer` / `emitEvent()` / `canEmitEvent()`，使中间件具备推流能力；
- 新增 `ToolApprovalMiddleware`（`onActing`，优先级 45）：命中 `ydsz.agent.approval.sensitive-tools` 的
  工具调用 → 登记审批 + 推事件 + 回填结构化「待审批」结果，推理循环**不阻塞**；
- **探测结论（重要）**：改造前 `requestApproval` **全仓零调用方**——审批服务只是独立 REST 面，
  Agent 执行从不触发审批。故本中间件即其真实接入点；**默认名单为空 = 零行为变更**；
- 轮询端点 `/approvals/pending` 保留为补偿路径（流断开 / 页面刷新后补捞）。

### 6.4 P1-1 中间件 onActing 钩子 ✅

- `AgentMiddleware` 新增 `onActing(context, ActingProceed)` + `ActingProceed` 函数式接口 +
  `TOOL_AUDIT_PRIORITY(45)` / `TOOL_EVICTION_PRIORITY(55)`；
- `MiddlewareContext` 增加 `toolCalls` / `toolResults` 读写；
- `MiddlewareChain` / `MiddlewareChainImpl` 新增 `executeActing` 洋葱递归器（`ActingOnionInvoker`），与 `onModelCall` 同构；
- `AbstractAgentExecutor#executeActing` 作为统一入口；ReAct 同步 / 流式两条路径的工具批次均改走该入口；
- 采纳 AgentScope 教训：工具结果在 `executeToolsConcurrently` 内**先落 TraceRecorder 再返回**，
  中间件驱逐只作用于结果视图，不丢证据链。

### 6.5 P1-2 AgentStateStore 统一状态门面 ✅

- 新增 `AgentStateKey`（`ydsz:agent:{ns}:{tenant}:{user}:{conv}[:{suffix}]` 四段分区 +
  段内容清洗 / 截断防键注入）；
- 新增 `AgentStateStore` 门面（put / get / exists / remove / keysOfPartition / keysOfNamespace / getBackendType）；
- 新增 `RedisAgentStateStore` 实现；`RedisDagCheckpointStore` 键格式收敛到统一分区；
- **新增 `RedisRuntimeSessionStore`**（`ydsz.agent.runtime.backend=redis`）：多副本会话可见 + 重启可恢复
  （原仅内存实现），并维护 `executionId → (tenant, user)` 索引；
  `InMemoryRuntimeSessionStore` 加 `matchIfMissing=true` 保持单实例零依赖启动；
- `RedisAgentWorkspaceStore` 由 TODO 占位改为真实实现（TTL 7 天）；
- 装配说明：`RedisAgentStateStore` **不使用** `@ConditionalOnBean`——该注解作用于被扫描的 `@Component` 时
  依赖自动配置注册顺序，存在被误判跳过的风险（Redis 已是模块必需依赖）。

> **前置条件仍成立**：当前 9008 单实例部署，本项收益在多副本（滚动发布 / 水平扩展）时兑现。
> 代码已就绪且默认零行为变更，可与多副本排期绑定启用。

### 6.6 P1-3 子代理安全继承 ✅

- `AgentExecutionRequest#deriveForSubAgent(subUserInput, subConversationId, childTools)`：
  工具白名单取 **父级 ∩ 子级**（父级为空 = 不限制，子级为空 = 继承父级），
  迭代上限 / 系统提示词 / 预置上下文一并继承；
- `SupervisorAgentExecutor` 两处子请求构造（`executeSubTask` / `executeSubTaskStream`）改走该派生方法；
- `createWorker` 的 `AgentDefinition.toolNames` 由 `List.of()`（= 不限制）改为 `request.getEnabledTools()`，
  消除「**子 Agent 工具面反而宽于父级**」这一真实越权路径（原实现子请求不设白名单 = 放开全部工具）。

> **关于 guardrailPolicy 继承**：核验后**未引入** `guardrailsEnabled` 字段——本项目护栏为全局配置
> （`ydsz.agent.guardrail.*`），不存在「父级按请求收紧的护栏策略」可供继承。为不存在的策略面新增字段
> 属发明能力（违反 §33.7 反过度设计），故仅落地真实存在的工具面继承。
> 若未来引入请求级护栏策略，`deriveForSubAgent` 即为承载继承语义的既定扩展点。

### 6.7 遗留与后续（未在本轮实施）

| 项 | 状态 | 说明 |
|---|---|---|
| 审批通过后「恢复被暂停执行」 | 未实施 | 需会话级暂停 / 恢复（Session resume）能力，属独立特性；当前为「不阻塞 + 登记审批 + 重新发起」语义，已在中间件类注释显式声明 |
| ToolResultEviction 中间件 | 未实施 | `onActing` 钩子已就绪（含优先级常量），无真实驱逐策略需求，不提前建设 |
| 多副本部署 | 未实施 | P1-2 代码已就绪，待部署形态变更时启用 |
| DTO 丢失 agentCode | **已修复** | `AgentController#toExecutionRequest` 原未映射 `agentCode`，导致 HTTP 入口按编码路由**从未生效**；已补映射 |
| 单元测试 | 未补 | 本轮为接线与协议改造，测试待补（`AbstractAgentExecutor#loadHistory`、`deriveForSubAgent` 交集语义、`SlidingWindowCompressor` 预算语义为优先覆盖点） |
