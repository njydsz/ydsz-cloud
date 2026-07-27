# ydsz-cronjob

> 分布式任务调度引擎（自研），对标 XXL-Job / PowerJob / ElasticJob / SchedulerX

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9005**（按构建顺序 6/8） |
| **服务名** | `ydsz-cronjob` |
| **构建顺序** | 6/8 |
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
| **任务调度** | Cron 表达式 + 固定频率 + 固定延迟 + 精准调度（时间轮预加载） |
| **分片广播** | 单机串行 / 广播并行 / 分片 MapReduce / 加权分片 |
| **故障转移** | `FailoverScanner` 定时扫描，节点宕机后自动转移 RUNNING 任务 |
| **自愈系统** | `SelfHealingScanner` 检测卡死任务并自动修复 + 重新派发 |
| **租户隔离** | `isolation-strategy: tenant` / `job_group` |
| **告警通道** | 消息中心 Feign + common-notify IM 直推（飞书/钉钉/企业微信） |
| **告警降噪** | `alert-dedup.enabled` 时间窗口聚合 + 自动升降级通知渠道 |
| **日志归档** | 每天凌晨 3 点清理 30 天前的日志 |
| **配额管理** | 租户级任务数 / 并发 / 日执行量 |
| **HTTP 任务** | `jobType=HTTP` 内置 HTTP 调用处理器 |
| **脚本沙箱** | `sandbox.enabled` 进程级/Docker 容器级隔离执行 Shell/Python |
| **调度器-执行器分离** | `scheduler-executor-separation.enabled` Leader 仅调度，Worker 执行 |

### 2. 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/job` | 任务 CRUD + 版本回滚 |
| `/job/log` | 执行日志（分页） |
| `/job/log-content` | 日志详情（支持慢日志） |
| `/job/slow-log` | 慢执行记录 |
| `/job/alert-log` | 告警日志 |
| `/job/alert` | 告警规则管理 |
| `/job/history` | 历史任务 + 版本管理 |
| `/job/dag` | DAG 定义管理 |
| `/job/dag-instance` | DAG 实例控制（启动/暂停/恢复） |
| `/job/task` | 分片任务管理 |
| `/job/sla` | SLA 规则管理 |
| `/job/webhook` | WebHook 订阅管理 |
| `/job/stats` | 任务统计 |
| `/job/glue` | 胶水代码在线编辑 |
| `/job/connector` | 数据连接器管理 |
| `/schedule-calendar` | 调度日历 |
| `/sse/log` | SSE 日志实时推送 |

## 数据库表设计

本模块在 `deploy/sql/V1.0.0.sql` 中持有 **18 张表**，覆盖任务定义/调度/执行/日志/告警/告警规则/历史/节点/分片关系/统计/产物/胶水代码/租户配额/DAG。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **任务定义** | `ydsz_job` | 任务主表（cron/频率/分片/隔离策略） |
| | `ydsz_job_glue` | 胶水代码（Groovy/Java/Python/Shell） |
| | `ydsz_job_relation` | 父子任务依赖 |
| **调度** | `ydsz_job_task` | 调度任务（待派发/运行中） |
| | `ydsz_job_history` | 历史任务（已完成） |
| **DAG** | `ydsz_job_dag` | DAG 定义 |
| | `ydsz_job_dag_instance` | DAG 实例 |
| | `ydsz_job_dag_node_instance` | DAG 节点实例 |
| **执行日志** | `ydsz_job_log` | 执行日志（分页） |
| | `ydsz_job_log_content` | 日志详情（TOAST 大字段） |
| **告警** | `ydsz_job_alert_log` | 告警日志 |
| | `ydsz_job_alert_rule` | 告警规则（失败/超时/阻塞） |
| **执行器** | `ydsz_job_node` | 执行器节点（注册/心跳） |
| **Webhook** | `ydsz_job_webhook` | 任务完成回调 |
| **产物** | `ydsz_job_artifact` | 任务产物（报表/MinIO） |
| **统计** | `ydsz_job_daily_stats` | 每日统计（成功率/平均耗时） |
| **配额** | `ydsz_tenant_quota` | 租户级任务数/并发/日执行量 |

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
│   │   ├── entity/                    # JobDO / JobLogDO / JobDagDO 等 18 个实体
│   │   ├── dto/                       # JobSaveDTO / JobBatchDTO 等
│   │   └── vo/
│   └── pom.xml
├── ydsz-cronjob-infra/                # 基础设施层（Mapper）
│   ├── src/main/java/com/njydsz/cronjob/infra/mapper/
│   └── pom.xml
├── ydsz-cronjob-server/               # 核心服务层
│   ├── src/main/java/com/njydsz/cronjob/server/
│   │   ├── config/                    # CronjobProperties + CronjobAutoConfiguration
│   │   ├── core/                      # 核心调度引擎（80+ 个类）
│   │   │   ├── DagDefinitionCodec.java     # DAG 编解码
│   │   │   ├── DagInstanceExecutor.java    # DAG 执行器
│   │   │   ├── DefaultTaskDispatcher.java # 任务派发器
│   │   │   ├── FailoverScanner.java        # 故障转移扫描
│   │   │   ├── JobScanner.java             # 任务扫描器
│   │   │   ├── LeaderElector.java          # Leader 选举
│   │   │   ├── LockKeyUtil.java            # 锁 key 工具 + Lua 脚本常量
│   │   │   ├── LogStreamManager.java       # SSE 日志推送
│   │   │   ├── SelfHealingScanner.java     # 自愈系统
│   │   │   ├── TimeoutMonitor.java         # 超时监控
│   │   │   ├── AlertDispatcher.java        # 告警派发
│   │   │   └── ...                         # 其余 70+ 个类
│   │   ├── handler/                   # 业务 JobHandler
│   │   ├── health/                    # CronjobHealthIndicator
│   │   ├── metrics/                   # CronjobMetrics（Prometheus 指标）
│   │   ├── notify/                    # CronjobNotifyHelper（IM 直推）
│   │   ├── queue/                     # 持久化重试队列
│   │   ├── service/                   # 业务 Service（11 个接口 + 实现）
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
│   │   └── controller/                # 17 个 Controller
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
| `ydsz.cronjob.leader.partition.enabled` | `false` | 多分区调度 |
| `ydsz.cronjob.leader.partition.total-partitions` | `4` | 分区总数 |
| `ydsz.cronjob.scanner.interval-ms` | `5000` | 任务扫描间隔 |
| `ydsz.cronjob.scanner.batch-size` | `500` | 单批触发任务数 |
| `ydsz.cronjob.scanner.parallel-dispatch-enabled` | `true` | 并行派发 |
| `ydsz.cronjob.executor.max-concurrent` | `16` | 最大并发 |
| `ydsz.cronjob.executor.isolation-strategy` | `none` | `none` / `tenant` / `job_group` |
| `ydsz.cronjob.executor.queue-capacity` | `32` | 线程池队列容量 |
| `ydsz.cronjob.executor.tenant-pool-size` | `10` | 租户隔离池大小 |

### 故障转移 & 自愈

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.failover.enabled` | `true` | 启用故障转移 |
| `ydsz.cronjob.failover.scan-interval-ms` | `30000` | 扫描间隔（毫秒） |
| `ydsz.cronjob.self-healing.enabled` | `false` | 启用自愈系统 |
| `ydsz.cronjob.self-healing.scan-interval-ms` | `60000` | 自愈扫描间隔（毫秒） |
| `ydsz.cronjob.self-healing.stuck-threshold-seconds` | `300` | 卡死超时阈值 |

### 高级功能

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.cronjob.precise-scheduling.enabled` | `false` | 精准调度（时间轮） |
| `ydsz.cronjob.adaptive-batch.enabled` | `false` | 自适应批量调度 |
| `ydsz.cronjob.alert-dedup.enabled` | `false` | 告警智能降噪 |
| `ydsz.cronjob.map-reduce.enabled` | `true` | MapReduce 分布式并行 |
| `ydsz.cronjob.scheduler-executor-separation.enabled` | `true` | 调度器-执行器分离 |
| `ydsz.cronjob.remote.enabled` | `true` | 远程派发 |
| `ydsz.cronjob.remote.fallback-to-local` | `true` | 远程失败降级本地 |
| `ydsz.cronjob.sandbox.enabled` | `false` | 脚本执行沙箱 |
| `ydsz.cronjob.sandbox.docker-enabled` | `false` | Docker 容器沙箱 |
| `ydsz.cronjob.quota.enabled` | `false` | 租户配额 |
| `ydsz.cronjob.log-retention.retention-days` | `30` | 日志保留天数 |
| `ydsz.cronjob.http.connect-timeout-seconds` | `10` | HTTP 任务连接超时 |
| `ydsz.cronjob.http.request-timeout-seconds` | `30` | HTTP 任务请求超时 |
| `ydsz.cronjob.alert.scan-interval-ms` | `300000` | 告警扫描间隔 |

## 启动

```bash
cd ydsz-backend
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

- 检查 `ydsz.cronjob.failover.enabled=true`
- `JobNodeHeartbeat` 是否在执行器节点正常上报
- 离线阈值（`offline-threshold-seconds`）是否合理

### Q3：DAG 任务卡住

DAG 节点依赖检查失败时任务会卡 PENDING。检查：
- 节点依赖关系是否循环
- 父节点状态是否为 SUCCESS

### Q4：告警未收到 IM 通知

- 检查 `CronjobNotifyHelper` 是否注入（需 common-notify 在 classpath）
- 检查 `AsyncNotifyService` 是否正常初始化
- IM 通知为非阻塞补充通道，主渠道仍为消息中心 Feign 调用

---

> 本模块**所有 JobHandler 必须实现幂等**（Redis SET NX EX 或 DB 唯一约束），
> 因为故障转移会重新派发任务，原任务可能已经执行了一部分。
