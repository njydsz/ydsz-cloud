# ydsz-common-seata

YDSZ 公共事务模块 — 分布式事务抽象（Seata AT / TCC / SAGA / Local 统一接口）。

## 核心能力

- **统一接口**：`DistributedTransactionManager` 抽象四种分布式事务模式
- **Seata AT 模式**：通过反射调用 Seata 2.x API，自动补偿型事务（undo_log 自动回滚）
- **TCC 模式**：Try-Confirm-Cancel 三阶段，事务日志持久化 + 空回滚/悬挂/幂等保护 + Confirm/Cancel 重试
- **TCC 日志存储**：内存版（默认）+ Redis 版（生产/跨服务共享，P1-4），通过 `ydsz.seata.tcc-log-store` 切换
- **SAGA 模式**：多步骤编排器 `SagaOrchestrator`，正向链 + 逆序补偿 + 补偿重试
- **Local 降级**：单机模式下使用 `TransactionTemplate` 编程式事务
- **XID 跨服务传递**：`XidPropagator` + Feign 拦截器 + Servlet 过滤器（详见[跨服务事务](#跨服务事务xid-传播)）
- **事务恢复**：`TccTransactionRecoveryScanner` 定时扫描超时未完成的事务，跨实例共享（Redis 存储）
- **可观测性**：`SeataHealthIndicator` + `SeataMetrics` (Micrometer/Prometheus) + `TransactionAuditLogger`

## 架构

```
api/
  ├── DistributedTransactionManager    统一接口
  ├── TransactionType                 事务类型枚举（LOCAL/TCC/SEATA_AT/SAGA）
  ├── TccAction                       TCC 业务接口（try/confirm/cancel）
  ├── TccContext                      TCC 上下文（线程安全 Map）
  ├── TccBranchStatus                 TCC 分支状态枚举（INIT→TRYING→TRIED→CONFIRMED/CANCELLED）
  ├── TccTransactionLog               TCC 事务日志记录
  ├── TccTransactionLogStore          事务日志存储接口
  ├── TccCoordinator                  异步 TCC 协调器接口（预留扩展点）
  ├── SagaStep                        SAGA 步骤定义
  ├── XidPropagator                   XID 传播接口
  └── TransactionalMessageSender      事务性消息发送接口（Outbox 集成）
impl/
  ├── AbstractTransactionManager       基类（XID 上下文管理）
  ├── LocalTransactionManager         本地事务降级（TransactionTemplate）
  ├── TccTransactionManager           TCC 实现（三大问题检查 + 重试）
  ├── SagaOrchestrator                SAGA 多步编排器
  ├── SeataTransactionManager         Seata AT 实现
  ├── SeataGlobalTransactionExecutor  Seata API 反射调用
  ├── GlobalTransactionExecutor       全局事务执行器接口
  ├── InMemoryTccTransactionLogStore  内存版事务日志存储（单机/测试）
  ├── RedisTccTransactionLogStore     Redis 版事务日志存储（生产/跨服务共享，P1-4）
  ├── TccTransactionRecoveryScanner   事务恢复扫描器
  └── DefaultXidPropagator            默认 XID 传播器
interceptor/
  ├── FeignXidRequestInterceptor      Feign XID 请求拦截器（上游→下游）
  └── XidServletFilter                XID Servlet 过滤器（下游接收）
health/
  └── SeataHealthIndicator            健康检查（/actuator/health/seata）
metrics/
  └── SeataMetrics                    指标采集（Micrometer）
audit/
  └── TransactionAuditLogger         审计日志（结构化 JSON）
event/
  └── TransactionalMessageSender      Outbox 集成接口
config/
  ├── SeataAutoConfiguration          自动配置
  └── SeataProperties                 配置属性
```

## 使用方式

### TCC 模式

```java
@Component
public class OrderTccAction implements TccAction<OrderResult> {
    @Override
    public OrderResult tryAction(TccContext ctx) throws Exception {
        inventoryMapper.freeze(ctx.get("skuId"), ctx.get("qty"));
        return new OrderResult(ctx.getXid());
    }

    @Override
    public void confirmAction(TccContext ctx) throws Exception {
        inventoryMapper.deductFrozen(ctx.get("skuId"), ctx.get("qty"));
    }

    @Override
    public void cancelAction(TccContext ctx) throws Exception {
        inventoryMapper.unfreeze(ctx.get("skuId"), ctx.get("qty"));
    }
}

// 调用（自动处理空回滚/悬挂/幂等 + Confirm/Cancel 重试）
tccTransactionManager.executeTcc("createOrder", orderTccAction);
```

### SAGA 模式

```java
List<SagaStep<Void>> steps = List.of(
    SagaStep.of("deduct", () -> { accountMapper.deduct(fromId, amount); return null; },
                       () -> { accountMapper.add(fromId, amount); }),
    SagaStep.of("add",    () -> { accountMapper.add(toId, amount); return null; },
                       () -> { accountMapper.deduct(toId, amount); })
);
sagaOrchestrator.execute("transfer", steps);  // 失败时逆序补偿
```

### Local 模式（降级）

```java
txManager.execute("localOp", TransactionType.LOCAL, () -> {
    mapper.insert(dto);
    return null;
});
```

### Seata AT 模式

```java
// 配置 ydsz.seata.default-type=SEATA_AT
// Seata 在类路径时自动注册 SeataTransactionManager
// 业务代码也可直接使用 Seata 原生 @GlobalTransactional
```

## 跨服务事务（XID 传播）

> **P1-4 新增**：完整文档化 XID 跨服务传播机制，使 TCC/SAGA 在 Feign/MQ 链路下接续全局事务。

### 传播路径

```
[Service A]                        [Service B]
   │                                  │
   │ tccManager.executeTcc(...)       │
   │   ├─ beginXid(): XID_HOLDER.set  │
   │   ├─ tryAction()                 │
   │   └─ Feign 调用 Service B ──────►│
   │                                  │ ├─ XidServletFilter 读取
   │                                  │ │   Header "Seata-XID"
   │                                  │ ├─ xidPropagator.bind(xid)
   │                                  │ ├─ 业务执行（同一 XID）
   │                                  │ └─ XidServletFilter.unbind
   │   ◄──────────────────────────────│
   │   ├─ confirmAction()             │
   │   └─ endXid(): XID_HOLDER.remove │
```

### 关键组件

| 组件 | 作用 | 注册条件 |
|------|------|----------|
| `XidPropagator` | XID 上下文绑定 / 解绑 | 总是注册 |
| `FeignXidRequestInterceptor` | 上游：将 XID 写入 HTTP Header | Feign 在类路径 |
| `XidServletFilter` | 下游：从 HTTP Header 解析 XID | Spring Web 在类路径 |

### HTTP Header

```
Seata-XID: <全局事务 ID>
```

> 下游服务通过 `XidServletFilter` 自动接收并绑定 XID，请求结束后自动解绑。
> 业务代码无需感知 XID 传播，调用 `tccManager.getCurrentXid()` 即可获取。

### MQ 传播（预留）

XID 也可通过 MQ 消息属性传递（如 RocketMQ/Kafka）：

```java
// 发送方
String xid = xidPropagator.currentXid();
message.setProperty(XidPropagator.XID_MQ_PROPERTY, xidPropagator.serialize(xid));

// 消费方
String xidHeader = message.getProperty(XidPropagator.XID_MQ_PROPERTY);
xidPropagator.bind(xidPropagator.deserialize(xidHeader));
try {
    // 业务处理
} finally {
    xidPropagator.unbind();
}
```

## TCC 事务日志存储

> **P1-4 新增**：`TccTransactionLogStore` 提供两种实现，可通过配置切换。

### 实现对比

| 实现 | 适用场景 | 持久化 | 跨服务共享 | 性能 |
|------|----------|--------|------------|------|
| `InMemoryTccTransactionLogStore` | 单机/测试 | 否（重启丢失） | 否 | 高 |
| `RedisTccTransactionLogStore` | 生产/多实例 | 是（Redis TTL） | 是 | 中（依赖 Redis 网络） |

### 配置切换

```yaml
ydsz:
  seata:
    tcc-log-store: redis          # memory（默认）或 redis
    tcc-log-redis-key-prefix: "ydsz:tcc:log:"
    tcc-log-redis-retention-hours: 24
```

### Redis 存储结构

```
Key:     {keyPrefix}:{xid}:{branchId}    # Redis Hash
Fields:
  xid, branchId, transactionName, status,  # 基础字段
  contextSnapshot,                          # 上下文快照（JSON）
  tryStartedAt, tryCompletedAt, finishedAt, # 时间戳（ISO_LOCAL_DATE_TIME）
  retryCount, lastError                     # 恢复扫描器维护
TTL:     {retention}（默认 24 小时）
```

### 跨服务 TCC 协调

使用 `RedisTccTransactionLogStore` 后，多服务实例可共享事务状态：

```
[Instance A]                                  [Instance B]
  tccManager.executeTcc(...)                    │
    ├─ save(log) ──► Redis ◄─────────────────────┤
    ├─ tryAction() ✗（实例崩溃）                  │
    │                                            │
    │   TccTransactionRecoveryScanner (定时)     │
    │       on Instance B                        │
    │       ├─ findTimeoutPending() ──► Redis ──►│ 找到 A 留下的 TRIED 状态分支
    │       ├─ recoverCancel(log)                │
    │       └─ updateStatus(CANCELLED) ──► Redis │
```

> **要求**：所有参与方必须使用相同的 `tcc-log-redis-key-prefix`，确保扫描器能发现其他实例留下的未完成事务。

### 降级策略

当 `ydsz.seata.tcc-log-store=redis` 但 `RedisTemplate` 不可用时，
自动回退到 `InMemoryTccTransactionLogStore` 并打印 WARN 日志，
保证服务可启动。

## 配置

```yaml
ydsz:
  seata:
    enabled: true
    default-type: LOCAL  # LOCAL / TCC / SEATA_AT / SAGA
    # 分模式开关
    tcc-enabled: true
    saga-enabled: true
    seata-at-enabled: true
    # Seata 配置
    seata-application-id: ydsz-app
    seata-tx-service-group: ydsz-tx-group
    # TCC 配置
    tcc-retry-count: 3
    tcc-retry-interval-ms: 1000
    tcc-try-timeout-ms: 60000
    # TCC 事务日志存储（P1-4）
    tcc-log-store: memory           # memory（默认，单机/测试）或 redis（生产，跨服务共享）
    tcc-log-redis-key-prefix: "ydsz:tcc:log:"
    tcc-log-redis-retention-hours: 24
    # SAGA 配置
    saga-max-retries: 5
    saga-retry-interval-ms: 2000
    saga-timeout-ms: 300000
    # 事务恢复
    recovery-scan-interval-ms: 10000
    recovery-timeout-threshold-ms: 60000
    # Seata 2.x 客户端
    tm-commit-retry-count: 3
    tm-rollback-retry-count: 3
    rm-report-success-enable: false
    rm-lock-retry-interval-ms: 10
    rm-lock-retry-times: 30
    undo-data-validation: true
    undo-log-serialization: jackson
    undo-compress-data: true
    undo-compress-type: zip
```

## DDL

- `deploy/sql/modules/ydsz_tcc_transaction_log.sql` — TCC 事务日志表（`JdbcTccTransactionLogStore` 使用，本模块未内置 JDBC 实现）
- `deploy/sql/modules/V1.0.0_system.sql` — Seata AT 模式 `undo_log` 表（已包含）

> **Redis 存储无需 DDL**：`RedisTccTransactionLogStore` 使用 Redis Hash，无需数据库表，
> 配合 `tcc-log-store=redis` 即可启用。

## 可观测性

| 端点/指标 | 说明 |
|-----------|------|
| `/actuator/health/seata` | 事务模式、TC 连通性、TCC 挂起事务数 |
| `seata.tx.count{type,result}` | 事务执行计数 |
| `seata.tx.duration{type}` | 事务执行耗时（P50/P90/P99） |
| `seata.tcc.confirm.retry` | TCC Confirm 重试次数 |
| `seata.tcc.cancel.retry` | TCC Cancel 重试次数 |
| `seata.saga.compensation.count` | SAGA 补偿次数 |
| `seata.tx.active` | 活跃事务数 |
| `SEATA_AUDIT` logger | 结构化审计日志（JSON） |
