# ydsz-cronjob 模块优化分析报告（v2 · 最新代码增量版）

> 对标对象：XXL-Job / PowerJob / ElasticJob / SchedulerX + 互联网大厂（阿里/腾讯/字节）研发规范
> 分析范围：`ydsz-cronjob` 最新代码（**293 个 Java 文件，0 个测试文件**）
> 相对上一版（`ydsz-cronjob-优化分析报告.md`，276 文件）：本版为增量更新，标注「✅已修复 / ⚠️仍存在 / 🆕新增」

---

## 一、结论速览

| 维度 | 评价 | 最需要动作的一件事 |
|---|---|---|
| 架构 | ★★★★☆ 分层清晰，但「双轨执行 + 双锁机制」冗余仍在 | 收敛 Leader/Leaderless，统一走 Dispatcher |
| 功能 | ★★★★★ 已超 XXL-Job，接近 PowerJob | 补固定频率任务的 Leader 化调度与故障转移 |
| 性能 | ★★★★☆ 有 SKIP LOCKED / Disruptor / ctid / jsonb 优化 | 修 3 份配置线程池名漂移（会启动失败） |
| 体验 | ★★★☆☆ 有 SSE / 拓扑视图，缺回放与检索 | 日志内容落对象存储 + ES 检索 |
| 工程质量 | ★★☆☆☆ 注释详尽但**零测试** + 多处复制粘贴 bug | 补 Testcontainers 集成测试 |

**一句话结论**：功能面已是「大厂调度中台 80% 能力」，但当前存在 **2 个会直接导致线上事故/启动失败的新增 P0**（`FOR UPDATE` 只读事务冲突、Leader 锁租约矛盾）+ 1 个配置漂移 P0（线程池 Bean 名三份不一致），优先级高于一切功能增强。

---

## 二、🆕 本次新增的关键问题（上一版未覆盖，优先级最高）

### 🆕 P0-1 `FOR UPDATE` 跑在只读事务里 → 运行时报错

**文件**：`server/core/dispatch/JobTransactionService.java` 第 53 行

```java
@Transactional(readOnly = true)   // ← 问题所在
public List<Job> acquireDueJobs(LocalDateTime now, int batchSize) {
    return jobMapper.selectDueJobs(now, batchSize);   // 内部是 SELECT ... FOR UPDATE SKIP LOCKED
}
```

`selectDueJobs` 的 SQL 是 `SELECT ... FOR UPDATE SKIP LOCKED`（`JobMapper.xml` 第 60-71 行）。PostgreSQL 中**只读事务禁止 `FOR UPDATE`**，会直接抛错：

```
ERROR: cannot execute SELECT FOR UPDATE in a read-only transaction (SQLSTATE 25006)
```

这是 Leader 模式扫描派发的**主路径**，一旦触发整个调度引擎停摆。此外，项目还引入了 dynamic-datasource 读写分离，`readOnly=true` 可能把该查询路由到只读副本，同样报错。

**落地动作**：去掉 `readOnly = true`，改为 `@Transactional`（或显式 `readOnly = false`）；补充一条集成测试：两节点并发 `acquireDueJobs` 不报错且不重复取行。

---

### 🆕 P0-2 Redisson Leader 锁「租约续期」自相矛盾 → Leader 每 30s 抖动

**文件**：`server/core/leader/RedissonLeaderElector.java`

- 抢锁：`lock.tryLock(0, lease.toMillis(), MILLISECONDS)`（第 104 行），`lease` 默认 30s。
- 续期：`renew()`（第 134 行）只刷新了一个**旁路 holder key**，并注释明说「Redisson RLock 自身不暴露 renew API」。

**关键问题**：Redisson 的 WatchDog 自动续期**只在 `leaseTime = -1` 时生效**。显式传了 `leaseTime=30s` 后，锁本身**不会自动续期**，30s 到期即释放。于是：

1. 30s 后 RLock 到期，`isLeader()`（`lock.isHeldByCurrentThread()`）返回 false；
2. `renewLeaseTask`（每 10s）发现续期失败 → 重新 `tryAcquire`，重新成为 Leader；
3. 在「到期 → 重新抢到」的空窗期（最长约 10s），**集群没有任何节点是 Leader**，`JobScanner`/`TimeoutMonitor` 全部跳过，任务延迟触发。

**落地动作**（二选一）：
- 推荐：改用 WatchDog 模式 `tryLock(0, -1, MILLISECONDS)`，靠 Redisson 自动续期，同时用 `holder key` 暴露真实 Leader（保留现有 `getCurrentLeader` 能力）；
- 或：保持显式 lease，但 `renew()` 必须真正续锁（用 `tryLock` 重入续期或改用 `RedissonClient` 的 `RLock` 手写续期），而不是只刷 holder key。

补充：当前 `getCurrentLeader()` 读 holder key、`isLeader()` 读锁本身，两者生命周期不同步，是「身份判定漂移」的根源。

---

### 🆕 P0-3 三份配置文件三套线程池名，`MapTaskExecutor` 会启动失败

**文件**：`web/src/main/resources/bootstrap.yml` vs `application.yml` vs `config/ydsz-cronjob-dev.yaml`（Nacos）

| 配置来源 | 线程池名 |
|---|---|
| `bootstrap.yml` | `jobDispatchExecutor` / `mapTaskExecutor` / `retrySchedulerExecutor` |
| `application.yml` | `cronjobMapReduce` / `cronjobDispatch` |
| `ydsz-cronjob-dev.yaml`（Nacos dev） | `cronjobDispatch` / `cronjobRetry` / `cronjobScan` / `cronjobMapTask` / `cronjobPrecise` |

而代码里：

- `JobScanner.init()` 找 `cronjobDispatchExecutor`（`getBean("cronjobDispatchExecutor")`）
- `MapTaskExecutor.initExecutor()` 找 `cronjobMapReduceExecutor`，**且没有 try-catch**：

```java
ThreadPoolTaskExecutor threadPool =
    applicationContext.getBean("cronjobMapReduceExecutor", ThreadPoolTaskExecutor.class);
```

dev/sit/uat 的 Nacos 配置里**根本没有 `cronjobMapReduce`**，`getBean` 会抛 `NoSuchBeanDefinitionException` → **应用启动直接失败**。上一版已提示「Bean 名漂移」，本版确认不仅没修，还从「两份」恶化成「三份」。

**落地动作**：三份配置统一收敛为一份（推荐以 Nacos 为准），统一池名命名规范，并把 `MapTaskExecutor.initExecutor()` 的 `getBean` 改为可选注入 + WARN 降级（而非静默崩溃）。

---

### 🆕 P1-1 `next_fire_time` 双写 → CRON 任务执行耗时长会「跳过触发」

**文件**：`JobScanner.dispatchSingleJob()` 与 `DefaultTaskDispatcher.executeAndFinalize()` 两处都在推进 `next_fire_time`。

- 扫描器：派发前 `advanceNextFireTime(job, oldNext, newNext, now)`（CAS 推进到「扫描时刻后的下一个 cron 边界」）。
- 派发器：执行**完成后** `updateStats(..., next, ...)`，其中 `next = nextFireTime(job)`（重新按「完成时刻」计算），再次覆盖 `next_fire_time`。

后果：若任务耗时跨越了下一个 cron 边界（如每分钟任务跑了 90s），扫描器推进到 T+1min，执行完成后派发器又覆盖为 T+2min，**T+1min 这一次触发被静默跳过**，语义从「cron 追帧」退化成「固定延迟」。这是调度正确性的隐患。

**落地动作**：明确单一职责——`next_fire_time` 只由扫描器推进；派发器的 `updateStats` 不再写 `next_fire_time`（仅更新 `last_fire_time` + 计数 + 熔断状态）。

---

### 🆕 P1-2 固定频率/固定延迟任务在 Leader 模式下「无故障转移」

**文件**：`JobMapper.xml` 的 `selectDueJobs`（第 65 行）与 `JobServiceImpl.register()`。

- 扫描器 SQL 明确 `AND schedule_type = 'CRON'`，**固定频率/固定延迟任务不进扫描器**。
- Leader 模式下 `run()` 直接 `return`，跳过 `loadOnStartup()`；固定频率任务只依赖 `register()` → 本地 `TaskScheduler`，而 `register()` 只在「创建/更新/恢复」动作发生的那个节点执行。

后果：固定频率任务只在被操作过的节点本地跑，节点宕机即停摆；多节点下也依赖 Redis 锁去重，但**谁在触发不明确、无法水平扩容、无故障转移**，与 README 宣称的「固定频率 + 固定延迟」能力不符。

**落地动作**：把 `FIXED_RATE`/`FIXED_DELAY` 纳入 Leader 扫描（`selectDueJobs` 去掉 `schedule_type='CRON'` 限制，改为按 `next_fire_time` 统一扫描），或引入独立的时间轮/队列统一调度。

---

### 🆕 P1-3 任务级时区支持在扫描路径失效

**文件**：`JobScanner.nextFireTime(String cron)`（第 450 行）。

- `JobScanner` 用 `CronExpression.parse(cron).next(LocalDateTime.now())`，**忽略任务时区**；且本地缓存 key 只含 cron（不含时区）。
- 而 `DefaultTaskDispatcher.nextFireTime(Job)` 与 `JobServiceImpl.nextFireTime(Job)` 都支持 `job.getTimezone()`。

后果：配置了非 Asia/Shanghai 时区的任务，其 `next_fire_time` 由扫描器按系统默认时区计算 → 实际触发时间错误。同一套 cron 存在 3 处实现且语义不一致。

**落地动作**：抽一个 `NextFireTimeCalculator` 统一计算（带时区 + 缓存 key 含 cron+timezone），三处统一调用。

---

### 🆕 P2-1 全局并发计数器 `calibrate` 是死代码，且 INCR/DECR 非原子

**文件**：`server/core/executor/GlobalConcurrencyController.java`。

- grep 确认 `calibrate()` 全项目**无人调用**（只有定义），进程崩溃后计数器永久漂移、无自愈。
- `tryAcquire()` 是「INCR → 判断 → 超限再 DECR」的非原子 check-then-act；`release()` 里「DECR → 若为负则 SET 0」存在竞态（可能把刚被其他节点 INCR 的计数清零）。

**落地动作**：改为 Redis Lua 脚本原子实现「INCR + 比较 + 回滚」，或直接以 `ydsz_job_log` 中 `status='RUNNING'` 的实时计数为准（DB 是唯一事实源），放弃内存计数器。

---

### 🆕 P2-2 `JobLockManager` 是半成品重构，两套锁机制并存

**文件**：`core/JobLockManager.java` vs `core/dispatch/DefaultTaskDispatcher.java`。

- `JobLockManager` 委托 `ydsz-common-lock` 的 `DistributedLocker`（可重入 + WatchDog + 锁指标），但**只有 `JobServiceImpl.executeJob()`（Leaderless 路径）在用**。
- `DefaultTaskDispatcher`（Leader 主路径）仍用原生 `redisTemplate.setIfAbsent` + Lua 释放。

后果：Leader 与 Leaderless 两套锁语义不一致（前者无 WatchDog、无重入、无锁指标），同一模块维护两套锁代码，且与「统一收敛到公共锁模块」的意图背道而驰。

**落地动作**：Dispatcher 也切换到 `JobLockManager`/`DistributedLocker`，删掉 Dispatcher 里的内联 Lua 释放逻辑。

---

## 三、分维度问题与建议

### 3.1 架构优化

#### A1【P0】零自动化测试仍是最大风险 ⚠️仍存在
293 个 Java 文件，`src/test` 下 **0 个测试文件**。调度器是强一致 + 高并发场景，任何改动都可能引入「重复执行/漏调度」致命问题。
**落地**：优先补 `LockKeyUtil`、`MisfirePolicy`、`CronjobProperties.normalizeTtl`、`DagDefinitionCodec` 单元测试；再用 Testcontainers（PG+Redis）补 `JobScanner` 两节点并发不重复、`DefaultTaskDispatcher` 锁竞争三策略、`TimeoutMonitor` 超时释放锁、`GlobalConcurrencyController` 配额拒绝。

#### A2【P0】双轨执行路径冗余 ⚠️仍存在
`JobServiceImpl` 仍保留完整 `executeJob()`（Leaderless 回退，约 90 行）与 `DefaultTaskDispatcher.executeJob()` 高度重复；第 521 行 `taskDispatcherProvider != null` 仍是死代码（final 字段永不为 null）；`trigger(id, holdLock)` 回退分支 `executeJob(j, !holdLock)` 语义反直觉。
**落地**：Leaderless 也统一走 `TaskDispatcher`，删除 `JobServiceImpl` 的 `executeJob/register/scheduledMap` 等 Leaderless 专属逻辑。

#### A3【P1】`executeJob` / `executeShard` 约 90% 重复 ⚠️仍存在
**落地**：抽 `executeWithTemplate(job, shardIndex, ...)` 模板方法。

#### A4【P1】`DefaultTaskDispatcher` 上帝类 + 30+ `ObjectProvider` ⚠️仍存在
近 2000 行，职责含：派发路由、分片、远程派发、锁管理、幂等锁、熔断、指标、告警、Webhook、Outbox、重试、COVER 中断。
**落地**：按职责拆为 `DispatchRouter` / `JobExecutor` / `LockGuard` / `PostExecutionNotifier`；核心依赖改构造器强依赖，可选能力用 Null Object 或策略注册表替代 `ObjectProvider` 判空链。

#### A5【P1】三个节点选择器职责重叠 ⚠️仍存在
`SmartRoutingSelector` / `LeastLoadNodeSelector` / `WorkerNodeSelector` 并存，实际 `dispatchToWorker` 只用 `WorkerNodeSelector`。
**落地**：收敛为单一接口 + 负载感知实现，删除无引用的 `LeastLoadNodeSelector`。

#### A6【P1】`@Scheduled` 线程池未隔离 ⚠️仍存在
`JobScanner.scan` / `TimeoutMonitor.scan` / `AlertScanner.scan` / `LogCleaner` / `AnomalyRecoveryScanner` 等 16 处 `@Scheduled` 默认共用一个单线程调度池，低频重量任务（如凌晨 `LogCleaner` 大表删除）会阻塞高频轻量扫描。
**落地**：配置专用 `TaskScheduler`，按「高频轻量/低频重量」分两组。

### 3.2 功能增强（对标大厂差距）

| 能力 | 竞品 | 现状 | 建议 |
|---|---|---|---|
| 秒级调度 | SchedulerX/PowerJob 支持 | 扫描器 5s 间隔 | 引入时间轮，精确到秒 |
| 任务级依赖 | 单任务依赖（A 完触发 B） | 仅 DAG 级编排 | 增加轻量 `job dependency` 字段 |
| 可视化回放 | Gantt + 运行回放 | 有拓扑 VO，无回放 | 基于 `JobDagNodeInstance` 时间线回放 |
| 全链路 span | 自动埋点 APM | 有 traceId，缺 span | 用 `TraceIntegrationHelper` 补 span |
| 告警收敛升级 | 聚合/升级/值班 | 仅冷却去重 | 加聚合（N 分钟归并）+ 升级 |
| 一键重跑 | 带原始参数重跑 | 仅手动触发 | 增加「按 logId 重跑」接口 |
| Schema 迁移 | Flyway | DDL 不在模块内、无版本管理 | 引入 Flyway 管 17 张表 + 索引（或至少文档化索引） |

### 3.3 性能提升

- **B1【P0】** 线程池 Bean 名三份漂移（见 🆕P0-3），统一 + 失败 WARN 降级。
- **B2【P1】** N+1 查询 ⚠️仍存在：`AnomalyRecoveryScanner.recoverOfflineNode()` 循环 `selectById`、`JobLogContentServiceImpl.batchSave()` 逐条 insert、`DagInstanceExecutor.findRunningNodesByJobId()` 先查所有 RUNNING 实例再 flatMap 逐查，应改为批量/JOIN。
- **B3【P1】** 日志内容存储与检索：`ydsz_job_log_content` 逐行存 DB、`LIKE` 检索；应归档 MinIO（模块已依赖）+ ES 检索（已有 `ydsz-common-search`）。
- **B4【P1】** 全局并发计数器漂移（见 🆕P2-1），改 Lua 原子或 DB 事实源。
- **B5【P2】** `selectDueJobs` 依赖 `(status, deleted, schedule_type, next_fire_time)` 复合索引、`selectTimedOutLogs` 依赖 `(status, start_time)` 索引——当前**无文档化**，应显式声明。
- **B6【P2】** `DagInstanceExecutor` 每次节点完成都 `selectById` 实例+DAG+`fromJson` 重解析定义，可加一层 Caffeine 缓存（DAG 定义 + 实例快照）。

### 3.4 体验改善

1. **审计 action 误标** ⚠️仍存在：`JobController` 中 `pause/resume/trigger/batchDelete` 等多处仍标注 `AuditAction.CREATE`（第 195/241/264/302/324/346/369/463 行），审计报表失真，应改为 `UPDATE/DELETE/EXECUTE`。
2. **错误码键不统一** 🆕：i18n 同时存在可读键（`cronjob.job.not.found`）与 UUID 键（`error.cronjob.msg_5d0044ca` / `msg_7e5ef640`），应统一为可读键 + 明确编号规则。
3. **`/reload` 语义误导** ⚠️仍存在：Leader 模式下 reload 实际 no-op，但接口返回 `ok`，应返回结构化结果并说明实际行为。
4. **DAG 失败原因下钻** ⚠️仍存在：节点失败信息只存「任务执行失败」，应透传 `JobLog.errorMessage`。
5. **告警码可读性** ⚠️仍存在：`alertCode = "CRONJOB-" + currentTimeMillis + "-" + ruleId` 并发下可能重复且不可读，改用雪花 ID 或 traceId。

### 3.5 过度设计与精简

1. **`ObjectProvider` 泛滥** ⚠️仍存在（同 A4），核心依赖改强依赖、可选能力用 Null Object。
2. **功能开关盘点** ⚠️仍存在：canary 灰度、MapReduce、partition 多分区、cross-cluster、adaptive-batch、调度器-执行器分离等**默认关闭且使用率存疑**，建议做「开关→使用率→是否保留」盘点。
3. **`CronjobProperties.spel` 已废弃** ⚠️仍存在：`@Deprecated` 字段仍在组合根，60 项 `spring-configuration-metadata.json` 需同步清理。
4. **25 个 config 配置类** 🆕：配置类数量偏多（`ClusterConfig/ExecutorConfig/HttpConfig/RemoteConfig/AlertScanConfig/LeaderConfig/ScannerConfig/...`），部分仅承载 2-3 个字段，可适度合并，降低认知负担。
5. **`COVER` 策略用 `Thread.getAllStackTraces()` + `Thread.interrupt()`** 🆕：中断任意执行线程是危险且昂贵的反模式（全量栈快照 + 线程不响应中断），XXL-Job 采用协作式中断/executor 级管理。建议 COVER 改为「标记取消 + 协作式检查」，或直接降级为 DISCARD。

---

## 四、落地路线图

### 阶段一（1 周，止血 — 均为线上事故级）
- [ ] 🆕P0-1 去掉 `acquireDueJobs` 的 `readOnly=true`，加集成测试
- [ ] 🆕P0-2 修 Redisson Leader 租约（改 WatchDog 或真续期）
- [ ] 🆕P0-3 三份线程池配置收敛 + `MapTaskExecutor` 降级不崩溃
- [ ] 补 `LockKeyUtil` / `MisfirePolicy` / `normalizeTtl` 单元测试

### 阶段二（2-4 周，还债）
- [ ] 收敛 Leader/Leaderless 双轨，删 `JobServiceImpl` 死代码，统一走 `Dispatcher`
- [ ] 抽 `executeJob/executeShard` 模板；拆 `DefaultTaskDispatcher` 上帝类
- [ ] 🆕P1-1 修 `next_fire_time` 双写
- [ ] 🆕P1-2 固定频率任务纳入 Leader 扫描
- [ ] 🆕P1-3 统一 `NextFireTimeCalculator`（时区一致）
- [ ] 修 N+1 查询、`@Scheduled` 分组隔离、`JobController` 审计 action
- [ ] 补 Testcontainers 集成测试（扫描/故障转移/锁竞争/超时）

### 阶段三（1-2 月，增强）
- [ ] 日志内容归档 MinIO + ES 检索
- [ ] 秒级调度（时间轮）、任务级依赖、一键重跑、DAG 回放
- [ ] 全链路 trace span、告警聚合 + 升级
- [ ] 功能开关盘点，砍掉半年内无人开启的能力

---

## 五、代码位置索引

| 问题 | 文件 | 位置 |
|---|---|---|
| 🆕FOR UPDATE 只读事务 | `core/dispatch/JobTransactionService.java` | L53 `readOnly=true` |
| 🆕Leader 锁租约矛盾 | `core/leader/RedissonLeaderElector.java` | L104 `tryLock(0,lease)` + L134 `renew()` |
| 🆕线程池名三份漂移 | `bootstrap.yml` / `application.yml` / `config/ydsz-cronjob-dev.yaml` + `MapTaskExecutor.java` L113 | `getBean("cronjobMapReduceExecutor")` |
| 🆕next_fire_time 双写 | `JobScanner.java` L383 + `DefaultTaskDispatcher.java` L1194 | 两处推进 |
| 🆕固定频率无故障转移 | `JobMapper.xml` L65 + `JobServiceImpl.run()` L205 | `schedule_type='CRON'` + 跳过 loadOnStartup |
| 🆕时区失效 | `JobScanner.java` L450 | `nextFireTime(cron)` |
| 🆕calibrate 死代码 | `GlobalConcurrencyController.java` L169 | 无人调用 |
| 🆕两套锁机制 | `JobLockManager.java` vs `DefaultTaskDispatcher.java` L998 | 原生 Redis vs DistributedLocker |
| ⚠️零测试 | 全模块 `src/test` | 0 文件 |
| ⚠️双轨执行 | `JobServiceImpl.java` | `executeJob()` + `trigger()` |
| ⚠️上帝类 | `DefaultTaskDispatcher.java` | 约 1938 行 / 30+ ObjectProvider |
| ⚠️审计 action 误标 | `web/controller/job/JobController.java` | L195/241/264/302/324/346/369/463 |
| ⚠️CPU 评分 bug | `core/dispatch/SmartRoutingSelector.java` | `getCpuUsage()` 读本地 CPU 给所有节点打分 |
| ⚠️N+1 | `AnomalyRecoveryScanner.java` / `JobLogContentServiceImpl.java` / `DagInstanceExecutor.java` | `recoverOfflineNode` / `batchSave` / `findRunningNodesByJobId` |
