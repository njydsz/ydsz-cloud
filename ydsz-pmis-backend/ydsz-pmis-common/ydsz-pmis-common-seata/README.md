# ydsz-pmis-common-seata

PMIS 公共事务模块 — 分布式事务抽象（Seata AT / TCC / SAGA / Local 统一接口）。

## 核心能力

- **统一接口**：`DistributedTransactionManager` 抽象四种分布式事务模式
- **Seata AT 模式**：通过反射调用 Seata 2.x API，自动补偿型事务（undo_log 自动回滚）
- **TCC 模式**：Try-Confirm-Cancel 三阶段，事务日志持久化 + 空回滚/悬挂/幂等保护 + Confirm/Cancel 重试
- **SAGA 模式**：多步骤编排器 `SagaOrchestrator`，正向链 + 逆序补偿 + 补偿重试
- **Local 降级**：单机模式下使用 `TransactionTemplate` 编程式事务
- **XID 跨服务传递**：`XidPropagator` + Feign 拦截器 + Servlet 过滤器
- **事务恢复**：`TccTransactionRecoveryScanner` 定时扫描超时未完成的事务
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
  ├── InMemoryTccTransactionLogStore  内存版事务日志存储
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
// 配置 pmis.seata.default-type=SEATA_AT
// Seata 在类路径时自动注册 SeataTransactionManager
// 业务代码也可直接使用 Seata 原生 @GlobalTransactional
```

## 配置

```yaml
pmis:
  seata:
    enabled: true
    default-type: LOCAL  # LOCAL / TCC / SEATA_AT / SAGA
    # 分模式开关
    tcc-enabled: true
    saga-enabled: true
    seata-at-enabled: true
    # Seata 配置
    seata-application-id: pmis-app
    seata-tx-service-group: pmis-tx-group
    # TCC 配置
    tcc-retry-count: 3
    tcc-retry-interval-ms: 1000
    tcc-try-timeout-ms: 60000
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

- `deploy/sql/modules/pm_tcc_transaction_log.sql` — TCC 事务日志表
- `deploy/sql/modules/V1.0.0_system.sql` — Seata AT 模式 `undo_log` 表（已包含）

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
