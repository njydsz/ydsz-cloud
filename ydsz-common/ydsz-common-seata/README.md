# ydsz-common-seata

> 分布式事务抽象模块（L5 业务服务层）— Seata AT / TCC / SAGA / Local 统一接口

提供 `DistributedTransactionManager` 统一抽象，底层实现覆盖 Seata AT（自动补偿）、TCC（Try-Confirm-Cancel）、SAGA（长事务编排）、Local（本地降级）四种模式；集成事务日志存储（Memory / Redis / DB）、空回滚/悬挂/幂等三大问题防护、XID 跨服务传播、定时恢复扫描（含分页模式）、XID 签名验证、可观测性指标与审计日志，是所有业务模块跨服务分布式事务的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供分布式事务统一抽象、TCC 三大问题防护、SAGA 编排、XID 跨服务传播、事务恢复扫描、可观测性能力 |
| **依赖** | common-core、common-exception、common-json、spring-tx、spring-jdbc、spring-aop、slf4j-api；可选依赖 seata-spring-boot-starter、feign-core、rocketmq-client、ydsz-common-lock、spring-boot-starter-data-redis、spring-web、spring-boot-actuator、spring-boot-health、micrometer-core、jackson-databind |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一接口与事务类型

| 类 | 说明 |
|---|---|
| `DistributedTransactionManager` | 统一接口，定义 `execute(name, type, action)`、`executeWithCompensation(name, action, compensation)`、`getCurrentType()`、`getCurrentXid()` |
| `TransactionType` | 事务类型枚举：`SEATA_AT` / `TCC` / `SAGA` / `LOCAL` |

### 2. 事务管理器实现

| 类 | 说明 |
|---|---|
| `AbstractTransactionManager` | 抽象基类，封装 XID 上下文管理、metrics/audit 注入 |
| `LocalTransactionManager` | 本地事务降级实现，基于 Spring `TransactionTemplate` 编程式事务 |
| `TccTransactionManager` | TCC 实现，集成事务日志解决三大问题，支持 Confirm/Cancel 重试；实现 `TccRecoveryHandler` 接受恢复扫描器回调 |
| `SeataTransactionManager` | Seata AT 实现，直接调用 `org.apache.seata` 2.x 原生 API（`GlobalTransactionContext` 等） |
| `SagaOrchestrator` | SAGA 多步编排器，正向链执行 + 失败逆序补偿 + 补偿重试 |

### 3. TCC 三大问题防护

| 类 | 说明 |
|---|---|
| `TccAction<T>` | TCC 业务接口，定义 `tryAction` / `confirmAction` / `cancelAction` |
| `TccContext` | TCC 上下文（线程安全 Map），携带 xid / branchId / 业务参数 |
| `TccBranchStatus` | 分支状态枚举：`INIT` → `TRYING` → `TRIED` → `CONFIRMING`/`CANCELLING` → `CONFIRMED`/`CANCELLED`（终态）；提供 `canTry()` / `canConfirm()` / `canCancel()` / `isFinal()` 检查方法 |
| `TccTransactionLog` | 事务日志实体（xid / branchId / transactionName / status / contextSnapshot / 时间戳 / retryCount / lastError） |

三大问题防护：

| 问题 | 防护机制 |
|---|---|
| **空回滚** | Cancel 前检查状态，若为 `INIT`/`TRYING` 则跳过（Try 未执行或未完成） |
| **悬挂** | Try 前检查状态，若已 `CANCELLED` 则跳过（Cancel 先于 Try 到达） |
| **幂等** | Confirm/Cancel 前检查是否已为终态（`CONFIRMED`/`CANCELLED`），是则跳过 |

### 4. TCC 事务日志存储

| 类 | 说明 |
|---|---|
| `TccTransactionLogStore` | 存储接口，定义 `save` / `updateStatus` / `findByXidAndBranchId` / `findTimeoutPending` / `delete` |
| `InMemoryTccTransactionLogStore` | 内存实现（默认），单机/测试场景，重启丢失 |
| `RedisTccTransactionLogStore` | Redis Hash 实现，生产环境跨服务共享；SCAN 遍历避免阻塞；TTL 自动清理；所有 field/value 以 String 写入兼容不同 Serializer |
| `DbTccTransactionLogStore` | 数据库实现，生产环境强持久化无需 Redis；支持 MySQL（`ON DUPLICATE KEY UPDATE`）和 PostgreSQL（`ON CONFLICT DO UPDATE`）方言 |

Redis 存储结构：

```
Key:     {keyPrefix}:{xid}:{branchId}    # Redis Hash
Fields:  xid / branchId / transactionName / status /
         contextSnapshot / tryStartedAt / tryCompletedAt /
         finishedAt / retryCount / lastError
TTL:     retention（默认 24 小时）
```

### 5. SAGA 编排

| 类 | 说明 |
|---|---|
| `SagaStep<T>` | SAGA 步骤定义，含 `name` / `forwardAction` / `compensation`；`SagaStep.terminal(...)` 创建无补偿的终态步骤 |
| `SagaOrchestrator` | 编排器，按顺序执行 `SagaStep` 列表，任一步骤失败则逆序执行已完成步骤的补偿；支持重试（`saga-max-retries` / `saga-retry-interval-ms`）与超时（`saga-timeout-ms`） |

### 6. XID 跨服务传播

| 类 | 说明 |
|---|---|
| `XidPropagator` | 传播器接口，定义 `serialize` / `deserialize` / `bind` / `unbind` / `currentXid`；常量 `XID_HEADER="Seata-XID"`、`XID_MQ_PROPERTY="seata-xid"` |
| `DefaultXidPropagator` | 默认实现，基于 ThreadLocal 绑定 XID |
| `FeignXidRequestInterceptor` | Feign 请求拦截器，上游服务将 XID 写入 HTTP Header |
| `XidServletFilter` | Servlet 过滤器，下游服务从 HTTP Header 解析 XID 并绑定到当前线程，请求结束后自动解绑 |
| `RocketMqXidMessageInterceptor` | RocketMQ XID 传播拦截器（可选，需 `rocketmq-client` 依赖） |

### 7. XID 签名验证

| 类 | 说明 |
|---|---|
| `XidSignValidator` | XID 签名验证器，HMAC-SHA256 签名验证，当 `xid-sign-enabled=true` 时跨服务 XID 传播携带签名，下游验证后才绑定到上下文，防止 XID 伪造注入 |

### 8. 事务恢复扫描

| 类 | 说明 |
|---|---|
| `TccTransactionRecoveryScanner` | 定时扫描器，`@Scheduled(fixedDelayString="${ydsz.seata.recovery-scan-interval-ms:10000}")`；扫描 `TRIED` 状态超时分支，回调 `TccRecoveryHandler` 执行 Cancel；支持分页模式渐进式处理 |

解决场景：JVM 崩溃后 Confirm/Cancel 未执行、Confirm/Cancel 失败需周期性重试。使用 Redis 存储时，扫描器可发现其他实例留下的未完成事务（跨实例恢复）。

### 9. 可观测性

| 类 | 说明 |
|---|---|
| `SeataMetrics` | Micrometer 指标采集，`MeterRegistry` 不可用时降级为内存计数器 |
| `TransactionAuditLogger` | 结构化 JSON 审计日志，输出到独立 logger `SEATA_AUDIT`，可由 Loki/ELK 采集 |

注册的 Micrometer 指标：

| 指标 | 类型 | 说明 |
|---|---|---|
| `seata.tx.count{type,result}` | Counter | 事务执行计数（按类型 + 结果标签） |
| `seata.tx.duration{type}` | Timer | 事务执行耗时（P50/P90/P99） |
| `seata.tcc.confirm.retry` | Counter | TCC Confirm 重试次数 |
| `seata.tcc.cancel.retry` | Counter | TCC Cancel 重试次数 |
| `seata.saga.compensation.count` | Counter | SAGA 补偿次数 |
| `seata.tx.active` | Gauge | 活跃事务数 |

### 10. 配置与自动装配

| 类 | 说明 |
|---|---|
| `SeataAutoConfiguration` | Spring Boot 自动配置，`ydsz.seata.enabled=true`（默认）时装配；含 `SeataAtConfiguration` 内嵌配置类（Seata 在 classpath 时注册） |
| `SeataProperties` | 配置属性（`ydsz.seata.*`，JSR-303 校验注解），含 `TccLogStoreType` 枚举（MEMORY / REDIS / DB） |
| `SeataTaskDecorator` | 任务装饰器模式，为线程池任务绑定 XID 上下文，实现 `@Async` / `@Scheduled` / `@TransactionalEventListener` 的 XID 传播 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-seata</artifactId>
</dependency>
```

如需 Seata AT 模式，额外引入：

```xml
<dependency>
    <groupId>org.apache.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
</dependency>
```

如需 TCC 日志 Redis 存储，额外引入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  seata:
    enabled: true
    default-type: LOCAL         # LOCAL / TCC / SEATA_AT / SAGA
    tcc-enabled: true
    saga-enabled: true
    seata-at-enabled: true
    tcc-log-store: memory       # memory（默认）/ redis / db
    xid-sign-enabled: false     # XID 签名开关（生产环境建议开启）
    xid-sign-key: null          # XID 签名密钥（xid-sign-enabled=true 时必填）
```

### 3. 注入 DistributedTransactionManager 使用

```java
import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.api.TransactionType;

@Service
public class OrderService {
    private final DistributedTransactionManager txManager;

    public void createOrder(OrderDTO dto) throws Exception {
        txManager.execute("createOrder", TransactionType.LOCAL, () -> {
            orderMapper.insert(dto);
            inventoryMapper.deduct(dto.getSkuId(), dto.getQty());
            return null;
        });
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.seata.enabled` | true | 是否启用分布式事务模块 |
| `ydsz.seata.default-type` | LOCAL | 默认事务类型（LOCAL / TCC / SEATA_AT / SAGA） |
| `ydsz.seata.seata-application-id` | `ydsz-app` | Seata 应用 ID |
| `ydsz.seata.seata-tx-service-group` | `ydsz-tx-group` | Seata 事务组 |
| `ydsz.seata.tcc-enabled` | true | 是否启用 TCC 模式 |
| `ydsz.seata.saga-enabled` | true | 是否启用 SAGA 模式 |
| `ydsz.seata.seata-at-enabled` | true | 是否启用 Seata AT 模式 |
| `ydsz.seata.tcc-retry-count` | 3 | TCC 补偿重试次数（0-10） |
| `ydsz.seata.tcc-retry-interval-ms` | 1000 | TCC 补偿重试间隔（毫秒，≥100） |
| `ydsz.seata.tcc-try-timeout-ms` | 60000 | TCC Try 阶段超时（毫秒，0=不限制） |
| `ydsz.seata.tcc-log-store` | memory | TCC 日志存储类型（memory / redis / db） |
| `ydsz.seata.tcc-log-redis-key-prefix` | `ydsz:tcc:log:` | Redis 日志 key 前缀（redis 模式生效） |
| `ydsz.seata.tcc-log-redis-retention-hours` | 24 | Redis 日志保留时长（小时，redis 模式生效） |
| `ydsz.seata.tcc-log-db-table` | `tcc_transaction_log` | TCC 日志 DB 存储时的表名 |
| `ydsz.seata.tcc-log-db-schema` | - | TCC 日志 DB 存储时的架构名（可选，null 使用默认） |
| `ydsz.seata.tcc-log-db-dialect` | - | TCC 日志 DB 存储时的数据库方言（mysql / postgresql，null 自动检测） |
| `ydsz.seata.xid-sign-enabled` | false | XID 签名开关（开启后跨服务 XID 传播携带 HMAC-SHA256 签名） |
| `ydsz.seata.xid-sign-key` | - | XID 签名密钥（当 xid-sign-enabled=true 时必填，长度 ≥ 16） |
| `ydsz.seata.saga-max-retries` | 5 | SAGA 最大重试次数（0-10） |
| `ydsz.seata.saga-retry-interval-ms` | 2000 | SAGA 重试间隔（毫秒，≥100） |
| `ydsz.seata.saga-timeout-ms` | 300000 | SAGA 事务超时（毫秒，0=不限制） |
| `ydsz.seata.recovery-scan-interval-ms` | 10000 | 事务恢复扫描间隔（毫秒，≥1000） |
| `ydsz.seata.recovery-timeout-threshold-ms` | 60000 | 事务超时判定阈值（毫秒，≥5000） |
| `ydsz.seata.recovery-batch-size` | 100 | 恢复扫描单次处理最大事务数（1-1000） |
| `ydsz.seata.recovery-paged-mode` | true | 恢复扫描是否启用分页模式（渐进式处理超时事务） |
| `ydsz.seata.tx-timeout-overrides` | `{}` | 按事务名称的差异化超时覆盖（key=事务名称，value=超时毫秒） |

## 使用示例

### 1. TCC 模式

```java
import com.njydsz.common.seata.api.TccAction;
import com.njydsz.common.seata.api.TccContext;
import com.njydsz.common.seata.impl.TccTransactionManager;
import org.springframework.stereotype.Component;

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

### 2. SAGA 模式

```java
import com.njydsz.common.seata.api.SagaStep;
import com.njydsz.common.seata.impl.SagaOrchestrator;
import java.util.List;

List<SagaStep<Void>> steps = List.of(
    SagaStep.of("deduct",
        () -> { accountMapper.deduct(fromId, amount); return null; },
        () -> { accountMapper.add(fromId, amount); }),
    SagaStep.of("add",
        () -> { accountMapper.add(toId, amount); return null; },
        () -> { accountMapper.deduct(toId, amount); })
);
sagaOrchestrator.execute("transfer", steps);  // 失败时逆序补偿
```

### 3. Seata AT 模式

```yaml
ydsz:
  seata:
    default-type: SEATA_AT
    seata-application-id: ydsz-order
    seata-tx-service-group: ydsz-tx-group
```

Seata 在 classpath 时自动注册 `SeataTransactionManager`；业务代码也可直接使用 Seata 原生 `@GlobalTransactional` 注解。需配套 `undo_log` 表（见 `deploy/sql/modules/1.0.0_system.sql`）。

### 4. Local 降级模式

```java
txManager.execute("localOp", TransactionType.LOCAL, () -> {
    mapper.insert(dto);
    return null;
});
```

### 5. XID 跨服务传播（Feign + Servlet）

上游服务（Feign 拦截器自动注入 Header）：

```java
// FeignXidRequestInterceptor 自动将 XID 写入 HTTP Header "Seata-XID"
// 业务代码无需感知
feignClient.callDownstream(params);
```

下游服务（Servlet 过滤器自动接收）：

```java
// XidServletFilter 自动从 Header 解析 XID 并绑定到当前线程
// 业务代码通过 XidPropagator 获取
String xid = xidPropagator.currentXid();
```

MQ 传播（手动注入）：

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

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `DistributedTransactionManager` | 分布式事务统一接口 | 框架内置 `LocalTransactionManager`、`TccTransactionManager`、`SeataTransactionManager`；业务可扩展 |
| `TccAction<T>` | TCC 业务接口（try/confirm/cancel） | 业务模块实现 |
| `TccTransactionLogStore` | TCC 事务日志存储 | 框架内置 `InMemoryTccTransactionLogStore`、`RedisTccTransactionLogStore`、`DbTccTransactionLogStore`；业务可扩展 |
| `XidPropagator` | XID 跨服务传播 | 框架内置 `DefaultXidPropagator`、`FeignXidRequestInterceptor`、`XidServletFilter`；业务可扩展自定义序列化 |
| `SagaStep<T>` | SAGA 步骤定义 | 业务模块通过 `SagaStep.of(...)` 创建 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/seata` | 分布式事务健康检查 | `spring-boot-health` 在 classpath 且 `ydsz.seata.enabled=true` |

健康检查暴露信息：

| 字段 | 说明 |
|---|---|
| `defaultType` | 当前默认事务类型 |
| `tccEnabled` / `sagaEnabled` / `seataAtEnabled` | 各模式启用状态 |
| `seataTc` | Seata TC 连通性（`UP` / `DOWN` / `not configured (Local mode)`） |
| `currentGlobalXid` | 当前全局事务 XID（无则 `none`） |
| `seataTcError` | TC 连接错误信息（DOWN 时填充） |
| `tccLogStore` | TCC 日志存储状态（`not configured` 或具体实现） |
| `tccPendingTransactions` | 挂起的 TCC 事务数（TRIED 状态超时未完成） |
| `tccRecoveryScanIntervalMs` | 恢复扫描间隔 |
| `tccWarning` | 告警原因（挂起事务数 > 10 时填充 `High number of pending TCC transactions`） |

状态判定规则：

- Seata TC 连接失败 → DOWN
- 其他情况 → UP（挂起事务数 > 10 仅填充 warning，不改变状态）

## 注意事项

1. **Seata 2.x 包名**：本项目使用 Seata 2.5.0，包名从 `io.seata`（1.x）变更为 `org.apache.seata`（2.x）；`@ConditionalOnClass` 已适配新包名。
2. **TCC 日志存储**：`DbTccTransactionLogStore`（强持久化，无需 Redis）、`RedisTccTransactionLogStore`（跨服务共享）、`InMemoryTccTransactionLogStore`（重启丢失）。生产环境根据基础设施选择 `redis` 或 `db`。
3. **Redis 不可用时降级**：`tcc-log-store=redis` 但 `RedisTemplate` 不可用时自动回退到 `InMemoryTccTransactionLogStore` 并打印 WARN 日志，保证服务可启动。
4. **TCC 三大问题依赖日志存储**：未配置 `TccTransactionLogStore` 时（无参构造），三大问题防护失效；推荐始终通过自动配置注入带日志存储的 `TccTransactionManager`。
5. **恢复扫描器超时阈值**：`recovery-timeout-threshold-ms` 应大于正常 Confirm/Cancel 耗时，避免误回收正在执行的分支；实例宕机后该阈值决定恢复延迟。
6. **XID 传播需 Feign + Web**：`FeignXidRequestInterceptor` 需 Feign 在 classpath，`XidServletFilter` 需 Spring Web 在 classpath；两者缺失时 XID 无法跨服务传递。
7. **XID 签名密钥长度**：`xid-sign-key` 长度至少 16 字符，建议 32 字节以上随机字符串，通过环境变量或配置中心注入。
8. **Seata AT 需 undo_log 表**：Seata AT 模式依赖 `undo_log` 表自动回滚，需提前执行 `deploy/sql/modules/1.0.0_system.sql`。
9. **SAGA 补偿必须可逆**：`SagaStep.of(...)` 的 `compensation` 必须能撤销 `forwardAction` 的副作用；`SagaStep.terminal(...)` 用于不可逆的最后一步，失败时不补偿。
10. **Local 模式需 PlatformTransactionManager**：`default-type=LOCAL` 时容器中必须存在 `PlatformTransactionManager`（如 `DataSourceTransactionManager`），否则启动抛 `IllegalStateException`。
11. **恢复扫描分页模式**：`recovery-paged-mode=true`（默认）时每次扫描仅处理 `recovery-batch-size` 条记录，避免一次性加载全部超时事务到内存；`recovery-batch-size` 范围 1-1000。
12. **DB 存储方言**：`tcc-log-db-dialect` 为空时自动根据数据源元数据检测；显式指定可避免运行时自动检测的开销。
13. **按事务名称的超时覆盖**：`tx-timeout-overrides` 允许为特定事务配置独立超时（覆盖全局 `tcc-try-timeout-ms`），与 XID 前缀匹配。
14. **SeataTaskDecorator 上下文传播**：通过任务装饰器模式自动绑定 XID 到 `@Async` / `@Scheduled` / `@TransactionalEventListener` 的执行线程，实现跨线程 XID 传播。
15. **JSR-303 配置校验**：`SeataProperties` 使用 `@Min` / `@Max` / `@NotBlank` / `@NotNull` 注解，配置非法时启动失败并打印明确错误信息。

## 变更记录

- **1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
