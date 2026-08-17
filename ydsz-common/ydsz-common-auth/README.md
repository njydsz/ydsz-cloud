# ydsz-common-auth

> 认证鉴权公共模块（L5 业务服务层）

YDSZ 认证与授权框架 — JWT Token 服务、RBAC 权限模型（菜单/按钮/API/行/列五级权限）、数据权限 `@DataScope`、多租户隔离、Token 黑名单（同步 + 响应式 + 布隆过滤器）、权限层级继承、权限预检、列权限签名、权限缓存热更新、Micrometer 指标、权限国际化、启动预热。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 JWT 认证、RBAC 鉴权、数据权限、列权限、Token 黑名单、权限缓存热更新等能力 |
| **源文件数** | 80+ |
| **依赖** | common-core、common-redis、common-util、common-exception、common-safe、common-cache、common-json |

## 核心能力

### 1. RBAC 权限评估

| 类 | 说明 |
|---|---|
| `RbacPermissionEvaluator` | 核心 API（`loadUserInfo` / `loadCurrentUserInfo` / `validateMenu` / `validateApi` / `hasPermission`），内置角色权限缓存与 Redis 降级 |
| `PermissionUtils` | 权限匹配工具（通配符 `*`/`**` 匹配、正则 LRU 缓存、超管判断、CSV 拆分、多角色权限合并） |
| `PermissionMerger` | 多角色权限合并工具（支持以 `!` 前缀标记拒绝角色，按集合减法移除） |

### 2. 注解体系

| 注解 | 切面 | 说明 |
|---|---|---|
| `@AuthMenuPermission` | `AuthPermissionAspect` | 菜单权限校验（支持 AND/OR 模式） |
| `@AuthApiPermission` | `AuthPermissionAspect` | API 接口权限校验（权限码 + URL 路径模式） |
| `@AuthRowPermission` | `AuthRowPermissionAspect` | 行级数据权限 |
| `@AuthColPermission` | `AuthColPermissionAspect` | 列级字段权限 |
| `@DataScope` | — | 数据范围注解（部门/用户字段过滤，配合 JDBC SQL 拦截器） |
| `@PermissionMode`（新增） | — | 权限校验模式枚举（`AND` 全部满足 / `OR` 任一满足） |
| `@EnableYdszAuth`（新增） | — | 启用认证注解，`@Import` 导入 `AuthConfiguration` + `AuthFilterConfiguration` |

### 3. 切面

| 类 | 说明 |
|---|---|
| `AuthPermissionAspect` | 统一菜单/API 权限校验切面，校验失败抛出 `PermissionDeniedException` |
| `AuthColPermissionAspect` | 列权限切面（可见列过滤 + 列脱敏） |
| `AuthRowPermissionAspect` | 行权限切面（数据范围注入） |

### 4. JWT Token

| 类 | 说明 |
|---|---|
| `TokenService` | Token 服务接口（签发 / 校验 / 刷新 / 吊销） |
| `JwtTokenService` | JWT 实现（基于 jjwt，签发 / 刷新 / 吊销） |
| `TokenProperties` | Token 配置（密钥 / 过期时间 / 刷新策略） |
| `AccessTokenUtils` | AccessToken 解析工具（优先上下文，回退请求头 `X-Access-Token`，含 JWT/Bearer 格式校验） |

### 5. 用户与角色权限

| 类 | 说明 |
|---|---|
| `RbacUserInfoService` / `RedisRbacUserInfoService` | 用户信息查询（Redis Hash 存储） |
| `RolePermissionLoader` / `RedisRolePermissionLoader` | 角色-权限加载器（支持 `loadByRoleCode` / `loadByRoleCodes` 批量 MGET） |
| `RolePermissions` | 角色权限模型（菜单 / 按钮 / API 三类权限集合） |

### 6. 数据权限

| 类 | 说明 |
|---|---|
| `DataPermissionResolver` / `RedisRoleDataPermissionResolver` | 数据权限解析器 |
| `DataPermissionCustomSqlProvider` | 数据权限动态 SQL 提供者（SPI 扩展点） |
| `DataScopeInfo` / `DataScopeAware` | 数据范围信息（租户/公司/部门/项目/区域） |

### 7. 列权限

| 类 | 说明 |
|---|---|
| `ColumnPermissionResolver` / `RedisRoleColumnPermissionResolver` | 列权限解析器 |
| `ColumnPermission` / `ColumnPermissionInfo` / `ColumnScopeInfo` | 列权限模型（可见列 / 可编辑列） |
| `ColumnDesensitizationService` | 列脱敏服务（按角色缓存脱敏规则） |
| `ColumnScopeAware` | 列范围感知接口 |

### 8. 权限层级

| 类 | 说明 |
|---|---|
| `PermissionHierarchyService` | 权限继承层级管理（`registerPermission(tenantId, code, ...)` 注册继承关系，`hasPermission` 自动递归检查父级权限） |

### 9. 权限预检

| 类 | 说明 |
|---|---|
| `PermissionPreCheck` | 预检注解（`PreCheckMode` RETURN/THROW、`CheckType` MENU/BUTTON/API、`CheckMode` ALL/ANY） |
| `PermissionCheckResult` | 预检结果（通过/拒绝、缺失权限、已有权限、消息、建议、错误码、用户角色） |

在执行业务逻辑前用 `PermissionPreCheck` 注解预检权限，返回详细结果而非直接抛异常，适用于前端按钮显隐控制、批量操作前校验、权限变更模拟、微服务间调用前校验等场景。

### 10. 缓存

| 类 | 说明 |
|---|---|
| `RolePermissionCacheService` / `RolePermissionsExpiry` | 角色权限缓存（Redis 加载 + 过期管理） |
| `CacheKeyStrategy` / `DefaultCacheKeyStrategy` | 缓存 Key 生成策略（SPI 扩展点） |

### 11. 权限变更事件

| 类 | 说明 |
|---|---|
| `PermissionChangedEvent` | 权限变更事件 |
| `PermissionChangeNotifier` | 权限变更通知器 |
| `PermissionChangeCacheInvalidator` | 缓存失效处理器 |
| `PermissionChangeListener` | 权限变更监听器接口 |
| `PermissionCacheInvalidationListener` | 缓存失效监听器（Spring 事件 + Redis Pub/Sub） |
| `PermissionKeyspaceNotificationListener` | Redis Keyspace 事件监听器（精确失效） |

### 12. Token 黑名单

| 类 | 说明 |
|---|---|
| `TokenBlacklistService` | 同步黑名单（Redis 存储，Token 以 SHA-256 摘要落键 `auth:token:blacklist:<sha256>` + 分布式锁） |
| `ReactiveTokenBlacklistService` | 响应式黑名单（WebFlux 网关专用，基于 `ReactiveStringRedisTemplate`，返回 `Mono`） |

> 说明：黑名单实现为 SHA-256 摘要 + 分布式锁，**未使用布隆过滤器**（历史版本曾规划 `TokenBlacklistBloomFilter`，未实现）。

### 13. CSRF

| 类 | 说明 |
|---|---|
| `CsrfTokenValidator` | CSRF Token 校验器 |

### 14. 限流

> 说明：认证限流能力由 `ydsz-common-safe` 的限流组件提供，本模块未内置独立限流器。

### 15. 认证上下文

| 类 | 说明 |
|---|---|
| `AuthContextUtils` | 统一认证上下文工具（委托 common-core `RequestContext`，含 `LoginUser`/租户/列权限） |
| `AuthInfoUtils` / `AuthInfo` | 认证信息获取（从 RequestContext 解析） |
| `TenantContext` | 租户上下文模型 |

### 16. 认证处理器

| 类 | 说明 |
|---|---|
| `AuthHandler` / `AbstractAuthHandler` | 认证信息解析处理器（SPI 扩展点） |
| `ParsedAuthHeaders` | 已解析的认证请求头 |
| `BaseAuthFilter` | 认证基础过滤器（Token 解析 → 用户信息加载 → 上下文设置） |

### 17. 工具与国际化

| 类 | 说明 |
|---|---|
| `AccessTokenUtils` / `PermissionMerger` / `PermissionUtils` | 工具类（见第 1、4 节） |
| `AuthErrorCode` / `PermissionDeniedException` | 错误码与异常 |
| `PermissionCodes` | 权限码常量 |

### 18. 指标（新增）

| 类 | 说明 |
|---|---|
| `AuthMetrics` | 认证指标采集契约接口（`recordAuthSuccess` / `recordAuthFailure` / `recordAuthSkip`） |
| `PermissionMetrics` | 权限指标采集契约接口（`recordPermissionAllow` / `recordPermissionDeny` / `recordCacheHit/Miss` / `recordCheckTime` / `updateRedisAvailable`） |
| `AuthMetricsCollector` | Micrometer 指标采集器（同时实现 `AuthMetrics` + `PermissionMetrics`，动态标签 Counter/Timer 缓存） |

**暴露指标**：

| 指标名 | 类型 | 说明 |
|---|---|---|
| `auth.login.total` | Counter | 认证总次数（tag: result, userType, reason） |
| `auth.login.duration` | Timer | 认证耗时（tag: result, userType） |
| `auth.permission.check.time` | Timer | 权限校验耗时 |
| `auth.permission.deny` | Counter | 权限拒绝次数（同步写安全审计日志） |
| `auth.permission.allow` | Counter | 权限通过次数 |
| `auth.cache.hit` / `auth.cache.miss` | Counter | 权限缓存命中/未命中 |
| `auth.redis.available` | Gauge | Redis 可用状态 |

### 19. 配置与健康检查

| 类 | 说明 |
|---|---|
| `AuthConfiguration` | 自动配置（`@AutoConfiguration`，`ydsz.auth.enabled=true` 激活，含 Redis 健康检查定时任务） |
| `AuthFilterConfiguration` | 过滤器配置（Servlet 可用时激活） |
| `AuthFilterProperties` | 过滤器配置属性（忽略 URL / 仅校验 Token） |
| `AuthProperties` | RBAC 核心配置属性 |
| `KeyspaceNotificationProperties` | Redis Keyspace 通知配置 |
| `TenantContextHolderConfiguration` | 租户上下文配置 |
| `AuthHealthIndicator` | 健康检查（`/actuator/health/auth`，Redis PING + 响应耗时） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-auth</artifactId>
</dependency>
```

### 2. 启用认证

在 Spring Boot 主类上添加 `@EnableYdszAuth`，自动装配 RBAC 权限、数据权限、列级权限等能力：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.njydsz.common.auth.annotation.EnableYdszAuth;

@SpringBootApplication
@EnableYdszAuth
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 配置示例

```yaml
ydsz:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expire: 7200
    refresh-token-expire: 604800
    issuer: ydsz
  auth:
    enabled: true
    wildcard-enabled: true
    filter:
      verify-permission: true
      common-ignore-url: [/api/public/**]
    blacklist:
      enabled: true
      expire-seconds: 7200
```

## 配置项

### ydsz.auth（`AuthProperties`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 RBAC 权限校验（false 时跳过所有校验） |
| `wildcard-enabled` | `true` | 是否启用通配符权限匹配（`sys:user:*`） |
| `role-menu-key` | `ydsz-auth:role-menu:{}` | 角色-菜单/按钮权限 Redis Key 模板 |
| `role-api-key` | `ydsz-auth:role-api:{}` | 角色-API 权限 Redis Key 模板 |
| `role-row-key` | `ydsz-auth:role-row:{}` | 角色-行权限 Redis Key 模板 |
| `role-col-key` | `ydsz-auth:role-col:{}` | 角色-列权限 Redis Key 模板 |
| `ignore-roles` | (空) | 跳过权限校验的超管角色（CSV） |
| `role-code-field` | `roleCode` | 用户信息中角色编码字段名 |
| `role-permission-cache-seconds` | `30` | 角色权限本地缓存过期（秒） |
| `permission-cache-ttl-seconds` | `1800` | 权限缓存 TTL（秒） |
| `role-data-cache-seconds` | `30` | 行权限本地缓存过期（秒） |
| `role-column-cache-seconds` | `30` | 列权限本地缓存过期（秒） |
| `desensitize-cache-max-size` | `1000` | 列脱敏缓存最大条目（LRU） |
| `desensitize-cache-ttl-seconds` | `1800` | 列脱敏缓存过期（秒） |
| `local-permission-cache-minutes` | `5` | 本地降级缓存过期（分钟） |
| `redis-unavailable-fallback` | `DENY` | Redis 不可用降级策略（`DENY` / `ALLOW`） |
| `blacklist.enabled` | `true` | 是否启用 Token 黑名单 |
| `blacklist.expire-seconds` | `7200` | 黑名单过期时间（秒，与 Token 有效期一致） |
| `csrf-enabled` | `false` | 是否启用 CSRF 校验 |
| `health-check-interval` | `60000` | Redis 健康检查间隔（毫秒） |

### ydsz.auth.filter（`AuthFilterProperties`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `common-ignore-url` | `[]` | 通用忽略 URL |
| `gateway-ignore-url` | `[]` | 网关忽略 URL |
| `custom-ignore-url` | `[]` | 自定义忽略 URL |
| `verify-permission` | `true` | 是否校验权限 |
| `only-verify-token` | `[]` | 仅校验 Token 不校验权限的 URL |

## 使用示例

### 1. 注解式权限控制

```java
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.annotation.AuthMenuPermission;
import com.njydsz.common.auth.annotation.PermissionMode;

// 单个权限码
@AuthApiPermission(apiCodes = "sys:user:list")
public UserVO getUser(Long id) { ... }

// OR 模式：满足其一即可
@AuthApiPermission(apiCodes = {"sys:user:view", "sys:user:manage"}, mode = PermissionMode.OR)
public UserVO viewUser(Long id) { ... }

// 菜单权限 + 角色限定
@AuthMenuPermission(roleCodes = "admin", menuCodes = "sys:user")
public void manageUser() { ... }
```

### 2. 数据权限

```java
import com.njydsz.common.auth.annotation.DataScope;

// 单表场景：按部门 + 创建人过滤
@DataScope(deptColumn = "department_id", userColumn = "created_by")
public List<Employee> listEmployees(PageQuery query) { ... }

// JOIN 场景：声明别名
@DataScope(deptAlias = "d", userAlias = "u", deptColumn = "dept_id", userColumn = "create_by")
public List<OrderVO> listOrdersWithUser(PageQuery query) { ... }
```

### 3. Token 黑名单

```java
import com.njydsz.common.auth.service.TokenBlacklistService;

// 用户登出时加入黑名单
tokenBlacklistService.addToBlacklist(accessToken);

// 请求校验时检查是否在黑名单（SHA-256 摘要键，Redis 查询）
boolean blocked = tokenBlacklistService.isBlacklisted(accessToken);
```

### 4. 权限变更通知

```java
import com.njydsz.common.auth.event.PermissionChangedEvent;
import org.springframework.context.ApplicationEventPublisher;

// 角色权限变更后发布事件，触发本地缓存失效 + 跨节点 Pub/Sub 通知
applicationEventPublisher.publishEvent(new PermissionChangedEvent(roleCode));
```

### 5. 权限预检注解

```java
import com.njydsz.common.auth.precheck.PermissionPreCheck;
import com.njydsz.common.auth.precheck.PermissionCheckResult;

// 通过注解声明式预检：API 权限检查（ALL 模式：需全部满足）
@PermissionPreCheck(checkType = PermissionPreCheck.CheckType.API,
                    checkMode = PermissionPreCheck.CheckMode.ALL,
                    value = {"sys:user:add", "sys:user:edit"})
public void batchCreate() { ... }
```

### 6. 权限层级

```java
import com.njydsz.common.auth.hierarchy.PermissionHierarchyService;

// 注册权限继承关系（启动时一次性注册，实例方法）
permissionHierarchyService.registerPermission(tenantId, "sys:user", "sys:user:list", "sys:user:add");

// 后续校验时拥有 sys:user 自动拥有 sys:user:list
boolean ok = permissionHierarchyService.hasPermission(granted, "sys:user:list");
```

## SPI 扩展点

| 接口 / 类 | 扩展说明 |
|---|---|
| `AuthHandler` | 认证信息解析（自定义请求头解析） |
| `DataPermissionResolver` | 数据权限解析（自定义数据范围策略） |
| `DataPermissionCustomSqlProvider` | 数据权限动态 SQL（自定义 WHERE 拼接） |
| `RolePermissionLoader` | 角色权限加载（自定义权限存储源） |
| `ColumnPermissionResolver` | 列级权限解析（自定义列权限策略） |
| `RbacUserInfoService` | RBAC 用户信息加载（自定义用户存储源） |
| `CacheKeyStrategy` | 权限缓存 Key 策略（自定义 Key 生成规则） |
| `TokenService` | Token 生成/校验/刷新（自定义 Token 实现） |
| `PermissionChangeListener` | 权限变更监听（自定义变更响应） |

所有 SPI 实现通过 `@ConditionalOnMissingBean` 注册，业务侧自定义 Bean 自动覆盖默认实现。

## 健康检查

`AuthHealthIndicator` 暴露 `/actuator/health/auth` 端点，检测权限模块依赖的 Redis 连通性：

- 验证 `RedisConnectionFactory` 连接状态
- 执行 `PING` 命令验证可达性
- 返回响应耗时作为性能指标

`AuthConfiguration` 内置定时任务（默认每 60 秒）检查 Redis 连通性，不可用时自动降级到本地缓存，并根据 `redis-unavailable-fallback` 策略（`DENY`/`ALLOW`）切换权限校验行为。

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `AuthConfiguration` | `ydsz.auth.enabled=true`（默认激活，含 `@EnableScheduling`） |
| `AuthFilterConfiguration` | Servlet 可用时激活 |
| `AuthMetricsCollector` | Micrometer `MeterRegistry` 在 classpath 且 Bean 存在 |
| `AuthHealthIndicator` | Spring Boot Health + `RedisConnectionFactory` 存在 |
| `JwtTokenService` | jjwt 在 classpath 且 `ydsz.auth.token.enabled=true` |

## 注意事项

1. **Redis 降级**：Redis 不可用时自动降级到本地缓存，默认策略 `DENY`（拒绝所有权限请求）。极端容灾场景可配置 `redis-unavailable-fallback=ALLOW` 放行。
2. **Token 黑名单**：基于 Redis 存储（SHA-256 摘要键），无本地布隆过滤器前置；登出即加入黑名单，过期时间与 Token 有效期一致。
3. **列权限签名**：列权限数据签名能力由业务侧自行实现（`AuthColPermissionSigner` 未内置），如需防篡改请使用内部头 HMAC 签名（`ydsz-gateway` `InternalHeaderSigner`）。
4. **权限预热**：本模块未内置启动权限预热（`PermissionWarmUpInitializer` 未实现）；角色权限采用按需加载 + 本地缓存（`role-permission-cache-seconds` 控制过期）。
5. **通配符缓存**：`PermissionUtils` 使用 LRU 缓存（最大 1024）编译后的正则模式，权限配置变更时调用 `clearPatternCache()` 清理。
6. **权限码规范**：建议采用三段式命名 `领域:资源:操作`（如 `sys:user:add`），不符合规范仅记录告警日志，不影响校验逻辑。
7. **多租户**：租户上下文由 common-core `RequestContext` / common-tenant `TenantContextHolder` 承载，线程池场景通过 `TransmittableThreadLocal` 传递；请求结束自动清理。

## 技术栈

- 缓存框架：ydsz-common-cache（替代 Caffeine，本地降级缓存）
- Redis：ydsz-common-redis（权限存储 + 黑名单 + Pub/Sub）
- JSON：ydsz-common-json（权限树序列化）
- 脱敏：ydsz-common-safe（列脱敏上下文）
- JWT：jjwt（optional）
- 指标：Micrometer（optional）
- 线程上下文：TransmittableThreadLocal（线程池传递）

## 变更记录

- **v1.0.0**（2026-08-02）：补全 `@PermissionMode`/`@EnableYdszAuth`、响应式 Token 黑名单（`ReactiveTokenBlacklistService`）、权限层级（`PermissionHierarchyService`）、权限预检（`PermissionPreCheck`/`PermissionCheckResult`）、指标（`AuthMetrics`/`AuthMetricsCollector`/`PermissionMetrics`）、工具（`AccessTokenUtils`/`PermissionMerger`/`PermissionUtils`）等章节。
