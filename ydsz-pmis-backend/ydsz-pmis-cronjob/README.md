# ydsz-pmis-cronjob

> 分布式任务调度引擎（自研）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9005**（按构建顺序 6/8） |
| **服务名** | `ydsz-pmis-cronjob` |
| **构建顺序** | 6/8 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 核心职责

本模块是 PMIS 的**自研分布式任务调度引擎**，对标 XXL-JOB + 自研特性。

### 1. 核心能力

| 能力 | 说明 |
|---|---|
| **Leader 选举** | 基于 DB 行锁（`pmis_job_lock`）+ Redis 分布式锁，多实例下保证任务不重复执行 |
| **节点发现** | Nacos 服务发现（推荐）/ DB 心跳表（向后兼容） |
| **任务调度** | Cron 表达式 + 固定频率 + 固定延迟 |
| **分片广播** | 单机串行 / 广播并行 / 分片 MapReduce |
| **故障转移** | `FailoverScanner` 30s 扫描一次，节点宕机后自动转移 RUNNING 任务 |
| **租户隔离** | `isolation-strategy: tenant` / `job_group` |
| **告警通道** | 飞书 / 短信 / 邮件（可配置 webhook） |
| **日志归档** | 每天凌晨 3 点清理 30 天前的日志 |
| **配额管理** | 租户级任务数 / 并发 / 日执行量 |

### 2. 关键功能

| 类别 | JobHandler |
|---|---|
| **报表** | 每日 / 每周 / 每月报表生成（push 到 MinIO） |
| **对账** | 成本 / 收入 / 工时 / 利润 / 开票 / 回款月度自动对账 |
| **预警** | 预警重试补偿、SLA 计算、巡检 |
| **流程** | 流程超时扫描、归档 |
| **消息** | 消息回执主动拉取、聚合扫描、重试扫描 |
| **资源** | Bench 自动入出池、租户配额检查 |
| **数据** | 数据导出审计、数据迁移 |

### 3. 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/job` | 任务 CRUD |
| `/job/log` | 执行日志（分页） |
| `/job/log-content` | 日志详情（支持慢日志） |
| `/job/slow-log` | 慢执行记录 |
| `/job/alert-log` | 告警日志 |
| `/job/history` | 历史任务 |
| `/job/monitor` | 实时监控 |
| `/job/dag` | DAG 可视化数据 API |
| `/job/node` | 执行器节点管理 |

## 启动顺序

依赖 `common` + `nacos`，**应在 `project` 之后**启动（通过 Feign 拉取任务数据）。

## 目录结构

```
ydsz-pmis-cronjob/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/cronjob/
    │   ├── CronJobApplication.java
    │   ├── controller/
    │   ├── service/
    │   │   ├── JobAdminService.java
    │   │   ├── JobDispatcher.java
    │   │   ├── JobExecutor.java
    │   │   ├── FailoverScanner.java
    │   │   ├── LeaderElector.java
    │   │   ├── DagInstanceExecutor.java
    │   │   └── AlertService.java
    │   ├── jobhandler/        # 业务 JobHandler
    │   ├── mapper/
    │   ├── entity/
    │   ├── enums/             # JobStatus / TriggerType / IsolationStrategy
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── application.yml
    │   ├── mapper/
    │   │   └── JobMapper.xml
    │   └── nacos-config/
    │       ├── ydsz-pmis-cronjob-dev.yaml
    │       ├── ydsz-pmis-cronjob-sit.yaml
    │       └── ydsz-pmis-cronjob-uat.yaml
    └── test/
        └── java/
            ├── JobDispatcherTest.java
            ├── FailoverScannerTest.java
            ├── DagInstanceExecutorTest.java
            └── ...
```

## 配置文件

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `pmis.cronjob.node-discovery.type` | `nacos` | `nacos` / `db` |
| `pmis.cronjob.leader.enabled` | `true` | 启用 Leader 选举 |
| `pmis.cronjob.leader.role` | `pmis-job-scheduler` | 角色标识 |
| `pmis.cronjob.failover.enabled` | `true` | 启用故障转移 |
| `pmis.cronjob.failover.scan-interval-seconds` | `30` | 扫描间隔 |
| `pmis.cronjob.scanner.interval-ms` | `5000` | 任务扫描间隔 |
| `pmis.cronjob.executor.max-concurrent` | `16` | 最大并发 |
| `pmis.cronjob.executor.isolation-strategy` | `none` | `none` / `tenant` / `job_group` |
| `pmis.cronjob.quota.enabled` | `false` | 启用租户配额 |
| `pmis.cronjob.log-retention.retention-days` | `30` | 日志保留天数 |
| `pmis.cronjob.alert.feishu.enabled` | `false` | 飞书告警 |
| `pmis.cronjob.alert.sms.enabled` | `false` | 短信告警 |

## 启动

```bash
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common -am install -DskipTests
mvn -pl ydsz-pmis-cronjob spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-cronjob -am test
```

测试覆盖：
- `JobDispatcher` 任务派发
- `FailoverScanner` 故障转移
- `DagInstanceExecutor` DAG 拓扑
- `QuotaService` 配额
- `AlertService` 告警
- `LogRetentionScheduler` 日志清理

## Feign 接口

被 `MessageFeignClient` / `ExecutionClient` 调用（推 / 拉任务数据）。

## 常见问题

### Q1：任务重复执行

- 检查 `pmis.cronjob.leader.enabled=true`
- 同一 Nacos namespace 下只允许一个 Leader
- 检查 `pmis_job_lock` 表的锁是否被异常持有

### Q2：故障转移不生效

- 检查 `pmis.cronjob.failover.enabled=true`
- `JobNodeHeartbeat` 是否在执行器节点正常上报
- 离线阈值（`offline-threshold-seconds`）是否合理

### Q3：DAG 任务卡住

DAG 节点依赖检查失败时任务会卡 PENDING。检查：
- 节点依赖关系是否循环
- 父节点状态是否为 SUCCESS

---

> 本模块**所有 JobHandler 必须实现幂等**（Redis SET NX EX 或 DB 唯一约束），
> 因为故障转移会重新派发任务，原任务可能已经执行了一部分。
