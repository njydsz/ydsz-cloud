# ydsz-literule 对标分析与优化建议报告

> 基于最新代码核查（2026-08-19），对标 URule Pro / LiteFlow / QLExpress4 / Drools 及互联网大厂研发规范，从架构、功能、性能、体验、过度设计五维度输出可落地建议。

---

## 一、现状总览

### 1.1 架构与规模

| 维度 | 现状 | 评估 |
|------|------|------|
| 分层 | DDD 五层（api/domain/infra/app/web）+ server 启动模块 | 规范，符合 ydsz-system DDD 范式 |
| 源文件 | 6 子模块，~180 Java 文件（server 占 110+） | 体量适中 |
| 规则类型 | 5 类全覆盖：表达式、决策表(DMN 6 命中策略)、评分卡、决策树、脚本 | 对标竞品无短板 |
| 编排 | RuleChain 支持 THEN/WHEN/IF/ELIF/SWITCH 五语义 | 对标 LiteFlow 基本对齐 |
| 通用能力 | 编排、热更新、分布式分片、灰度、AB Test、审批、版本、回放、压测、搜索 | 矩阵完整 |

### 1.2 云顶规范合规性（零违规）

- **JSON**：全模块 `new ObjectMapper()` / Gson / FastJSON 检查 = 0 匹配；`YdszJson` 在 12 个文件 18+ 处使用。**合规**
- **Cache**：`com.github.benmanes.caffeine` 检查 = 0 匹配；多级缓存 `CachingRuleConfigProvider` 用 Redisson + 自实现 L1。**合规**
- **POI**：`org.apache.poi` 检查 = 0 匹配；`DecisionTableExcelExporter` 用自研 `ExcelFacade`。**合规**

### 1.3 核心短板已补齐情况

基于 memory 中历史短板清单与最新代码交叉核对：

| 历史短板 | 当前代码状态 | 结论 |
|----------|--------------|------|
| CEP 缺时间窗口/序列模式 | `CEPEngine` 实现 4 窗口（TUMBLING/SLIDING/SESSION/COUNT）× 4 模式（TIME_WINDOW/SEQUENCE/AGGREGATE/ABSENCE）+ Checkpoint/Restore + 队列上限保护 | **已补齐** |
| 规则流画布执行缺后端 | `GraphExecutionProvider` 仅 SPI 接口（dryRunGraph + collectInvalidReferences），由外部 project 模块实现；literule 自身无默认实现 | **仍为短板** |
| 断点非真断点 | Grep `Breakpoint/断点/stepInto/stepOver` = 0 匹配；仅有 `ExecutionReplayService`（事后回放）+ `evalBooleanWithTrace`（执行后追踪树），均为"事后追溯"而非"断点挂起" | **仍为短板** |
| 冲突检测深度不足 | `RuleConflictDetector` 实现 4 类型（IDENTICAL_CONDITION/CONTRADICTORY_SEVERITY/NAME_COLLISION/CONDITION_OVERLAP）+ 死规则 + 不可达子条件；但仅支持简单比较表达式，复杂表达式（含 && / || 嵌套）降级为不检测 | **部分补齐** |
| 多租户仅逻辑隔离 | 逻辑隔离（行级 tenant_id + MyBatis 拦截器自动 SQL 改写）为主；SCHEMA/ISOLATE_DB 物理隔离能力已具备但默认未启用 | **部分补齐** |

---

## 二、对标竞品差距矩阵

| 核心能力 | ydsz-literule | Drools | URule Pro | LiteFlow | QLExpress4 | 大厂标准 |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|
| CEP 时间窗口/序列 | 完整 | 完整 | 部分 | - | - | 完整 |
| 规则流画布执行后端 | **缺失** | 完整 | 完整 | 完整 | - | 完整 |
| 断点调试器 | **缺失** | 完整 | 完整 | - | 完整 | 完整 |
| 冲突检测深度 | 部分 | 完整(RETE) | 部分 | - | - | 完整 |
| 多租户物理隔离 | 部分(可选) | 部分 | 缺失 | - | - | 完整 |
| 热更新一致性 | 部分(双写风险) | 完整(KieScanner) | 部分 | 部分 | - | 完整(Outbox) |
| RETE α/β 网络 | 部分(倒排) | 完整 | 部分 | - | - | 完整 |
| 表达式追踪树 | 完整 | 完整 | - | - | 完整 | 完整 |
| 编排语义(THEN/WHEN/IF/SWITCH) | 完整 | - | 部分 | 完整 | - | 完整 |
| 灰度/AB Test/回滚 | 完整 | 部分 | 部分 | - | - | 完整 |

---

## 三、分维度优化建议

### 3.1 架构优化

#### A1 [P0] 引入事务性 Outbox 消除热更新双写不一致
- **现状**：`RuleAdminService.publishRefreshEvent`（L836-847）先发 Spring 事件，再 `broadcaster.broadcast`，广播失败仅 `log.warn` 不回滚。pom 已引 `ydsz-common-event`（Outbox 能力）但未在热更新链路使用。
- **对标**：Drools KieScanner 原子更新；大厂标准 Outbox 模式。
- **建议**：将 `RuleConfigRefreshEvent` 写入 Outbox 表（同事务），由 Outbox Relay 异步投递到 Redis Pub/Sub；广播失败由 Relay 重试。改 `publishRefreshEvent` 为仅写 Outbox，删除直接 `broadcaster.broadcast`。
- **落地**：`RuleAdminService` + 新增 `RuleConfigOutboxRelay`。

#### A2 [P1] 画布执行后端默认实现下沉到 literule
- **现状**：`GraphExecutionProvider` 是纯 SPI，literule 自身无默认画布执行实现，画布执行依赖外部 project 模块。
- **对标**：URule Pro / LiteFlow 均自带画布执行引擎。
- **建议**：在 `server/orchestrator` 下新增 `DefaultGraphExecutionProvider`，基于已有 `ChainGraphConverter`（Graph→RuleChain）+ `RuleChain.evaluate` 实现默认画布执行；保留 SPI 供业务方覆盖。
- **落地**：新增 `DefaultGraphExecutionProvider`，装配为 `@ConditionalOnMissingBean(GraphExecutionProvider.class)`。

#### A3 [P1] 命名修正：AviatorExpressionEngine → LiteExprEngine
- **现状**：`AviatorExpressionEngine` 实际是自研 LiteExpr（Compiler+TreeInterpreter+Sandbox），不依赖 Aviator，类名严重误导。
- **建议**：重命名为 `LiteExprEngine`，保留旧类名作 `@Deprecated` 别名 1-2 个版本。
- **落地**：重命名 + 配置项 `ydsz.literule.evaluator` 文档同步。

### 3.2 功能增强

#### F1 [P0] 实现真断点调试器
- **现状**：无任何断点代码。`ExecutionReplayService` 是事后回放，`evalBooleanWithTrace` 是执行后追踪树。
- **对标**：URule Pro / QLExpress4 均支持断点挂起、单步、变量监视、条件断点。
- **建议**：新增 `RuleDebugSession` + `Breakpoint` 模型：
  - 断点类型：规则级断点（ruleCode）、表达式节点级断点（AST 节点）、条件断点（满足表达式时挂起）
  - 执行模型：单步(stepOver/stepInto/stepOut)、继续(resume)、查看变量(inspect)、修改变量(set)
  - 实现：在 `TreeInterpreter.eval` 节点遍历时检查断点，命中则通过 `CountDownLatch`/`Phaser` 挂起工作线程，等待调试客户端指令
  - 传输：WebSocket 双向通道推送断点事件 + 接收调试指令
- **落地**：新增 `server/debug` 包，含 `DebugSession`/`Breakpoint`/`DebugController`/`BreakpointHit`。

#### F2 [P1] 冲突检测引入 SAT/SMT 求解器
- **现状**：`RuleConflictDetector` 仅对简单比较（`var OP number`）做范围重叠分析，复杂表达式（含 && / || 嵌套）降级为不检测，无跨规则执行顺序冲突检测。
- **对标**：Drools 基于 RETE 网络的冲突解析；学术界用 Z3 SAT/SMT 求解器做精确冲突分析。
- **建议**：
  - 短期：扩展 `parseComparison` 支持 AND/OR 嵌套表达式的合取范式（CNF）分解，对每个合取项做范围交集
  - 中期：引入 Z3-java binding 对复杂表达式做可满足性求解，判断两条规则是否存在同时命中的输入
  - 新增冲突类型：`EXECUTION_ORDER_CONFLICT`（同优先级规则无显式互斥组但执行顺序影响结果）
- **落地**：`RuleConflictDetector` 增强 + 可选 `Z3ConflictAnalyzer`（SPI）。

#### F3 [P1] 多租户物理隔离默认启用 SCHEMA 模式
- **现状**：`TenantProperties.mode` 默认 `SINGLE`（逻辑隔离），`SCHEMA`/`ISOLATE_DB` 物理隔离能力已具备但需手动开启。
- **对标**：大厂金融级规则引擎默认物理隔离。
- **建议**：新增配置 `ydsz.literule.tenant.physical-isolation-required=true`，在金融场景下强制 SCHEMA 模式；`LiteRuleAutoConfiguration` 启动时校验 mode 与配置一致性，不一致则启动失败（fail-fast）。
- **落地**：`LiteRuleAutoConfiguration` 增加校验逻辑。

#### F4 [P2] 热更新粒度增加 RulePack 批量级
- **现状**：`RuleConfigRefreshEvent.ChangeType` 有 CREATE/UPDATE/DELETE/TOGGLE/FULL_RELOAD，无 RulePack 维度；`RuleHotReloader.reloadSingle` 无版本号去重。
- **对标**：大厂规则包批量发布能力。
- **建议**：新增 `PACK_RELOAD` ChangeType，`RuleHotReloader` 增加 `reloadPack(packCode)` 批量重载；`reloadSingle` 增加版本号比对（`event.getVersion() vs currentVersion`）避免重复加载。
- **落地**：`RuleConfigRefreshEvent` + `RuleHotReloader` 增强。

### 3.3 性能提升

#### P1 [P1] 评估结果缓存 key 优化为指纹
- **现状**：`EvaluationResultCache.get(context)` 以整个 `RuleContext` 为 key，facts Map 直接作 key命中率低且内存占用大。
- **建议**：将 context 的 scenario+tenantId+environment+facts 计算为 MD5 指纹作为缓存 key，减少内存占用并提升命中率；缓存失效从整 namespace 清空优化为按 ruleCode 精准 evict（规则变更时仅清该规则相关的缓存条目）。
- **落地**：`EvaluationResultCache` + `CacheKeyBuilder` 增强。

#### P2 [P2] 引入轻量 RETE α 网络
- **现状**：`RuleIndexer` 是 5 维倒排索引（tenant/env/scope/mutex/field），对标 Drools RETE α/β 网络仍差距大；字段提取用正则，无表达式预编译。
- **对标**：Drools RETE α 网络（单字段条件节点共享）+ β 网络（多字段 join）。
- **建议**：
  - 短期：对高频字段（如 amount、score）构建 α 节点共享池，同字段的多个比较条件挂载到同一 α 节点
  - 中期：表达式预编译为 `Predicate<RuleContext>` 函数式接口缓存（对标 Aviator ASM 编译），避免每次 AST 遍历
- **落地**：`RuleIndexer` 增强 `alphaNodeIndex` + `LiteExprCompiler` 增加预编译缓存。

#### P3 [P2] CEP 高吞吐场景事件入队异步化
- **现状**：`CEPEngine.feed` 同步遍历所有 pattern，每个 pattern 同步入队 + 评估，高吞吐（万级 TPS）下成为瓶颈。
- **建议**：`feed` 改为写入 `Disruptor`/`ArrayBlockingQueue`，由独立消费者线程异步处理；模式间并行评估（无 partition 依赖的模式可独立处理）。
- **落地**：`CEPEngine` 引入事件总线 + 多消费者。

### 3.4 体验改善

#### E1 [P1] 规则测试用例覆盖率提升
- **现状**：仅有 `DefaultRuleEngineCoreTest`/`ParallelTest`/`CacheTest`/`CircuitBreakerTest`/`IndexerTest`/`TimeoutTest` + `AviatorExpressionEngineTest`/`LiteExprSandboxTest` + `RuleChainSemanticsTest`。**CEP、冲突检测、回放、审批、权限等核心模块无单测覆盖**。
- **对标**：大厂核心模块单测覆盖率 ≥80%。
- **建议**：补齐 `CEPEngineTest`（4 窗口×4 模式全覆盖）、`RuleConflictDetectorTest`（6 冲突类型）、`ExecutionReplayServiceTest`（3 回放模式）、`RulePermissionCheckerTest`（Ant 通配符）、`RuleApprovalServiceTest`（审批流）。
- **落地**：`server/src/test` 下补齐测试类。

#### E2 [P2] 规则 DSL 可视化调试增强
- **现状**：`RuleDslController` 有 DSL 导入导出，`RuleTraceController` 有轨迹查询，但调试链路分散。
- **建议**：整合 `traceExpression`（表达式追踪树）+ `dryRun`（仿真）+ 回放为一站式调试入口，前端可"选规则→选历史 trace→单步回放→查看每步变量快照"。
- **落地**：`RuleDebugController` 整合 API。

#### E3 [P2] 慢规则与热点规则监控看板
- **现状**：`RuleMetrics`（Micrometer）已有慢规则告警（`recordSlowRule`），但无可视化看板。
- **建议**：`RuleDashboardController` 增加"慢规则 Top10"+"热点规则 Top10"+"规则 P99 耗时分布"看板，对标大厂规则运营平台。
- **落地**：`RuleDashboardController` + 前端看板。

### 3.5 过度设计收敛

#### O1 [P1] 清理 server 下空目录与未装配 SPI
- **现状**：
  - `server/json`、`server/listener`、`server/metrics` 三目录 100% 空（遗留脚手架）
  - `RuleSource` 5 实现中 `NacosRuleSource`/`ApolloRuleSource`/`ZookeeperRuleSource` 3 个在 `LiteRuleAutoConfiguration` 中无 @Bean 装配（纯预留死代码）
  - 12 个 SPI 接口（ThresholdProvider/ABTestAutoRollbackProvider/BudgetSnapshotProvider/DashboardDataProvider/ReconcileDataProvider/GraphExecutionProvider/RulePackProvider/RuleTemplateProvider/RuleDependencyProvider/RuleCategoryProvider/RuleConflictDetectorProvider/RuleChainGraphProvider）在 AutoConfiguration 中无 @Bean
- **建议**：
  - 删除 3 个空目录
  - 未装配的 3 个 RuleSource 实现标注 `@ConditionalOnProperty` 明确启用条件，或在文档中标注"预留扩展点，未默认装配"
  - 12 个预留 SPI 接口在模块 README 中建立"扩展点状态表"，标注每个 SPI 的装配状态、预期实现方、是否已有业务方接入
- **落地**：删除空目录 + SPI 状态文档。

#### O2 [P2] LiteExprSandbox 沙箱规则外置化
- **现状**：`LiteExprSandbox` 是 AST 级沙箱，黑名单/白名单规则硬编码。
- **对标**：Aviator 沙箱支持配置化策略。
- **建议**：沙箱规则（禁用类、禁用方法、禁用包、最大循环次数、最大递归深度）外置为 YAML 配置，支持热更新。
- **落地**：`LiteExprSandbox` + `SandboxProperties`。

---

## 四、可落地待办清单（阶段化）

按 P0→P1→P2 优先级与依赖关系，分 4 个阶段推进：

### S1（P0，2-3 周）：一致性 + 断点调试
- [ ] A1 引入 Outbox 消除热更新双写不一致
- [ ] F1 实现真断点调试器（规则级 + 表达式节点级）
- [ ] O1 清理 3 个空目录

### S2（P1，3-4 周）：画布后端 + 冲突检测 + 测试补齐
- [ ] A2 画布执行后端 DefaultGraphExecutionProvider 下沉
- [ ] A3 AviatorExpressionEngine 重命名为 LiteExprEngine
- [ ] F2 冲突检测引入 CNF 分解（短期）+ Z3 SPI 预留（中期）
- [ ] F3 多租户物理隔离强制校验
- [ ] P1 评估结果缓存 key 指纹化 + 精准 evict
- [ ] E1 补齐 CEP/冲突检测/回放/审批/权限单测

### S3（P2，2-3 周）：性能 + 体验 + 收敛
- [ ] F4 热更新 RulePack 批量级 + 版本号去重
- [ ] P2 引入轻量 RETE α 网络 + 表达式预编译
- [ ] P3 CEP 高吞吐异步化
- [ ] E2 一站式调试入口整合
- [ ] E3 慢规则/热点规则看板
- [ ] O2 LiteExprSandbox 规则外置化
- [ ] O1 12 个预留 SPI 状态文档化

### S4（持续）：文档与对标同步
- [ ] 更新 ydsz-literule 模块文档（消除 doc drift）
- [ ] 持续对标 URule Pro / LiteFlow / QLExpress4 / Drools 新版本能力
- [ ] 跟踪 S1-S3 完成状态，形成常态化迭代闭环

---

## 五、关键代码事实索引

| 结论 | 文件 | 行号 |
|------|------|------|
| 热更新双写不一致 | `RuleAdminService.java` | L836-847 |
| 画布执行仅 SPI | `GraphExecutionProvider.java` | L17-35 |
| 断点代码缺失 | Grep `Breakpoint/断点` | 0 匹配 |
| 冲突检测仅简单表达式 | `RuleConflictDetector.java` | L397-422 |
| 多租户默认逻辑隔离 | `TenantProperties.java` | L90 (mode=SINGLE) |
| Outbox 依赖已引但未用 | `ydsz-literule-server/pom.xml` | L57-61 |
| CEP 已补齐 4 窗口 4 模式 | `CEPEngine.java` | L222-465 |
| 表达式引擎类名误导 | `AviatorExpressionEngine.java` | L40 (实为自研 LiteExpr) |
| 缓存整 namespace 清空 | `CachingRuleConfigProvider.java` | L357-360 |
| RuleIndexer 5 维倒排 | `RuleIndexer.java` | L60-83 |
| 3 个空目录 | `server/json` `server/listener` `server/metrics` | 空目录 |
| 3 个未装配 RuleSource | `NacosRuleSource` `ApolloRuleSource` `ZookeeperRuleSource` | AutoConfiguration 无 @Bean |

---

*本报告基于 2026-08-19 最新代码核查，所有结论均有文件路径与行号支撑。建议按 S1→S4 阶段化推进，每阶段完成后回归验证。*
