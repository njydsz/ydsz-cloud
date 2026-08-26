# ydsz-cronjob 竞品对标与优化建议报告

> 基准：当前工作区最新代码（322 个 Java 文件，6 个子模块：api / domain / infra / server / app / web，DDD 分层）
> 对标对象：XXL-Job（社区版）、PowerJob、阿里云 SchedulerX 2.0、Apache ElasticJob
> 生成时间：2026-08-26

---

## 一、现状盘点（代码事实）

### 1.1 能力总览

| 能力域 | 现状 | 关键证据 |
|---|---|---|
| 调度模型 | Leader 单点扫描（5s 轮询 DB，`FOR UPDATE SKIP LOCKED` 抢占，batchSize=500）+ 可选分区多 Active + 可选毫秒级内存预读（默认关） | `JobScanner.java:187`、`TaskPreloadScheduler.java:103` |
| 调度类型 | CRON（Spring `CronExpression`，支持秒级字段与任务级时区）/ FIXED_RATE / FIXED_DELAY / API | `ScheduleType.java:18-26`、`NextFireTimeCalculator.java:121` |
| 防重复 | 三层兜底：DB 行锁 + next_fire_time CAS + Redis 任务锁（common-lock WatchDog 续期，回退 SETNX+Lua 释放） | `JobLockGuard.java:101-170`、`JobScanner.java:52-58` |
| 漏触发 | FIRE_NOW / SKIP / COALESCE 三策略，宽限窗口可配（默认 30min） | `MisfirePolicy.java:28-49` |
| 重试 | 异步延迟重试，FIXED/EXPONENTIAL 退避（封顶 5min），任务级 maxRetries | `RetryScheduler.java:75-139` |
| 高可用 | Redisson RLock + WatchDog Leader 选举（全局或分区），**无 fencing token** | `RedissonLeaderElector.java:121` |
| 节点治理 | Nacos 发现（默认）/ DB 心跳发现（10s 心跳、30s 判死、30min 清除），下线即时故障转移 | `JobNodeHeartbeat.java`、`JobNodeReaper.java`、`AnomalyRecoveryScanner.java` |
| 路由 | round_robin / least_load + 分片广播（平均 / 自研一致性哈希 160 虚拟节点）+ 失败换节点重试 + 本地降级 | `WorkerNodeSelector.java:99-146`、`ConsistentHashShardingStrategy.java:59` |
| 执行类型 | BEAN / GLUE（Groovy·Python·Shell·JS，AST 沙箱 + 版本管理 + 编译缓存）/ HTTP / SHELL / MAP / MAP_REDUCE；进程级沙箱，可选 Docker CLI 容器沙箱 | `GlueJobHandler.java`、`DockerSandboxExecutor.java:122`、`MapTaskExecutor.java` |
| 并发/配额 | Redis 全局并发闸（按在线节点动态扩容 + 定期校准）、租户三类配额（任务数/并发/日执行量，默认关）、JVM 分桶线程池 | `GlobalConcurrencyController.java`、`TenantQuotaServiceImpl.java:52-64` |
| 日志 | Disruptor 异步攒批写库（50 条/批）+ SSE 实时滚动 + 30 天保留定时清理 | `DisruptorLogEventHandler.java:46`、`LogStreamManager.java` |
| DAG | 校验（无环/根节点/规模上限）+ 事件驱动串联 + 4 种失败策略 + 上下文合并；**无独立 cron 触发** | `DagDefinitionValidator.java`、`DagInstanceExecutor.java` |
| 告警 | 6 通道（邮件/钉钉/企微/飞书/Webhook/SMS）× 6 类型（失败/超时/慢/失败率/P95/SLA 预警），CAS 冷却去重，经消息中心路由 | `AlertChannel.java`、`AlertType.java`、`AlertDispatcher.java` |
| 可靠性 | 事务性 Outbox（1s 扫描、指数退避、3 次死信、至少一次）+ 事件 SETNX 去重 + 幂等锁 | `OutboxPublisher.java:40-46` |
| 特色 | 金丝雀 handler（jobKey 稳定哈希分桶）、任务健康诊断评分、工作日历过滤、线程池热更新 + Actuator 端点、Micrometer/Prometheus + Grafana 看板 | `CanaryReleaseService.java`、`TaskDiagnosisService.java`、`CalendarScheduleFilter.java`、`ThreadPoolMetricsEndpoint.java` |
| 规范 | 全模块零 fastjson/jackson import、零 Caffeine 直连，统一 YdszJson + ConcurrentHashMap 手写缓存，符合云顶规范 | 全量 grep 验证 |

### 1.2 已确认的问题清单（本报告建议的事实依据）

| # | 问题 | 证据 |
|---|---|---|
| F1 | `JobRetryHandler` 为死代码（仅自身定义，零引用），与 `RetryScheduler` 两套重试并存 | 全仓 grep 仅 1 处命中 |
| F2 | 告警日志表已合并到 `ydsz_alert_dispatch`，但 `LogCleaner.java:32`、`AlertEvent`、`AlertDispatcher.java:47`、`AlertService.java:26` 等 5+ 处注释仍写 `ydsz_job_alert_log` | `JobAlertLog.java:17-18`（P3-1-merge 说明）vs 残留注释 |
| F3 | `db/changelog/` 为空目录，`db/README.md` 声称基线 `V1__init_schema.sql` 存在，实际 DDL 在仓库根 `data/mysql/ydsz-cronjob.sql`（18 张表）——Flyway 文档与实现漂移 | 目录 ls 为空 |
| F4 | Leader 锁无 fencing token / epoch，Redis 主从切换存在理论双主窗口（WatchDog 租约 30s 内） | `RedissonLeaderElector.java` 全文无 epoch 机制 |
| F5 | 测试仅 5 个（misfire 枚举、cron 计算、2 个分片策略、内网过滤器），核心链路（扫描/派发/选举/故障转移/DAG/Outbox）零测试 | src/test 目录全量 |
| F6 | ~~无 RBAC~~（**已修正**）：后端 18 个 Controller 中 15 个已有 `@AuthApiPermission` 权限码注解（共 85 处），但 `JobLogStreamController`/`JobTaskController`/`JobDiagnosisController` 三个 Controller **零权限注解**；内部 token 未配置时 `InternalTokenFilter` 直接放行；前端 cronjob-web 无按钮级权限控制 | `JobController.java` 等 vs `InternalTokenFilter.java:72-75`、前端全 src 无 `v-access` |
| F7 | DAG 仅手动/API 触发，不支持 cron 定时触发 | `JobDagServiceImpl.triggerDag` 为唯一入口 |
| F8 | 配置面过大：server/config 下 23 个配置类，README 中 151 个 `ydsz.cronjob.*` 配置键 | 统计 |
| F9 | `AuditOutboxSubscriber` 仅 log.info 占位 | 类内注释自述 |
| F10 | `WorkerNodeSelector` 与 `NodeSelector`/`LeastLoadNodeSelector` 两套节点选择器并存，职责重叠 | 两接口同时被注入 |
| F11 | `JobVO` 缺 `slaMs` 字段（实体有），OpenAPI 出参不完整 | `Job.java:65` vs `JobVO.java` |
| F12 | 执行记录三表并存（`ydsz_job_log` / `ydsz_job_task` / `ydsz_job_history`），语义有重叠 | DDL + Repository 层 |

---

## 二、竞品能力矩阵

| 能力 | ydsz-cronjob | XXL-Job | PowerJob | SchedulerX 2.0 | ElasticJob |
|---|---|---|---|---|---|
| 调度精度 | 5s 轮询（预读开启后毫秒级，**默认关**） | ~1s（DB 轮询+时间轮） | 秒级 | 秒级/毫秒级 | 秒级（Quartz） |
| 调度类型 | CRON/固定频率/固定延迟/API | CRON | CRON/固定频率/固定延迟/OpenAPI | CRON/固定频率/固定延迟/日历/API | CRON |
| 分片广播 | ✅ 平均+一致性哈希，事件驱动重平衡 | ✅ 静态取模 | ✅ | ✅ | ✅ ZK 事件驱动（标杆） |
| MapReduce | ✅ 动态子任务+多节点并行+reduce | ❌ | ✅ | ✅ | ❌ |
| 工作流/DAG | ✅（无 cron 触发、无审批外的网关节点） | 仅父子任务链 | ✅ | ✅（可视化） | ❌ |
| GLUE 在线代码 | ✅ 4 语言+版本管理+AST 沙箱 | ✅ Java | ✅ 5 语言+容器任务 | ✅ | ❌ |
| 路由策略 | 轮询/最少负载/分片/故障转移 | 10 种（含随机/一致性哈希/最久未用） | 负载均衡 | 多种 | 分片即路由 |
| 故障转移 | ✅ 30s 判死+即时重派 | ✅ | ✅ | ✅ | ✅（标杆） |
| 漏触发补偿 | ✅ 3 策略 | ✅ | ✅ | ✅ | ✅ |
| 告警 | ✅ 6 通道×6 类型（领先） | 邮件（弱） | Webhook | 多维（强） | ❌ |
| 权限/RBAC | ❌ | ✅ 登录+角色 | ✅ 应用级密码隔离 | ✅ RAM | ❌ |
| 多租户 | ✅ 配额+隔离（逻辑） | ❌ | 应用分组 | Namespace 物理隔离 | ❌ |
| 灰度/诊断/SLA | ✅（独有） | ❌ | ❌ | 部分 | ❌ |
| 可观测 | ✅ Micrometer+Prometheus+Grafana+SSE 日志 | 弱 | 在线日志 | 强（云原生） | OpenTracing |
| 控制台 UI | REST API（无内建 UI） | ✅ 内建（弱但全） | ✅ 内建（强） | ✅ 商业级 | 独立 console（弱） |

**总体定位**：ydsz-cronjob 在告警、多租户、灰度、诊断、SLA、可靠性（Outbox）维度已**超越 XXL-Job 社区版与 ElasticJob**，与 PowerJob 相当、部分超出；主要差距集中在 **SchedulerX 级的能力**：调度精度默认态、DAG 定时触发与工作流可视化深度、RBAC、命名空间物理隔离，以及工程成熟度（测试、文档、DDL 治理）。

---

## 三、五维度优化建议（P0 = 立即 / P1 = 近期 / P2 = 规划）

### 维度一：架构优化

| 优先级 | 建议 | 依据/落点 |
|---|---|---|
| **P0** | **Leader 锁增加 fencing token（epoch）**：派发线程在执行前校验 epoch，防止 Redis 主从切换窗口内双主双写。实现：Leader 持锁成功后在 Redis 写入单调递增 epoch（INCR `ydsz:job:leader:epoch:{role}`），`DefaultTaskDispatcher` 执行前比对当前 epoch 与持锁时 epoch，不一致则放弃执行 | F4；对标 SchedulerX/大厂调度系统的 fencing 实践 |
| **P0** | **收敛两套节点选择器**：`WorkerNodeSelector`（调度派发用）与 `NodeSelector`/`LeastLoadNodeSelector` 合并为单一策略接口 + 策略枚举（round_robin / least_load / consistent_hash / random），对齐 XXL-Job 的路由策略模型，顺手补齐"随机"与"一致性哈希路由" | F10；`WorkerNodeSelector.java:99` |
| **P1** | **执行记录三表归一**：`ydsz_job_log`（调度日志）/`ydsz_job_task`（执行记录）/`ydsz_job_history`（历史）语义重叠，建议保留 `job_log`（实例级）+ `job_task`（子任务/分片级），`job_history` 定位为快照归档或下线，写路径同步收敛 | F12 |
| **P1** | **DDL 治理落地**：将 `data/mysql/ydsz-cronjob.sql` 按 Flyway 版本化迁入模块内 `db/changelog/V1__init_schema.sql`（或更新 `db/README.md` 指认真实位置），消除 F3 漂移；多租户/分库扩展需要可重放的 DDL 基线 | F3 |
| **P1** | **Worker 端执行隔离成独立可部署单元**：当前调度器-执行器分离已默认开启（`SchedulerExecutorSeparationConfig`），建议把 `/internal/execute*` 执行面抽成轻量 executor-starter（类似 XXL-Job executor / PowerJob worker），业务方按需内嵌，调度中心不携带业务 handler | 对标 XXL-Job admin/executor 拆分 |
| **P2** | **租户隔离从逻辑走向命名空间级**：`tenantId` 目前仅配额与数据过滤，参考 SchedulerX Namespace，增加任务分组（jobGroup）与执行器集群（`cluster` 字段已有）的强制绑定校验，防止跨租户误调度 | `Job.java:80` cluster 字段已在但未充分利用 |

### 维度二：功能增强

| 优先级 | 建议 | 依据/落点 |
|---|---|---|
| **P0** | **DAG 支持 cron 定时触发**：`ydsz_job_dag` 增加 cronExpression/scheduleType/nextFireTime，纳入 `JobScanner` 扫描域（或独立 DagScanner），使工作流可定时自动跑批——这是 PowerJob/SchedulerX 工作流的基本盘 | F7 |
| **P0** | **补 RBAC 最小闭环**：操作面（任务的启停/触发/删除/GLUE 保存）接入 common-web 的登录态与角色鉴权，至少区分 viewer/operator/admin；`InternalTokenFilter` 在 token 未配置时**默认拒绝**而非放行（白名单内网 CIDR 兜底） | F6；XXL-Job/PowerJob 均有权限层 |
| **P1** | **OpenAPI 触发补齐回调与幂等参数**：`trigger` 接口支持 `bizId` 幂等键（已有幂等锁机制可复用）与同步/异步模式选择，对齐 SchedulerX OpenAPI；补 `JobVO.slaMs` 出参 | F11；`DefaultTaskDispatcher.java:1146` 幂等锁 |
| **P1** | **审计订阅器落库**：`AuditOutboxSubscriber` 对接 common-audit 或独立 `ydsz_job_audit` 表，记录任务变更/触发/删除的操作审计（who/when/what），大厂合规基线 | F9 |
| **P1** | **工作流节点类型按需回加**：DAG 当前仅 TASK/SUB_WORKFLOW/APPROVAL，若业务有分支诉求，按"条件节点（SpEL 已有 `SpelConfig` 可复用）"单点回加，勿一次性回加全套网关 | `SpelConfig` 已存在 |
| **P2** | **任务依赖的数据就绪检查**：对标 Airflow/SchedulerX 的"依赖数据到达才执行"，可选实现 `DependencyPatrolScanner` 已巡检依赖完整性，可扩展为前置条件（precondition）钩子 | `DependencyPatrolScanner.java` |
| **P2** | **K8s CronJob/Job 导出**：为云原生部署场景提供 ydsz_job → K8s CronJob YAML 的一键导出（只读映射），低成本提升部署体验 | 对标 SchedulerX K8s 集成 |

### 维度三：性能提升

| 优先级 | 建议 | 依据/落点 |
|---|---|---|
| **P0** | **预读调度器默认开启并压测调优**：`preload.enabled` 默认 false 导致精度停留在 5s 轮询；建议在预读开启 + 窗口 30s + CAS 互斥让位机制（已完备）下将默认开启，核心链路精度对齐 PowerJob 秒级；先在预发布以 `scanner_due_jobs`/`dispatch_delay` 指标验证 | `TaskPreloadScheduler.java:103`；指标已就绪 |
| **P1** | **扫描查询冷热分离**：`JobScanner` 每 5s 全量 `SELECT ... FOR UPDATE SKIP LOCKED`，建议在 Leader 内存维护 `next_fire_time` 最小堆，DB 仅做 30s 级对账刷新（类 XXL-Job 时间轮预读模式的强化版）；任务量上万时 DB 压力从 12 QPS 轮询降至近零 | `JobScanner.java:187` |
| **P1** | **日志大内容外置**：`ydsz_job_log_content` 行级存库（≤4000 字符截断），大日志任务会撑爆单表；模块已依赖 MinIO 且有 `JobArtifact` 制品体系，建议日志 content 超过阈值（如 64KB）转存 MinIO、库内留引用——复用现有基础设施，零新增依赖 | `JobLogContent.java`、`ArtifactConfig` |
| **P1** | **Outbox 扫描自适应**：当前固定 1s 扫描 + 分布式锁，空转率高；建议空扫描指数退避（1s→5s→30s），有事件即时恢复 1s；或改 `LISTEN/NOTIFY`（PG）/`binlog`（MySQL，重）触发 | `OutboxScanTask.java:36-44` |
| **P2** | **分片任务批量 HTTP 派发的压缩与流水线**：`dispatchShardsBatch` 单请求携带节点全部分片，大分片数（>500）时建议分块流水（边算边发）+ 参数 gzip | `DefaultTaskDispatcher.java:567` |
| **P2** | **统计聚合异步化**：`fireCount/successCount/failCount` 与 `DailyStatsAggregator` 当前在派发收尾链路，评估走 Disruptor 同款异步批写，进一步缩短派发关键路径 | `DailyStatsAggregator.java` |

### 维度四：体验改善（使用与运维体验）

| 优先级 | 建议 | 依据/落点 |
|---|---|---|
| **P0** | **补齐文档与消除注释漂移**：(1) 修正 5+ 处 `ydsz_job_alert_log` 残留注释；(2) `docs/` 目前只有 Grafana JSON，按大厂开源标准补 ARCHITECTURE.md（架构图+端口表 9006+Known Issues+贡献指南），README 已 476 行建议拆分为 quickstart/config-reference/ops 三篇 | F2；docs 目录现状 |
| **P0** | **核心链路测试补齐**（当前仅 5 个测试类）：优先 (1) `JobScanner` 并发抢占（多线程抢同一批任务的唯一性断言）；(2) Leader 切换故障转移（Testcontainers Redis+PG 已在依赖中）；(3) misfire 三策略行为；(4) DAG 失败策略矩阵；(5) Outbox 退避与死信。目标核心包行覆盖 ≥60% | F5 |
| **P1** | **cron 校验接口增强**：`/cron/check` 返回未来 5 次触发时间（带任务时区）+ 与现有日历过滤的联动预览（"该任务下次实际执行时间"），对齐 XXL-Job 控制台的体验细节 | `CalendarScheduleFilter` + `NextFireTimeCalculator` 现成可组合 |
| **P1** | **错误信息可诊断化**：`CronjobExceptionCode` 异常携带 jobKey/triggerType/traceId 三段上下文；`TaskDiagnosisService` 的警告输出到任务详情 API（`/jobs/{id}/diagnosis` 已有，确认前端/OpenAPI 透出完整） | `TaskDiagnosisService.java:55-65` |
| **P1** | **GLUE 编辑体验**：GLUE 版本列表 API 增加 diff（相邻版本 unified diff 计算），回滚接口显式化（当前"回滚=新建版本"，建议 `POST /glue/{jobId}/rollback/{version}` 语义化封装） | `GlueCodeServiceImpl` |
| **P2** | **告警规则模板**：按场景预置规则模板（"连续失败 3 次 + 钉钉"、"P95>SLA80% + 企微"），降低配置心智；AlertRule 已有完整模型，仅需模板常量+批量创建接口 | `AlertRuleSaveDTO` |

### 维度五：过度设计审视（做减法）

> 原则：ydsz-common 体系"最小化外部依赖、绝对可控"。以下不是能力否定，而是**默认态收敛与代码归并**建议。

| 优先级 | 建议 | 依据/落点 |
|---|---|---|
| **P0** | **删除死代码 `JobRetryHandler`**：同步阻塞式重试与 `RetryScheduler` 并存且零引用，直接删除（或移入 `legacy/` 并标注），避免后来者误用导致执行线程被 sleep 阻塞 | F1 |
| **P0** | **配置面收敛**：23 个配置类 / 151 个配置键超出同类项目一个量级（XXL-Job 约 20 项、PowerJob 约 40 项）。建议：(1) 合并为 `CronjobProperties` 的 8~10 个内嵌 record 分组（scanner/preload/cluster/remote/alert/log/quota/dag）；(2) 每个键给出"何时需要改"的一行说明；(3) 默认关闭项（partition/quota/docker/preload）在 README 明确"开启前请读" | F8 |
| **P1** | **Docker 沙箱定位为可选扩展**：`DockerSandboxExecutor` 用 ProcessBuilder 调 docker CLI（实现克制、无 docker-java 依赖，方向正确），但它带来部署环境强假设。建议保持默认关 + 在 README 标注"生产开启需评审"；不作为默认能力宣传 | `DockerSandboxExecutor.java:122` |
| **P1** | **金丝雀统计持久化取舍**：`CanaryReleaseService` 用 ConcurrentHashMap 内存统计成功率，重启即丢。若灰度观测是真实需求则落 `job_log` 维度聚合；若低频使用，建议简化为"路由+手工观察日志"，删掉统计面 | `CanaryReleaseService.java:38` |
| **P1** | **双节点发现策略保一留一**：Nacos 为默认且项目体系已强制 Nacos，`DbNodeDiscoveryStrategy`+`JobNodeHeartbeat`+`JobNodeReaper` 一整套（3 个类+1 张表+心跳写库）实际构成备用路径。建议明确"Nacos 唯一推荐、DB 模式仅作灾备文档保留"，或评估直接移除 DB 模式以砍掉心跳写库开销 | `NacosNodeDiscoveryStrategy.java:41` |
| **P2** | **Disruptor 的必要性记账**：ring buffer 1024 + BlockingWait 仅服务于日志攒批（50 条/批），一个 `ArrayBlockingQueue` + 单消费者攒批即可达到同等效果。不建议现在改（已稳定），但写入 Known Issues："若日志量未达万级/分钟，Disruptor 属超配" | `DisruptorLogPublisher.java:51` |
| **P2** | **分区 Leader 的使用前提文档化**：`PartitionLeaderManager`（默认关）解决的是单 Leader 扫描瓶颈，任务量 <1 万时无收益。README 标注"开启门槛：单表任务 >1 万或扫描周期告警"，避免误开引入多 Active 复杂度 | `PartitionConfig.java:24` |

---

## 四、落地路线建议

**第一阶段（P0，约 1~2 迭代）—— 正确性与安全基线**
1. 删除 `JobRetryHandler` 死代码；修正告警表名注释漂移（F1/F2）
2. Leader fencing token（F4）；InternalTokenFilter 未配置默认拒绝（F6）
3. DAG cron 定时触发（F7）；RBAC 最小闭环（F6）
4. 预读调度器默认开启 + 预发布指标验证；`JobVO` 补 `slaMs`（F11）
5. 核心链路测试 5 组（F5）；docs 补 ARCHITECTURE.md + 修正 db/README 漂移（F3）

**第二阶段（P1）—— 架构收敛与性能**
6. 节点双选择器合并、路由策略补齐（F10）；执行记录三表归一（F12）
7. DDL Flyway 版本化迁入模块（F3）；配置面收敛为分组 record（F8）
8. 扫描冷热分离（内存最小堆）；大日志 MinIO 外置；Outbox 自适应扫描
9. 审计订阅器落库（F9）；cron 校验预览；GLUE diff/rollback API

**第三阶段（P2）—— 体验与云原生**
10. 告警规则模板；诊断透出；K8s CronJob 导出
11. 租户命名空间隔离评审；Docker 沙箱/Disruptor/分区 Leader 的开启门槛写入 Known Issues

---

## 五补、前后端联合分析（ydsz-micro/apps/cronjob-web）

> 前端：pnpm monorepo，自研 micro-kernel 微前端（非 qiankun/模块联邦），Vue3 + element-plus + vxe-table（适配层）+ tailwind，dev 端口 5605，basename `/YDSZ-cron`。实际业务代码约 4,449 行、6 个页面模块，在 8 个微前端中水位倒数第三，且是"后端能力/前端覆盖"落差最大的一个。

### 5补.1 前后端能力缺口（核心事实）

- **API 覆盖**：后端 18 个 REST Controller、约 93 个端点，前端页面实际消费 **33 个（约 35%）**；剔除 InternalJob 后仍有 **12 个能力域完全无前端入口**：

| 完全闲置的能力域 | 闲置端点数 | 后果 |
|---|---|---|
| DAG 实例运行态（JobDagInstance + DagInstanceControl + TaskTopology） | 17 | DAG 只建不看：无实例列表、无运行拓扑、无暂停/恢复/取消/节点重试。后端连 cytoscape/mermaid 两种图数据都备好了 |
| 统计看板（JobStats） | 5 | 无 dashboard；daily/summary/dashboard/recentFailures/heatmap 全闲置，echarts 封装（literule-web 有示范）未复用 |
| GLUE 在线编辑（GlueCode） | 7 | save/versions/diff/rollback/test/template 全闲置；GLUE 任务建了没法在线写代码 |
| 任务版本管理（JobHistory） | 4 | 版本/diff/回滚无入口 |
| SSE 实时日志（JobLogStream） | 1 | 连 API 封装文件都没生成；`shared-auth/src/sse.ts` 通用 SSE 工具无人用 |
| Webhook 管理 | 6 | 无页面 |
| 诊断 / 队列监控（JobDiagnosis/JobQueue） | 2 | 无页面 |
| 日历与触发预览（ScheduleCalendar） | 2 | `getUpcomingFireTimes` 未调用，cron 无"未来 5 次触发"预览 |

- **字段覆盖**：后端可写配置面约 24 个字段，前端表单只暴露 **7 个（约 29%）**。`fixedRateMs/fixedDelayMs/paramsJson/timeoutMs/slowThresholdMs/misfirePolicy/shardTotal/timezone/cluster/lockTtlMs` 等 10 个字段 DTO 已有、只是缺控件（纯前端工作量）；`maxRetries/retryIntervalMs/retryBackoff/blockStrategy/maxConsecutiveFails/autoResumeAfterMinutes/priority/slaMs/canaryRatio/canaryHandler` 等 **10 个字段连 JobPostDTO/JobPutDTO 都没有**，需先动后端契约。FIXED_RATE/FIXED_DELAY 类型选了也没有间隔输入框，**该调度类型实质不可用**。

### 5补.2 已确认的前后端失配 Bug（P0）

| # | Bug | 证据 |
|---|---|---|
| B1 | 任务列表/日志列表传 `pageSize`，后端收 `size`——**每页条数永远走后端默认 20** | `views/job/index.vue:95-99`、`views/jobLog/index.vue:62-66` vs `JobController.java:402`、生成的 `api/job.ts:121` 类型声明就是 `size` |
| B2 | 基座注册表 `redirect: '/YDSZ-cron/jobs'` 与子应用实际路由 `/job/list` 不一致，**激活后首跳 404** | `conf/vite-config/src/micro-apps.config.ts:117` vs `router/routes/modules/cronjob.ts:14-19` |
| B3 | `models.ts:856,861` 生成器缺陷：`DagDefinition.nodes` 类型生成成 `'TASK' \| 'SUB_WORKFLOW' \| 'APPROVAL'[]`、`DagEdge` 是空接口 | gen-contract.py 对嵌套泛型解析缺陷 |

### 5补.3 UI/UX 对标竞品控制台

| 体验项 | XXL-Job Admin | PowerJob 前端 | SchedulerX 控制台 | cronjob-web 现状 |
|---|---|---|---|---|
| 任务列表 | 状态/负责人筛选、下次触发时间、操作列齐全 | 同类 + 运行状态实时刷新 | 多维筛选 + 运行大盘 | 仅 keyword+group 筛选；**无下次触发时间列、无状态筛选、无详情抽屉、无"查看日志"跳转** |
| 任务表单 | 全量配置（路由/阻塞/超时/重试/子任务） | 全量 + 处理器参数 | 全量 + 标签/告警联动 | 7 个字段；FIXED_RATE/DELAY 无间隔输入框 |
| DAG/工作流 | 无（父子任务列表） | 可视化画布（拖拽编排） | 商业级画布 + 运行态染色 | **裸 textarea 写 DSL JSON**，无画布、无只读拓扑 |
| 日志 | 日志详情弹窗 + 滚动查看 | 在线日志实时拉取 | 实时日志 + 诊断 | 只读列表；errorMessage 平铺在列里；无详情、无 SSE |
| 统计看板 | 简单报表 | 任务/实例趋势图 | 多维大盘 | **无**（后端 5 端点闲置） |
| 权限 | 登录 + 角色 | 应用级隔离 | RAM | 路由级权限已接（@ydsz/access），**按钮级零控制**；后端权限码已就绪 |

### 5补.4 前端专项优化建议（按优先级）

**P0（修 bug + 补最短板，1 个迭代内）**
1. 修 B1（pageSize→size）与 B2（redirect 改 `/job/list`）——两行改动，直接影响基本可用性
2. 任务表单补"DTO 已有"的 10 个字段控件（fixedRate/fixedDelay/paramsJson/timeoutMs/slowThresholdMs/misfirePolicy/shardTotal/timezone/cluster/lockTtlMs），FIXED_RATE/DELAY 立即从不可用变可用
3. 后端补 JobPostDTO/JobPutDTO 缺失的 10 个字段（重试/阻塞/SLA/优先级/熔断/灰度），重新跑 gen-contract.py 生成契约
4. 任务列表加：状态筛选、nextFireTime/lastFireTime 列、"查看日志"行内跳转、详情抽屉

**P1（运行态闭环）**
5. **DAG 运行态三件套**：实例列表页（JobDagInstance 10 端点）+ 实例控制（暂停/恢复/取消/节点重试）+ 只读拓扑图。拓扑图第一步不引重型画布库——后端已输出 mermaid 文本与 cytoscape JSON，用轻量渲染（mermaid.js 或自绘 SVG）即可对齐 PowerJob 的"运行态染色"体验；可编辑画布待 literule 规则流画布选型后统一引入
6. **日志体验**：日志详情抽屉（errorMessage/resultJson/paramsJson/traceId）+ SSE 实时滚动（复用 `shared-auth/src/sse.ts`，需 gen-contract 支持 SSE 端点或手写封装）
7. **统计看板**：复用 `@ydsz/plugins/echarts`（literule-web 有完整示范），落地 daily 趋势 + summary 卡片 + recentFailures 列表 + heatmap 四块
8. **权限补齐**：后端补 JobLogStream/JobTask/JobDiagnosis 三个 Controller 的权限码；前端接入按钮级权限（@ydsz/access 能力对齐其他 app）

**P2（体验对标 SchedulerX）**
9. GLUE 在线编辑器页（monaco-editor 按需引入，版本列表 + diff + 回滚 + 测试运行，后端 7 端点全就绪）
10. cron 未来 5 次触发时间预览（scheduleCalendar.getUpcomingFireTimes 现成）+ 日历管理页
11. Webhook 管理页、诊断/队列监控页
12. i18n 实装（locales key 已齐但 0 处 `$t` 调用）、vitest 补关键组件测试
13. 契约生成器修复嵌套泛型解析（B3）

### 5补.5 横向复用机会（monorepo 视角）

- echarts 封装已在 `@ydsz/plugins`，literule-web dashboard 是现成抄板；
- SSE 工具在 `shared-auth/src/sse.ts`，message-web 若已用可作参考；
- 全 monorepo **无任何画布库**（workflow-web 也没有流程设计器）——DAG 画布选型（X6/LogicFlow/vue-flow）建议与 literule 规则流画布、workflow 流程设计器**统一决策一次**，避免三个 app 三种库；
- 统一列表页范式（Page + vxe-grid + useYDSZModal）已成熟，新增页面边际成本低，P1 各页主要是"照范式填内容"。

---

## 六、结论（前后端联合版）

**后端**能力面已越过 XXL-Job 社区版，与 PowerJob 相当；**前端** cronjob-web 只兑现了后端约 1/3 的能力（API 覆盖 35%、可配置字段覆盖 29%），是全模块当前**最大的价值漏损点**——后端的 DAG 运行态、统计看板、GLUE 编辑、实时日志、任务版本管理全部"建成即闲置"。

联合视角下的行动优先级重排：
1. **先修两个低级但致命的失配 bug**（分页 size、注册表 redirect）；
2. **表单补全 + DTO 补字段**让既有能力可配置（纯体力活、收益立竿见影）；
3. **DAG 运行态三件套 + 日志详情/SSE + 统计看板**（P1 三件套）把前端覆盖率从 35% 推到 70%+，体验即可对齐 PowerJob；
4. 后端自身的 P0 项（fencing token、死代码、预读默认开启、权限码补齐三个 Controller）不变，与前端工作可并行。

GLUE 编辑器与可视化 DAG 画布属 P2——前者需引入 monaco，后者建议与 literule/workflow 两个 app 统一画布选型后再动。
