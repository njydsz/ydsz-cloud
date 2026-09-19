# ydsz-common-tenant

> 多租户隔离（L4 基础数据层）— TTL 传播 + SQL 改写 + 多数据源路由 + 诊断能力

提供基于 WebFilter + MyBatis-Plus InnerInterceptor 的租户 ID 注入与 SQL 行级隔离（SINGLE / MULTI / ISOLATE_DB / SCHEMA 四模式），多数据源路由，Feign / 线程池异步上下文传播（基于阿里巴巴 TTL），租户级限流，Redis Key 自动拼接租户前缀，数据库索引校验、fail-closed 诊断信息等能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多租户上下文注入、SQL 改写、数据源路由、跨服务传播、运行时诊断 |
| **依赖** | ydsz-common-core、ydsz-common-jdbc、ydsz-common-util、ydsz-common-domain、ydsz-common-cache、ydsz-common-redis；可选 feign-core、ydsz-common-thread、spring-boot-starter-web、micrometer-core |
| **版本** | 1.4.0 |

## 核心能力

### 1. 多租户上下文

| 类 | 说明 |
|---|---|
| `TenantAutoConfiguration` | 自动配置入口 |
| `TenantProperties` | 租户配置属性（`ydsz.tenant.*`） |
| `TenantConfigProvider` | 租户配置提供者（运行时查询租户隔离模式） |
| `SystemTenantContextRunner` | 系统级上下文运行器（定时任务/MQ Consumer 无租户上下文时的 fallback） |

**上下文传播**：基于阿里巴巴 `TransmittableThreadLocal`（TTL）实现跨线程池自动传播；配合 `TenantContextTaskDecorator`，异步线程无上下文时兜底为系统租户，避免 fail-closed 误触发。

### 2. SQL 行级隔离

| 类 | 说明 |
|---|---|
| `TenantInterceptorProvider` | 租户拦截器提供者（内部隔离逻辑） |
| `TenantIsolationInterceptor` | SQL 拦截器（基于 MP InnerInterceptor，改写 WHERE 追加 tenant_id = ?） |
| `@TenantColumn` | 租户列声明注解（标注在 Entity 字段上指定列名） |
| `@TenantColumnScanner` | 租户列扫描器（启动期扫描所有 @TenantColumn 字段） |
| `DiagnosticsUtil` | fail-closed 诊断收集器（携带 thread/uri/tenantId/requestSnapshot 上下文） |

**隔离模式**：

| 模式 | 说明 |
|---|---|
| SINGLE | 单租户模式（只取第一个字段，兼容历史数据） |
| MULTI | 多字段组合行级隔离（默认，WHERE tenant_id = ? AND company_id = ? ...） |
| ISOLATE_DB | 独立数据源模式（每租户使用独立数据库，DynamicRoutingDataSource 路由） |
| SCHEMA | PostgreSQL Schema 级隔离，可选 search_path 或 JSqlParser 表前缀方式 |

**特性亮点**：

- **fail-closed 诊断**：异常消息自动携带 `[reason=..., thread=..., uri=..., hasTenantContext=..., tenantId=...]`，快速定位上下文丢失原因
- **SQL 改写缓存**：支持 LRU + TTL 二级缓存（默认关闭），缓存 Key 使用 SHA-256 哈希摘要避免长 SQL 膨胀

### 3. 多数据源路由

| 类 | 说明 |
|---|---|
| `DatasourceKeyResolver` | 数据源 Key 解析 SPI（根据租户 ID 解析目标数据源） |
| `TenantDataSourceFilter` | 租户数据源 Filter（ISOLATE_DB 模式下 Web 层切换数据源） |
| `TenantDataSourceRouter` | 租户数据源路由器（带缓存，预热后只读） |
| `SchemaSearchPathExecutor` | SCHEMA 模式 search_path 自动设置（可选，减少 JSqlParser 改写开销） |

**SCHEMA 模式定制化**：

| 配置 | 说明 |
|---|---|
| `ydsz.tenant.schema-mapping.{tenantId}` | 自定义 schema 名称（如 `tenant_A -> schema_grp1`） |
| `ydsz.tenant.schema-search-path-enabled` | true 时启用 `SET search_path TO {schema},public`，业务 SQL 无需带 schema 前缀 |

### 4. 上下文传播

| 类 | 说明 |
|---|---|
| `TenantContextFeignInterceptor` | Feign 拦截器（请求携带 X-Tenant-Id Header，下游服务自动提取） |
| `TenantHeaderContract` | 租户 Header 合约（统一 Header 命名规范） |
| `TenantContextTaskDecorator` | 线程池任务装饰器（TTL 传播兜底：异步线程无上下文时注入系统租户） |

**传播策略**：TTL 已挂载到 `RequestContext.CONTEXT_HOLDER`，所有经过 `TaskDecorator` / `TtlExecutors` / `CompletableFuture` 包装的任务自动传播租户上下文；对于无父线程上下文的场景（定时任务、MQ Consumer），装饰器兜底为系统租户。

### 5. Redis Key 隔离

| 类 | 说明 |
|---|---|
| `CacheIsolationStrategy` | 缓存隔离策略（本地缓存按租户隔离，避免跨租户泄漏） |
| `CacheKeyBuilderInitializer` | 缓存 Key 构建器初始化（自动拼接 `tenant:` 前缀） |
| `TenantRedisKeyPrefixer` | Redis Key 前缀器（所有 Redis key 自动添加租户前缀） |

### 6. 租户级限流

| 类 | 说明 |
|---|---|
| `TenantRateLimiter`（ratelimit） | 租户级限流器（基于 Redis，支持固定窗口/滑动窗口/令牌桶三种算法） |

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

#### MULTI 模式（多字段组合行级隔离，推荐）

```yaml
ydsz:
  tenant:
    enabled: true
    mode: MULTI
    tenant-fields:
      - column: tenant_id
        claim: tenantId
        header: X-Tenant-Id
      - column: company_id
        claim: companyId
        header: X-Company-Ids
      - column: dept_id
        claim: deptId
        header: X-Dept-Ids
        multi-value: true
    ignore-tables:
      - sys_dict
      - sys_config
    anon-urls:
      - /auth/login
      - /health
    tenant-sharing:  # 跨租户共享（合作伙伴数据互通）
      tenant_A: [tenant_B, tenant_C]
    sql-cache:
      enabled: true
      max-size: 2000
      expire-minutes: 10
```

#### SCHEMA 模式

```yaml
ydsz:
  tenant:
    enabled: true
    mode: SCHEMA
    schema-mapping:  # 可选：自定义 schema 名称映射
      tenant_001: schema_east
      tenant_002: schema_west
    schema-search-path-enabled: true  # 启用 search_path，减少 SQL 改写开销
```

#### ISOLATE_DB 模式

```yaml
ydsz:
  tenant:
    enabled: true
    mode: ISOLATE_DB
    datasource:
      mapping:  # 租户 ID -> 数据源 key
        tenant_001: ds_tenant_001
        tenant_002: ds_tenant_002
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

// 在系统级上下文中运行（定时任务/MQ Consumer 等无用户上下文场景）
SystemTenantContextRunner.run(() -> {
    // 此处 TenantContextHolder.getTenantId() = systemTenantId，SQL 跳过隔离
    loadSystemData();
});

// CompletableFuture 自动传播 TTL 上下文
CompletableFuture.supplyAsync(() -> {
    // 此处自动继承父线程租户上下文
    return service.query();
}, executor);
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.tenant.enabled` | `false` | 是否启用多租户（默认关闭，需显式开启） |
| `ydsz.tenant.mode` | `SINGLE` | 隔离模式（SINGLE / MULTI / ISOLATE_DB / SCHEMA） |
| `ydsz.tenant.tenant-fields` | `[]` | 多字段组合配置（claim/header/multiValue 灵活组合） |
| `ydsz.tenant.ignore-tables` | `[]` | 忽略租户隔离的表列表（大小写不敏感） |
| `ydsz.tenant.anon-urls` | `[]` | URL 白名单（前缀匹配），跳过租户隔离 |
| `ydsz.tenant.super-tenant-id` | `"0"` | 超级管理员租户 ID（跳过 SQL 隔离） |
| `ydsz.tenant.system-tenant-id` | `"0"` | 系统租户 ID（定时任务/MQ Consumer fallback） |
| `ydsz.tenant.tenant-sharing` | `{}` | 跨租户共享映射（key=租户 ID，value=可访问源租户 ID 列表） |
| `ydsz.tenant.datasource.mapping` | `{}` | ISOLATE_DB 模式数据源映射（key=租户 ID，value=数据源 key） |
| `ydsz.tenant.schema-mapping` | `{}` | SCHEMA 模式自定义 schema 名称映射（key=tenantId，value=schema 名） |
| `ydsz.tenant.schema-search-path-enabled` | `false` | SCHEMA 模式是否启用 search_path 自动设置 |
| `ydsz.tenant.sql-cache.enabled` | `false` | SQL 改写缓存开关 |
| `ydsz.tenant.sql-cache.max-size` | `2000` | SQL 缓存最大条目数 |
| `ydsz.tenant.sql-cache.expire-minutes` | `10` | SQL 缓存未访问过期时间 |
| `ydsz.tenant.validation.index-check.enabled` | `true` | 启动期索引校验开关 |

## 端点

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/tenant` | 多租户健康检查 | `HealthIndicator` 在 classpath |

`TenantHealthIndicator` 暴露信息：`enabled` / `mode` / `tenantColumn` / `superTenantId` / `systemTenantId` / `interceptPassCount` / `interceptBlockedCount` / `activeContexts` / `isolateDbMode`（ISOLATE_DB 模式下）。

## 健康检查与指标

| 指标名 | 类型 | 说明 |
|---|---|---|
| `tenant.active` | Gauge | 当前活跃租户数 |
| `tenant.sql.intercept.total` | Counter | SQL 拦截次数（tag: result=pass/blocked/skipped） |
| `tenant.failclosed.total` | Counter | fail-closed 拒绝次数 |
| `tenant.context.skip.total` | Counter | 跳过隔离次数 |
| `tenant.superadmin.total` | Counter | 超级管理员绕过次数 |
| `tenant.datasource.switch.total` | Counter | 数据源切换次数 |
| `tenant.sql.cache.total` | Counter | SQL 缓存命中/未命中（tag: result=hit/miss） |

## 生产部署 Checklist

### 启用前检查

- [ ] **DDL 验证**：MULTI 模式下所有业务表必须有对应的租户列；SCHEMA 模式下确认各 schema 已创建
- [ ] **索引确认**：`ydsz.tenant.validation.index-check.enabled=true` 已启用（默认），启动日志无 `[TenantIndexValidator] WARN`
- [ ] **anon-urls 配置**：登录、健康检查、回调等无需租户的 URL 已加入白名单
- [ ] **ignore-tables 配置**：系统配置表、字典表等全局共享表已加入忽略列表
- [ ] **system-tenant-id**：定时任务/MQ Consumer 场景使用的系统租户 ID 已正确配置
- [ ] **TTL 线程传播**：业务模块 `@Async` / `CompletableFuture` / `ThreadPoolTaskExecutor` 已引入 `TenantContextTaskDecorator`

### 模式选择

- [ ] **SINGLE → MULTI 迁移**：需停机重启（MP InnerInterceptor 初始化期绑定 SQL 解析器）
- [ ] **MULTI → SCHEMA 迁移**：需在 `schema-mapping` 中配置每个租户的 schema 名称；启用 `schema-search-path-enabled` 前在测试环境验证 search_path 行为
- [ ] **ISOLATE_DB 数据源**：确认 `DynamicRoutingDataSource` 已注册所有租户数据源；`DatasourceKeyResolver` 自定义实现已测试

### 运行时观察

- [ ] **fail-closed 监控**：配置 Prometheus 告警 `tenant_failclosed_total > 5 in 5m`，及时发现上下文传播丢失
- [ ] **缓存命中率**：观测 `tenant_sql_cache_total`，若命中率 < 50% 考虑增加 `max-size` 或关停缓存
- [ ] **异步线程泄漏**：观察 `tenant_active` Gauge 是否持续增长（指示上下文未及时清理）

### 安全加固

- [ ] **跨租户共享审计**：`tenant-sharing` 配置项需定期审计，清理不再需要的共享映射
- [ ] **search_path 安全检查**：SCHEMA 模式下 `search_path` 赋值经过正则校验 `^[a-zA-Z_][a-zA-Z0-9_]*$`，防止注入
- [ ] **Header 透传清洗**：确保网关层清洗外部请求的 `X-Tenant-*` Header，防止租户上下文伪造

## 变更记录

- **1.4.0**（2026-09-19）：
  - 异步上下文传播改用 TTL 自动传播，移除手动 snapshot/restore 冗余逻辑
  - SCHEMA 模式新增 `schema-mapping`（自定义 schema 名称映射）与 `schema-search-path-enabled`（search_path 自动设置）
  - SQL 改写缓存 Key 改用 SHA-256 哈希摘要，减少 Key 内存占用（长 SQL 场景减少 50%+）
  - fail-closed 异常新增诊断信息 `[reason, thread, uri, hasTenantContext, tenantId, hasRequestSnapshot]`
  - 新增 `SchemaSearchPathExecutor`、`DiagnosticsUtil` 类
- **1.3.0**（2026-09-07）：新增 `TenantIndexValidator` 索引校验工具；修复 WITH CTE SQL 改写与 WHERE/HAVING/selectItems 标量子查询注入。
- **1.2.0**（2026-09-04）：SCHEMA 模式数据源路由新增 `DatasourceKeyResolver`；Feign 拦截器 Header 规范统一。
- **1.0.0**（2026-08-02）：初始版本（MULTI 行级隔离 + WebFilter + MP InnerInterceptor）。

## FAQ

**Q: 异步线程中获取租户 ID 为 null，但父线程有上下文？**
A: 检查 `TaskDecorator` 是否已注册到线程池。推荐使用 ydsz-common-thread 的 `TtlExecutors.getTtlExecutor()` 或手动配置 `executor.setTaskDecorator(tenantContextTaskDecorator)`。新版 TTL 会自动 snap 并传播。

**Q: fail-closed 异常消息中 `hasTenantContext=false` 但 `hasRequestSnapshot=true`？**
A: 说明 Web Filter 已执行但 TenantContext 解析失败（如 JWT 中无对应 claim 或 Header 缺失）。检查 `tenant-fields` 配置中的 claim 名、是否配置了 `anon-urls` 跳过。

**Q: SCHEMA 模式下启用 search_path 后，MyBatis-Plus 的 `SELECT last_insert_id()` 找不到表？**
A: 这类查询需指定 schema 前缀(select last_insert_id() from dual)，或额外将 search_path 加 public：`SET search_path TO {schema},public`。
