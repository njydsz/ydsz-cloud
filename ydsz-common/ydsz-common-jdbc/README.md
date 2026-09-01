# ydsz-common-jdbc

> MyBatis-Plus 增强与数据访问公共模块（L4 基础数据层）

提供 MyBatis-Plus 增强、动态数据源、数据权限、SQL 防火墙、SQL 追踪、字段填充、逻辑删除（@TableLogic）、乐观锁（@Version）等开箱即用的能力，是所有业务模块数据访问层的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 MyBatis-Plus 增强、动态数据源、数据权限、SQL 防火墙、SQL 追踪等能力 |
| **依赖** | common-core、common-domain、common-exception、common-util、common-json、common-cache；可选依赖 dynamic-datasource、mysql-connector-j、postgresql、spring-boot-actuator、spring-boot-health |
| **版本** | 26.09.01 |

## 核心能力

### 1. MyBatis-Plus 集成

| 类 | 说明 |
|---|---|
| `MybatisPlusConfiguration` | MP 全局配置，按顺序组装乐观锁、字段填充、SPI、数据权限、分页、SQL 防火墙拦截器链 |

拦截器链执行顺序（按添加顺序）：

1. `OptimisticLockerInnerInterceptor` — 乐观锁（MP 内置，配合 `@Version` 注解；26.09.01 起替代自研拦截器）
2. `CombinedFieldFillInterceptor` — 字段填充（合并多 Handler 单次解析，配合 `FieldFillHandler` 体系）
3. SPI 拦截器 — 外部模块通过 `InnerInterceptorProvider` 注入（按 order 排序）
4. `RowPermissionInnerInterceptor` + `ColPermissionInnerInterceptor` — 数据权限
5. `PaginationInnerInterceptor` — 分页（动态适配 DbType + maxLimit 安全加固）
6. `SqlFirewallInnerInterceptor` — SQL 防火墙（置于链末端，所有改写完成后做安全校验）

> 逻辑删除采用 MP 原生 `@TableLogic`（26.09.01 起统一），不再使用自研拦截器。

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

### 3. 字段填充

| 类 | 说明 |
|---|---|
| `AbstractFieldFillHandler` / `FieldFillHandler` | 字段填充抽象基类与接口 |
| `CreatedByHandler` / `CreatedAtHandler` | 创建人 / 创建时间填充（INSERT） |
| `UpdatedByHandler` / `UpdatedAtHandler` | 更新人 / 更新时间填充（INSERT_UPDATE） |
| `CombinedFieldFillInterceptor` | 合并多 Handler 的 InnerInterceptor，单次 SQL 解析完成所有字段填充 |
| `AbstractSqlHandler` | SQL 处理抽象基类 |
| `FieldFillConfiguration` | 字段填充配置（`ydsz.jdbc.field-fill.*`） |

> 说明：26.09.01 起逻辑删除统一使用 MP `@TableLogic`、乐观锁统一使用 MP `OptimisticLockerInnerInterceptor`（`@Version`），原自研 `LogicalDeleteInterceptor` / `OptimisticLockInterceptor` / `MyMetaObjectHandler` 已移除。动态数据源用于多库场景；**SQL 级自动读写分离（SELECT 走从库）未实现**，读写分离由 `Dynamic-Datasource`（baomidou）在数据源层面提供。

### 4. 数据权限

| 类 | 说明 |
|---|---|
| `DataPermissionConfiguration` | 数据权限配置（`ydsz.jdbc.data-permission.*`） |
| `DataPermissionContext` / `DataPermissionContextResolver` | 数据权限上下文与解析器 |
| `DataScopeContextHolder` | ThreadLocal 持有器，供 `@AuthRowPermission` 切面直接注入结构化上下文 |
| `DataScopeIdExpander` / `NoopDataScopeIdExpander` | 数据范围 ID 展开 SPI（部门/角色/自定义）；未引入 common-tenant 时使用 Noop 实现 |
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

### 5. SQL 防火墙

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

### 6. SQL 追踪

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

### 7. 连接池

| 类 | 说明 |
|---|---|
| `HikariCPPoolConfigurer` | HikariCP 连接池定制（挂载到 Spring Boot 默认 HikariCP 配置之上） |
| `DataSourceHealthIndicator` | 数据源健康检查（见健康检查章节） |

### 8. 配置属性类概览

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `JdbcProperties` | `ydsz.jdbc` | 主配置，含 enabled / mapperScanPackages / slowSql / sqlAudit / safeQuery |
| `SlowSqlProperties` | `ydsz.jdbc.slow-sql` | 慢 SQL 配置：enabled / thresholdMillis / alertThresholdMillis |
| `SqlAuditProperties` | `ydsz.jdbc.sql-audit` | SQL 审计配置：enabled / auditSelect / auditInsert / auditUpdate / auditDelete / logParameters / maxParameterLength / excludeTables / excludeMethods |
| `SafeQueryProperties` | `ydsz.jdbc.safe-query` | 安全查询配置：enabled / strictMode / orderByWhitelist |
| `PaginationProperties` | `ydsz.jdbc.pagination` | 分页配置：dbType / maxLimit / overflow |
| `SqlFirewallProperties` | `ydsz.jdbc.sql-firewall` | SQL 防火墙配置：enabled / blockDropTable / blockTruncate / blockDeleteWithoutWhere / blockUpdateWithoutWhere / blockMultiStatement / blockPermissionOps / allowTables |
| `DataPermissionConfiguration` | `ydsz.jdbc.data-permission` | 数据权限配置（独立 `@Configuration` 类） |
| `FieldFillConfiguration` | `ydsz.jdbc.field-fill` | 字段填充配置（独立 `@Configuration` 类） |

### 9. 租户隔离

> 租户隔离拦截器（`TenantIsolationInterceptor`）、`TenantDataSourceRouter`、`TenantProperties` 均由 `common-tenant` 模块提供，并通过 `InnerInterceptorProvider` SPI 注入本模块拦截器链。本模块仅承载拦截器装配位置，无独立租户配置类。

### 10. SPI 扩展点

| 接口 | 说明 |
|---|---|
| `InnerInterceptorProvider` | MyBatis-Plus InnerInterceptor 提供者，外部模块实现后注册为 Spring Bean 即可自动插入拦截器链 |
| `OrderedInnerInterceptor` | 带 `Ordered` 顺序的 `InnerInterceptor` 包装器，让任意拦截器具备排序能力 |
| `DataScopeIdExpander` | 数据权限范围 ID 扩展 SPI |
| `FieldFillHandler` | MyBatis-Plus 审计字段填充 SPI |
| `SafeQueryInnerInterceptor` / `SafeQueryProperties` | 安全查询拦截（`ydsz.jdbc.safe-query.*`） |

拦截器链顺序约定（值越小越靠前）：

| 顺序 | 拦截器 | 说明 |
|---|---|---|
| 100 | OptimisticLocker | 乐观锁（MP 内置 `@Version`） |
| 300 | FieldFill | 字段填充 |
| 400 | TenantIsolation | 租户隔离（由 common-tenant 提供） |
| 500 | DataPermission | 行级 + 列级数据权限 |
| 600 | Pagination | 分页 |
| 700 | SqlFirewall | SQL 防火墙（链末端安全校验） |

### 11. 类型处理器

| 类 | 说明 |
|---|---|
| `JsonTypeHandler` | JSON → PostgreSQL JSONB 类型处理器 |
| `ListTypeHandler` | `List` ↔ JSON 字符串 |
| `MapTypeHandler` | `Map` ↔ JSON 字符串 |
| `IntegerStringTypeHandler` | `Integer` ↔ `String` 转换处理 |

### 12. 健康检查

| 类 | 说明 |
|---|---|
| `DataSourceHealthIndicator` | 主数据源健康检查，暴露 HikariCP 连接池指标；仅读取 `HikariPoolMXBean`，不获取连接，避免加剧连接竞争 |
| `DynamicDataSourceHealthIndicator` | 动态多数据源健康检查，检查 master 数据源连接池状态 |

`DataSourceHealthIndicator` 暴露指标：`active`、`idle`、`waiting`、`max`、`min`、`utilization`。

降级判定：

- 等待线程数 > max/2 → 标记为 DEGRADED（`reason=High connection wait queue`）
- 连接池利用率 > 90% → 标记为 DEGRADED（`reason=Connection pool near exhaustion`）

### 13. 实体基类

| 类 | 说明 |
|---|---|
| `MpBaseEntity<T>` | 增强版基础实体，含主键（雪花算法）、审计字段、revision 乐观锁、deleted 逻辑删除、tenant_id 租户隔离、status 状态 |
| `MpBaseAuditEntity<T>` | 增强版审计实体，仅含审计字段（createdBy/createdAt/updatedBy/updatedAt） |
| `MpBaseIdEntity<T>` | 增强版主键实体，仅含 `@TableId` 雪花算法主键 |

业务模块应直接继承 `MpBaseEntity` 等类，而非 `common-domain` 的 `BaseEntity`。`MpBaseEntity` 使用 `@Version` 与 `@TableLogic` 注解（MP 原生机制）。

### 14. 监控

| 指标 | 说明 |
|---|---|
| `jdbc.slow.sql{sql_fingerprint}` Timer | 慢 SQL 执行耗时（使用指纹避免高基数） |
| `jdbc.slow.sql.count{sql_fingerprint}` Counter | 慢 SQL 执行计数 |
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

### 顶部摘要

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.enabled` | true | 是否启用 JDBC 模块 |
| `ydsz.jdbc.mapper-scan-packages` | `["com.njydsz.**.mapper"]` | MyBatis Mapper 扫描包 |

### 慢 SQL 追踪（SlowSqlProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.slow-sql.enabled` | false | 慢 SQL 检测开关（默认关闭，需显式开启） |
| `ydsz.jdbc.slow-sql.threshold-millis` | 1000 | 慢 SQL 阈值（ms） |
| `ydsz.jdbc.slow-sql.alert-threshold-millis` | 3000 | 慢 SQL 告警阈值（ms） |

### SQL 审计（SqlAuditProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.sql-audit.enabled` | false | SQL 审计开关（默认关闭） |
| `ydsz.jdbc.sql-audit.audit-select` | false | 是否审计 SELECT |
| `ydsz.jdbc.sql-audit.audit-insert` | true | 是否审计 INSERT |
| `ydsz.jdbc.sql-audit.audit-update` | true | 是否审计 UPDATE |
| `ydsz.jdbc.sql-audit.audit-delete` | true | 是否审计 DELETE |
| `ydsz.jdbc.sql-audit.log-parameters` | true | 是否记录 SQL 参数 |
| `ydsz.jdbc.sql-audit.max-parameter-length` | 500 | 参数最大长度 |
| `ydsz.jdbc.sql-audit.exclude-tables` | - | 排除审计的表名列表 |
| `ydsz.jdbc.sql-audit.exclude-methods` | - | 排除审计的 Mapper 方法名列表 |

### 安全查询（SafeQueryProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.safe-query.enabled` | true | 安全查询拦截开关 |
| `ydsz.jdbc.safe-query.strict-mode` | false | 严格模式（true: 抛异常；false: 仅日志警告） |
| `ydsz.jdbc.safe-query.order-by-whitelist` | - | 排序字段白名单（配置后仅允许白名单字段参与排序，为空时仅用正则校验） |

### 分页（PaginationProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.pagination.db-type` | - | 数据库类型（mysql/oracle/postgresql/sqlserver/h2 等，不配则自动检测） |
| `ydsz.jdbc.pagination.max-limit` | 500 | 单页最大记录数（安全加固，防止全表扫描） |
| `ydsz.jdbc.pagination.overflow` | false | 页码溢出是否继续查询 |

### SQL 防火墙（SqlFirewallProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.sql-firewall.enabled` | false | SQL 防火墙开关（默认关闭，需显式开启） |
| `ydsz.jdbc.sql-firewall.block-drop-table` | true | 拦截 DROP 操作 |
| `ydsz.jdbc.sql-firewall.block-truncate` | true | 拦截 TRUNCATE 操作 |
| `ydsz.jdbc.sql-firewall.block-delete-without-where` | true | 拦截无 WHERE 的 DELETE |
| `ydsz.jdbc.sql-firewall.block-update-without-where` | true | 拦截无 WHERE 的 UPDATE |
| `ydsz.jdbc.sql-firewall.block-multi-statement` | true | 拦截分号多语句 |
| `ydsz.jdbc.sql-firewall.block-permission-ops` | true | 拦截 GRANT/REVOKE |
| `ydsz.jdbc.sql-firewall.allow-tables` | - | DROP/TRUNCATE 表白名单 |

### 动态数据源

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jdbc.dynamic-datasource.enabled` | true | 动态数据源开关 |
| `ydsz.jdbc.read-write-splitting.enabled` | false | 读写分离开关 |
| `ydsz.jdbc.read-write-splitting.master-ds` | master | 主库数据源名称 |
| `ydsz.jdbc.read-write-splitting.slave-ds-list` | `[slave]` | 从库数据源名称列表 |
| `ydsz.jdbc.read-write-splitting.load-balance-strategy` | round-robin | 负载均衡策略（round-robin/random/weighted） |
| `ydsz.jdbc.read-write-splitting.weights` | - | 从库权重映射（weighted 策略生效） |

> 说明：`ydsz.jdbc.hikari.*`、`ydsz.jdbc.tenant-isolation.*`、`ydsz.jdbc.circuit-breaker.*`、`ydsz.jdbc.logical-delete.*`、`ydsz.jdbc.optimistic-lock.*` 等配置组**不存在**——连接池沿用 Spring Boot 默认 HikariCP 配置，租户隔离由 common-tenant 提供，读写分离由 Dynamic-Datasource 数据源层面实现，熔断/自研逻辑删除/自研乐观锁已移除。

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

### 5. 租户隔离

> 租户隔离拦截器由 `common-tenant` 模块通过 `InnerInterceptorProvider` SPI 注入，配置项见 common-tenant 文档（`ydsz.tenant.*`）。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `InnerInterceptorProvider` | 内部拦截器提供者，将自定义拦截器注册到 MP 链 | common-tenant（TenantIsolation）、业务模块 |
| `OrderedInnerInterceptor` | 带顺序的拦截器包装器，让任意 InnerInterceptor 具备排序能力 | 框架内部 |
| `DataScopeIdExpander` | 数据权限范围 ID 展开（部门/角色/自定义） | 业务模块 |
| `FieldFillHandler` | MyBatis-Plus 审计字段填充 | 框架内置四种 Handler，业务可扩展 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/db` | 主数据源健康检查（HikariCP 指标） | `spring-boot-health` 在类路径 |
| `/actuator/health/db-dynamic` | 动态多数据源健康检查 | `DynamicRoutingDataSource` Bean 存在 |

健康检查仅读取 `HikariPoolMXBean` 指标，不实际获取数据库连接，避免在连接池高负载时加剧竞争。等待线程数超过 max/2 或利用率超过 90% 时标记为 DEGRADED。

## 注意事项

1. **乐观锁**：使用 MP `@Version` 注解 + `OptimisticLockerInnerInterceptor`（内置），实体在 `revision` 字段上加注即可。
2. **逻辑删除**：使用 MP `@TableLogic` 注解，`MpBaseEntity` 已内置 `deleted` 字段。
3. **SQL 防火墙位置**：`SqlFirewallInnerInterceptor` 置于拦截器链末端，在所有 SQL 改写完成后做安全校验，避免改写后的 SQL 被误判。
4. **慢 SQL 指纹**：使用 `SqlFingerprint` 归一化 SQL 作为 Micrometer tag，避免原始 SQL 高基数导致 Prometheus 内存爆炸。
5. **租户隔离**：拦截器由 `common-tenant` 模块通过 SPI 注入，本模块仅承载装配；未引入 `common-tenant` 时 `DataScopeIdExpander` 使用 `NoopDataScopeIdExpander`，`tenant_id` 字段被忽略（DDL 默认值 '1'）。
6. **SQL 防火墙默认关闭**：`ydsz.jdbc.sql-firewall.enabled` 默认 `false`，需显式开启。
7. **安全查询默认开启**：`ydsz.jdbc.safe-query.enabled` 默认 `true`，所有查询方法自动拦截注入 ORDER BY 子句 SQL。
8. **慢 SQL 默认关闭**：`ydsz.jdbc.slow-sql.enabled` 默认 `false`，需显式开启。
9. **配置拆分为 7 个属性类**：每组配置由独立的 `@ConfigurationProperties` 类承载，`MybatisPlusConfiguration` 统一通过 `@EnableConfigurationProperties` 注册。

## 变更记录

- **26.09.01**（2026-08-02）：补全 SQL 防火墙、SQL 追踪、SPI 扩展点、健康检查章节；完善配置项表与使用示例。26.09.01 起逻辑删除/乐观锁统一收口到 MP 原生注解，移除自研拦截器与读写分离/数据库熔断。
