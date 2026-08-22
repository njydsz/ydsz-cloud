# ydsz-common-tenant

> 多租户运行时隔离模块（L4 基础数据层，数据库无关，可选引入）

提供多租户运行时隔离能力，覆盖 SQL 改写、上下文传播、Redis Key 隔离、限流、指标等场景。基于 MyBatis-Plus `InnerInterceptor` SPI + JSqlParser 实现，业务代码零侵入。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（可选引入） |
| **作用** | 提供多租户运行时隔离能力（识别租户 → 注入隔离条件 → 传播上下文） |
| **数据库无关** | 通过 JSqlParser SQL 改写实现，不绑定具体数据库 |
| **源文件数** | 24 |
| **强依赖** | `ydsz-common-core`（TTL ThreadLocal）、`ydsz-common-jdbc`（InnerInterceptor SPI + MpBaseEntity + JSqlParser）、`ydsz-common-util`（AuthInfoUtils）、`ydsz-common-domain` |
| **可选依赖** | `ydsz-common-redis`、`ydsz-common-feign`、`ydsz-common-thread`、`spring-boot-starter-web`、`spring-boot-health` |
| **启用条件** | `ydsz.tenant.enabled=true`（默认 false，不启用时无任何租户逻辑） |

> **职责边界说明**：1.0.0 起，本模块聚焦"运行时隔离"职责。租户生命周期管理（上下线/状态转换）已剥离至独立运营域模块；字段加密能力下沉至 `common-safe`；PostgreSQL RLS DDL 生成工具迁移至 devops 脚本目录。

## 核心能力

### 1. 统一租户上下文

| 组件 | 说明 |
|---|---|
| `TenantContextHolder` | 基于 `TransmittableThreadLocal` 的上下文持有者，全项目唯一入口（单一路径，无冗余写入） |
| `TenantContext` | 不可变上下文值对象，携带主租户 ID、动态字段（单值/多值）、系统租户、超级管理员、跳过隔离等标记 |
| `SystemTenantContextRunner` | 系统租户上下文执行器，用于定时任务、MQ Consumer、`@Scheduled` 等无用户上下文的场景 |

`TenantContext` 支持四种构造方式：`of(tenantId)` 普通上下文、`system(id)` 系统租户、`skip()` 跳过隔离（登录/注册）、`builder(tenantId)` 多字段组合。

### 2. SQL 隔离拦截器

| 组件 | 说明 |
|---|---|
| `TenantIsolationInterceptor` | 基于 MyBatis-Plus `InnerInterceptor` + JSqlParser 实现，自动在 SELECT/INSERT/UPDATE/DELETE 中注入租户条件 |
| `TenantInterceptorProvider` | SPI 提供者，自动注册到 `MybatisPlusInterceptor` 链（order=400，位于字段填充 300 之后、数据权限 500 之前） |

支持场景：
- SELECT：在 WHERE 注入 `tenant_id = ?`，多表 JOIN 时为每张表注入条件
- INSERT：自动追加租户列与值
- UPDATE / DELETE：在 WHERE 注入租户条件
- 子查询（`ParenthesedSelect`）、集合操作（`SetOperationList`）递归处理
- 多值字段使用 `IN (?, ?, ...)`，单值字段使用 `= ?`
- SQL 改写缓存：默认关闭（可通过 `ydsz.tenant.sql-cache.enabled=true` 开启）

### 3. 多级租户支持

通过 `TenantProperties.TenantMode` 配置四种模式，仅改配置不改代码：

| 模式 | 说明 |
|---|---|
| `SINGLE` | 只取 `tenant-fields` 第一个字段注入 SQL |
| `MULTI` | 取 `tenant-fields` 全部字段注入 SQL（AND 连接），支持集团+公司+部门+项目多维度 |
| `ISOLATE_DB` | 独立数据源模式，每租户使用独立数据库（配合 `TenantDataSourceRouter`） |
| `SCHEMA` | Schema 隔离模式，每租户使用独立 PostgreSQL Schema（`search_path`），SQL 拦截器不注入列条件 |

### 4. 全链路传播

| 组件 | 传播通道 | 触发条件 |
|---|---|---|
| `TenantContextWebFilter` | Web 入口 | 从 JWT claim 与 HTTP header 解析全部字段，设置到 `TenantContextHolder` 与 MDC（order=`HIGHEST_PRECEDENCE + 90`） |
| `TenantContextFeignInterceptor` | Feign 跨服务 | `feign.RequestInterceptor` 在 classpath 时自动装配，将上下文字段透传为 `X-Tenant-*` header |
| `TenantContextTaskDecorator` | `@Async` / 线程池 | `TaskDecorator` 在 classpath 时自动装配，并通过 `BeanPostProcessor` 自动注入到所有 `ThreadPoolTaskExecutor` |
| `TenantContextHolder` | TTL 透传 | 基于 `TransmittableThreadLocal`，线程池场景自动传播 |
| `SystemTenantContextRunner` | 定时任务 / MQ | 无父线程上下文时回退到系统租户 |

异步传播策略：父线程有上下文 → snapshot + restore；父线程无上下文 → 系统租户。

### 5. Redis Key 隔离

| 组件 | 说明 |
|---|---|
| `TenantAwareRedisKey` | 静态工具类，自动添加 `{tenantId}:` 前缀 |
| `CacheIsolationStrategy` | 缓存隔离策略枚举：`KEY_PREFIX`（默认）/ `NONE` |
| `TenantRedisKeyPrefixer` | common-redis 提供的序列化层前缀器，自动装配 |

无租户上下文、跳过隔离、或超级管理员时不加前缀。

### 6. per-table 列名覆盖

支持两种方式自定义表对应的租户列名（覆盖全局默认 `tenant_id`）：

- `@TenantColumn("org_id")` 注解标注在 DO 类上
- `ydsz.tenant.table-column-mapping` 配置映射，key=表名（小写），value=列名

### 7. 租户限流

`TenantRateLimiter` 包装 `common-redis` 的 `RedisRateLimiter`，自动在限流 Key 前添加 `tenant:{tenantId}:` 前缀，提供令牌桶 / 固定窗口 / 滑动窗口三种算法。

### 8. 租户指标

`TenantMetrics` 基于 Micrometer 上报以下指标：

| 指标 | 类型 | 说明 |
|---|---|---|
| `tenant.sql.intercept.total` | Counter | SQL 拦截次数（tag: `result=pass/blocked/skipped`） |
| `tenant.failclosed.total` | Counter | fail-closed 拒绝次数 |
| `tenant.context.skip.total` | Counter | 跳过隔离次数 |
| `tenant.superadmin.total` | Counter | 超级管理员绕过次数 |
| `tenant.datasource.switch.total` | Counter | 数据源切换次数（ISOLATE_DB 模式） |
| `tenant.active` | Gauge | 当前活跃租户上下文数 |

### 9. Fail-Closed 保护

`TenantIsolationInterceptor.resolveTenantValues()` 在以下场景拒绝执行 SQL（抛出 `TenantIsolationException`）：

- 无租户上下文（`TenantContextHolder.get()` 返回 null）
- 上下文为空（`TenantContext.isEmpty()`）
- 配置的任一租户字段值缺失

跳过隔离的场景：匿名 URL、超级管理员、`@InterceptorIgnore` 注解标注的 Mapper 方法。

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
    mode: SINGLE
    tenant-column: tenant_id
    ignore-tables:
      - ydsz_tenant
    anon-urls:
      - /auth/login
      - /auth/register
```

### 3. DO 继承 MpBaseEntity

`tenantId` 字段已在 `MpBaseEntity` 基类中统一声明，业务 DO 无需再单独声明。如某表使用非标准列名，通过 `@TenantColumn` 注解或 `table-column-mapping` 配置覆盖。

### 4. 自动装配

`TenantAutoConfiguration` 在 `ydsz.tenant.enabled=true` 时装配以下 Bean（按条件触发）：

| Bean | 条件 | 说明 |
|---|---|---|
| `TenantInterceptorProvider` | 总是 | SPI 注册 SQL 拦截器到 MybatisPlusInterceptor 链 |
| `FilterRegistrationBean<TenantContextWebFilter>` | Web 应用 + `jakarta.servlet.Filter` | order=`HIGHEST_PRECEDENCE + 90` |
| `TenantContextFeignInterceptor` | `feign.RequestInterceptor` 在 classpath | Feign 跨服务透传 |
| `TenantContextTaskDecorator` | `TaskDecorator` 在 classpath | 异步传播装饰器 |
| `tenantTaskDecoratorPostProcessor` | `ThreadPoolTaskExecutor` 在 classpath | 自动注入 TaskDecorator 到所有线程池 |
| `TenantRedisKeyPrefixer` | `RedisSerializer` 在 classpath | Redis Key 租户前缀 |
| `TenantRateLimiter` | `RedisRateLimiter` 在 classpath | 租户级限流门面 |
| `TenantConfigProvider` | 总是 | 租户级配置隔离 |
| `DatasourceKeyResolver` | 总是 | 数据源 Key 解析器（内置实现） |
| `TenantMetrics` | `MeterRegistry` 在 classpath | Micrometer 指标 |
| `TenantHealthIndicator` | `HealthIndicator` 在 classpath | 健康检查端点 |
| `TenantDataSourceRouter` | `mode=ISOLATE_DB` + `DynamicRoutingDataSource` | 数据源路由器 |
| `FilterRegistrationBean<TenantDataSourceFilter>` | `mode=ISOLATE_DB` + Web 应用 | order=`HIGHEST_PRECEDENCE + 100` |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.tenant.enabled` | `false` | 是否启用多租户（不引入依赖或设为 false 时无任何租户逻辑） |
| `ydsz.tenant.mode` | `SINGLE` | 隔离模式：`SINGLE` / `MULTI` / `ISOLATE_DB` / `SCHEMA` |
| `ydsz.tenant.tenant-column` | `tenant_id` | 默认租户列名（`tenant-fields` 为空时使用） |
| `ydsz.tenant.default-claim` | `tenantId` | 默认 JWT claim 名（`tenant-fields` 为空时使用） |
| `ydsz.tenant.default-header` | `X-Tenant-Id` | 默认 HTTP header 名（`tenant-fields` 为空时使用） |
| `ydsz.tenant.super-tenant-id` | `0` | 超级管理员租户 ID（绕过隔离） |
| `ydsz.tenant.system-tenant-id` | `0` | 系统租户 ID（定时任务/异步/MQ） |
| `ydsz.tenant.tenant-fields` | 空 | 租户字段配置列表，每项含 `column` / `claim` / `header` / `multi-value` |
| `ydsz.tenant.table-column-mapping` | 空 | per-table 列名覆盖映射（key=表名小写，value=列名，覆盖第一个字段） |
| `ydsz.tenant.ignore-tables` | 空 | 忽略租户隔离的表列表（忽略大小写） |
| `ydsz.tenant.anon-urls` | 空 | URL 级白名单（跳过租户隔离的请求路径，前缀匹配） |
| `ydsz.tenant.sql-cache.enabled` | `false` | 是否启用 SQL 改写缓存（仅 SINGLE 模式热点 SQL 推荐开启） |
| `ydsz.tenant.datasource.mapping` | 空 | ISOLATE_DB 模式下租户 → 数据源 Key 的映射（未配置时使用命名约定 `tenant_{tenantId}`） |

`tenant-fields` 子项结构：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `column` | 必填 | 数据库列名 |
| `claim` | null | JWT claim 名（不填则不从 JWT 取值） |
| `header` | null | HTTP header 名（不填则不从 header 取值，Feign 跨服务恢复用） |
| `multi-value` | `false` | 是否多值（false 用 `= ?`，true 用 `IN (...)`，多值用逗号分隔） |

## 使用示例

```java
import com.njydsz.common.tenant.SystemTenantContextRunner;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.annotation.TenantColumn;
import com.njydsz.common.tenant.redis.TenantAwareRedisKey;
import com.baomidou.mybatisplus.annotation.TableName;

// 1. 设置租户上下文（通常由 TenantContextWebFilter 自动完成）
TenantContextHolder.set(TenantContext.of("tenant_001"));

// 多字段组合
TenantContext ctx = TenantContext.builder("tenant_001")
        .field("companyId", "comp_001")
        .fieldValues("deptId", List.of("dept_001", "dept_002"))
        .build();
TenantContextHolder.set(ctx);

// 2. DO 继承 MpBaseEntity（tenantId 已声明）
@TableName("ydsz_user")
public class User extends MpBaseEntity<String> {
    private String username;
    // 无需声明 tenantId，基类已包含
}

// 3. 自定义列名
@TenantColumn("org_id")
@TableName("ydsz_file_node")
public class FileNode extends MpBaseEntity<String> {
    private String name;
}

// 4. 系统租户上下文切换（定时任务/异步）
@Scheduled(cron = "0 0 2 * * ?")
public void scanJobs() {
    SystemTenantContextRunner.run(() -> {
        // 此处 TenantContextHolder.getTenantId() = systemTenantId
        jobScanner.scan();
    });
}

// 有返回值
Result result = SystemTenantContextRunner.call(() -> service.query());

// 5. Redis Key 自动加租户前缀
String redisKey = TenantAwareRedisKey.resolve("user:001");
// 结果：tenant_001:user:001

// 6. 异步传播（线程池自动注入 TaskDecorator，无需手动处理）
executor.submit(() -> {
    // 此处 TenantContextHolder.getTenantId() = "tenant_001"
    service.process();
});

// 7. 手动快照与恢复（跨线程自定义场景）
TenantContext snapshot = TenantContextHolder.snapshot();
new Thread(() -> {
    TenantContextHolder.set(snapshot);
    try {
        service.process();
    } finally {
        TenantContextHolder.clear();
    }
}).start();
```

## SPI 扩展点

本模块基于 `common-jdbc` 的 `InnerInterceptorProvider` SPI 接口提供以下扩展点。

### 1. SQL 拦截器 SPI（核心）

| SPI 接口 | 实现类 | 作用 |
|---|---|---|
| `InnerInterceptorProvider`（来自 `common-jdbc`） | `TenantInterceptorProvider` | 通过 SPI 自动注册 `TenantIsolationInterceptor` 到 `MybatisPlusInterceptor` 链，order=400（位于字段填充 300 之后、数据权限 500 之前） |

**扩展方式**：实现 `InnerInterceptorProvider` 接口，提供 `createInterceptor()` 与 `getOrder()` 方法，通过 Spring `@Component` 注册即可被 `common-jdbc` 的 `MybatisPlusConfiguration` 自动发现并按 order 排序注入拦截器链。

### 2. 数据源路由扩展点（ISOLATE_DB 模式）

| 扩展类 | 作用 | 覆盖方式 |
|---|---|---|
| `DatasourceKeyResolver` | 根据 `TenantContextHolder.getTenantId()` 解析数据源 Key | 实现接口并注册 `@Primary` Bean |
| `TenantDataSourceRouter` | 调用 `DatasourceKeyResolver` 切换数据源 | 子类化重写 `resolveDatasourceKey(tenantId)` |

**内置 `DatasourceKeyResolver` 支持**：
- 配置映射：从 `ydsz.tenant.datasource.mapping` 读取租户→数据源映射
- 命名约定回退：未配置时使用 `tenant_{tenantId}` 命名约定

### 3. 配置隔离扩展点

| 扩展类 | 作用 | 覆盖方式 |
|---|---|---|
| `TenantConfigProvider` | per-tenant 配置覆盖（feature flag / 参数 / 阈值），优先级：per-tenant 覆盖 > 全局默认 | 编程式调用 `setOverride(tenantId, key, value)` 或 YAML 配置 `ydsz.tenant.overrides.{tenantId}.{key}` |

### 4. 注解扩展点

| 注解 | 作用 | 扩展方式 |
|---|---|---|
| `@TenantColumn("org_id")` | per-table 覆盖默认租户列名（`tenant_id`） | 标注在 DO 类上 |

### 5. 缓存隔离策略

| 枚举 | 作用 |
|---|---|
| `CacheIsolationStrategy` | 缓存隔离策略选择：`KEY_PREFIX`（默认，`{tenantId}:` 前缀）/ `NONE`（不隔离） |

### 6. 可覆盖的 Bean（`@ConditionalOnMissingBean` / `@ConditionalOnClass` 守卫）

| Bean | 激活条件 | 覆盖方式 |
|---|---|---|
| `TenantInterceptorProvider` | `ydsz.tenant.enabled=true`（总是） | 提供同类型 Bean |
| `TenantContextFeignInterceptor` | `feign.RequestInterceptor` 在 classpath | 提供同类型 Bean |
| `TenantContextTaskDecorator` | `TaskDecorator` 在 classpath | 提供同类型 Bean |
| `TenantRedisKeyPrefixer` | `RedisSerializer` 在 classpath | 提供同类型 Bean |
| `TenantRateLimiter` | `RedisRateLimiter` 在 classpath | 提供同类型 Bean |
| `TenantConfigProvider` | 总是 | 提供同类型 Bean |
| `DatasourceKeyResolver` | 总是 | 提供同类型 Bean |
| `TenantMetrics` | `MeterRegistry` 在 classpath | 提供同类型 Bean |
| `TenantHealthIndicator` | `HealthIndicator` 在 classpath | 提供同类型 Bean |
| `TenantDataSourceRouter` | `mode=ISOLATE_DB` + `DynamicRoutingDataSource` | 提供同类型 Bean |

## 健康检查

访问 `/actuator/health/tenant` 端点，返回以下信息：

```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "mode": "SINGLE",
    "tenantColumn": "tenant_id",
    "superTenantId": "0",
    "systemTenantId": "0",
    "ignoreTables": ["ydsz_tenant"],
    "anonUrls": ["/auth/login"],
    "interceptPassCount": 1024,
    "interceptBlockedCount": 3,
    "interceptSkippedCount": 12,
    "failClosedCount": 3,
    "superAdminBypassCount": 5,
    "activeContexts": 8,
    "isolateDbMode": false,
    "datasourceSwitchCount": 0
  }
}
```

## 注意事项

1. **Fail-Closed 优先**：无法确定租户时拒绝执行 SQL，避免数据泄露。如遇 `TenantIsolationException`，检查 `TenantContextWebFilter` 是否注册、异步任务是否使用 `SystemTenantContextRunner` 包装、相关表是否加入 `ignore-tables`。
2. **超级管理员绕过**：`super-tenant-id` 对应的租户跳过 SQL 隔离，适用于跨租户管理场景。
3. **匿名 URL**：`anon-urls` 配置的路径设置 `skip()` 上下文，跳过所有租户隔离（登录/注册等公开接口）。
4. **ISOLATE_DB 模式**：`DatasourceKeyResolver` 内置实现支持配置映射（`ydsz.tenant.datasource.mapping`）和命名约定（`tenant_{tenantId}`）回退。
5. **多值字段**：`multi-value=true` 时，header/claim 的值用逗号分隔（如 `dept_001,dept_002`），自动解析为 `IN (...)`。
6. **header 安全**：外部请求的 `X-Tenant-*` header 应在网关层清洗，`TenantContextWebFilter` 仅在 JWT 不可用时从 header 恢复（Feign 内部调用场景）。
7. **不启用时的行为**：未引入依赖或 `enabled=false` 时，`MpBaseEntity.tenantId` 字段被忽略，无任何租户逻辑。
8. **拦截器顺序**：`TenantInterceptorProvider` 的 order=400，位于字段填充（300）之后、数据权限（500）之前，可通过 SPI 自动注册。
9. **SQL 缓存**：默认关闭。仅当 SINGLE 模式下热点 SQL 重复度高时建议开启（`ydsz.tenant.sql-cache.enabled=true`），MULTI 模式因缓存 Key 含全字段签名，命中率可能较低。

## 模块结构

```
com.njydsz.common.tenant/
├── TenantContextHolder          # 上下文持有者（类型安全入口）
├── TenantContext                # 上下文值对象（不可变）
├── SystemTenantContextRunner    # 系统租户执行器
├── annotation/
│   ├── TenantColumn             # per-table 列名覆盖注解
│   └── TenantColumnScanner      # 启动时扫描 @TenantColumn 并填充配置
├── async/
│   └── TenantContextTaskDecorator  # 异步传播装饰器
├── cache/
│   └── CacheIsolationStrategy   # 缓存隔离策略枚举
├── config/
│   ├── TenantProperties         # 配置属性
│   ├── TenantConfigProvider     # 租户级配置覆盖
│   ├── TenantAutoConfiguration  # 自动装配
│   └── TenantPropertiesAnnotationPopulator  # BeanPostProcessor：注解扫描回填
├── datasource/
│   ├── DatasourceKeyResolver    # 数据源键解析策略接口（含内置实现）
│   ├── TenantDataSourceRouter   # ISOLATE_DB 数据源路由器
│   └── TenantDataSourceFilter   # ISOLATE_DB Web 过滤器
├── feign/
│   ├── TenantContextFeignInterceptor  # Feign 透传拦截器
│   └── TenantHeaderContract     # Feign/WebFilter header 契约统一
├── health/
│   └── TenantHealthIndicator    # 健康检查
├── interceptor/
│   ├── TenantInterceptorProvider  # SPI 拦截器提供者
│   └── TenantIsolationInterceptor # SQL 改写拦截器
├── metrics/
│   └── TenantMetrics            # Micrometer 指标
├── ratelimit/
│   └── TenantRateLimiter        # 租户限流门面
├── redis/
│   └── TenantAwareRedisKey      # Redis Key 构建器
├── validation/
│   └── TenantIndexValidator     # 启动时租户列索引校验
└── web/
    └── TenantContextWebFilter   # Web 入口过滤器
```

## 架构决策记录（ADR）

### ADR-001：职责剥离（1.0.0）

**背景**：原 common-tenant 模块承载了运行时隔离、租户运营管理、字段加密、审计门面、RLS DDL 生成等多维度职责，模块体量过大（35 个类），与"L4 基础数据层"定位不符。

**决策**：
- 聚焦核心职责：识别租户 → 注入隔离条件 → 传播上下文
- 删除：`lifecycle/`（5 类）、`encryption/`（2 类）、`audit/`（1 类）、`provisioning/`（1 类）、`rls/`（1 类）
- 简化：数据源解析器从 3 层 SPI 合并为 1 个接口 + 内置实现

**收益**：
- 模块从 35 个类精简至 24 个类（含 1.0.0 新增的 TenantColumnScanner / TenantPropertiesAnnotationPopulator / TenantIndexValidator / DatasourceKeyResolver），职责边界清晰
- 消除对 Redis 的强依赖（lifecycle 模块独立后按需引入）
- `TenantContextWebFilter` 移除生命周期检查，启动速度提升
- 双路径上下文收敛为单一路径，降低心智模型复杂度

## 变更记录

- **1.0.0**（2026-08-18）：完善模块定位（新增源文件数 24）；补充 `TenantHeaderContract`（Feign/WebFilter header 契约统一）和 `TenantPropertiesAnnotationPopulator`（BeanPostProcessor：注解扫描回填）文档；补全 `validation/` 包（`TenantIndexValidator` 启动时租户列索引校验）说明。
- **1.0.0**（2026-08-15）：架构瘦身与职责剥离。
  - **删除**：生命周期管理（5 类）、字段加密（2 类）、审计日志门面（1 类）、Provisioning 钩子（1 类）、PostgresRLSAdvisor（1 类）
  - **简化**：数据源解析器 NamingConventionResolver + ConfigurationResolver 合并为单一接口（内置实现）
  - **收敛**：TenantContextHolder 双路径写入收敛为单一路径
  - **配置**：SQL 缓存改为可配置开关（默认关闭）
  - **新增**：sql-cache.enabled 配置项
  - **测试性**：TenantContextWebFilter 提取 protected resolveClaimValue 方法支持单元测试覆盖
  - **文档**：更新 README 反映职责边界与模块结构变化
- **1.0.0**（2026-08-14）：安全加固与架构优化。新增 SCHEMA 模式、`@TenantColumn` 启动扫描、Feign/WebFilter header 契约统一、SQL IN 表达式树防注入、Caffeine 缓存替代 ConcurrentHashMap、双路径上下文统一为 `TenantContextHolder`、`TenantIndexValidator` 启动校验。
- **1.0.0**（2026-08-02）：初始版本。提供统一租户上下文、SQL 隔离拦截器、多级租户支持（SINGLE/MULTI/ISOLATE_DB）、全链路传播、Redis Key 隔离、per-table 列名覆盖、租户限流、Micrometer 指标、Fail-Closed 保护、健康检查。
