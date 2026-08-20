# ydsz-cronjob

> 分布式任务调度引擎（自研），对标 XXL-Job / PowerJob / ElasticJob / SchedulerX

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9006**（按构建顺序 7/10） |
| **服务名** | `ydsz-cronjob` |
| **构建顺序** | 7/10 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 核心职责

本模块是 YDSZ 的**自研分布式任务调度引擎**，对标 XXL-Job + PowerJob + ElasticJob + SchedulerX。

### 1. 核心能力

| 能力 | 说明 |
|---|---|
| **Leader 选举** | 基于 Redis 分布式锁 + 租约续期，多实例下保证任务不重复执行 |
| **多分区调度** | `leader.partition.enabled=true` 启用多 Active Leader 分区调度，提升吞吐量 |
| **节点发现** | Nacos 服务发现（推荐）/ DB 心跳表（向后兼容） |
| **任务调度** | Cron 表达式 + 固定频率 + 固定延迟 + API 触发 |
| **秒级预读调度** | `preload.enabled=true` 启用内存精准触发（预读窗口内 CRON 任务毫秒级派发，主扫描器兜底） |
| **分片广播** | 单机串行 / 广播并行 / 分片 MapReduce（平均 / 一致性哈希策略，`sharding.strategy` 切换） |
| **故障转移** | `AnomalyRecoveryScanner` 定时扫描 + 节点下线事件即时触发，宕机节点 RUNNING 任务自动转移 |
| **异常自愈** | `AnomalyRecoveryScanner` 检测卡死任务并自动修复 + 重新派发 |
| **租户隔离** | `isolation-strategy: tenant` / `job_group` |
| **告警通道** | 消息中心 Feign + common-notify IM 直推（飞书/钉钉/企业微信），SMS 仅 ERROR/CRITICAL 级别 |
| **日志归档** | 每天凌晨 3 点清理 30 天前的日志 |
| **配额管理** | 租户级任务数 / 并发 / 日执行量 |
| **HTTP 任务** | `jobType=HTTP` 内置 HTTP 调用处理器 |
| **脚本沙箱** | `sandbox.enabled`（默认开启）进程级隔离；`sandbox.docker-enabled=true` 时 Docker 容器级隔离（网络/资源/权限受限） |
| **调度器-执行器分离** | `scheduler-executor-separation.enabled` Leader 仅调度，Worker 执行 |
| **Webhook** | 出站回调 + HMAC-SHA256 签名 + 5 事件类型 + 失败指数退避重试（1s/5s，最多 3 次） |

### 2. 关键 Controller（基路径 `/api/v1/cronjob`，共 17 个）

| 路径前缀 | Controller | 作用 |
|---|---|---|
| `/job` | `JobController` | 任务 CRUD + 版本回滚 |
| `/job/history` | `JobHistoryController` | 历史任务 + 版本管理 |
| `/job/alert` | `AlertController` | 告警规则管理 + 告警日志 |
| `/job/dag` | `JobDagController` | DAG 定义管理 |
| `/job/dag/instance` | `JobDagInstanceController` / `DagInstanceControlController` | DAG 实例控制（启动/暂停/恢复） |
| `/job/task` | `JobTaskController` | 分片任务管理 |
| `/job/webhook` | `JobWebhookController` | WebHook 订阅管理 |
| `/job/stats` | `JobStatsController` | 任务统计 |
| `/job/glue` | `GlueCodeController` | 胶水代码在线编辑 |
| `/job/connector` | `ConnectorController` | 数据连接器管理 |
| `/job/group` | `JobGroupController` | 任务分组管理 |
| `/job/queue` | `JobQueueController` | 任务队列视图 |
| `/job/topology` | `TaskTopologyController` | 任务拓扑视图 |
| `/monitor/diagnosis` | `JobDiagnosisController` | 任务诊断 |
| `/calendar` | `ScheduleCalendarController` | 调度日历 |
| `/internal` | `InternalJobController` | 内部调用端点 |

## 数据库表设计

实体 `@TableName` 共映射 **18 张表**（DDL 由各部署环境统一维护，不在模块内）：

| 业务域 | 表名 | 说明 |
|---|---|---|
| **任务定义** | `ydsz_job` | 任务主表（cron/频率/分片/隔离策略） |
| | `ydsz_job_glue` | 胶水代码（Groovy/Java/Python/Shell） |
| **调度** | `ydsz_job_task` | 调度任务（待派发/运行中） |
| | `ydsz_job_history` | 历史任务（已完成） |
| **DAG** | `ydsz_job_dag` | DAG 定义 |
| | `ydsz_job_dag_version` | DAG 版本 |
| | `ydsz_job_dag_instance` | DAG 实例 |
| | `ydsz_job_dag_node_instance` | DAG 节点实例 |
| **执行日志** | `ydsz_job_log` | 执行日志（分页） |
| | `ydsz_job_log_content` | 日志详情（TOAST 大字段） |
| **告警** | `ydsz_alert_dispatch` | 告警派发日志 |
| | `ydsz_job_alert_rule` | 告警规则（失败/超时/阻塞） |
| **执行器** | `ydsz_job_node` | 执行器节点（注册/心跳） |
| **Webhook** | `ydsz_job_webhook` | 任务完成回调 |
| **产物** | `ydsz_job_artifact` | 任务产物（报表/MinIO） |
| **统计** | `ydsz_job_daily_stats` | 每日统计（成功率/平均耗时） |
| **配额** | `ydsz_tenant_quota` | 租户级任务数/并发/日执行量 |
| **Outbox 事件** | `ydsz_job_outbox` | 领域事件 Outbox（事务消息） |

## 目录结构

```
ydsz-cronjob/                          # 父 POM
├── pom.xml
├── ydsz-cronjob-api/                  # Feign API + Fallback
│   ├── src/main/java/com/njydsz/cronjob/api/
│   │   ├── client/                    # CronjobServiceClient
│   │   └── fallback/                  # CronjobServiceClientFallback
│   └── pom.xml
├── ydsz-cronjob-domain/               # 领域层（Entity / DTO / VO）
│   ├── src/main/java/com/njydsz/cronjob/domain/
│   │   ├── entity/                    # 实体（无 DO 后缀，共 17 个）
│   │   │   ├── dag/                   # JobDag / JobDagInstance / JobDagNodeInstance / JobDagVersion
│   │   │   ├── job/                   # Job / JobAlertLog / JobAlertRule / JobArtifact
│   │   │   │                          # JobHistory / JobNode / JobTask / JobWebhook / TenantQuota
│   │   │   └── schedule/              # GlueCode（胶水代码）
│   │   ├── dto/                       # JobBatchDTO / JobRelationSaveDTO / alert / dag / post / put
│   │   └── vo/                        # 16 个 VO（含 JobVO / JobDagVO / JobLogVO 等）
│   └── pom.xml
├── ydsz-cronjob-infra/                # 基础设施层（Mapper）
│   ├── src/main/java/com/njydsz/cronjob/infra/mapper/
│   └── pom.xml
├── ydsz-cronjob-server/               # 核心服务层
│   ├── src/main/java/com/njydsz/cronjob/server/
│   │   ├── config/                    # CronjobProperties + CronjobAutoConfiguration
│   │   ├── core/                      # 核心调度引擎（80 个类）
│   │   │   ├── DagDefinitionCodec.java     # DAG 编解码
│   │   │   ├── DagInstanceExecutor.java    # DAG 执行器
│   │   │   ├── DefaultTaskDispatcher.java # 任务派发器
│   │   │   ├── healing/AnomalyRecoveryScanner.java # 故障转移 + 异常自愈
│   │   │   ├── dispatch/JobScanner.java   # 任务扫描器
│   │   │   ├── LeaderElector.java          # Leader 选举
│   │   │   ├── LockKeyUtil.java            # 锁 key 工具 + Lua 脚本常量
│   │   │   ├── logger/LogStreamManager.java # 日志流管理
│   │   │   ├── dispatch/TimeoutMonitor.java # 超时监控
│   │   │   ├── alert/AlertDispatcher.java  # 告警派发
│   │   │   ├── sharding/                   # 平均分片策略 + 分片重平衡
│   │   │   └── ...                         # 其余 70 个类
│   │   ├── handler/                   # 业务 JobHandler
│   │   ├── health/                    # CronjobHealthIndicator
│   │   ├── metrics/                   # CronjobMetrics（Prometheus 指标）
│   │   ├── queue/                     # 持久化重试队列
│   │   ├── service/                   # 业务 Service（8 个接口 + 实现）
│   │   └── vo/
│   ├── src/main/resources/
│   │   ├── META-INF/
│   │   │   ├── additional-spring-configuration-metadata.json  # 60 个配置项
│   │   │   └── spring/
│   │   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   └── mapper/                    # MyBatis XML
│   └── pom.xml
├── ydsz-cronjob-web/                  # Web 层（Controller + 启动类）
│   ├── src/main/java/com/njydsz/cronjob/web/
│   │   ├── CronjobApplication.java   # Spring Boot 启动类
│   │   └── controller/                # 16 个 Controller
│   └── pom.xml
└── README.md
```

## 配置文件

### 核心配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.leader.enabled` | `true` | 启用 Leader 选举 |
| `ydsz.cronjob.leader.role` | `ydsz-job-scheduler` | 角色标识 |
| `ydsz.cronjob.leader.lease-seconds` | `30` | 租约时长 |
| `ydsz.cronjob.leader.renew-interval-seconds` | `10` | Leader 续期间隔（秒） |
| `ydsz.cronjob.leader.partition.enabled` | `false` | 多分区调度 |
| `ydsz.cronjob.leader.partition.total-partitions` | `4` | 分区总数 |
| `ydsz.cronjob.leader.partition.hash-strategy` | `job_key` | 分片分配策略（job_key/job_group） |
| `ydsz.cronjob.scanner.interval-ms` | `5000` | 任务扫描间隔 |
| `ydsz.cronjob.scanner.batch-size` | `500` | 单批触发任务数 |
| `ydsz.cronjob.scanner.lock-ttl-seconds` | `30` | 扫描锁 TTL（秒） |
| `ydsz.cronjob.scanner.misfire-grace-minutes` | `30` | Misfire 宽容窗口（分钟） |
| `ydsz.cronjob.scanner.parallel-dispatch-enabled` | `true` | 并行派发 |
| `ydsz.cronjob.scanner.parallel-dispatch-pool-size` | `8` | 并行派发线程池大小 |
| `ydsz.cronjob.preload.enabled` | `false` | 秒级预读调度（CRON 任务毫秒级触发） |
| `ydsz.cronjob.preload.window-seconds` | `30` | 预读窗口（秒） |
| `ydsz.cronjob.preload.scan-interval-ms` | `3000` | 预读扫描周期（毫秒） |
| `ydsz.cronjob.preload.batch-size` | `200` | 单批预读最大任务数 |
| `ydsz.cronjob.sharding.strategy` | `average` | 分片策略：`average`（轮询）/ `consistent_hash`（一致性哈希） |
| `ydsz.cronjob.executor.register-on-startup` | `true` | 启动时注册到节点表 |
| `ydsz.cronjob.executor.heartbeat-interval-seconds` | `10` | 心跳上报间隔（秒） |
| `ydsz.cronjob.executor.offline-threshold-seconds` | `30` | 节点离线判定阈值（秒） |
| `ydsz.cronjob.executor.drain-on-shutdown` | `true` | 优雅下线时排空在执行任务 |
| `ydsz.cronjob.executor.drain-timeout-seconds` | `60` | 排空超时时间（秒） |
| `ydsz.cronjob.executor.max-concurrent` | `16` | 最大并发 |
| `ydsz.cronjob.executor.isolation-strategy` | `none` | `none` / `tenant` / `job_group` |
| `ydsz.cronjob.executor.queue-capacity` | `32` | 线程池队列容量 |
| `ydsz.cronjob.executor.thread-name-prefix` | `job-exec-` | 线程名前缀 |
| `ydsz.cronjob.executor.tenant-pool-size` | `10` | 租户隔离池核心线程数 |
| `ydsz.cronjob.executor.tenant-pool-queue-capacity` | `200` | 租户隔离池队列容量 |
| `ydsz.cronjob.executor.isolation-buckets` | `8` | 分桶隔离的桶数量 |

### 故障转移 & 异常自愈（`ydsz.cronjob.anomaly-recovery.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.anomaly-recovery.failover-enabled` | `true` | 启用故障转移 |
| `ydsz.cronjob.anomaly-recovery.self-healing-enabled` | `false` | 启用异常自愈 |
| `ydsz.cronjob.anomaly-recovery.scan-interval-seconds` | `30` | 扫描间隔（秒） |
| `ydsz.cronjob.anomaly-recovery.scan-node-limit` | `10` | 单批最多扫描节点数 |
| `ydsz.cronjob.anomaly-recovery.failover-task-limit` | `50` | 单节点最多转移任务数 |
| `ydsz.cronjob.anomaly-recovery.stuck-threshold-seconds` | `300` | 卡死超时阈值 |
| `ydsz.cronjob.anomaly-recovery.max-heal-per-scan` | `20` | 单次扫描最大修复任务数 |
| `ydsz.cronjob.anomaly-recovery.auto-redispatch` | `true` | 是否自动重新派发修复后的任务 |
| `ydsz.cronjob.anomaly-recovery.max-redispatch-retries` | `3` | 重新派发最大重试次数 |

### 高级功能

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.adaptive-batch.enabled` | `false` | 自适应批量调度 |
| `ydsz.cronjob.adaptive-batch.min-batch-size` | `50` | 最小批量大小 |
| `ydsz.cronjob.adaptive-batch.max-batch-size` | `1000` | 最大批量大小 |
| `ydsz.cronjob.map-reduce.enabled` | `true` | MapReduce 分布式并行 |
| `ydsz.cronjob.map-reduce.max-parallel-sub-tasks` | `8` | 最大并行子任务数 |
| `ydsz.cronjob.scheduler-executor-separation.enabled` | `true` | 调度器-执行器分离 |
| `ydsz.cronjob.remote.enabled` | `true` | 远程派发 |
| `ydsz.cronjob.remote.connect-timeout-seconds` | `5` | 远程派发连接超时（秒） |
| `ydsz.cronjob.remote.request-timeout-seconds` | `60` | 远程派发请求超时（秒） |
| `ydsz.cronjob.remote.fallback-to-local` | `true` | 远程失败降级本地 |
| `ydsz.cronjob.sandbox.enabled` | `false` | 脚本执行沙箱 |
| `ydsz.cronjob.sandbox.docker-enabled` | `false` | Docker 容器沙箱 |
| `ydsz.cronjob.quota.enabled` | `false` | 租户配额 |
| `ydsz.cronjob.log-retention.retention-days` | `30` | 日志保留天数 |
| `ydsz.cronjob.log-retention.batch-size` | `1000` | 单批删除条数 |
| `ydsz.cronjob.log-retention.cron` | `0 0 3 * * ?` | 定时清理 cron |
| `ydsz.cronjob.http.connect-timeout-seconds` | `10` | HTTP 任务连接超时 |
| `ydsz.cronjob.http.request-timeout-seconds` | `30` | HTTP 任务请求超时 |
| `ydsz.cronjob.http.success-status-range` | `200-299` | HTTP 任务成功状态码范围 |
| `ydsz.cronjob.http.follow-redirects` | `true` | HTTP 任务是否跟随重定向 |
| `ydsz.cronjob.alert.scan-interval-ms` | `300000` | 告警扫描间隔 |
| `ydsz.cronjob.alert.rule-cache-ttl-seconds` | `60` | 告警规则本地缓存 TTL（秒） |
| `ydsz.cronjob.timeout-monitor.interval-ms` | `10000` | 超时监控扫描间隔（毫秒） |
| `ydsz.cronjob.slow-task-detector.interval-ms` | `30000` | 慢任务诊断扫描间隔（毫秒） |

### 分布式锁配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.job-lock-ttl` | `5m` | 分布式锁默认 TTL |
| `ydsz.cronjob.job-lock-ttl-min` | `30s` | 任务级 TTL 下限 |
| `ydsz.cronjob.job-lock-ttl-max` | `24h` | 任务级 TTL 上限 |
| `ydsz.cronjob.scheduler-pool-size` | `8` | 调度器线程池大小 |
| `ydsz.cronjob.scheduler-await-termination-seconds` | `30` | 调度器优雅关闭等待时间（秒） |

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-cronjob spring-boot:run
```

## Feign 接口

被 `CronjobServiceClient` 调用（Feign 远程调用任务数据）。

## 常见问题

### Q1：任务重复执行

- 检查 `ydsz.cronjob.leader.enabled=true`
- 同一 Nacos namespace 下只允许一个 Leader
- 检查 Redis 分布式锁是否被异常持有

### Q2：故障转移不生效

- 检查 `ydsz.cronjob.anomaly-recovery.failover-enabled=true`
- `JobNodeHeartbeat` 是否在执行器节点正常上报
- 离线阈值（`ydsz.cronjob.executor.offline-threshold-seconds`）是否合理

### Q3：DAG 任务卡住

DAG 节点依赖检查失败时任务会卡 PENDING。检查：
- 节点依赖关系是否循环
- 父节点状态是否为 SUCCESS

### Q4：告警未收到 IM 通知

- 检查 common-notify 的 `AsyncNotifyService` 是否正常初始化并在 classpath
- IM 通知为非阻塞补充通道，主渠道仍为消息中心 Feign 调用

---

> 本模块**所有 JobHandler 必须实现幂等**（Redis SET NX EX 或 DB 唯一约束），
> 因为故障转移会重新派发任务，原任务可能已经执行了一部分。
