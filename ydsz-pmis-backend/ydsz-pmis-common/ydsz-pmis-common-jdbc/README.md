# ydsz-pmis-common-jdbc

PMIS 数据库访问层增强 — MyBatis-Plus 扩展、动态数据源、行/列权限拦截器、逻辑删除、乐观锁、租户隔离、字段自动填充、SQL 追踪。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 53 |

## 核心能力

### MyBatis-Plus 增强

| 类 | 说明 |
|---|---|
| `MybatisPlusConfiguration` | MP 全局配置（分页插件 / 拦截器链 / 类型处理器） |
| `MpBaseEntity` / `MpBaseAuditEntity` / `MpBaseIdEntity` | MP 实体基类 |
| `MapperScanConfiguration` | Mapper 扫描配置 |

### 动态数据源

| 类 | 说明 |
|---|---|
| `DynamicDataSourceConfiguration` | baomidou 动态数据源配置（master/slave 读写分离） |
| `DataSourceType` | 数据源类型枚举（MASTER / SLAVE） |
| `RoundRobinLoadBalanceStrategy` | 轮询负载均衡 |
| `RandomLoadBalanceStrategy` | 随机负载均衡 |
| `WeightedLoadBalanceStrategy` | 加权负载均衡 |
| `DataSourceLoadBalanceStrategy` | 负载均衡策略接口 |

### 连接池

| 类 | 说明 |
|---|---|
| `HikariCPConfiguration` / `HikariCPProperties` | HikariCP 连接池配置（连接泄漏检测 / 慢 SQL 记录） |
| `DataSourceHealthIndicator` | 数据源健康检查 |

### 拦截器链

| 拦截器 | 说明 |
|---|---|
| `TenantIsolationInterceptor` | 租户隔离（自动注入 `tenant_id` 条件） |
| `DataPermissionInterceptor` → `RowPermissionInnerInterceptor` | 行级数据权限（基于 SQL 解析注入 WHERE 条件） |
| `ColPermissionInnerInterceptor` | 列级权限（SELECT 字段过滤） |
| `LogicalDeleteInterceptor` | 逻辑删除（DELETE → UPDATE + SELECT 自动排除） |
| `OptimisticLockInterceptor` | 乐观锁（`@Version` 自动 CAS） |
| `CombinedFieldFillInterceptor` | 字段自动填充（创建/更新审计字段） |
| `SqlTraceInnerInterceptor` | SQL 追踪（慢 SQL 检测 + Micrometer 指标） |
| `OrderedInnerInterceptor` | 有序拦截器接口 |

### 数据权限

| 类 | 说明 |
|---|---|
| `DataPermissionContext` / `DataPermissionContextResolver` | 数据权限上下文 |
| `DataScopeIdExpander` | 数据范围 ID 展开（部门/角色/自定义） |
| `DataPermissionIgnore` | 数据权限忽略标记 |
| `DataPermissionHelper` | JSqlParser 辅助工具 |
| `JSqlParserHelper` | SQL 解析工具 |

### 字段填充处理器

| 类 | 说明 |
|---|---|
| `AbstractFieldFillHandler` / `FieldFillHandler` | 字段填充抽象基类 |
| `CreatedByHandler` / `CreatedAtHandler` | 创建人 / 创建时间填充 |
| `UpdatedByHandler` / `UpdatedAtHandler` | 更新人 / 更新时间填充 |
| `MyMetaObjectHandler` | MP MetaObjectHandler 实现 |
| `AbstractSqlHandler` | SQL 处理抽象基类 |

### 类型处理器

| 类 | 说明 |
|---|---|
| `JsonTypeHandler` | JSON → PostgreSQL JSONB 类型处理器 |
| `ListTypeHandler` | List ↔ JSON 字符串 |
| `MapTypeHandler` | Map ↔ JSON 字符串 |

## 配置项

```yaml
pmis:
  jdbc:
    hikari:
      leak-detection-threshold: 30000  # 连接泄漏检测（ms）
      slow-sql-threshold: 1000         # 慢 SQL 阈值（ms）
    tenant:
      enabled: true                    # 租户隔离开关
      ignore-tables: [sys_config]      # 忽略表
    data-permission:
      enabled: true                    # 数据权限开关
    pagination:
      max-page-size: 500               # 最大分页大小
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `MybatisPlusConfiguration` | MyBatis-Plus 可用时激活 |
| `DynamicDataSourceConfiguration` | 动态数据源可用时激活 |
| `HikariCPConfiguration` | HikariCP 可用时激活 |
| `LogicalDeleteConfiguration` | 总是激活 |
| `OptimisticLockConfiguration` | 总是激活 |
| `FieldFillConfiguration` | 总是激活 |
| `SqlTraceAutoConfiguration` | 总是激活 |
| `DataPermissionConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-jdbc</artifactId>
</dependency>
```
