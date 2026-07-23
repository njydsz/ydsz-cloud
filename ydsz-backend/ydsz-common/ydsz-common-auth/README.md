# ydsz-common-auth

YDSZ 认证与授权框架 — JWT Token 服务、RBAC 权限模型（菜单/API/行/列四级权限）、数据权限 @DataScope、多租户隔离、TOTP 双因子认证、密码策略、权限缓存与热更新。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 82 |

## 核心能力

### JWT Token 服务

| 类 | 说明 |
|---|---|
| `JwtTokenService` | Token 服务（签发 / 刷新 / 吊销） |
| `JwtTokenProvider` | Token 生成与解析 |
| `TokenService` | Token 服务接口 |
| `TokenProperties` | Token 配置（密钥 / 过期时间 / 刷新策略） |
| `TokenBlacklistService` | Token 黑名单（Redis 存储） |
| `AccessTokenUtils` | AccessToken 工具 |

### RBAC 权限模型

| 注解 | 切面 | 说明 |
|---|---|---|
| `@AuthMenuPermission` | `AuthPermissionAspect` | 菜单权限校验 |
| `@AuthApiPermission` | `AuthPermissionAspect` | API 接口权限校验 |
| `@AuthRowPermission` | `AuthRowPermissionAspect` | 行级数据权限 |
| `@AuthColPermission` | `AuthColPermissionAspect` | 列级字段权限 |
| `@DataScope` | — | 数据范围注解（ALL / DEPT / DEPT_AND_SUB / SELF） |

### 权限服务

| 类 | 说明 |
|---|---|
| `RbacUserInfoService` / `RedisRbacUserInfoService` | 用户信息查询 |
| `RbacPermissionEvaluator` | 权限评估器 |
| `RolePermissionLoader` / `RedisRolePermissionLoader` | 角色-权限加载器 |
| `DataPermissionResolver` / `RedisRoleDataPermissionResolver` | 数据权限解析器 |
| `ColumnPermissionResolver` / `RedisRoleColumnPermissionResolver` | 列权限解析器 |
| `PermissionCacheService` / `LocalPermissionCache` | 权限缓存（本地 + Redis 两级） |
| `PermissionCodeValidator` / `PermissionCodes` | 权限码校验 |
| `PermissionPreChecker` / `PermissionPreCheck` | 权限前置检查 |

### 权限缓存热更新

| 类 | 说明 |
|---|---|
| `PermissionChangePublisher` | 权限变更发布器 |
| `PermissionChangeNotifier` | 权限变更通知器 |
| `PermissionChangeCacheInvalidator` | 缓存失效处理器 |
| `PermissionKeyspaceNotificationListener` | Redis Keyspace 事件监听器 |
| `PermissionWarmUpInitializer` | 启动时权限预热 |
| `CacheKeyStrategy` / `DefaultCacheKeyStrategy` | 缓存 Key 策略 |

### 安全能力

| 类 | 说明 |
|---|---|
| `PasswordPolicy` | 密码策略（复杂度 / 历史 / 过期） |
| `TotpUtil` | TOTP 双因子认证（RFC 6238） |
| `CsrfSecurityPolicy` | CSRF 安全策略 |
| `LoginUser` / `LoginAuditEvent` / `LoginStatus` | 登录用户模型与审计 |
| `AccountLockInfo` / `AccountLockedEvent` | 账户锁定 |
| `ColumnDesensitizationService` | 列脱敏服务 |

### 数据权限上下文

| 类 | 说明 |
|---|---|
| `DataScope` / `DataScopeHelper` / `DataScopeContext` | 数据范围上下文 |
| `DataScopeInfo` / `DataScopeAware` | 数据范围信息 |
| `TenantContext` | 租户上下文 |
| `AuthContext` / `AuthHandler` / `AbstractAuthHandler` | 认证上下文与处理器 |
| `ColumnPermissionContext` / `PermissionContextHolder` | 列权限上下文 |

### 过滤器

| 类 | 说明 |
|---|---|
| `BaseAuthFilter` | 认证基础过滤器（Token 解析 → 用户信息加载 → 上下文设置） |

## 配置项

```yaml
ydsz:
  jwt:
    secret: ${JWT_SECRET}          # JWT 密钥
    access-token-expire: 7200      # AccessToken 过期（秒）
    refresh-token-expire: 604800   # RefreshToken 过期（秒）
    issuer: ydsz
  auth:
    filter:
      enabled: true
      ignore-urls: [/api/public/**]
    permission:
      cache-ttl: 300               # 权限缓存 TTL（秒）
      warmup-on-start: true        # 启动预热
    keyspace-notification:
      enabled: true                # Redis Keyspace 通知开关
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `AuthConfiguration` | 总是激活 |
| `AuthFilterConfiguration` | Servlet 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-auth</artifactId>
</dependency>
```
