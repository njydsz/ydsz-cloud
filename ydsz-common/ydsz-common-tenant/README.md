# ydsz-common-tenant

> 多租户隔离（L4 基础数据层）— WebFilter + MP 拦截器 + Feign 传播

提供基于 WebFilter + MyBatis-Plus InnerInterceptor 的租户 ID 注入与 SQL 行级隔离（SINGLE / MULTI / SCHEMA 三模式），多数据源路由，Feign / 线程池异步上下文传播，租户级限流，Redis Key 自动拼接租户前缀，数据库索引校验等能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多租户上下文注入、SQL 改写、数据源路由、跨服务传播 |
| **依赖** | ydsz-common-core、ydsz-common-jdbc、ydsz-common-util、ydsz-common-domain、ydsz-common-cache、ydsz-common-redis；可选 feign-core、ydsz-common-thread、spring-boot-starter-web、micrometer-core |
| **版本** | 1.3.0 |

## 核心能力

### 1. 多租户上下文

| 类 | 说明 |
|---|---|
| `TenantAutoConfiguration` | 自动配置入口 |
| `TenantProperties` | 租户配置属性（`ydsz.tenant.*`） |
| `TenantConfigProvider` | 租户配置提供者（运行时查询租户隔离模式） |
| `TenantPropertiesAnnotationPopulator` | 配置属性填充（扫描 @TenantColumn 注解注入白名单） |
| `SystemTenantContextRunner` | 系统级上下文运行器（无租户上下文时的 fallback） |

### 2. SQL 行级隔离

| 类 | 说明 |
|---|---|
| `TenantInterceptorProvider` | 租户拦截器提供者（内部隔离逻辑） |
| `TenantIsolationInterceptor` | SQL 拦截器（基于 MP InnerInterceptor，改写 WHERE 追加 tenant_id = ?） |
| `@TenantColumn` | 租户列声明注解（标注在 Entity 字段上指定列名） |
| `@TenantColumnScanner` | 租户列扫描器（启动期扫描所有 @TenantColumn 字段） |

**隔离模式**：

| 模式 | 说明 |
|---|---|
| SINGLE | 单租户模式（不注入租户条件，兼容历史数据） |
| MULTI | 多租户行级隔离（默认，WHERE tenant_id = ? 自动追加） |
| SCHEMA | Schema 级隔离（数据库 schema 分离，切换数据源） |

### 3. 多数据源路由

| 类 | 说明 |
|---|---|
| `DatasourceKeyResolver` | 数据源 Key 解析（根据租户 ID 解析目标数据源） |
| `TenantDataSourceFilter` | 租户数据源 Filter（WebFilter 注入租户上下文） |
| `TenantDataSourceRouter` | 租户数据源路由器（DynamicDataSource 策略） |

**SCHEMA 模式下**：根据租户 ID 自动路由到对应 schema 数据源。

### 4. 上下文传播

| 类 | 说明 |
|---|---|
| `TenantContextFeignInterceptor` | Feign 拦截器（请求携带 X-Tenant-Id Header，下游服务自动提取） |
| `TenantHeaderContract` | 租户 Header 合约（统一 Header 命名规范） |

### 5. Redis Key 隔离

| 类 | 说明 |
|---|---|
| `CacheIsolationStrategy` | 缓存隔离策略（本地缓存按租户隔离，避免跨租户泄漏） |
| `CacheKeyBuilderInitializer` | 缓存 Key 构建器初始化（自动拼接 tenant: 前缀） |

### 6. 租户级限流

| 类 | 说明 |
|---|---|
| `TenantRateLimiter`（ratelimit） | 租户级限流器（基于 Redis，不同租户独立计数） |

### 7. 数据库约束校验

| 类 | 说明 |
|---|---|
| `TenantIndexValidator`（validation） | 租户表索引校验工具（SQL 改写后必须有 tenant_id 索引，启动期检测并报错） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-tenant</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  tenant:
    enabled: true
    mode: MULTI                      # SINGLE / MULTI / SCHEMA
    column: tenant_id                # 租户列名（数据库列）
    header: X-Tenant-Id              # 租户 ID Header 名称
    ignore-tables:                   # 忽略租户隔离的表（系统级共享表）
      - sys_dict
      - sys_config
    datasource:
      mode: SHARED                   # SHARED / SCHEMA / DB
      default-ds: master             # 默认数据源
    propagation:
      feign: true                    # Feign 传播
      thread-pool: true              # TTL 传播
    rate-limit:
      enabled: true
      default-qps: 100
```

### 3. Entity 标注

```java
@Data
@TableName("t_order")
public class Order {

    @TableId(type = AUTO)
    private Long id;

    @TenantColumn                    // 标注租户列
    private String tenantId;

    // 业务字段...
}
```

### 4. 编程式使用

```java
// 获取当前租户 ID
String tenantId = TenantContextHolder.getTenantId();

// 手动切换租户（谨慎使用）
TenantContextTenantHolder.setTenantId("tenant_002");

// 在系统级上下文中运行（绕过租户隔离）
SystemTenantContextRunner.run(() -> {
    // 此处 SQL 不注入 tenant_id 条件
    loadSystemData();
});
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.tenant.enabled` | true | 是否启用租户隔离 |
| `ydsz.tenant.mode` | MULTI | 隔离模式（SINGLE / MULTI / SCHEMA） |
| `ydsz.tenant.column` | tenant_id | 租户列名 |
| `ydsz.tenant.header` | X-Tenant-Id | Header 名称 |
| `ydsz.tenant.ignore-tables` | - | 忽略隔离的表列表 |
| `ydsz.tenant.datasource.mode` | SHARED | 数据源模式（SHARED / SCHEMA / DB） |
| `ydsz.tenant.propagation.feign` | true | Feign 传播开关 |
| `ydsz.tenant.propagation.thread-pool` | true | TTL 传播开关 |
| `ydsz.tenant.rate-limit.enabled` | false | 租户级限流开关 |
| `ydsz.tenant.rate-limit.default-qps` | 100 | 默认租户 QPS 上限 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/tenant` | 多租户健康检查 | `spring-boot-health` 在 classpath |

`TenantHealthIndicator` 暴露信息：`tenant_id` / `mode` / `datasource_status` / `status`。

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `TenantAutoConfiguration` | `ydsz-common-redis` 在 classpath |

## 注意事项

1. **DDL 要求**：启用 MULTI 模式后，所有业务表必须有 `tenant_id` 列；否则启动期校验报错。
2. **超级管理员**：tenantId = null / "0" 视为超级管理员（可跨租户访问，SQL 隔离自动跳过）。
3. **Feign 传播**：调用前确保 `TenantContextHolder` 已设置 tenantId；否则 Header 缺失导致下游上下文丢失。
4. **本地缓存隔离**：同一 JVM 多租户共享本地缓存时，CacheKey 必须带 `tenant:` 前缀。
5. **模式切换**：SINGLE ↔ MULTI 模式切换需重启服务（MP InnerInterceptor 初始化期绑定 SQL 解析器）。

## 变更记录

- **1.3.0**（2026-09-07）：新增 `TenantIndexValidator` 索引校验工具；修复 WITH CTE SQL 改写与 WHERE/HAVING/selectItems 标量子查询注入。
- **1.2.0**（2026-09-04）：SCHEMA 模式数据源路由新增 `DatasourceKeyResolver`；Feign 拦截器 Header 规范统一。
- **1.0.0**（2026-08-02）：初始版本（MULTI 行级隔离 + WebFilter + MP InnerInterceptor）。
