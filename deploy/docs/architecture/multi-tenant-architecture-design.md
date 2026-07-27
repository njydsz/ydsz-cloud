# PMIS 多租户架构设计方案（独立可选公共模块）

> **版本**: v3.0
> **日期**: 2026-07-27
> **核心思路**: 将多租户能力封装为独立公共模块 `ydsz-common-tenant`，子应用按需引入
> **约束**: 数据库无关（同时支持 PostgreSQL / MySQL / Oracle / 达梦等）
> **对标**: Salesforce 多租户引擎、阿里云 SaaS 引擎、AWS SaaS Factory

---

## 一、设计理念

### 1.1 核心原则

```
                    ┌─────────────────────────────┐
                    │   ydsz-common-tenant (可选)   │
                    │                             │
                    │  引入依赖 + 配置 = 启用多租户  │
                    │  不引入依赖        = 无多租户  │
                    │                             │
                    │  L1: 统一租户上下文           │
                    │  L2: SQL 隔离拦截器           │
                    │  L3: 数据源路由 (ISOLATE_DB)  │
                    │  L4: Redis Key 隔离          │
                    │  L5: 全链路传播               │
                    └──────────┬──────────────────┘
                               │ (SPI 插入)
                    ┌──────────▼──────────────────┐
                    │   ydsz-common-jdbc (基础)    │
                    │                             │
                    │  InnerInterceptorProvider   │
                    │  MpBaseEntity (租户字段)      │
                    │  MybatisPlusConfiguration    │
                    └─────────────────────────────┘
```

| 原则 | 说明 |
|---|---|
| **模块独立** | 多租户所有代码在 `ydsz-common-tenant` 中，`common-jdbc` 零租户代码 |
| **按需引入** | 子应用 POM 加依赖 + YAML 配置 → 启用；不加 → 完全无租户逻辑 |
| **SPI 插入** | 通过 `InnerInterceptorProvider` SPI 接口，`common-jdbc` 自动发现并加载租户拦截器 |
| **数据库无关** | 不依赖 RLS 等特定数据库能力，纯 JSqlParser SQL 改写 |
| **Fail-Closed** | 启用多租户后，无法确定租户时拒绝执行 SQL |

### 1.2 依赖关系

```
common-tenant → depends on → common-core (TTL ThreadLocal)
common-tenant → depends on → common-jdbc (MP base, InnerInterceptor SPI)
common-tenant → optional  → common-redis (Redis Key 隔离)
common-tenant → optional  → common-feign (跨服务透传)
common-tenant → optional  → common-thread (TaskDecorator)

common-jdbc → does NOT depend on → common-tenant (反向不依赖)
common-domain → has TenantAware/TenantId (领域契约，不依赖 common-tenant)
```

---

## 二、现有代码迁移清单

### 2.1 从 `common-jdbc` 迁移到 `common-tenant` 的文件

| 文件 | 原路径 | 迁移目标 | 说明 |
|---|---|---|---|
| `TenantIsolationInterceptor.java` | `jdbc/interceptor/` | `tenant/interceptor/` | SQL 改写拦截器，改为从 `TenantContextHolder` 取值 |
| `TenantIsolationProperties.java` | `jdbc/config/` | `tenant/config/` | 重命名为 `TenantProperties`，配置前缀 `ydsz.tenant` |
| `TenantIsolationException.java` | `jdbc/exception/` | `tenant/exception/` | 租户隔离异常 |
| `TenantDataSourceRouter.java` | `jdbc/interceptor/` | `tenant/datasource/` | ISOLATE_DB 数据源路由 |

### 2.2 `common-jdbc` 中需清理的租户代码

| 文件 | 改动 |
|---|---|
| `RowPermissionInnerInterceptor.java` | 删除 `shouldApplyTenantIsolation()` + `buildRowScope` 中 TENANT 分支 |
| `DataPermissionConfiguration.java` | 删除 `tenantColumn` 字段 |
| `MybatisPlusConfiguration.java` | 删除 `TenantIsolationInterceptor` 注册代码 + 改为 SPI 收集 |
| `DataPermissionContext.java` | 删除 `tenantIsolationEnabled` / `tenantId` 字段 |

### 2.3 `common-domain` 中保留的领域契约

| 文件 | 保留理由 |
|---|---|
| `TenantAware.java` | 领域标记接口，修正返回类型为 `String` |
| `@TenantId` 注解 | 领域层注解，标识实体租户字段 |
| `EntityCapabilities` | 实体能力检测，保留 `isTenantIdEnabled()` |

### 2.4 `MpBaseEntity` 新增租户字段

```java
// common-jdbc entity/MpBaseEntity.java — 新增
@TableField("tenant_id")
@YdszJsonField(ignore = true)
private String tenantId;
```

> **说明**：`tenantId` 放在 `MpBaseEntity` 中而非 `common-tenant`，因为 DDL 中所有表已有 `tenant_id` 列。不引入 `common-tenant` 时，该字段存在但被忽略（DDL 默认值 '1'）；引入后，拦截器自动注入条件。

---

## 三、SPI 插入机制设计

### 3.1 核心：`InnerInterceptorProvider` 接口

在 `common-jdbc` 中定义 SPI 接口，允许公共模块按需注册 MyBatis-Plus 拦截器：

```java
package com.njydsz.common.jdbc.spi;

/**
 * MyBatis-Plus InnerInterceptor 提供者（SPI）。
 *
 * <p>公共模块通过实现此接口，将自定义拦截器注册到 MybatisPlusInterceptor 链中。
 * <p>使用 Spring {@link ObjectProvider} 自动发现，无需 common-jdbc 硬依赖。
 *
 * <p>使用示例（common-tenant 模块）：
 * <pre>
 * &#64;Component
 * public class TenantInterceptorProvider implements InnerInterceptorProvider {
 *     &#64;Override
 *     public InnerInterceptor createInterceptor() {
 *         return new TenantIsolationInterceptor(tenantProperties);
 *     }
 *
 *     &#64;Override
 *     public int getOrder() { return 400; }  // 在字段填充之后，数据权限之前
 * }
 * </pre>
 */
public interface InnerInterceptorProvider {

    /**
     * 创建拦截器实例。
     *
     * @return MyBatis-Plus InnerInterceptor 实例
     */
    InnerInterceptor createInterceptor();

    /**
     * 拦截器在链中的顺序（值越小越靠前）。
     *
     * <p>参考顺序：
     * <ul>
     *   <li>100: OptimisticLock</li>
     *   <li>200: LogicalDelete</li>
     *   <li>300: FieldFill</li>
     *   <li>400: TenantIsolation（common-tenant 提供）</li>
     *   <li>500: DataPermission</li>
     *   <li>600: Pagination</li>
     * </ul>
     */
    int getOrder();
}
```

### 3.2 `MybatisPlusConfiguration` 改造

```java
// common-jdbc config/MybatisPlusConfiguration.java

@Slf4j
@AutoConfiguration
public class MybatisPlusConfiguration {

    // 现有配置注入...
    private final ObjectProvider<List<InnerInterceptorProvider>> interceptorProviders;

    // 构造器注入...

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁
        // 2. 逻辑删除
        // 3. 字段填充
        // ... 现有拦截器不变 ...

        // === SPI 收集外部拦截器（common-tenant 等） ===
        List<InnerInterceptorProvider> providers = interceptorProviders
            .getIfAvailable(Collections::emptyList);
        if (!providers.isEmpty()) {
            // 按 getOrder() 排序后注入
            providers.stream()
                .sorted(Comparator.comparingInt(InnerInterceptorProvider::getOrder))
                .forEach(provider -> {
                    interceptor.addInnerInterceptor(provider.createInterceptor());
                    log.info("MyBatis Plus: SPI interceptor loaded [{}] order={}",
                        provider.createInterceptor().getClass().getSimpleName(),
                        provider.getOrder());
                });
        }

        // 4. 数据权限（在 SPI 之后，确保 tenant 先注入）
        configureDataPermissionInterceptor(interceptor);

        // 5. 分页
        // ...

        return interceptor;
    }
}
```

### 3.3 拦截器链顺序

```
执行顺序（值越小越先执行）:
  100  OptimisticLockInterceptor        ← common-jdbc 内置
  200  LogicalDeleteInterceptor          ← common-jdbc 内置
  300  CombinedFieldFillInterceptor     ← common-jdbc 内置
  400  TenantIsolationInterceptor       ← common-tenant SPI 注入（可选）
  500  RowPermissionInnerInterceptor    ← common-jdbc 内置（已删除 tenant 逻辑）
  510  ColPermissionInnerInterceptor    ← common-jdbc 内置
  600  PaginationInnerInterceptor       ← common-jdbc 内置
```

---

## 四、`ydsz-common-tenant` 模块设计

### 4.1 目录结构

```
ydsz-common-tenant/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/njydsz/common/tenant/
    │   ├── TenantContext.java                    # 租户上下文值对象
    │   ├── TenantContextHolder.java             # TTL 统一持有者
    │   ├── TenantDimension.java                  # 维度枚举
    │   ├── annotation/
    │   │   └── TenantColumn.java                 # per-table 列名覆盖注解
    │   ├── config/
    │   │   ├── TenantProperties.java             # 配置属性 (ydsz.tenant.*)
    │   │   └── TenantAutoConfiguration.java      # 自动装配
    │   ├── datasource/
    │   │   ├── TenantDataSourceFilter.java       # ISOLATE_DB Web Filter
    │   │   └── TenantDataSourceAutoConfiguration.java
    │   ├── exception/
    │   │   └── TenantIsolationException.java     # 租户隔离异常
    │   ├── feign/
    │   │   └── TenantContextFeignInterceptor.java # Feign 跨服务透传
    │   ├── interceptor/
    │   │   ├── TenantIsolationInterceptor.java   # SQL 改写拦截器
    │   │   └── TenantInterceptorProvider.java    # SPI Provider 实现
    │   ├── redis/
    │   │   └── TenantAwareRedisKey.java          # Redis Key 隔离
    │   ├── spi/
    │   │   ├── TenantIgnoreTableRegistry.java    # ignore-tables SPI 注册
    │   │   └── SystemTenantContextRunner.java    # 系统租户上下文执行器
    │   └── web/
    │       └── TenantContextWebFilter.java       # 请求入口 Filter
    └── resources/META-INF/
        ├── spring/
        │   ├── AutoConfiguration.imports          # 自动装配注册
        │   └── tenant-ignore-tables-default.properties  # 默认忽略表
        └── additional-spring-configuration-metadata.json
```

### 4.2 POM 依赖

```xml
<dependencies>
    <!-- common-core: TTL ThreadLocal -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-core</artifactId>
    </dependency>
    <!-- common-jdbc: InnerInterceptor SPI + MpBaseEntity + JSqlParser -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-jdbc</artifactId>
    </dependency>
    <!-- common-util: AuthInfoUtils -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-util</artifactId>
    </dependency>

    <!-- 可选依赖：Redis Key 隔离 -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-redis</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- 可选依赖：Feign 跨服务透传 -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-feign</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- 可选依赖：TaskDecorator 异步传播 -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-thread</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 4.3 核心组件设计

#### 4.3.1 TenantContext + TenantContextHolder

```java
package com.njydsz.common.tenant;

public final class TenantContext {
    private final String tenantId;
    private final Map<TenantDimension, String> dimensions;
    private final boolean systemTenant;
    private final boolean superAdmin;
    private final boolean skipIsolation;

    public static TenantContext of(String tenantId) { ... }
    public static TenantContext system(String systemTenantId) { ... }
    public static TenantContext skip() { ... }
    public static TenantContext empty() { ... }
    public String getDimension(TenantDimension dim) { ... }
}

public enum TenantDimension {
    TENANT, GROUP, COMPANY
}

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new TransmittableThreadLocal<>();

    public static void set(TenantContext context) { ... }
    public static TenantContext get() { ... }
    public static String getTenantId() { ... }
    public static boolean isSystem() { ... }
    public static boolean isSuperAdmin() { ... }
    public static boolean isSkipped() { ... }
    public static TenantContext snapshot() { ... }
    public static void restore(TenantContext snapshot) { ... }
    public static void clear() { ... }
}
```

#### 4.3.2 TenantInterceptorProvider（SPI 实现）

```java
package com.njydsz.common.tenant.interceptor;

import com.njydsz.common.jdbc.spi.InnerInterceptorProvider;
import com.njydsz.common.tenant.config.TenantProperties;

/**
 * 租户隔离拦截器 SPI 提供者。
 *
 * <p>当 common-tenant 在 classpath 且 ydsz.tenant.enabled=true 时，
 * 自动注册到 MybatisPlusInterceptor 链。
 */
public class TenantInterceptorProvider implements InnerInterceptorProvider {

    private final TenantProperties properties;

    public TenantInterceptorProvider(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public InnerInterceptor createInterceptor() {
        return new TenantIsolationInterceptor(properties);
    }

    @Override
    public int getOrder() {
        return 400;  // 字段填充之后，数据权限之前
    }
}
```

#### 4.3.3 TenantAutoConfiguration

```java
package com.njydsz.common.tenant.config;

/**
 * 多租户自动装配。
 *
 * <p>条件：ydsz.tenant.enabled=true（默认 false，不启用）
 *
 * <p>装配内容：
 * <ul>
 *   <li>TenantProperties — 配置属性</li>
 *   <li>TenantInterceptorProvider — SPI 注册 SQL 拦截器</li>
 *   <li>TenantContextWebFilter — Web 入口上下文设置</li>
 *   <li>TenantContextFeignInterceptor — Feign 跨服务透传（common-feign 在 classpath 时）</li>
 *   <li>TenantContextTaskDecorator — 异步传播（common-thread 在 classpath 时）</li>
 *   <li>TenantAwareRedisKey — Redis Key 隔离（common-redis 在 classpath 时）</li>
 *   <li>TenantDataSourceFilter — ISOLATE_DB 模式（mode=ISOLATE_DB 时）</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.tenant", name = "enabled", matchIfMissing = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    // === L3: SQL 拦截器 SPI ===
    @Bean
    @ConditionalOnMissingBean
    public TenantInterceptorProvider tenantInterceptorProvider(TenantProperties props) {
        return new TenantInterceptorProvider(props);
    }

    // === L1: Web 入口 ===
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnWebApplication
    public TenantContextWebFilter tenantContextWebFilter(TenantProperties props) {
        return new TenantContextWebFilter(props);
    }

    // === L1: Feign 跨服务（可选） ===
    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnMissingBean
    public TenantContextFeignInterceptor tenantContextFeignInterceptor() {
        return new TenantContextFeignInterceptor();
    }

    // === L1: 异步传播（可选） ===
    @Bean
    @ConditionalOnClass(name = "org.springframework.core.task.TaskDecorator")
    @ConditionalOnMissingBean
    public TenantContextTaskDecorator tenantContextTaskDecorator(TenantProperties props) {
        return new TenantContextTaskDecorator(props);
    }

    // === L3: ISOLATE_DB 数据源路由（可选） ===
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.tenant", name = "mode",
                          havingValue = "ISOLATE_DB")
    @ConditionalOnClass(name = "com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource")
    public TenantDataSourceRouter tenantDataSourceRouter(
            DynamicRoutingDataSource ds, TenantProperties props) {
        return new TenantDataSourceRouter(ds, props);
    }
}
```

### 4.4 配置设计

#### 默认不启用（子应用不引入依赖即无多租户）

```yaml
# 不引入 common-tenant 依赖 → 无任何租户逻辑，MpBaseEntity.tenantId 字段被忽略
```

#### 启用多租户（子应用引入依赖 + 配置）

```yaml
ydsz:
  tenant:
    enabled: true                          # 启用多租户（默认 false）
    mode: SINGLE                           # SINGLE / MULTI / ISOLATE_DB
    tenant-column: tenant_id               # 默认列名
    super-tenant-id: "0"                   # 超级管理员租户 ID
    system-tenant-id: "0"                  # 系统租户 ID（定时任务/异步）
    # MULTI 模式字段列表
    tenant-fields:
      - column: tenant_id
        source: TENANT
      # - column: group_tenant_id
      #   source: GROUP
      # - column: company_tenant_id
      #   source: COMPANY
    # per-table 列名覆盖
    table-column-mapping:
      ydsz_file_node: org_id
    # 全局忽略表
    ignore-tables:
      - ydsz_tenant
      - ydsz_tenant_plan
      - ydsz_tenant_quota
    # 匿名 URL（不注入租户条件）
    anon-urls:
      - /auth/login
      - /auth/register
      - /auth/captcha
    # Redis Key 隔离
    redis:
      key-isolation: true
      prefix-template: "{tenantId}:{key}"
```

### 4.5 子应用接入方式

#### 步骤 1：POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-tenant</artifactId>
</dependency>
```

#### 步骤 2：配置启用

```yaml
ydsz:
  tenant:
    enabled: true
    mode: SINGLE
```

#### 步骤 3：DO 类继承 MpBaseEntity（已继承无需改动）

```java
// tenantId 字段已在 MpBaseEntity 中，DO 无需单独声明
@TableName("ydsz_project")
public class ProjectDO extends MpBaseEntity<String> {
    private String projectName;
    // private String tenantId; ← 删除，已在基类
}
```

#### 步骤 4：定时任务/MQ Consumer 用 SystemTenantContextRunner

```java
@Scheduled(cron = "0 0 2 * * ?")
public void scanJobs() {
    SystemTenantContextRunner.run(() -> jobScanner.scan());
}
```

---

## 五、全链路传播设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                        全链路租户上下文传播                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─ Web 请求 ─────────────────────────────────────────────────────┐ │
│  │  HTTP Request → TenantContextWebFilter                         │ │
│  │  ├─ 从 AuthInfo/JWT 解析 tenantId                              │ │
│  │  ├─ TenantContextHolder.set(context)                          │ │
│  │  ├─ MDC.put("tenantId", tenantId)                            │ │
│  │  └─ finally: TenantContextHolder.clear()                     │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                              ↓                                       │
│  ┌─ SQL 拦截 ─────────────────────────────────────────────────────┐ │
│  │  TenantIsolationInterceptor (SPI order=400)                   │ │
│  │  ├─ TenantContextHolder.getTenantId()                         │ │
│  │  ├─ fail-closed: 无上下文 → throw TenantIsolationException    │ │
│  │  └─ SQL 改写: WHERE tenant_id = ?                             │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                              ↓                                       │
│  ┌─ Feign 跨服务 ────────────────────────────────────────────────┐ │
│  │  TenantContextFeignInterceptor                                │ │
│  │  ├─ RequestTemplate.header("X-Tenant-Id", tenantId)           │ │
│  │  └─ MULTI 模式: 多级维度 header 透传                           │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                              ↓                                       │
│  ┌─ @Async / 线程池 ─────────────────────────────────────────────┐ │
│  │  TenantContextTaskDecorator                                  │ │
│  │  ├─ 父线程有上下文 → snapshot → restore（传播用户租户）        │ │
│  │  └─ 父线程无上下文 → SystemTenantContext（系统租户）           │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                              ↓                                       │
│  ┌─ 定时任务 / MQ Consumer ─────────────────────────────────────┐ │
│  │  SystemTenantContextRunner                                    │ │
│  │  ├─ TenantContextHolder.set(TenantContext.system("0"))       │ │
│  │  ├─ 执行业务逻辑                                               │ │
│  │  └─ finally: TenantContextHolder.clear()                    │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                              ↓                                       │
│  ┌─ Redis 操作 ─────────────────────────────────────────────────┐ │
│  │  TenantAwareRedisKey.resolve(key)                             │ │
│  │  └─ 返回 "{tenantId}:{key}"                                  │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 六、文件变更清单

### 6.1 新增文件

| 文件 | 模块 |
|---|---|
| `ydsz-common-tenant/pom.xml` | common-tenant |
| `ydsz-common-tenant/README.md` | common-tenant |
| `TenantContext.java` | common-tenant |
| `TenantDimension.java` | common-tenant |
| `TenantContextHolder.java` | common-tenant |
| `TenantProperties.java` | common-tenant (迁移自 TenantIsolationProperties) |
| `TenantAutoConfiguration.java` | common-tenant |
| `TenantColumn.java` (注解) | common-tenant |
| `TenantIsolationInterceptor.java` | common-tenant (迁移自 common-jdbc) |
| `TenantInterceptorProvider.java` | common-tenant (SPI 实现) |
| `TenantIsolationException.java` | common-tenant (迁移自 common-jdbc) |
| `TenantContextWebFilter.java` | common-tenant |
| `TenantContextFeignInterceptor.java` | common-tenant |
| `TenantContextTaskDecorator.java` | common-tenant |
| `SystemTenantContextRunner.java` | common-tenant |
| `TenantAwareRedisKey.java` | common-tenant |
| `TenantDataSourceRouter.java` | common-tenant (迁移自 common-jdbc) |
| `TenantDataSourceFilter.java` | common-tenant |
| `TenantDataSourceAutoConfiguration.java` | common-tenant |
| `TenantIgnoreTableRegistry.java` | common-tenant |
| `InnerInterceptorProvider.java` | **common-jdbc** (SPI 接口) |
| `AutoConfiguration.imports` | common-tenant |
| `tenant-ignore-tables-default.properties` | common-tenant |
| `additional-spring-configuration-metadata.json` | common-tenant |

### 6.2 修改文件

| 文件 | 模块 | 改动 |
|---|---|---|
| `MpBaseEntity.java` | common-jdbc | 新增 `tenantId` 字段 |
| `MybatisPlusConfiguration.java` | common-jdbc | 删除 tenant 注册 + 新增 SPI 收集 |
| `RowPermissionInnerInterceptor.java` | common-jdbc | 删除 `shouldApplyTenantIsolation` |
| `DataPermissionConfiguration.java` | common-jdbc | 删除 `tenantColumn` |
| `DataPermissionContext.java` | common-jdbc | 删除 tenant 相关字段 |
| `TenantAware.java` | common-domain | `getTenantId()` 返回 `String` |
| `pom.xml` | common (parent) | `<module>ydsz-common-tenant</module>` |
| 50+ DO 类 | 各业务模块 | 删除 `private String tenantId` |
| 各模块 `bootstrap.yml` | 各业务模块 | 添加 `ydsz.tenant` 配置 |
| 各模块 `pom.xml` | 需多租户的模块 | 添加 common-tenant 依赖 |

### 6.3 删除文件

无文件删除，均为迁移和改造。

---

## 七、实施计划

| 阶段 | 内容 | 工时 |
|---|---|---|
| **P0-A: SPI 基础设施** | `InnerInterceptorProvider` 接口 + `MybatisPlusConfiguration` SPI 收集改造 | 1d |
| **P0-B: 模块创建** | `common-tenant` POM + 目录 + 注册到 parent | 0.5d |
| **P0-C: 代码迁移** | 4 个文件从 common-jdbc 迁移到 common-tenant + common-jdbc 清理 | 1d |
| **P0-D: 实体层收敛** | `MpBaseEntity` 新增 tenantId + 50+ DO 删除重复字段 | 1d |
| **P1: 上下文层** | `TenantContext` / `TenantContextHolder` / `WebFilter` | 1.5d |
| **P1: 全链路传播** | `FeignInterceptor` / `TaskDecorator` / `SystemRunner` | 1.5d |
| **P1: Redis 隔离** | `TenantAwareRedisKey` + 各模块 Key 改造 | 1d |
| **P1: ISOLATE_DB** | `TenantDataSourceFilter` + `AutoConfiguration` | 1d |
| **P2: per-table 列名** | `@TenantColumn` 注解 + 拦截器列名解析 | 0.5d |
| **P2: 配置元数据** | metadata json + AutoConfiguration.imports | 0.5d |
| **P2: 可观测性** | Micrometer tenant tag + MDC | 0.5d |
| **总计** | | **9.5d** |

---

## 八、验收标准

### 8.1 模块独立性

- [ ] `common-jdbc` 中零租户相关代码（grep `tenant` 仅剩 `MpBaseEntity.tenantId` 字段）
- [ ] 不引入 `common-tenant` 依赖的模块，启动无任何租户逻辑
- [ ] 引入 `common-tenant` 依赖 + `ydsz.tenant.enabled=true` → 多租户自动生效
- [ ] 引入 `common-tenant` 依赖 + `ydsz.tenant.enabled=false` → 多租户不生效

### 8.2 功能验收

- [ ] SINGLE 模式：`WHERE tenant_id = ?`
- [ ] MULTI 模式：`WHERE group_tenant_id = ? AND company_tenant_id = ?`
- [ ] ISOLATE_DB 模式：不同租户路由到不同数据源
- [ ] @Async / @Scheduled / MQ Consumer：自动注入系统租户上下文
- [ ] Feign 跨服务：下游从 `X-Tenant-Id` header 恢复上下文
- [ ] Redis Key：多租户下自动添加 `{tenantId}:` 前缀
- [ ] per-table 列名：`@TenantColumn("org_id")` 生效
- [ ] 模式切换：仅改 `ydsz.tenant.mode` 配置值

### 8.3 安全验收

- [ ] 未认证请求执行 SQL → `TenantIsolationException`
- [ ] 租户 A 无法查询租户 B 数据
- [ ] 超级管理员可跨租户查询
- [ ] 定时任务用系统租户，不影响业务隔离

### 8.4 规范验收

- [ ] 零 `private String tenantId` 在业务 DO 中
- [ ] 零 `RequestContext.getTenantId()` 调用（迁移到 `TenantContextHolder`）
- [ ] 零 `AuthInfoUtils.getTenantId()` 在拦截器中
- [ ] 零硬编码 `"0"` 超管判断
- [ ] 零 FQN 违规、零 `@SuppressWarnings`
