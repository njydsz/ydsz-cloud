# ydsz-cronjob 深度评审报告

> 评审对象：`ydsz-cronjob` 模块（自研分布式任务调度引擎）
> 评审时间：2026-08-15
> 对标基线：XXL-Job / PowerJob / SchedulerX / ElasticJob / DolphinScheduler / Airflow + 互联网大厂调度平台研发规范
> 评审方式：基于最新代码逐类审读（server 126 类 / domain 63 类 / infra 17 类 / web 17 类）

---

## 一、执行摘要

`ydsz-cronjob` 是一个**成熟度相当高**的自研分布式调度引擎，采用标准 DDD 五层架构（api / domain / infra / server / web），核心能力已覆盖：

- **调度精度**：CRON 轮询（JobScanner）+ 精准调度（PreciseSchedulingManager，±0.1s）+ 秒级 FIXED_RATE/FIXED_DELAY + 事件驱动
- **分布式**：Redisson Leader 选举 + Fencing Token、分区调度、DB 行锁 `FOR UPDATE SKIP LOCKED`、Redis 分布式锁 + Lua 安全释放
- **任务类型**：BEAN / HTTP / GLUE / SHELL / Python / MapReduce / Docker 沙箱
- **编排**：DAG（条件/循环/并行网关/子工作流/审批节点）+ 4 种失败策略 + 跨节点上下文传递（jsonb 原子合并）
- **治理**：多租户配额 + 线程池隔离、告警降噪 + 恢复通知、自愈（Failover / SelfHealing / AutoResume / 熔断）、Prometheus 指标、traceId 全链路

**对标结论**：在调度精度、分片策略丰富度、DAG 编排、告警治理、多租户隔离等维度，**已达到甚至局部超过 XXL-Job**，逼近 PowerJob / SchedulerX 的商用水平。

**但存在一个系统性风险与一批"半成品"能力**：

| 严重度 | 问题 | 一句话结论 |
|--------|------|-----------|
| 🔴 P0 | **零测试覆盖** | 126 个核心类无任何单测/集成测试，并发调度、CAS、分片、DAG 状态机全靠人工保障 |
| 🔴 P0 | 全局并发控制未接线 | `GlobalConcurrencyController` 是死代码，且硬编码"3 节点" |
| 🔴 P0 | 防脑裂校验未落地 | `FencingTokenManager.validateToken` 无任何调用方，脑裂防护只做了一半 |
| 🔴 P0 | 脚本默认裸执行 | `sandbox.enabled=false`、`docker-enabled=false`，SHELL/Python 直接 ProcessBuilder 裸跑 |
| 🟠 P1 | 生态连接器是空壳 | `JobConnector` 接口 + `ConnectorManager` 已就绪，但**零实现类** |
| 🟠 P1 | "时间轮"名不副实 | `timeWheelSlots` 配置项未被使用，PreciseSchedulingManager 实为 ScheduledExecutorService |
| 🟠 P1 | Dispatcher 上帝类 | `DefaultTaskDispatcher` 1689 行，`executeJob`/`executeShard` 大量重复 |
| 🟡 P2 | 代码可读性 | `executeJob` 946-957 行缩进错乱，`ScriptJobHandler.execute(Job,…)` 超时覆盖空实现 |

---

## 二、现状盘点（能力矩阵）

### 2.1 架构分层（✅ 达标）

```
ydsz-cronjob-api      — OpenFeign 客户端 + Fallback（对外契约）
ydsz-cronjob-domain   — 实体 / VO / DTO / 枚举 / JobHandler SPI（纯领域，无框架依赖）
ydsz-cronjob-infra    — MyBatis Mapper（持久化）
ydsz-cronjob-server   — 调度核心引擎（126 类，包结构清晰：core/dispatch、core/scheduler…）
ydsz-cronjob-web      — Controller 层
```

依赖方向单向、边界清晰，符合 DDD 与大厂工程规范。`ObjectProvider` + `@ConditionalOnXXX` 的可插拔设计贯穿全模块，扩展性良好。

### 2.2 核心能力覆盖

| 能力域 | 实现 | 对标 |
|--------|------|------|
| CRON 调度 | JobScanner（5s 轮询 + SKIP LOCKED 抢占）| 对标 XXL-Job |
| 精准调度 | PreciseSchedulingManager（±0.1s 预加载）| 超 XXL-Job，追平 PowerJob |
| 固定频率/延迟 | SecondLevelScheduler | 对标 PowerJob FixedRate/FixedDelay |
| 事件驱动 | EventDrivenScheduler（Redis SETNX 去重）| — |
| Leader 选举 | RedissonLeaderElector + Fencing Token | 对标 SchedulerX |
| 分区调度 | PartitionLeaderManager（多 Active Leader）| 对标 PowerJob 多分区 |
| 分片 | 平均 / 一致性哈希 / 负载感知 三种策略 | 对标 ElasticJob |
| DAG | 条件/循环/并行网关/子工作流/审批 + 失败策略 | 对标 DolphinScheduler |
| MapReduce | 分布式子任务 + reduce 汇总 | 对标 PowerJob MapReduce |
| 任务类型 | BEAN/HTTP/GLUE/SHELL/Python/Docker 沙箱 | 对标 XXL-Job GLUE |
| 调度-执行分离 | WorkerNodeSelector 远程派发 | 对标 XXL-Job/PowerJob 架构 |
| 告警 | 多通道 + 冷却去重 + 智能降噪 + 恢复通知 | 追平 SchedulerX |
| 多租户 | 配额 + 线程池隔离 + 全局并发 | 对标大厂 SaaS 化 |
| 自愈 | Failover/SelfHealing/AutoResume/熔断/超时监控 | 追平 PowerJob |
| 可观测 | Prometheus + 健康检查 + SSE 日志流 + traceId | 达标 |

---

## 三、五大维度优化建议

### 维度一：架构优化

#### A1. 【P0】补齐测试体系（最紧迫，无之一）
**现状**：`find ydsz-cronjob -path "*/src/test/*" -name "*.java"` 返回 **0**。

**风险**：本模块是典型的**并发 + 状态机 + 分布式**系统，以下逻辑一旦回归，仅靠肉眼无法保障正确性：
- `JobTransactionService` 的 CAS 推进 next_fire_time（防重复派发的核心）
- `PreciseSchedulingManager` 预加载去重 + 取消语义
- `DagInstanceExecutor` 的 LOOP 迭代聚合 / RETRY / SKIP_SUBSEQUENT 状态机
- `AlertDispatcher` 冷却窗口 CAS 去重、多通道部分失败（PARTIAL）
- 三种分片策略的确定性

**落地动作**：
1. 用 Testcontainers（PostgreSQL + Redis）搭集成测试底座，覆盖 `SKIP LOCKED`、`SELECT ... FOR UPDATE`、Lua 释放锁等 DB/Redis 语义；
2. 对 `DagInstanceExecutor` 用**状态机单元测试**（纯内存 mock mapper），穷举节点类型 × 失败策略组合；
3. 对三种 `ShardingStrategy` 做**确定性断言**（相同输入 → 相同输出）；
4. 对 `DefaultTaskDispatcher` 的锁竞争路径（DISCARD/COVER/SERIAL）用并发测试；
5. 把测试覆盖率纳入 CI 门槛（核心包 `core/dispatch`、`core/dag`、`core/leader` 要求 ≥ 80%）。

#### A2. 【P0】接线全局并发控制（或删除）
**现状**：`GlobalConcurrencyController.tryAcquire()/release()` **在派发链路中零调用**（全模块 grep 无引用）。且 `maxGlobal = maxConcurrent * 3` 硬编码"假设 3 节点"。

**问题**：这是典型的"写完了但没接上"，属于无效代码，还误导维护者以为有全局并发保护。

**落地动作（二选一）**：
- **方案 A（推荐）**：接入 `DefaultTaskDispatcher.executeJob`，在 `checkExecutionQuota` 之后、抢锁之前调用 `tryAcquire()`，`finally` 中 `release()`；同时把节点数改为**从 `NodeDiscoveryStrategy.getOnlineNodes().size()` 动态读取**，去掉魔法数 `* 3`；
- **方案 B**：若确认租户级配额已足够覆盖需求，**直接删除该组件**，避免死代码长期误导。

#### A3. 【P0】补全 Fencing Token 校验闭环
**现状**：`FencingTokenManager.acquireNewToken()` 在 Leader 抢占时被调用，但 **`validateToken()` / `isCurrentTokenValid()` 全模块无调用方**。

**问题**：防脑裂的"写操作前校验 Token"这一步从未执行，脑裂防护只做了"发 Token"没做"验 Token"，形同虚设。

**落地动作**：在 `JobScanner.doScan()` 与 `PreciseSchedulingManager.fastScan()` 的 **Leader 身份校验之后**，追加 `fencingTokenManager.isCurrentTokenValid(role)` 判断；校验失败立即终止本次扫描并告警。这才是 Fencing Token 设计的真正价值点。

#### A4. 【P1】拆解 DefaultTaskDispatcher 上帝类
**现状**：`DefaultTaskDispatcher` 高达 **1689 行**，承载了：锁管理、配额检查、分片路由、跨集群、调度-执行分离、COVER 策略、熔断、重试、告警、指标、Webhook、Outbox、日志器初始化等十余项职责。`executeJob` 与 `executeShard` 有约 200 行**高度重复**的"写日志 → 设上下文 → 执行 → 回写统计 → 释放锁 → 告警"模板代码。

**落地动作**：
1. 抽取 `JobExecutionTemplate`（模板方法 / 策略模式），统一 `executeJob` / `executeShard` / MapReduce 的"执行骨架"；
2. 把"完成后置处理链"（指标 → 事件 → 告警 → Webhook → Outbox → 重试）抽取为独立的 `PostExecutionProcessor` 责任链，Dispatcher 只做路由与派发；
3. 目标：Dispatcher ≤ 500 行，每个子职责独立可测。

#### A5. 【P1】统一自建线程池的生命周期管理
**现状**：`PreciseSchedulingManager`、`SecondLevelScheduler`、`DefaultTaskDispatcher` 各自 `Executors.newScheduledThreadPool` / `new ThreadPoolExecutor` 手写，`@PreDestroy` 手写关闭。虽然注释说明"因 PriorityBlockingQueue / scheduled 语义保留"，但散落多处、参数不统一。

**落地动作**：封装一个 `CronjobThreadPools` 内部工具，统一线程工厂（daemon + 命名 + 未捕获异常处理器）+ 优雅关闭模板；`DefaultTaskDispatcher` 的 `retryScheduler`/`taskExecutorPool` 与调度器线程池统一收敛。线程池参数（core/max/queue）继续走 `CronjobProperties` 配置化。

---

### 维度二：功能增强

#### F1. 【P1】落地生态连接器（接口已就绪，零实现）
**现状**：`JobConnector` 接口定义了 XXL_JOB / POWER_JOB / SCHEDULER_X / ELASTIC_JOB / SPRING_BATCH / QUARTZ 六类适配，`ConnectorManager` 注册机制、`ConnectorController` 均已就绪，但 **`implements JobConnector` 的实现类为 0 个**。

**落地动作**：优先实现 **XXL-Job 导入**（存量迁移是刚需，市场上 XXL-Job 存量最大），交付 `XxlJobConnector`：解析 XXL-Job 执行器地址 + 任务表结构，映射到 `Job` 实体。这是"迁移友好"的差异化竞争力，可显著降低客户切换成本。

#### F2. 【P1】补齐 HTTP 任务的安全与幂等
**现状**：`HttpJobHandler` 已支持 method/header/body/timeout/状态码范围，但：
- 无 **TLS 校验开关 / 证书配置**；
- 无 **响应体截断上限**（`body` 全量返回并落库 result_json，大响应会撑爆日志）；
- 无 **重试语义**（靠外层 Job 重试，但 GET 类请求无幂等保护）。

**落地动作**：1）为 `result.body` 增加 `maxResponseBodySize` 截断；2）增加 `allowInsecure`（跳过 TLS 校验，默认 false）与 `followRedirects` 已有，补充自定义信任库；3）文档明确 HTTP 任务的幂等约束（推荐配合 `blockStrategy=DISCARD` 使用）。

#### F3. 【P1】GLUE 在线编码的版本化与灰度
**现状**：`GlueCodeService` + `GlueJobHandler` + `GlueCode` 实体已具备在线编辑，但版本化（历史回滚）与灰度（canaryHandler 对 GLUE 是否生效）未闭环。

**落地动作**：1）`GlueCode` 增加版本快照（每次保存生成不可变版本），支持回滚；2）GLUE 任务接入 `canaryRatio` 灰度路由（当前 `resolveCanaryHandler` 仅对 BEAN 生效）。

#### F4. 【P2】补全子工作流（SUB_WORKFLOW）执行闭环
**现状**：`DagDefinitionValidator` 已校验 `SUB_WORKFLOW` 节点的 `subWorkflowDagKey`，但 `DagInstanceExecutor.dispatchNode` 的 switch **没有 `SUB_WORKFLOW` 分支**（默认走 `dispatchTaskNode`，会因 jobId 为 null 直接 markNodeFailed）。

**落地动作**：在 `dispatchNode` 增加 `SUB_WORKFLOW` 分支，递归触发子 DAG 实例，子实例终态回调父节点状态。这是 DAG 能力"校验先行、执行缺失"的典型断点。

#### F5. 【P2】任务级 Timeout 的统一落地
**现状**：`Job.timeoutMs` 字段存在，但 `ScriptJobHandler.execute(Job, paramsJson)` 中任务级超时覆盖是**空实现**（仅有注释）；`TimeoutMonitor` 仅做超时清理，未在任务执行前设置 deadline。

**落地动作**：统一在 `executeJob` 骨架中根据 `job.timeoutMs` 设置执行超时（线程中断 + 锁释放），handler 层只处理自身默认超时，避免"配置了 timeout 但不生效"。

---

### 维度三：性能提升

#### P1. 【P1】DAG 完成事件的查询下推（N+1）
**现状**：`DagInstanceExecutor.findRunningNodesByJobId` 每次任务完成事件都：
1. `selectByStatus(RUNNING)` 查**所有** RUNNING 实例；
2. 对每个实例 `selectAllByDagInstanceAndJob` 再查节点。

**问题**：DAG 规模增长后，每个完成事件触发 O(实例数) 次 DB 查询。

**落地动作**：新增一条联表 SQL（`ydsz_job_dag_node_instance` JOIN `ydsz_job_dag_instance`），按 `jobId + nodeStatus IN (PENDING,RUNNING) + instance.status=RUNNING` 一次性查出候选节点，消除 N+1。

#### P2. 【P1】告警扫描器的规则级 N+1
**现状**：`AlertScanner.scanFailRateRules` / `scanDurationP95Rules` 对每条规则单独 `countByJobIdSince` / `selectDurationP95`。规则多时，5 分钟一次的全量扫描产生 N 次 DB 查询。

**落地动作**：1）将 FAIL_RATE / DURATION_P95 统计改为**批量 SQL**（按 jobId 分组聚合）；2）为 `countByJobIdSince` 的 `created_at` 列建复合索引；3）规则超阈值才逐条发告警（统计阶段批量，触发阶段逐条）。

#### P3. 【P2】JobScanner 的 Leader 单点吞吐
**现状**：虽然已有 `PartitionLeaderManager`（多 Active Leader 分区），但默认 `partition.enabled=false`，意味着**万级任务场景下所有调度压力集中在单 Leader**。

**落地动作**：在规模压测后开启分区调度（`totalPartitions=节点数×2~4`），将扫描压力分散到多节点；同时关注 `PreciseSchedulingManager` 开启后与 JobScanner 的配合（当前有 30s 降频兜底，但需验证 Leader 切换后的补偿延迟）。

#### P4. 【P2】一致性哈希环的复用
**现状**：`ConsistentHashShardingStrategy` 每次 `assign()` 都重建 160 虚拟节点 × N 节点的 TreeMap。若节点列表高频变化，存在重复构建开销。

**落地动作**：对"节点集合"做版本缓存（节点列表 hash 不变则复用哈希环），节点变更时重建。

---

### 维度四：体验改善

#### E1. 【P1】补齐 OpenAPI 接口文档
**现状**：`ydsz-cronjob-domain` 已引入 springdoc 依赖（DTO 有 Swagger 注解），但 `ydsz-cronjob-web` 的 Controller 层未见统一 `@Tag`/`@Operation` 标注，API 文档覆盖不全。

**落地动作**：为 17 个 Controller 统一补齐 `@Tag`（按 domain 分组）+ 关键接口 `@Operation` summary，产出可交互 Swagger UI，降低前端对接与联调成本。

#### E2. 【P1】运行时诊断面板（运维体验）
**现状**：已有 `JobQueueController`（队列监控）、`TaskTopologyController`（拓扑可视化）、`CronjobHealthIndicator`、`CronjobMetrics`，但缺少一个**聚合的运行时状态视图**：当前 Leader 是谁、各节点负载、分区归属、调度延迟 P99、任务积压量。

**落地动作**：新增 `CronjobRuntimeController`，聚合暴露：Leader 节点、分区表、在线节点负载（cpu/mem/running）、最近扫描延迟、全局并发计数。对标 SchedulerX 控制台的"集群状态"页。

#### E3. 【P2】告警消息的语义化升级
**现状**：`AlertDispatcher.buildContent` 已生成 Markdown 表格，但 `broadcastAlert` 中 `alertCode = "CRONJOB-" + System.currentTimeMillis() + "-" + ruleId` 与 `persistAlertLog` 中的 `alertCode` **各自独立生成**，同一次告警在"实时广播"与"落库"两条路径的 code 不一致，无法关联追踪。

**落地动作**：统一 alertCode 生成（在 `AlertTrigger` 入口生成一次，随 `AlertContext` 传递），使前端告警卡片可跳转到落库的告警日志。

#### E4. 【P2】代码可读性治理
**现状**：
- `DefaultTaskDispatcher.executeJob` 第 946-957 行缩进错乱（`ShardingContext` 设置、`recordExecutionStart`、`dispatchWebhookEvent` 混排），明显是多次合并遗留；
- `ScriptJobHandler.execute(Job, paramsJson)` 存在空实现的 TODO 痕迹；
- 大量注释用"P0-x/P1-x/P2-x"标记演进阶段，历史包袱重。

**落地动作**：1）重排 `executeJob` 缩进（顺手并入 A4 重构）；2）清理空实现与失效注释；3）建立 `P0/P1/P2` 阶段标记的**收口约定**（已完成的标注改为 `@since` 版本，未完成的统一挂 TODO 单）。

---

### 维度五：过度设计（需收敛）

这部分是本次评审**最需要关注的方向**：模块存在"能力做了但没接线/没实现/默认关闭"的倾向，导致**代码复杂度与实际可用能力不匹配**。

#### O1. 【P0】全局并发控制 = 死代码
见 A2。`GlobalConcurrencyController` 写了完整的 INCR/DECR/校准逻辑，但从未被调用，属于纯负债。

#### O2. 【P0】Fencing Token 半成品
见 A3。发了 Token 却不校验，脑裂防护的"防"字没有落地。

#### O3. 【P1】生态连接器空壳
见 F1。接口 + Manager + Controller 三层就绪，实现为零，属于"提前设计、滞后交付"。

#### O4. 【P1】"时间轮"名不副实
`CronjobProperties.PreciseScheduling.timeWheelSlots`（默认 60）**全模块无使用**，`PreciseSchedulingManager` 实际是"预加载窗口 + ScheduledExecutorService 逐任务调度"，注释却写"时间轮预加载"。

**落地动作**：二选一——① 若精度已达标（±0.1s），删掉 `timeWheelSlots` 配置并修正 Javadoc 为"预加载 + 延迟调度"；② 若确有海量秒级任务需求，真正实现时间轮（HashedWheelTimer），而非留一个误导性配置。

#### O5. 【P2】安全沙箱默认关闭
`Sandbox.enabled=false`、`Sandbox.dockerEnabled=false`，意味着**生产默认直接 `ProcessBuilder` 裸执行任意脚本**，而 `SandboxScriptExecutor` / `DockerSandboxExecutor` 两个完整实现处于休眠。

**落地动作**：生产环境 profile 强制 `sandbox.enabled=true`（或至少 shell 类强制 docker 沙箱），把"裸执行"作为 dev-only 的降级路径而非默认路径。这是安全边界，不是可选优化。

#### O6. 【P2】LoadAwareShardingStrategy 的成功率硬编码
`calculateLoadScore` 中 `successRate = 0.95` 硬编码，`recordNodeSuccess/recordNodeFailure` 方法存在但**无调用方**，节点稳定性评分实际恒为常量。

**落地动作**：要么在 `DefaultTaskDispatcher` 执行成功后调用 `recordNodeSuccess(nodeId)`、失败调用 `recordNodeFailure`，让成功率真正生效；要么删掉这两个方法与 `nodeFailStreak`，避免"看起来有自适应、实际是死的"。

---

## 四、落地路线图（分阶段）

| 阶段 | 优先级 | 事项 | 预期收益 |
|------|--------|------|----------|
| **S1（立即）** | P0 | A1 测试体系 + A2 全局并发接线 + A3 Fencing 校验 + O4 时间轮澄清 + O5 沙箱默认开启 | 消除正确性/安全红线 |
| **S2（1-2 周）** | P1 | A4 Dispatcher 拆解 + F1 XXL-Job 连接器 + F4 子工作流闭环 + P1/P2 N+1 查询下推 | 可维护性 + 迁移竞争力 |
| **S3（2-4 周）** | P1 | A5 线程池统一 + F2 HTTP 安全 + F3 GLUE 版本化 + E1 OpenAPI + E2 运行时面板 | 工程化 + 体验 |
| **S4（持续）** | P2 | E3 告警语义统一 + E4 可读性 + P3 分区吞吐 + P4 哈希环复用 + O6 负载成功率 | 精细化打磨 |

---

## 五、总结

`ydsz-cronjob` 的**能力广度**已经非常接近甚至局部超越主流竞品，是一个"产品级"而非"Demo 级"的调度引擎。当前的主要矛盾不是"缺功能"，而是：

1. **可靠性与复杂度不匹配**——零测试 + 上帝类 + 死代码，让一个本该高可靠的调度系统处于高风险状态；
2. **"设计先行、交付滞后"**——连接器、Fencing、全局并发、沙箱、时间轮等多个能力处于"半成品"状态，代码在但价值没释放；
3. **默认配置偏保守**——分区调度、精准调度、自适应批量、自愈、告警降噪、沙箱等大量能力默认关闭，实际线上只跑通了最小闭环。

建议优先按 **S1 阶段**收口 P0 项，把"写了但没用"的能力要么接线、要么删除，然后集中补齐测试底座——这是让这个模块从"看起来很强"走向"真正可信"的关键一步。
