# ydsz-common-auth

> JWT 认证与 RBAC 权限（L5 业务服务层）— JWT / Token / RBAC / 数据权限 / OIDC / Redis 失效监听

提供 JWT Token 签发 / 校验 / 黑名单、RBAC 4 注解 + 3 切面（API 权限 / 行级 / 列级 / 菜单权限）、`@DataScope` 数据权限 SQL 注入（fail-closed）、Redis Keyspace Notification 缓存失效、权限缓存热更新、OIDC 端点、内部请求签名 / 校验、TOTP 认证器、布隆过滤器加速权限评估等企业级能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供认证、权限、数据权限、缓存热更新等企业级安全能力 |
| **依赖** | ydsz-common-core、ydsz-common-domain、ydsz-common-util、ydsz-common-exception、ydsz-common-safe、ydsz-common-cache、ydsz-common-json；可选 ydsz-common-lock、ydsz-common-redis、jjwt-api/jjwt-impl/jjwt-jackson、spring-boot-actuator、spring-boot-health、micrometer-core、guava、spring-security-crypto、transmittable-thread-local |
| **版本** | 2.2.0 |

## 核心能力

### 1. JWT / Token 服务

| 类 | 说明 |
|---|---|
| `TokenService` **SPI** | Token 生成 / 校验 / 刷新接口（默认 JWT） |
| `JwtTokenService` | JWT HmacSHA256 实现（支持 accessToken / refreshToken 双 Token） |
| `TokenProperties` | Token 配置属性（`ydsz.jwt.signing-key` / `ydsz.jwt.access-token-ttl` 等） |
| `KeyspaceNotificationProperties` | Redis Keyspace 通知配置（启用后监听 `__keyevent@*__:expired` 事件触发密码 / 权限重置） |
| `PermissionKeyspaceNotificationListener` | Keyspace 通知监听器（消费过期事件，联动权限缓存失效） |

**Token 标准载荷（Claims）**：

```json
{
  "sub": "user_123",
  "username": "zhangsan",
  "tenantId": "tenant_001",
  "roles": ["admin", "manager"],
  "permissions": ["user:read", "order:*"],
  "dataScope": "DEPT_AND_CHILD",
  "iat": 1717200000,
  "exp": 1717203600,
  "type": "access"
}
```

### 2. RBAC 权限注解与切面

| 注解 | 说明 |
|---|---|
| `@AuthApiPermission` | API 接口权限（需具备指定 permission 才可访问） |
| `@AuthRowPermission` | 行级数据权限（SQL 行过滤） |
| `@AuthColPermission` | 列级数据权限（返回字段过滤） |
| `@AuthMenuPermission` | 菜单权限（控制菜单 / 按钮显隐） |
| `@PermissionMode` | 权限匹配模式（AND / OR） |
| `@EnableYdszAuth` | 启用 ydsz-auth 注解扫描（注册 3 个切面） |

| 切面 | 说明 |
|---|---|
| `AuthPermissionAspect` | 评估 `@AuthApiPermission` + `@AuthMenuPermission` 注解 |
| `AuthRowPermissionAspect` | 评估 `@AuthRowPermission`（注入行级 SQL） |
| `AuthColPermissionAspect` | 评估 `@AuthColPermission`（移除无权限列） |

### 3. 数据权限（DataScope）

| 类 / 注解 | 说明 |
|---|---|
| `@DataScope` | 数据权限注解（标注在 Service 方法上指定数据范围规则） |
| `DataScopeHelper` | 数据权限辅助工具 |
| `DataScope*` 类 | 数据权限上下文 DTO |
| `DataPermissionContext` | 数据权限上下文（行级 / 列级权限聚合） |

**数据范围等级**（fail-closed：未知返回 `AND 1 = 0`）：

| 等级 | 说明 |
|---|---|
| ALL | 全部数据（管理员） |
| DEPT_AND_CHILD | 本部门及下属部门 |
 | DEPT | 仅本部门 |
| SELF | 仅本人 |
| CUSTOM | 自定义（`DataPermissionCustomSqlProvider` SPI 实现） |
| NONE | 无权限（`AND 1 = 0`，默认 fail-closed 行为） |

### 4. 认证 Filter

| 类 | 说明 |
|---|---|
| `BaseAuthFilter` | 认证过滤器入口（从 Header 解析 Token → 验证 → 写入 AuthContext） |
| `AbstractAuthHandler` / `AuthHandler` **SPI** | 认证信息解析器（Web 默认 JWT / App 可自定义） |
| `ParsedAuthHeaders` | 解析后的认证信息 DTO |
| `AuthContextUtils` / `AuthInfoUtils` | 认证上下文工具（读取当前登录用户） |
| `RbacUserInfoService` | RBAC 用户信息加载 |
| `AuthCurrentUserIdResolver` | 当前用户 ID 解析器 |

### 5. 权限缓存与失效

| 类 | 说明 |
|---|---|
| `PermissionHierarchyService` | 权限层级服务（计算权限继承树） |
| `PermissionChangeNotifier` / `PermissionChangedEvent` |权限变更通知器 / 事件（发布 Spring ApplicationEvent） |
| `PermissionCacheInvalidationListener` | 权限缓存失效监听器（消费 `PermissionChangedEvent` 清理 Redis / 本地缓存） |
| `PermissionChangeCacheInvalidator` | 权限缓存失效器 |
| `PermissionChangeListener` **SPI** | 权限变更回调接口（业务方实现联动逻辑） |
| `CacheKeyStrategy` / `DefaultCacheKeyStrategy` | 权限缓存 Key 策略 |
| `BloomFilter`（util） | 布隆过滤器加速权限评估（避免每次都读 Redis） |

### 6. 列级权限

| 类 | 说明 |
|---|---|
| `ColumnPermissionResolver` **SPI** | 列级权限解析器 |
| `ColumnPermissionFilter`（util） | 列权限过滤工具 |
| `ColumnDesensitizationService` / ColumnDesensitization* | 列级脱敏 |
| `ColumnPermission*` 模型 | 列权限 DTO |

### 7. OIDC 端点

| 类 | 说明 |
|---|---|
| `JwksEndpoint` | JWKS 端点（`/.well-known/jwks`，暴露公钥） |
| `OidcDiscoveryEndpoint` | OIDC Discovery 端点（`/.well-known/openid-configuration`） |

### 8. 内部请求签名

| 类 | 说明 |
|---|---|
| `CsrfTokenValidator` | CSRF Token 校验器 |
| `InternalHeaderSigner` | 内部请求签名 / 校验（HMAC-SHA256，防伪造内部调用） |
| `InternalSignatureHeaderConstants` | 内部签名 Header 常量 |

### 9. 双因素认证（TOTP）

| 类 | 说明 |
|---|---|
| `TotpAuthenticator`（util） | TOTP 认证器（时间同步一次性密码，兼容 Google Authenticator） |

### 10. 可观测性

| 类 | 说明 |
|---|---|
| `AuthMetrics` / `AuthMetricsCollector` | 认证指标（token 签发 / 校验失败次数） |
| `PermissionMetrics` | 权限评估指标（命中率 / 缓存命中） |
| `AuthHealthIndicator` | 认证健康检查 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-auth</artifactId>
</dependency>
```

### 2. 启用注解

```java
@SpringBootApplication
@EnableYdszAuth              // 启用切面支持
public class SystemApplication { }
```

### 3. 配置属性

```yaml
ydsz:
  jwt:
    signing-key: ${JWT_SIGNING_KEY:JzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ}
    access-token-ttl: 7200           # 2 小时
    refresh-token-ttl: 604800        # 7 天
    issuer: ydsz-auth
    blacklist-enabled: true          # 启用黑名单（登出 / 密码重置后 token 失效）
  auth:
    enabled: true
    token-header: Authorization
    token-prefix: "Bearer "
    ignore-paths:
      - /actuator/**
      - /api/public/**
    data-scope:
      fail-closed: true              # 未知 dataScope 拒绝访问
      cache-ttl: 300
  auth-filter:
    ignore:
      enabled: true
      paths: /public/**,/login
```

### 4. API 权限注解使用

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

   @AuthApiPermission("order:read")
   @GetMapping("/{id}")
   public OrderVO getOrder(@PathVariable Long id) { ... }

   @AuthApiPermission(value = {"order:write", "order:approve"}, mode = PermissionMode.OR)
   @PostMapping
   public void createOrder(@RequestBody @Valid CreateOrderRequest request) { ... }
}
```

### 5. 数据权限注解使用

```java
@Service
public class OrderService {

    @DataScope(rule = DataScopeRule.DEPT_AND_CHILD)  // 仅本部门及下属部门订单
    public IPage<Order> listOrders(PageQuery query) {
        return orderMapper.selectPage(query);
    }

    @DataScope(rule = DataScopeRule.SELF)           // 仅本人创建的订单
    public List<Order> myOrders(Long userId) {
        return orderMapper.selectByCreator(userId);
    }
}
```

## 配置项

### Token 配置（`ydsz.jwt.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jwt.signing-key` | - | JWT 签名密钥（Base64 编码，建议 256bit+） |
| `ydsz.jwt.access-token-ttl` | 7200 | Access Token TTL（秒） |
| `ydsz.jwt.refresh-token-ttl` | 604800 | Refresh Token TTL（秒） |
| `ydsz.jwt.issuer` | ydsz-auth | Token 签发者 |
| `ydsz.jwt.blacklist-enabled` | true | 黑名单开关 |

### 认证配置（`ydsz.auth.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.auth.enabled` | true | 是否启用认证 |
| `ydsz.auth.token-header` | Authorization | Token Header 名称 |
| `ydsz.auth.token-prefix` | "Bearer " | Token 前缀 |
| `ydsz.auth.ignore-paths` | - | 认证放行路径（Ant 模式） |
| `ydsz.auth.data-scope.fail-closed` | true | 未知 scope 拒绝 / 放行 |
| `ydsz.auth.data-scope.cache-ttl` | 300 | 数据权限缓存 TTL（秒） |

### Filter 配置（`ydsz.auth-filter.ignore.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.auth-filter.ignore.enabled` | true | 白名单启用 |
| `ydsz.auth-filter.ignore.paths` | - | 白名单列表（Ant 模式） |

### Keyspace 配置（`ydsz.jwt.keyspace.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.jwt.keyspace.enabled` | false | 启用 Keyspace Notification |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `AuthHandler` **SPI** | 认证信息解析（Web / App 分离） | `@Component` |
| `DataPermissionResolver` **SPI** | 数据权限解析（定制规则） | `@ConditionalOnMissingBean` |
| `DataPermissionCustomSqlProvider` **SPI** | 数据权限动态 SQL 注入 | `@Component` + `getOrder()` |
| `RolePermissionLoader` **SPI** | 角色权限加载（Redis / DB / 远程） | `@Bean` |
| `ColumnPermissionResolver` **SPI** | 列级权限解析 | `@Component` |
| `RbacUserInfoService` **SPI** | RBAC 用户信息加载 | `@Component` |
| `CacheKeyStrategy` | 权限缓存 Key 生成 | `@Component` |
| `PermissionChangeListener` **SPI** | 权限变更回调 | `List<PermissionChangeListener>` 自动收集 |
| `TokenService` **SPI** | Token 生成 / 校验 / 刷新 | `@ConditionalOnMissingBean` |
| `AuthMetrics` / `PermissionMetrics` | 认证 / 权限指标采集 | `@Component` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/auth` | 认证健康检查 | `ydsz.auth.enabled=true` + Health 存在 |

`AuthHealthIndicator` 暴露信息：
- `token_service` — Token 服务状态（UP / DOWN）
- `cache_hit_rate` — 权限缓存命中率
- `keyspace_notification` — Keyspace 监听状态（UP / DOWN）

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `AuthConfiguration` | `ydsz.auth.enabled=true` |
| `AuthFilterConfiguration` | Auth + Filter 依赖存在 |

## 注意事项

1. **fail-closed 数据权限**：未配置 `@DataScope` 的 Service 方法默认 fail-closed（`AND 1 = 0`，无权限）。业务方法务必显式声明 DataScope 规则。
2. **InternalHeaderSigner**：内部微服务间调用必须开启签名校验，避免伪造内部请求。
3. **@EnableYdszAuth** 必须在启动类显式声明，否则 3 个切面不生效。
4. **JWT 密钥安全**：生产环境务必使用强随机密钥（256bit+），建议使用 KMS 管理。

## 变更记录

- **2.2.0**（2026-09-04）：新增 OIDC 端点（JwksEndpoint / OidcDiscoveryEndpoint）；新增布隆过滤器加速权限评估；新增 PermissionKeyspaceNotificationListener。
- **2.1.0**（2026-09-01）：RBAC 4 注解 + 3 切面（API 权限 / 行级 / 列级 / 菜单权限）；列级权限新增 `ColumnDesensitizationService`；修复 ORDER BY 与 DataScope 冲突。
- **2.0.0**（2026-08-02）：初始版本（JWT / Token / RBAC）。
