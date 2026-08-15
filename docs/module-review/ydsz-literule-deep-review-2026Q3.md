# ydsz-literule 规则引擎全面对标分析报告（2026 Q3）

> 分析基准：最新代码（2026-08-15，297 个 Java 类 / 约 21K 行）
> 对标对象：Drools / LiteFlow / URule Pro / QLExpress4 / DMN 1.4 标准 / 互联网大厂（阿里 Java 手册、银行风控、美团 Leaf 等）研发规范
> 关联文档：`README.md`（模块说明）、`docs/云顶编码规范.md`

---

## 0. 报告定位

本报告基于 ydsz-literule 模块**最新全量代码**扫描与精读，从五个维度输出可落地建议：

1. **架构优化** — 分层、包结构、SPI 抽象、自动装配的健壮性
2. **功能增强** — 对标 Drools / LiteFlow / DMN 1.4 的能力缺口
3. **性能提升** — 热路径（表达式求值、决策表、脚本、CEP、缓存）的优化点
4. **体验改善** — 可观测性、可运维性、文档与配置的易用性
5. **过度设计** — 需要瘦身收窄的"备而不用"能力

**总体结论**：该模块能力密度已显著高于多数开源规则引擎（LiteExpr 自研 AST 引擎 + 常量折叠 + 短路求值、倒排索引、多级缓存、热加载、灰度、A/B、回放、审批、熔断、超时、分布式分片均已落地），但存在 **5 个 P0 级正确性/可靠性缺陷** 与 **1 个 P0 级工程债（零测试）**，建议优先收口后再继续堆叠功能。

---

## 1. 现状能力盘点（对标矩阵）

| 能力 | ydsz-literule 现状 | Drools | LiteFlow | URule Pro | 结论 |
|---|---|---|---|---|---|
| 表达式引擎 | ✅ 自研 LiteExpr（AST+沙箱+常量折叠） | MVEL | 无（仅编排） | QL 类 | 持平/领先 |
| 规则类型 | ✅ 7+CEP | 规则/决策表/树 | 组件链 | 全套 | 基本对齐 |
| 决策表命中策略 | ⚠️ 6 种（缺 OUTPUT_ORDER/聚合） | 完整 DMN | — | 完整 | 有差距 |
| 规则链编排 | ✅ 10 语义（DSL+DAG 画布） | RuleFlow | EL 表达式 | 画布 | 对齐 |
| **画布执行后端** | ❌ 仅 SPI，无默认实现 | ✅ | ✅ | ✅ | **短板** |
| CEP 复杂事件 | ⚠️ 自研窗口，无持久化 | Fusion(已移除) | — | — | 有但浅 |
| 热加载 | ✅ 多源 DB/Nacos/Apollo/ZK/Redis/File | KieScanner | ✅ | ✅ | 领先 |
| 灰度/A-B | ✅ | — | — | 部分 | 领先 |
| 断点调试 | ⚠️ 规则级挂起，非真断点 | — | — | — | 有但浅 |
| 冲突检测 | ⚠️ 仅简单比较式 | 有（静态分析） | — | — | 深度不足 |
| 多租户 | ⚠️ 仅逻辑隔离（tenantId 过滤） | — | — | 物理隔离 | 差距 |
| 单元测试 | ❌ 核心零测试（仅 calc 2 类） | 高覆盖 | 高覆盖 | 高覆盖 | **差距** |

---

## 2. 架构优化

### 2.1 🔴 P0：LiteRuleAutoConfiguration 上帝类

**证据**：`config/LiteRuleAutoConfiguration.java` 共 **1399 行、37 个 `@Bean`**，承担了引擎、缓存、热加载、CEP、分布式、审批、A/B、回放、指标等全部装配职责。

**问题**：单类认知负荷极高，任何配置改动都需触碰此文件，违背单一职责；无法按场景（单机/分布式/启用 CEP）选择性装配。

**建议**：按能力域拆分多个 `@AutoConfiguration`（`CoreEngineAutoConfiguration` / `CacheAutoConfiguration` / `CepAutoConfiguration` / `DistributedAutoConfiguration` / `ApprovalAutoConfiguration`），通过 `@ConditionalOnProperty` 按需装配；`LiteRuleProperties`（719 行）同步按子域拆分为 `@ConfigurationProperties(prefix="ydsz.literule.xxx")` 的多个配置类。

### 2.2 🔴 P0：expr 子包物理目录与 package 声明不一致

**证据**：`server/expr/` 目录下，14 个类声明 `package ...server.expr.liteexpr`，5 个类（`EmptyVariableRegistry`/`ExpressionValidationService`/`VariableRegistry` 等）声明 `package ...server.expr`，**物理同目录**。

**问题**：违反 Java"目录=包名"约定；IDE 导航/重构/模块化（JPMS）会出错；`CEPEngine` 通过 `Class.forName("...expr.liteexpr.LiteExprEvaluator")` 反射加载，包名不一致是潜在运行时隐患。

**建议**：统一到单一包（建议 `...server.expr`），删除 `liteexpr` 子包概念，或把 `expr.liteexpr` 类真实下沉到 `expr/liteexpr/` 目录；消除反射依赖，改为构造器注入 `ExpressionEvaluator`。

### 2.3 🟡 P1：统计/指标体系多套并存、职责重叠

**证据**：`DefaultRuleEngine` 内置 `AtomicLong` 计数器 + `perRuleStats`（`RuleEngineStats`），同时存在 `RuleMetrics` 接口及 `InMemoryRuleMetrics`/`MicrometerRuleMetrics` 两套实现，另有 `metrics/LiteruleMetricsHolder`。

**问题**：同一"评估次数/触发次数/耗时"被三处独立记录，口径易漂移，维护成本高。

**建议**：收敛为单一指标门面（`RuleMetrics` 为唯一出口，Micrometer 作为其实现），引擎内统计改为委托指标门面，删除 `LiteruleMetricsHolder` 冗余。

### 2.4 🟡 P1：配置源/Provider 抽象层级偏多

**证据**：`RuleConfigProvider` / `DecisionTableConfigProvider` / `ScorecardConfigProvider` / `DecisionTreeConfigProvider` / `ScriptConfigProvider` 五套平行 SPI，全部默认实现在本服务 server 层内，无外部消费方替换场景；上层又有 `RuleSourceManager`（多源路由）+ `CachingRuleConfigProvider`（装饰）+ `RuleHotReloader`（装配）三层叠加。

**问题**：作为"独立微服务、内部自实现全部 Provider"，这些 SPI 的"反转依赖"价值很低，链路偏长、调试成本高。

**建议**：保留 `RuleConfigProvider`（真正可替换）一个 SPI；其余 4 个 Provider 降为 server 内普通 Service 接口；若确需跨服务，走 Feign 而非 SPI。

---

## 3. 功能增强

### 3.1 🔴 P0：规则流画布执行后端仍缺失（已确认开放）

**证据**：`spi/GraphExecutionProvider.java` 与 `spi/RuleChainGraphProvider.java` 均为接口，注释明确"由消费方（如 project 模块）提供实现"；全仓 grep 无 `implements GraphExecutionProvider` 实现类；`RuleGraphController` 仅提供画布 CRUD/校验/转换预览，**无 execute 端点**。`ChainGraphConverter`（785 行）只做了 graph→RuleChain 的转换，未接入引擎执行链路。

**问题**：前端 `designer.vue` 画布编排的规则流，保存后无法被引擎真正执行——"画布"与"执行"断链。这是对标 LiteFlow（`chain` 直接可执行）/ Drools（RuleFlow）的核心能力缺口。

**建议**（S1 优先）：
1. 提供 `DefaultGraphExecutionProvider`：`loadGraph(ruleCode)` → `ChainGraphConverter.toRuleChain()` → 注册为 `ChainAsRule` → `engine.evaluate()`。
2. 在 `RuleGraphController` 暴露 `POST /ruleEngine/graph/{code}/execute`（dry-run + 正式）端点，闭环"画布→执行→Trace 回放"。

### 3.2 🔴 P0：ChainAsRule 两个正确性缺陷

**证据**：
- `ChainAsRule.evaluate()` 第 55 行 `chain.evaluate(context, null)` 传入 `null` 求值器，注释自认"仅 IF/ELIF/SWITCH 需要 evaluator，此处使用 null 降级"——嵌套子链一旦含条件/分支节点即失效或 NPE。
- `ChainAsRule.getCode()` 第 32-34 行返回 `"CHAIN_" + chain.getChainType().name()`，同类型多条链 code 冲突，后注册覆盖先注册。

**建议**：`ChainAsRule` 构造注入 `ExpressionEvaluator`；`getCode()` 返回链的唯一标识（`"CHAIN_" + chainId/name`）而非类型名。

### 3.3 🟡 P1：DMN 命中策略不完整

**证据**：`api/HitPolicy.java` 仅 6 种（UNIQUE/FIRST/PRIORITY/COLLECT/ANY/RULE_ORDER）。

**缺口**（对标 DMN 1.4）：
- 缺 **OUTPUT_ORDER**（输出顺序命中）。
- **COLLECT 不支持输出聚合函数**（SUM/MIN/MAX/COUNT/AVG）——`DecisionTableRule` 的 COLLECT 仅把多行结果塞进 `collectedResults`，无聚合语义。

**建议**：补齐 `OUTPUT_ORDER` 枚举；为 `DecisionTableDefinition` 增加 `collectAggregator` 字段并在 COLLECT 策略下实现聚合（可复用 `cep/aggregate` 的聚合逻辑）。

### 3.4 🟡 P1：断点调试是"规则级挂起"，非真断点（已知短板确认）

**证据**：`DefaultBreakpointHook` 通过 `CountDownLatch` 在规则评估**前/后**阻塞引擎线程，只能"暂停/单步跳过整条规则"，无法进入表达式内部、查看中间变量、修改 facts 状态。

**问题**：对标 IDE 断点与商业规则平台（URule 的调试）差距明显；且 `onBeforeEvaluate` 阻塞在请求线程上最多 60s，生产误留断点会占满线程池。

**建议**：
1. 短期：断点功能加"仅 dev 环境可用"的强约束 + 全局开关 + 超时下限，防止生产误用。
2. 中期：在 `TreeInterpreter` 增加可注入的 `StepListener`，实现表达式级单步与中间值观察（真断点基础）。

### 3.5 🟡 P1：冲突检测深度不足（已知短板确认）

**证据**：`RuleConflictDetector` 仅能识别**简单数值比较式**（`var OP number`，正则 `COMPARISON_PATTERN`），复杂表达式（含函数/多变量/字符串比较）一律"降级为不检测"。

**缺口**：无法检测跨规则的语义等价（如 `amount > 100` vs `amount >= 101` 的整数域等价）、互斥组之外的隐性重叠、决策表行间冲突。

**建议**：基于 LiteExpr AST 做符号化等价/蕴含分析（复用 `LiteExprCompiler` 的 AST），替代正则；至少先覆盖"单变量区间蕴含"的完整算子组合（含 `!=`、字符串比较）。

### 3.6 🟡 P1：多租户仅逻辑隔离（已知短板确认）

**证据**：`DefaultRuleEngine.doEvaluate` 通过 `Objects.equals(rule.getTenantId(), contextTenantId)` 字段过滤，`RuleIndexer` 按 `tenantId` 建索引，无独立 schema/DB 物理隔离。

**建议**：中期提供 `ISOLATE_DB` 策略（租户级独立 DataSource 路由），对齐 README 宣称的三种多租户策略；短期至少补齐"越权规则读取"的租户校验审计。

---

## 4. 性能提升

### 4.1 🔴 P0：ScriptRule 每次评估新建平台线程

**证据**：`ScriptRule.evaluate()` 第 264-267 行，沙箱模式下每次脚本评估 `new Thread(future, "literule-script-" + code)` + `future.get(5s)`。

**问题**：
- 每次评估新建一个**平台线程**（约 1MB 栈 + 内核态切换），脚本规则高 QPS 下吞吐崩塌、内存/线程耗尽。
- `Thread.interrupt()` 对 Groovy/Jython 的 `while(true)` **无效**（脚本不响应中断），超时后守护线程永久泄漏。
- `SANDBOX_TIMEOUT_MS = 5000` 硬编码不可配置。

**建议**：
1. 用 **Java 21 虚拟线程**（`Executors.newVirtualThreadPerTaskExecutor()`）替代平台线程，消除栈/内核开销。
2. 死循环防护改为"指令计数超时"（Groovy 注入循环检查点 / 编译期植入），而非不可靠的 `interrupt()`。
3. 超时时间下放为 `ydsz.literule.script.timeout-ms` 配置项。

### 4.2 🟡 P1：决策表未预编译，大表全行正则扫描

**证据**：`DecisionTableRule.evaluate()` 第 76-95 行每次评估遍历所有行；`matchCondition` 对每行每列做 `INTERVAL_PATTERN.matcher`/`COMPARISON_PATTERN.matcher` 正则匹配，未做条件结构预解析。

**问题**：1000+ 行决策表时，每次评估 O(rows×cols) 次正则，对标 Drools/URule 的"编译期把决策表编译为可执行结构"有明显差距。

**建议**：构造期把每行的条件字符串预解析为 `CompiledCondition`（操作符/区间/枚举/表达式 AST），评估期直接执行；对命中行做简单的列值哈希分桶加速。

### 4.3 🟡 P1：并行评估未使用虚拟线程

**证据**：`ParallelRuleEvaluator.createExecutor()` 第 316-327 行创建**固定大小平台线程池**（`ThreadPoolExecutor` + `LinkedBlockingQueue(1024)` + `CallerRunsPolicy`）。

**问题**：与项目"Java 21 虚拟线程"战略矛盾；固定池大小在虚拟线程时代是过度保守的资源模型。

**建议**：改用 `Executors.newVirtualThreadPerTaskExecutor()`，或复用项目统一的 `ydsz-common-thread` 虚拟线程池；队列背压保留。

### 4.4 🟡 P1：CEP feed() 每事件遍历所有模式

**证据**：`CEPEngine.feed()` 第 151-160 行 `for (CEPPattern pattern : patterns.values())` 全量遍历，无按 `eventType` 的模式索引。

**建议**：增加 `eventType → List<CEPPattern>` 倒排索引，`feed()` 仅匹配事件类型相关的模式（`matchesType` 提前短路）。

### 4.5 🟢 P2：缓存失效粒度过粗（潜在雪崩）

**证据**：`CachingRuleConfigProvider.invalidateAll()` 第 357-369 行，任何单条规则写操作都调用 `invalidateL1()` **全量清空** `listCache` + `singleCache`，并递增 Redis 版本号让**所有节点**下次检查时全量失效。

**问题**：规则量大的场景，单条规则保存 → 全节点全量回源 DB，产生缓存雪崩/惊群。

**建议**：改为按 key 精确失效（`listCache.invalidate(KEY_ENABLED)` / `singleCache.invalidate(KEY_CODE_PREFIX+code)`），版本号只作为兜底。

---

## 5. 体验改善

### 5.1 🟡 P1：热加载非原子 + 无 last-known-good 回退

**证据**：`RuleHotReloader.fullReload()` 第 140-160 行先注销全部动态规则再逐个加载；`reloadSingle` 中单条规则构建失败仅 `log.warn` 后丢弃。

**问题**：
- 全量重载期间存在"空引擎"窗口，请求可能评估到 0 条规则。
- 新规则表达式编译失败时，**旧版本也被注销**，规则静默消失——生产事故风险。

**建议**：改为"先构建新规则集 → 成功后原子 swap（`engine.replaceAll()` 或双缓冲）"；单条刷新失败保留 last-known-good 版本并告警。

### 5.2 🟢 P2：文档漂移

**证据**：
- README 称"8 种编排语义"，实际 `RuleChainType` 有 **10 种**（含 CATCH/RETRY）。
- README 称"7 种规则类型"但表格列了 8 项（含 CEP）。
- README 多处标注"待补齐"（Feign Client、application.yml/bootstrap.yml），与现状是否一致需复核。

**建议**：README 改为由代码注释/枚举自动校验，或随版本同步更新；补一份"能力矩阵 + 已知限制"清单。

### 5.3 🟢 P2：配置项过多、认知负担大

**证据**：`LiteRuleProperties` 719 行，几十个配置项横跨 cache/circuit-breaker/canary/distributed/model/fact/performance/lifecycle，部分默认关闭、部分互斥。

**建议**：提供配置 Presets（`profile: simple / standard / distributed / high-perf`）；对"默认关闭且使用率低"的能力（结果缓存、并行评估）在文档中标注适用场景与副作用。

---

## 6. 过度设计

| 项 | 现状 | 建议 |
|---|---|---|
| **多配置源**（Nacos/Apollo/ZooKeeper 三个 `RuleSource` 实现） | 作为独立微服务，实际主用 DB；三种外部配置源维护成本高、无消费场景 | 收窄为 DB + 可选的 Nacos；Apollo/ZK 降级为"贡献者自行维护"或移出核心 |
| **五套平行 Provider SPI** | 全部默认实现在 server 内，无替换方 | 仅保留 `RuleConfigProvider`，其余降为普通接口 |
| **`CEPEngine implements Serializable`** | 内部全是 `ConcurrentHashMap`/`ConcurrentLinkedDeque`，序列化无意义且不可靠 | 移除 `Serializable`，避免误导 |
| **`SequenceState.matchedEvents` 用 `CopyOnWriteArrayList`** | 高频 `add` 场景，COW 每次 O(n) 拷贝，选型错误 | 改用普通 `ArrayList`（局部访问已加锁/单线程） |
| **多套统计/指标实现并存** | 见 2.3 | 收敛为单一口径 |

---

## 7. 优先级路线图（P0 → P1 → P2）

### P0（正确性/可靠性/安全，建议 2 个迭代内收口）

| # | 项 | 维度 | 落点 |
|---|---|---|---|
| P0-1 | ScriptRule 虚拟线程化 + 可靠超时 | 性能/资源 | `ScriptRule.evaluate` |
| P0-2 | 画布执行后端补齐 | 功能 | 新增 `DefaultGraphExecutionProvider` |
| P0-3 | ChainAsRule 的 null 求值器 + code 冲突 | 正确性 | `ChainAsRule` |
| P0-4 | CEP 状态内存泄漏（sessionLastEventAt/sequenceStates 清理） | 可靠性 | `CEPEngine` |
| P0-5 | 核心模块单元测试补齐（引擎/表达式/DSL/CEP/熔断/冲突检测） | 工程债 | 新增测试 |
| P0-6 | LiteExprSandbox 白名单实化（当前是黑名单） | 安全 | `LiteExprSandbox` |

### P1（能力/体验收口，建议 S1–S6）

| # | 项 | 维度 |
|---|---|---|
| P1-1 | 热加载原子 swap + last-known-good | 可靠性 |
| P1-2 | 缓存精确失效（防雪崩） | 性能 |
| P1-3 | 决策表预编译 + 大表索引 | 性能 |
| P1-4 | DMN OUTPUT_ORDER + COLLECT 聚合 | 功能 |
| P1-5 | 并行评估/脚本改虚拟线程 | 性能 |
| P1-6 | CEP 模式倒排索引 | 性能 |
| P1-7 | 冲突检测 AST 化 | 功能 |
| P1-8 | LiteRuleAutoConfiguration 拆分 | 架构 |
| P1-9 | expr 包目录/声明统一 | 架构 |
| P1-10 | 指标体系收敛 | 架构 |
| P1-11 | 断点加 dev 环境强约束 | 体验/安全 |

### P2（长期演进，S7–S12）

| # | 项 | 维度 |
|---|---|---|
| P2-1 | 真断点（TreeInterpreter StepListener + 中间值观察） | 功能 |
| P2-2 | CEP 状态持久化/checkpoint（对标 Flink CEP） | 功能 |
| P2-3 | 多租户物理隔离（ISOLATE_DB） | 架构 |
| P2-4 | 配置源/Provider 抽象瘦身 | 过度设计 |
| P2-5 | 配置 Presets + 文档自动校验 | 体验 |

---

## 8. 附录：关键证据索引

| 发现 | 文件 | 关键位置 |
|---|---|---|
| 脚本每评估新建线程 | `impl/ScriptRule.java` | L264-267、L102、L105 |
| 画布执行无实现 | `spi/GraphExecutionProvider.java` | 全文件（仅接口） |
| ChainAsRule null 求值器/code 冲突 | `orchestrator/ChainAsRule.java` | L55、L32-34 |
| CEP 状态泄漏 | `cep/CEPEngine.java` | L616-635（仅清 eventQueues）、L74-75、L72 |
| 沙箱白名单形同虚设 | `expr/LiteExprSandbox.java` | L188-198（空块）、L157-162 |
| 缓存全量失效 | `cache/CachingRuleConfigProvider.java` | L357-369、L341-344 |
| 热加载非原子 | `config/RuleHotReloader.java` | L140-160 |
| 上帝类 | `config/LiteRuleAutoConfiguration.java` | 1399 行 / 37 @Bean |
| 并行评估平台线程 | `core/ParallelRuleEvaluator.java` | L316-327 |
| DMN 策略不全 | `api/HitPolicy.java` | 全文件（6 种） |
| 决策表全行正则 | `impl/DecisionTableRule.java` | L76-95、L235-265 |
| 冲突检测仅简单比较 | `config/RuleConflictDetector.java` | L61-66（正则）、L371-373 |
| 零测试 | 全模块 | 仅 `calc/*Test` 2 类 |
| 包目录不一致 | `server/expr/` | 14 类 `expr.liteexpr` + 5 类 `expr` |
