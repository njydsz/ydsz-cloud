# ydsz-common-audit

> 操作审计留痕模块（L5 业务服务层）— AOP 切面拦截 + SpEL 模板解析 + JDBC 分表存储 + Gateway 事件桥接

提供声明式操作审计能力，通过 `@Audit` 注解标记业务方法，`AuditAspect` 自动采集请求上下文、方法参数、执行结果等全链路信息，经 SpEL 模板解析后异步写入 JDBC 存储（支持按月/日/年分表）；无 DataSource 时降级为控制台输出；配套磁盘兜底写入器避免审计丢失；网关事件桥接器将 WebFlux 网关拦截的操作日志安全落地到 Spring 事件体系。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供操作审计留痕、合规追溯、数据导出审计等能力 |
| **依赖** | common-core、common-util、common-safe、common-exception、common-thread、common-json；spring-boot-starter、spring-boot-starter-aspectj；可选依赖 spring-jdbc、spring-webmvc、jakarta.servlet-api、spring-boot-health、micrometer-core |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. AOP 审计切面

| 类 | 说明 |
|---|---|
| `AuditAspect` | 核心拦截器，通过 `@Around` 拦截 `@Audit` 注解方法，自动采集请求上下文（URL/UA/IP/TraceId）、方法签名、执行耗时、返回值、异常信息等 |
| `AuditTemplateProcessor` | SpEL 模板处理器，解析 `@Audit#content()` 中的动态表达式（如 `'创建用户:' + #user.username`），支持方法参数（按参数名）、`#result` 返回值、目标对象访问 |

### 2. 审计记录器

| 类 | 说明 |
|---|---|
| `AuditRecorder` | 审计记录器接口，定义 `record(AuditLog)` 方法 |
| `DefaultAuditRecorder` | 同步实现，直接委托 `AuditWriter` 写入 |
| `AsyncAuditRecorder` | 异步实现，基于 `LinkedBlockingQueue` 批量写入，支持按 batchSize/batchInterval 触发刷盘，队列满时按拒绝策略处理 |
| `AuditLog` | 审计日志实体（含 appKey、模块名、类型、行为、内容、请求参数、响应结果、操作人、IP、UA、TraceId、耗时、执行状态等字段） |

### 3. 审计写入器与存储

| 类 | 说明 |
|---|---|
| `AuditWriter` | 写入器接口 |
| `JdbcAuditStorage` | JDBC 实现，写入审计表，支持分表（按月/日/年），依赖 `DataSource` |
| `DefaultAuditStorage` | 控制台输出实现，无 DataSource 时降级，仅日志记录不落库 |
| `DefaultAuditQueryService` | 审计日志查询服务，从分表后的审计表分页查询 |
| `AuditQueryService` | 审计查询接口 |
| `TableNameResolver` | 分表名解析器，根据分表类型和日期动态计算目标表名 |

### 4. 磁盘兜底与降级

| 类 | 说明 |
|---|---|
| `AuditFallbackWriter` | 磁盘兜底写入器，当 JDBC 写入失败、队列满时，将审计日志降级写入本地磁盘文件（JSON Lines 格式），保障审计数据不丢失 |

### 5. 事件桥接

| 类 | 说明 |
|---|---|
| `AuditEventListener` | 审计事件监听器，消费 `OperationLogEvent` / `DataExportAuditEvent`，转译为 `AuditLog` 后异步落库 |
| `OperationLogEvent` | 操作日志事件（业务模块通过 `ApplicationEventPublisher` 发布） |
| `DataExportAuditEvent` | 数据导出审计事件，记录大批量数据导出行为，满足合规要求 |
| `GatewayAuditEventBridge` | 网关审计事件桥接器，供 WebFlux / Spring Cloud Gateway GlobalFilter 使用，将响应式上下文中的操作日志安全发布到 Spring 事件体系 |

### 6. 敏感字段脱敏

| 类 | 说明 |
|---|---|
| `SensitiveFieldMask` | 敏感字段脱敏处理器，扫描请求参数中命中敏感词列表的字段值，替换为 `***` |

默认敏感词：`password`、`oldPassword`、`newPassword`、`confirmPassword`、`token`、`accessToken`、`refreshToken`、`authorization`、`secret`、`apiKey`、`privateKey`。

### 7. Diff 快照（合规追溯）

| 类 | 说明 |
|---|---|
| `DiffSnapshotHelper` | Diff 快照助手，在方法执行前查询旧值（`diffBeforeSnapshot`），执行后记录新值（`diffAfterSnapshot`），仅对 `action = UPDATE/DELETE` 场景意义最大 |

开启方式：`@Audit(recordDiff = true, resourceIdSpEL = "#id")`，会在执行前额外引入一次查询旧值的数据库操作。

### 8. 异步线程池

| 类 | 说明 |
|---|---|
| `auditAsyncExecutor` | 审计专用异步线程池（Bean 名：`auditAsyncExecutor`），与业务主链路线程池隔离，通过 `common-thread` 的 `ExecutorUtils` 创建并自动注册到 `ThreadPoolRegistry` |

### 9. 自动配置

| 类 | 说明 |
|---|---|
| `AuditAutoConfiguration` | 自动配置类，Bean 注册条件：`@ConditionalOnMissingBean` 允许业务方覆盖，`@ConditionalOnBean(DataSource)` 控制 JDBC/控制台存储切换 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-audit</artifactId>
</dependency>
```

### 2. 启用注解

```java
@SpringBootApplication
@EnableYdszAudit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 使用示例

```java
@Audit(module = "用户管理",
       type = AuditType.OPERATION,
       action = AuditAction.CREATE,
       content = "'创建用户:' + #user.username",
       recordRequest = true,
       recordResponse = false,
       excludeParams = {"password"})
@PostMapping("/users")
public R<User> createUser(@RequestBody UserDTO user) { ... }
```

### 4. Diff 快照示例

```java
@Audit(module = "商品管理",
       action = AuditAction.UPDATE,
       content = "'更新商品价格:' + #dto.goodsId",
       recordDiff = true,
       resourceIdSpEL = "#dto.goodsId")
public void updateGoods(@RequestBody GoodsDTO dto) { ... }
```

## 配置项

### AuditProperties（`ydsz.audit.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.audit.enabled` | `true` | 是否启用审计模块 |
| `ydsz.audit.app-key` | - | 应用标识，多应用场景区分审计数据归属 |
| `ydsz.audit.storage-type` | `LOCAL` | 存储策略（LOCAL / DEFAULT / REMOTE / MQ） |
| `ydsz.audit.record-request` | `true` | 是否记录请求参数 |
| `ydsz.audit.record-response` | `false` | 是否记录响应结果 |
| `ydsz.audit.record-async` | `true` | 是否异步记录 |
| `ydsz.audit.sensitive-params` | password/token/secret/... | 敏感参数名列表，命中字段不序列化 |
| `ydsz.audit.mask-enabled` | `true` | 是否启用敏感字段脱敏 |
| `ydsz.audit.retention-days` | `90` | 审计日志保留天数 |
| `ydsz.audit.sharding-enabled` | `false` | 是否启用分表存储 |
| `ydsz.audit.sharding-type` | `monthly` | 分表类型（monthly / daily / yearly） |
| `ydsz.audit.sharding-base-table-name` | `sys_audit_log` | 基础表名 |

### 异步配置（`ydsz.audit.async.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.audit.async.batch-size` | `100` | 批量写入阈值（条数） |
| `ydsz.audit.async.batch-interval-millis` | `5000` | 批量写入刷新间隔（毫秒） |
| `ydsz.audit.async.queue-capacity` | `10000` | 异步队列最大容量 |
| `ydsz.audit.async.thread-core-size` | `2` | 异步线程池核心线程数 |
| `ydsz.audit.async.thread-max-size` | `4` | 异步线程池最大线程数 |
| `ydsz.audit.async.reject-policy` | `CALLER_RUNS` | 队列满拒绝策略（CALLER_RUNS / DISCARD_OLDEST / DISCARD_NEWEST） |
| `ydsz.audit.async.shutdown-timeout` | `30` | 优雅停机超时（秒） |

## 使用示例

### 1. 基础审计留痕

```java
@Audit(module = "订单管理",
       type = AuditType.OPERATION,
       action = AuditAction.CREATE,
       content = "'创建订单: ' + #orderDTO.orderNo")
@PostMapping("/orders")
public R<Order> createOrder(@RequestBody OrderDTO orderDTO) { ... }
```

### 2. 数据导出审计

```java
// 通过事件发布方式，无需注解
@Data
public class DataExportAuditEvent {
    private String module;
    private String operator;
    private int recordCount;
    private String reason;
}

applicationEventPublisher.publishEvent(new DataExportAuditEvent(...));
```

### 3. 网关操作日志（WebFlux）

```java
// Gateway GlobalFilter 中
GatewayAuditEventBridge bridge = ...; // 注入
bridge.publishOperationLog(module, action, content, operator);
```

### 4. 变更 Diff 审计

```java
@Audit(module = "员工管理",
       action = AuditAction.UPDATE,
       content = "'更新员工: ' + #dto.employeeId",
       recordDiff = true,
       resourceIdSpEL = "#dto.employeeId")
public void updateEmployee(@RequestBody EmployeeDTO dto) { ... }
```

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/audit` | 审计模块健康检查（异步队列深度、写入成功率、磁盘兜底使用率等） | `spring-boot-health` 在 classpath + `AuditRecorder` Bean 存在 |

`AuditHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `queueSize` | 异步队列当前积压量 |
| `queueUsage` | 异步队列使用率 |
| `writeSuccessCount` | 累计写入成功次数 |
| `writeFailureCount` | 累计写入失败次数 |
| `fallbackWriteCount` | 磁盘兜底写入次数 |
| `status` | 健康状态（UP / DEGRADED / DOWN） |

降级判定：队列使用率 > 80% → DEGRADED；写入失败率 > 10% → DEGRADED；DataSource 不可用且非控制台模式 → DOWN。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `AuditWriter` | 自定义审计存储后端（如 ES / MQ / 远程 API），替换默认 JDBC 实现 | 业务模块实现 |
| `AuditRecorder` | 自定义记录器（如 Disruptor 高性能实现），替换默认异步/同步实现 | 业务模块实现 |
| `AuditQueryService` | 自定义审计查询服务（如 ES 全文查询），替换默认 JDBC 实现 | 业务模块实现 |
| `EventPublishGateway` | 操作日志事件投递渠道扩展（AuditEventListener 消费后二次分发） | 业务模块实现 |

## 自动配置类

| 类 | 说明 |
|---|---|
| `AuditAutoConfiguration` | 核心自动配置，注册切面、记录器、写入器、查询服务、指标绑定器、健康检查、网关桥接器、事件监听器等全部 Bean |

条件装配规则：

| Bean | 条件 |
|---|---|
| `JdbcAuditStorage` (作为 AuditWriter) | `DataSource` 存在 + 无自定义 `AuditWriter` |
| `DefaultAuditStorage` (作为 AuditWriter) | 无 `DataSource` + 无自定义 `AuditWriter` |
| `AsyncAuditRecorder` | 异步模式开启 + 无自定义 `AuditRecorder` + 存在 `AuditWriter` |
| `DefaultAuditRecorder` | 异步模式关闭 + 无自定义 `AuditRecorder` |
| `AuditAspect` | 无自定义 `AuditAspect` + classpath 存在 `YdszJson` |
| `AuditHealthIndicator` | spring-boot-health 在 classpath + `AuditRecorder` 存在 |
| `AuditMetricsBinder` | micrometer 在 classpath + `AuditRecorder` 存在 |

## 注意事项

1. **审计默认异步**：`ydsz.audit.record-async=true`（默认），审计落盘不阻塞业务主链路。关闭后同步写入将直接影响接口 RT。
2. **敏感参数必须显式排除**：涉及密码、证件号等敏感字段的接口，必须通过 `@Audit(excludeParams=...)` 排除，框架默认敏感词列表仅覆盖常见项。
3. **响应记录默认关闭**：`recordResponse=false` 避免大响应体和敏感数据落库。开启前需评估日志存储成本与合规风险。
4. **分表需预建表**：开启 `sharding-enabled=true` 前，审计表必须按月/日/年提前创建（如 `sys_audit_log_202601`），框架不会自动建表。
5. **队列满默认 CALLER_RUNS**：默认拒绝策略保证审计数据不丢失（调用者阻塞 → 超时后磁盘兜底）。高吞吐场景可改为 `DISCARD_OLDEST` 但会丢审计。
6. **磁盘兜底目录**：`AuditFallbackWriter` 写入路径为 `logs/audit-fallback/`，需确保该目录有写权限并定期清理。
7. **Diff 快照引入额外查询**：`recordDiff=true` 会在方法执行前引入一次「查询旧值」数据库操作，评估性能后再开启。
8. **网关事件桥接依赖 WebFlux**：`GatewayAuditEventBridge` 供 Spring Cloud Gateway 使用，MVC 应用无需关注。
9. **覆盖 Bean 提供自定义实现**：所有 Bean 标注 `@ConditionalOnMissingBean`，业务方可自行注册同名 Bean 覆盖默认实现。

## 变更记录

- **26.09.01**（2026-09-01）：对标 common-jdbc 标准格式重构 README，补全全部章节。
- **26.09.01**（2026-08-15）：新增 Diff 快照（`recordDiff` / `resourceIdSpEL`）、数据导出审计事件（`DataExportAuditEvent`）、磁盘兜底写入器（`AuditFallbackWriter`）；重构异步线程池与优雅停机逻辑；移除同步模式主路径，默认异步。
- **26.09.01**（2026-08-02）：初始版本，提供 `@Audit` 注解 + `AuditAspect` + JDBC 存储 + 控制台降级。
