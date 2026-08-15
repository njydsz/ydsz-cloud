# ydsz-common-jdbc

> MyBatis-Plus 增强与数据访问公共模块（L4 基础数据层）

提供 MyBatis-Plus 增强、动态数据源、读写分离、数据权限、SQL 防火墙、SQL 追踪、数据库熔断、字段填充、逻辑删除、乐观锁等开箱即用的能力，是所有业务模块数据访问层的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 MyBatis-Plus 增强、动态数据源、读写分离、数据权限、SQL 防火墙、SQL 追踪、数据库熔断等能力 |
| **依赖** | common-core、common-domain、common-exception、common-util、common-json；可选依赖 dynamic-datasource、spring-boot-actuator、spring-boot-health |
| **版本** | 1.0.0 |

## 核心能力

### 1. MyBatis-Plus 集成

| 类 | 说明 |
|---|---|
| `MybatisPlusConfiguration` | MP 全局配置，按顺序组装乐观锁、逻辑删除、字段填充、SPI、数据权限、分页、SQL 防火墙拦截器链 |
| `MyMetaObjectHandler` | MP `MetaObjectHandler` 实现，配合 `FieldFillHandler` 完成审计字段填充 |

拦截器链执行顺序（按添加顺序）：

1. `OptimisticLockInterceptor`（自定义 revision 列）或内置 `OptimisticLockerInnerInterceptor`（`@Version`）
2. `LogicalDeleteInterceptor` — 逻辑删除（SELECT/DELETE 改写）
3. `CombinedFieldFillInterceptor` — 字段填充（合并多 Handler 单次解析）
4. SPI 拦截器 — 外部模块通过 `InnerInterceptorProvider` 注入（按 order 排序）
5. `RowPermissionInnerInterceptor` + `ColPermissionInnerInterceptor` — 数据权限
6. `PaginationInnerInterceptor` — 分页（动态适配 DbType + maxLimit 安全加固）
7. `SqlFirewallInnerInterceptor` — SQL 防火墙（置于链末端，所有改写完成后做安全校验）

### 2. 动态数据源

| 类 | 说明 |
|---|---|
| `DynamicRoutingDataSource` | 继承 `AbstractRoutingDataSource`，支持运行时动态增删数据源、栈式嵌套切换 |
| `DynamicDataSourceContextHolder` | 基于 ThreadLocal 的栈式数据源上下文持有器 |
| `DsAnnotationInterceptor` | `@DS` 注解 AOP 拦截器，方法级优先于类级 |
| `@DS` | 数据源切换注解，支持固定名称与 SpEL 表达式 |
| `DynamicDataSourceAutoConfiguration` | 动态数据源自动配置（`ydsz.jdbc.dynamic-datasource.enabled=true`，默认启用） |

`@DS` 用法：

```java
import com.njydsz.common.jdbc.annotation.DS;

// 类级别：整个 Service 使用 slave 数据源
@Service
@DS("slave")
public class UserService {

    // 方法级别：覆盖类级别，强制走主库
    @DS("master")
    public void updateUser(User user) {
        // 写操作使用主库
    }
}
```

### 3. 读写分离

| 类 | 说明 |
|---|---|
| `ReadWriteSplittingAutoConfiguration` | 读写分离自动配置，注册为 MyBatis 外层拦截器 Bean |
| `ReadWriteSplittingProperties` | 读写分离配置属性 |
| `ReadWriteSplittingInterceptor` | 基于 SQL 类型自动路由：SELECT → 从库，INSERT/UPDATE/DELETE → 主库；事务激活时强制路由主库 |
| `DataSourceLoadBalanceStrategy` | 负载均衡策略接口 |
| `RoundRobinLoadBalanceStrategy` | 轮询负载均衡（默认） |
| `RandomLoadBalanceStrategy` | 随机负载均衡 |
| `WeightedLoadBalanceStrategy` | 加权负载均衡 |

事务感知：`@Transactional` 激活时 SELECT 也走主库，保证读写一致性。ThreadLocal 使用 `try-finally` 确保 `poll()` 被调用，避免线程池复用泄漏。

### 4. 字段填充

| 类 | 说明 |
|---|---|
| `AbstractFieldFillHandler` / `FieldFillHandler` | 字段填充抽象基类与接口 |
| `CreatedByHandler` / `CreatedAtHandler` | 创建人 / 创建时间填充（INSERT） |
| `UpdatedByHandler` / `UpdatedAtHandler` | 更新人 / 更新时间填充（INSERT_UPDATE） |
| `MyMetaObjectHandler` | MP `MetaObjectHandler` 实现 |
| `CombinedFieldFillInterceptor` | 合并多 Handler 的 InnerInterceptor，单次 SQL 解析完成所有字段填充 |
| `AbstractSqlHandler` | SQL 处理抽象基类 |
| `FieldFillConfiguration` | 字段填充配置（`ydsz.jdbc.field-fill.*`） |

### 5. 逻辑删除

| 类 | 说明 |
|---|---|
| `LogicalDeleteConfiguration` | 逻辑删除配置（`ydsz.jdbc.logical-delete.*`） |
| `LogicalDeleteInterceptor` | 自定义实现：DELETE 转 UPDATE，SELECT 自动追加 `deleted = 0`，替代 `@TableLogic` 注解 |

### 6. 乐观锁

| 类 | 说明 |
|---|---|
| `OptimisticLockConfiguration` | 乐观锁配置（`ydsz.jdbc.optimistic-lock.*`） |
| `OptimisticLockInterceptor` | 自定义实现：基于 `revision` 列 CAS，替代 `@Version` 注解 |

启动期通过 `ApplicationReadyEvent` 扫描实体类，检测自定义拦截器与 `@Version` 注解冲突并打印警告。未启用自定义拦截器时回退到 MP 内置 `OptimisticLockerInnerInterceptor`。

### 7. 数据权限

| 类 | 说明 |
|---|---|
| `DataPermissionConfiguration` | 数据权限配置（`ydsz.jdbc.data-permission.*`） |
| `DataPermissionContext` / `DataPermissionContextResolver` | 数据权限上下文与解析器 |
| `DataScopeContextHolder` | ThreadLocal 持有器，供 `@AuthRowPermission` 切面直接注入结构化上下文 |
| `DataScopeIdExpander` | 数据范围 ID 展开 SPI（部门/角色/自定义） |
| `DataPermissionIgnore` | 数据权限忽略标记 |
| `DataPermissionHelper` / `JSqlParserHelper` | JSqlParser 辅助工具 |
| `RowPermissionInnerInterceptor` | 行级数据权限（基于 SQL 解析注入 WHERE 条件） |
| `ColPermissionInnerInterceptor` | 列级权限（SELECT 字段过滤） |

#### 与 ydsz-common-auth 的对接关系

```
┌──────────────────────────────┐     ┌──────────────────────────────────┐
│      ydsz-common-auth        │     │        ydsz-common-jdbc          │
│                              │     │                                  │
│  @AuthRowPermission          │     │  DataPermissionContextResolver   │
│         ↓                    │     │         ↓                        │
│  AuthRowPermissionAspect     │     │  DataPermissionInnerInterceptor  │
│         ↓                    │     │    (RowPermission / ColPermission)│
│  DataPermissionResolver      │     │         ↓                        │
│    .resolve() → DataScopeInfo│     │  DataPermissionContext           │
│         ↓                    │     │                                  │
│  adaptToDataPermissionContext│     │                                  │
│         ↓                    │     │                                  │
│  DataScopeContextHolder.set()├────→│  DataScopeContextHolder.get()    │
│                              │     │    (优先路径，避免 Header 解析)   │
└──────────────────────────────┘     └──────────────────────────────────┘
```

**解析器优先级** (`DataPermissionContextResolver.resolve()`):
1. **DataScopeContextHolder**（由 `@AuthRowPermission` 切面注入，结构化对象，无序列化开销）
2. **HttpServletRequest Headers**（HTTP 请求头，标准 Web 场景）
3. **RequestContext extra headers**（Feign 透传兜底）

**生命周期**：`DataScopeContextHolder` 使用 ThreadLocal 存储，在 `AuthRowPermissionAspect` 的
`try-finally` 块中确保清除，防止线程池复用场景下上下文泄漏。

### 8. SQL 防火墙

| 类 | 说明 |
|---|---|
| `SqlFirewallProperties` | 配置属性（`ydsz.jdbc.sql-firewall.*`） |
| `SqlFirewallInnerInterceptor` | 拦截器实现，置于拦截器链末端，在所有 SQL 改写完成后做安全校验 |

防护能力：

- 拦截 `DROP TABLE/DATABASE/INDEX/SCHEMA/VIEW`
- 拦截 `TRUNCATE TABLE`
- 拦截无 WHERE 条件的 `DELETE`（使用 JSqlParser AST 精确判断，解析失败时保守拒绝）
- 拦截无 WHERE 条件的 `UPDATE`（同上）
- 拦截分号分隔的多语句执行（去除字符串字面量后检测）
- 拦截 `GRANT/REVOKE` 权限操作
- 支持 DROP/TRUNCATE 表白名单（`allow-tables`，忽略大小写）

触发拦截时抛出 `SysException`，并打印 ERROR 日志记录原始 SQL（截断 200 字符）。

### 9. SQL 追踪

| 类 | 说明 |
|---|---|
| `SqlTraceAutoConfiguration` | 自动配置，触发条件：慢 SQL 或 SQL 审计任一启用 |
| `SqlTraceInnerInterceptor` | 拦截器实现，合并慢 SQL 检测与 SQL 审计，单次解析完成两项工作 |
| `SqlFingerprint` | SQL 指纹归一化工具，避免高基数标签导致 Prometheus 内存爆炸 |

慢 SQL 检测：

- 超过 `threshold-millis` 记录 WARN 日志（SQL_ID + SQL + 耗时）
- 超过 `alert-threshold-millis` 记录 ERROR 告警并打印调用堆栈（前 15 帧）
- 通过 `SqlFingerprint` 归一化后上报 Micrometer 指标

SQL 审计：

- 独立 logger `sql.audit`，便于单独配置 appender
- 支持按 SQL 类型开关（SELECT/INSERT/UPDATE/DELETE）
- 支持排除表名、排除 Mapper 方法名
- 参数最大长度可配（默认 500，超长截断）

SQL 指纹归一化规则：

- 字符串字面量 `'xxx'` → `?`
- 数字字面量 `123` → `?`
- IN 列表 `IN (1,2,3)` → `IN (?)`
- 多空格折叠为单空格
- 超长指纹截断到 200 字符

### 10. 数据库熔断

| 类 | 说明 |
|---|---|
| `DatabaseCircuitBreaker` | 轻量级熔断器，无需引入 Resilience4j 外部依赖 |
| `DatabaseCircuitBreakerAutoConfiguration` | 自动配置（`ydsz.jdbc.circuit-breaker.enabled=true`） |
| `CircuitBreakerProperties` | 配置属性（`ydsz.jdbc.circuit-breaker.*`） |
| `CircuitBreakerInterceptor` | MyBatis 外层拦截器，拦截 `Executor.query` 与 `Executor.update` |

状态机：

| 状态 | 行为 |
|---|---|
| `CLOSED` | 正常状态，所有请求通过；连续失败计数达到阈值后切换到 OPEN |
| `OPEN` | 熔断状态，所有请求被快速拒绝（抛出 `SQLException`）；超过 `open-duration-millis` 后切换到 HALF_OPEN |
| `HALF_OPEN` | 半开状态，允许有限请求探测恢复；探测成功切换到 CLOSED，探测失败切换回 OPEN |

异常分类：

- `SQLException` 及其子类 — 计为数据库故障，触发熔断计数
- 由 `SQLException` 包装的 `RuntimeException`（如 Spring `DataAccessException`）— 同样计数
- 其他 `RuntimeException`（业务异常）— 不计入熔断计数

可观测性指标（Micrometer）：

- `dbc.circuitbreaker.state` Gauge — 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）
- `dbc.circuitbreaker.consecutive.failures` Gauge — 当前连续失败次数

### 11. 连接池

| 类 | 说明 |
|---|---|
| `HikariCPConfiguration` | HikariCP 配置，优先使用 `ydsz.jdbc.hikari.*`，未配置时回退到 Spring Boot 默认；支持连接池预热与 Micrometer 指标注册 |
| `HikariCPProperties` | HikariCP 配置属性 |
| `DataSourceHealthIndicator` | 数据源健康检查（见健康检查章节） |

### 12. 租户隔离

| 类 | 说明 |
|---|---|
| `TenantIsolationProperties` | 租户隔离配置（`ydsz.jdbc.tenant-isolation.*`），支持 SINGLE/MULTI/ISOLATE_DB 三种模式 |
| `TenantIsolationException` | 租户隔离异常 |

> 租户隔离拦截器（`TenantIsolationInterceptor`）由 `common-tenant` 模块通过 `InnerInterceptorProvider` SPI 注入，本模块仅提供配置类与异常。`TenantDataSourceRouter` 也已迁移至 `common-tenant`。

### 13. SPI 扩展点

| 接口 | 说明 |
|---|---|
| `InnerInterceptorProvider` | MyBatis-Plus InnerInterceptor 提供者，外部模块实现后注册为 Spring Bean 即可自动插入拦截器链 |
| `OrderedInnerInterceptor` | 带 `Ordered` 顺序的 `InnerInterceptor` 包装器，让任意拦截器具备排序能力 |
| `DataScopeIdExpander` | 数据权限范围 ID 扩展 SPI |
| `DataSourceLoadBalanceStrategy` | 数据源负载均衡策略 SPI |
| `FieldFillHandler` | MyBatis-Plus 审计字段填充 SPI |

拦截器链顺序约定（值越小越靠前）：

| 顺序 | 拦截器 | 说明 |
|---|---|---|
| 100 | OptimisticLock | 乐观锁 |
| 200 | LogicalDelete | 逻辑删除 |
| 300 | FieldFill | 字段填充 |
| 400 | TenantIsolation | 租户隔离（由 common-tenant 提供） |
| 500 | DataPermission | 行级 + 列级数据权限 |
| 600 | Pagination | 分页 |

### 14. 类型处理器

| 类 | 说明 |
|---|---|
| `JsonTypeHandler` | JSON → PostgreSQL JSONB 类型处理器 |
| `ListTypeHandler` | `List` ↔ JSON 字符串 |
| `MapTypeHandler` | `Map` ↔ JSON 字符串 |
| `IntegerStringTypeHandler` | `Integer` ↔ `String` 转换处理 |

### 15. 健康检查

| 类 | 说明 |
|---|---|
| `DataSourceHealthIndicator` | 主数据源健康检查，暴露 HikariCP 连接池指标；仅读取 `HikariPoolMXBean`，不获取连接，避免加剧连接竞争 |
| `DynamicDataSourceHealthIndicator` | 动态多数据源健康检查，检查 master 数据源连接池状态 |

`DataSourceHealthIndicator` 暴露指标：`active`、`idle`、`waiting`、`max`、`min`、`utilization`。

降级判定：

- 等待线程数 > max/2 → 标记为 DEGRADED（`reason=High connection wait queue`）
- 连接池利用率 > 90% → 标记为 DEGRADED（`reason=Connection pool near exhaustion`）

### 16. 实体基类

| 类 | 说明 |
|---|---|
| `MpBaseEntity<T>` | 增强版基础实体，含主键（雪花算法）、审计字段、revision 乐观锁、deleted 逻辑删除、tenant_id 租户隔离、status 状态 |
| `MpBaseAuditEntity<T>` | 增强版审计实体，仅含审计字段（createdBy/createdAt/updatedBy/updatedAt） |
| `MpBaseIdEntity<T>` | 增强版主键实体，仅含 `@TableId` 雪花算法主键 |

业务模块应直接继承 `MpBaseEntity` 等类，而非 `common-domain` 的 `BaseEntity`。`MpBaseEntity` 不使用 `@Version` 与 `@TableLogic` 注解，改由自定义拦截器处理，避免双重处理冲突。

### 17. 监控

| 指标 | 说明 |
|---|---|
| `jdbc.slow.sql{sql_fingerprint}` Timer | 慢 SQL 执行耗时（使用指纹避免高基数） |
| `jdbc.slow.sql.count{sql_fingerprint}` Counter | 慢 SQL 执行计数 |
| `dbc.circuitbreaker.state` Gauge | 数据库熔断器状态 |
| `dbc.circuitbreaker.consecutive.failures` Gauge | 熔断器连续失败次数 |
| `hikaricp.connections.*` | HikariCP 连接池指标（active/idle/min/max/pending） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-jdbc</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  jdbc:
    enabled: true
```

> **Mapper 扫描**：请在主应用类上添加 `@MapperScan("com.njydsz.xxx.infra.mapper")` 注解指定模块的 Mapper 扫描包。

### 3. DO 继承 MpBaseEntity

```java
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends MpBaseEntity<Long> {
    private String username;
    private String email;
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.enabled` | true | 是否启用 JDBC 模块 |
| `ydsz.jdbc.hikari.*` | - | HikariCP 连接池配置（优先于 spring.datasource.hikari.*） |
| `ydsz.jdbc.tenant-isolation.enabled` | true | 租户隔离开关 |
| `ydsz.jdbc.tenant-isolation.mode` | SINGLE | 租户隔离模式（SINGLE/MULTI/ISOLATE_DB） |
| `ydsz.jdbc.tenant-isolation.ignore-tables` | - | 忽略租户隔离的表 |
| `ydsz.jdbc.tenant-isolation.anon-urls` | - | 跳过租户隔离的 URL 白名单 |
| `ydsz.jdbc.data-permission.enabled` | true | 数据权限开关 |
| `ydsz.jdbc.pagination.max-limit` | - | 最大分页大小（安全加固） |
| `ydsz.jdbc.pagination.db-type` | - | 显式指定数据库类型 |
| `ydsz.jdbc.dynamic-datasource.enabled` | true | 动态数据源开关 |
| `ydsz.jdbc.read-write-splitting.enabled` | false | 读写分离开关 |
| `ydsz.jdbc.read-write-splitting.master-ds` | master | 主库数据源名称 |
| `ydsz.jdbc.read-write-splitting.slave-ds-list` | `[slave]` | 从库数据源名称列表 |
| `ydsz.jdbc.read-write-splitting.load-balance-strategy` | round-robin | 负载均衡策略（round-robin/random/weighted） |
| `ydsz.jdbc.read-write-splitting.weights` | - | 从库权重映射（weighted 策略生效） |
| `ydsz.jdbc.slow-sql.enabled` | false | 慢 SQL 检测开关 |
| `ydsz.jdbc.slow-sql.threshold-millis` | 1000 | 慢 SQL 阈值（ms） |
| `ydsz.jdbc.slow-sql.alert-threshold-millis` | 3000 | 慢 SQL 告警阈值（ms） |
| `ydsz.jdbc.sql-audit.enabled` | false | SQL 审计开关 |
| `ydsz.jdbc.sql-audit.audit-select` | false | 是否审计 SELECT |
| `ydsz.jdbc.sql-audit.audit-insert` | true | 是否审计 INSERT |
| `ydsz.jdbc.sql-audit.audit-update` | true | 是否审计 UPDATE |
| `ydsz.jdbc.sql-audit.audit-delete` | true | 是否审计 DELETE |
| `ydsz.jdbc.sql-audit.log-parameters` | true | 是否记录 SQL 参数 |
| `ydsz.jdbc.sql-audit.max-parameter-length` | 500 | 参数最大长度 |
| `ydsz.jdbc.sql-audit.exclude-tables` | - | 排除审计的表名列表 |
| `ydsz.jdbc.sql-audit.exclude-methods` | - | 排除审计的 Mapper 方法名列表 |
| `ydsz.jdbc.sql-firewall.enabled` | false | SQL 防火墙开关 |
| `ydsz.jdbc.sql-firewall.block-drop-table` | true | 拦截 DROP 操作 |
| `ydsz.jdbc.sql-firewall.block-truncate` | true | 拦截 TRUNCATE 操作 |
| `ydsz.jdbc.sql-firewall.block-delete-without-where` | true | 拦截无 WHERE 的 DELETE |
| `ydsz.jdbc.sql-firewall.block-update-without-where` | true | 拦截无 WHERE 的 UPDATE |
| `ydsz.jdbc.sql-firewall.block-multi-statement` | true | 拦截分号多语句 |
| `ydsz.jdbc.sql-firewall.block-permission-ops` | true | 拦截 GRANT/REVOKE |
| `ydsz.jdbc.sql-firewall.allow-tables` | - | DROP/TRUNCATE 表白名单 |
| `ydsz.jdbc.circuit-breaker.enabled` | false | 数据库熔断开关 |
| `ydsz.jdbc.circuit-breaker.failure-threshold` | 10 | 连续失败次数阈值 |
| `ydsz.jdbc.circuit-breaker.open-duration-millis` | 30000 | 熔断持续时间（ms） |
| `ydsz.jdbc.circuit-breaker.half-open-probe-size` | 3 | 半开探测请求数 |

## 使用示例

### 1. 动态数据源切换

```java
import com.njydsz.common.jdbc.annotation.DS;

@Service
public class OrderService {

    @DS("report_ds")
    public List<Order> queryReport() {
        // 路由到报表库
        return orderMapper.selectList(null);
    }
}
```

### 2. 数据权限

```java
import com.njydsz.common.jdbc.permission.DataPermissionIgnore;

@Service
public class UserService {

    // 默认受数据权限约束
    public List<User> page() {
        return userMapper.selectList(null);
    }

    // 标记忽略数据权限（如管理员全量查询）
    @DataPermissionIgnore
    public List<User> adminList() {
        return userMapper.selectList(null);
    }
}
```

### 3. 自定义 SPI 拦截器

```java
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.jdbc.spi.InnerInterceptorProvider;

@Component
public class CustomInterceptorProvider implements InnerInterceptorProvider {

    @Override
    public InnerInterceptor createInterceptor() {
        return new CustomInnerInterceptor();
    }

    @Override
    public int getOrder() {
        return 350; // 在 FieldFill(300) 与 TenantIsolation(400) 之间
    }
}
```

### 4. SQL 防火墙配置

```yaml
ydsz:
  jdbc:
    sql-firewall:
      enabled: true
      block-drop-table: true
      block-truncate: true
      block-delete-without-where: true
      block-update-without-where: true
      block-multi-statement: true
      allow-tables:
        - temp_log_table
```

### 5. 数据库熔断配置

```yaml
ydsz:
  jdbc:
    circuit-breaker:
      enabled: true
      failure-threshold: 10          # 连续失败 10 次触发熔断
      open-duration-millis: 30000    # 熔断持续 30 秒
      half-open-probe-size: 3        # 半开状态允许 3 次探测
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `InnerInterceptorProvider` | 内部拦截器提供者，将自定义拦截器注册到 MP 链 | common-tenant（TenantIsolation）、业务模块 |
| `OrderedInnerInterceptor` | 带顺序的拦截器包装器，让任意 InnerInterceptor 具备排序能力 | 框架内部 |
| `DataScopeIdExpander` | 数据权限范围 ID 展开（部门/角色/自定义） | 业务模块 |
| `DataSourceLoadBalanceStrategy` | 数据源负载均衡策略 | 框架内置三种实现，业务可扩展 |
| `FieldFillHandler` | MyBatis-Plus 审计字段填充 | 框架内置四种 Handler，业务可扩展 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/db` | 主数据源健康检查（HikariCP 指标） | `spring-boot-health` 在类路径 |
| `/actuator/health/db-dynamic` | 动态多数据源健康检查 | `DynamicRoutingDataSource` Bean 存在 |

健康检查仅读取 `HikariPoolMXBean` 指标，不实际获取数据库连接，避免在连接池高负载时加剧竞争。等待线程数超过 max/2 或利用率超过 90% 时标记为 DEGRADED。

## 注意事项

1. **乐观锁冲突**：启用自定义 `OptimisticLockInterceptor` 后，实体类不应再使用 `@Version` 注解，启动期会扫描并打印冲突警告。
2. **逻辑删除**：自定义 `LogicalDeleteInterceptor` 不依赖 `@TableLogic` 注解，两者不应同时使用。
3. **SQL 防火墙位置**：`SqlFirewallInnerInterceptor` 置于拦截器链末端，在所有 SQL 改写完成后做安全校验，避免改写后的 SQL 被误判。
4. **慢 SQL 指纹**：使用 `SqlFingerprint` 归一化 SQL 作为 Micrometer tag，避免原始 SQL 高基数导致 Prometheus 内存爆炸。
5. **熔断器异常分类**：仅 `SQLException` 及其包装异常触发熔断计数，业务异常不计入，避免误熔断。
6. **读写分离事务感知**：`@Transactional` 激活时 SELECT 强制走主库，保证读写一致性。
7. **租户隔离**：拦截器由 `common-tenant` 模块通过 SPI 注入，本模块仅提供配置类；未引入 `common-tenant` 时 `tenant_id` 字段被忽略（DDL 默认值 '1'）。

## 变更记录

- **v1.0.0**（2026-08-02）：补全 SQL 防火墙、SQL 追踪、数据库熔断、SPI 扩展点、健康检查、读写分离章节；完善配置项表与使用示例
