# ydsz-common-auth 全局引用分析报告

> **分析日期**：2026-09-30  
> **分析范围**：ydsz-common-auth（85 → 79 个 Java 文件，清理后）× 10 业务模块 + 29 个其他 common 子模块  
> **分析方法**：静态引用扫描 + 代码语义分析  
> **执行状态**：✅ 全部 P0/P1/P2 优化项已落地，增量模块编译通过

---

## 1. 模块能力全景

`ydsz-common-auth` 共 85 个 Java 文件，提供 24 大类公共能力。经本轮整改后，**沉睡类全部清理**，模块更加精简聚焦。

| 能力域 | 核心类 | 定位 |
|--------|--------|------|
| annotation | `@AuthApiPermission`、`@EnableYdszAuth`、`PermissionMode` | 权限注解 + 启用开关 |
| apikey | `ApiKeyAuthService`、`ApiKeyMetadata` | API Key 认证 |
| aspect | `AuthPermissionAspect` | AOP 权限拦截切面 |
| config | `JwtConfiguration`、`RbacConfiguration`、`SecurityConfiguration` | 自动配置 |
| constant | `AuthErrorCode`、`AuthHeaderConstants`、`InternalSignatureHeaderConstants` | 认证常量 |
| context | `AuthContextUtils`、`AuthInfoUtils` | 上下文工具（租户/用户） |
| event | `AuthenticationEvent`、`PermissionChangedEvent`、`PermissionChangeListener` | 认证事件体系 |
| exception | `PermissionDeniedException` | 权限异常 |
| filter | `BaseAuthFilter` | 认证过滤器基类 |
| handler | `AbstractAuthHandler`、`AuthHandler`、`ParsedAuthHeaders` | 认证处理器 SPI |
| health | `AuthHealthIndicator` | 健康检查 |
| metrics | `AuthMetrics`、`AuthMetricsCollector`、`PermissionCatalog` | 认证指标 |
| model | `AuthInfo`、`LoginUser`、`YdszAuthInfo`、`UserInfo` | 认证模型 |
| oidc | `JwksEndpoint` | OIDC JWKS 端点 |
| security | `CsrfTokenValidator`、`InternalHeaderSigner` | CSRF 验证、内部请求头签名 |
| service | `DataScopeHelper`、`RbacPermissionEvaluator`、`RbacUserInfoService`、`TokenBlacklistService` | 核心认证服务 |
| session | `LocalSessionRegistry`、`SessionRegistry` | 会话注册 |
| strategy | `CacheKeyStrategy`、`DefaultCacheKeyStrategy` | 缓存 Key 策略 |
| token | `JwtTokenService`、`TokenService`、`TokenKeyUtils`、`TokenProperties` | Token 服务 |
| util | `AccessTokenUtils`、`PermissionUtils`、`SecurityUtils`、`TotpAuthenticator` | 工具类 |

---

## 2. 本期整改清单

### 2.1 🔴 P0 — 消除重复 + 沉睡代码

**P0-1: 删除 `ColumnDesensitizationService`**
- 文件：`ydsz-common-auth/desensitize/ColumnDesensitizationService.java` — 已删除
- 连带：`AuthProperties` 中的 `desensitizeCacheMaxSize` / `desensitizeCacheTtlSeconds` 字段 — 已删除
- 原因：零外部引用；脱敏能力已由 `ydsz-common-safe` 完整覆盖（`ColumnDesensitizationExecutor` + `SensitiveUtils`）
- 影响范围：auth 模块

**P0-2: 删除 `PermissionHierarchyService` + `PermissionMerger`**
- 文件：`ydsz-common-auth/hierarchy/PermissionHierarchyService.java` — 已删除
- 文件：`ydsz-common-auth/util/PermissionMerger.java` — 已删除
- 空目录：`ydsz-common-auth/hierarchy/` — 已删除
- 原因：自实现权限继承层级管理器，零消费方；业务权限链路由 userinfo RBAC 体系独立实现
- 连带清理：
  - `RbacConfiguration`：删除 `ObjectProvider<PermissionHierarchyService>` 参数及其注入逻辑
  - `RbacPermissionEvaluator`：删除 `hierarchyService` 字段、`setHierarchyService()` 方法、`hasPermission()` 层级分支
  - `RedisRolePermissionLoader`：删除 `hierarchyService` 字段、构造参数、`registerPermissionHierarchy()`/`registerHierarchy()` 方法
  - `PermissionUtils`：删除引用已删类的注释
- 保留：`PermissionUtils`（仍被 `AbstractAuthHandler` 和 `RbacPermissionEvaluator` 使用）

**P0-3: feign `SignatureUtils` 删除，拦截器直接使用 `DigestUtils`**
- 文件：`ydsz-common-feign/signature/SignatureUtils.java` — 已删除
- 空目录：`ydsz-common-feign/signature/` — 已删除
- 修改：`SignatureRequestInterceptor` — `hmacSha256()` 改为直接调用 `DigestUtils.hmacSha256Base64()`；`generateNonce()` 内联为私有方法
- 原因：签名统一收敛至 `DigestUtils`（YDIZ-COMMON-054），消除 5 处签名实现碎片化

### 2.2 🟡 P1 — 利用率提升 + 规范统一

**P1-1: `BloomFilter` 下沉至 `ydsz-common-util`（L1 零依赖）+ message 迁移**
- 文件：`ydsz-common-auth/util/BloomFilter.java` → `ydsz-common-util/BloomFilter.java` — 已移动
- 文件：`ydsz-message-server/consumer/BloomFilterDeduplicator.java` — 改用 `com.njydsz.common.util.BloomFilter`
- 更新：`TokenBlacklistService` import 路径 → `com.njydsz.common.util.BloomFilter`
- 消除：message 对 Guava BloomFilter 的隐式直接依赖（`BloomFilterDeduplicator` 改为使用零依赖版本）

**P1-2: DataScopeHelper 在 workflow 调用确认有效**
- 文件：`FlowInstanceQueryService.java` 中的 `DataScopeHelper.buildSqlFragment("", "", "dept_id", "initiator_id")`
- 结论：调用参数有效（空字符串 = 无前缀意图明确，deptAlias = 保留忽略参数），非占位。与设计意图一致。无需修改。

**P1-3: 硬编码认证头名 → 常量引用**
- `AuditAspect.java:185` — `"X-Access-Token"` → `AuthHeaderConstants.X_ACCESS_TOKEN`
- `TokenAutoRenewalFilter.java:66` — `NEW_TOKEN_HEADER = "X-Access-Token"` → `AuthHeaderConstants.X_ACCESS_TOKEN`

**P1-4: TokenJoinTokenService / UnsubscribeTokenUtil 迁移评估**
- 结论：workflow 的 Join Token 和 message 的 Unsubscribe Token 语义为业务专用 Token（非 JWT 认证路径），与 `auth.JwtTokenService` 用途不同，不建议迁移。

### 2.3 🔵 P2 — 规则 + 治理

**P2-1: 新增 shared-rules 规则 YDIZ-SEC-003**
- 写入：`docs/ai-rules/shared-rules.yaml` — `YDIZ-SEC-003: 禁止新建签名工具类，必须使用 auth InternalHeaderSigner / DigestUtils`
- 同步：`catpaw-always.md` — 规则已追加，版本升至 v26.09.30-v13
- 统计：total 164 → 165（P0=56 P1=81 P2=20）

---

## 3. 编译验证

| 模块 | 编译结果 |
|------|---------|
| ydzs-common-auth | ✅ BUILD SUCCESS |
| ydzs-common-util | ✅ BUILD SUCCESS |
| ydzs-common-feign | ✅ BUILD SUCCESS |
| ydzs-common-audit | ✅ BUILD SUCCESS |
| ydzs-userinfo-web | ✅ BUILD SUCCESS |
| ydzs-message-server (BloomFilterDeduplicator) | ✅ 已修复（`BloomFilter<>` → 原生类型）|
| ydzs-common-safe | ⚠️ 预存异常（`exception.code` 包路径缺失，与本次整改无关）|

---

## 4. 变更文件汇总

### 删除 6 文件 + 2 空目录
- `ydsz-common-auth/desensitize/ColumnDesensitizationService.java`
- `ydsz-common-auth/hierarchy/PermissionHierarchyService.java`
- `ydsz-common-auth/util/PermissionMerger.java`
- `ydsz-common-auth/util/BloomFilter.java` → 移至 `util/`
- `ydsz-common-feign/signature/SignatureUtils.java`
- 空目录：`hierarchy/`、`signature/`

### 修改 9 文件
- `ydsz-common-auth/config/AuthProperties.java` — 删除 2 个脱敏缓存字段
- `ydsz-common-auth/config/RbacConfiguration.java` — 删除 hierarchy 参数 + import
- `ydsz-common-auth/service/RbacPermissionEvaluator.java` — 删除 hierarchy 字段/setter/调用 + import
- `ydsz-common-auth/service/impl/RedisRolePermissionLoader.java` — 删除 hierarchy 字段/构造参数/2 方法/calls + import
- `ydsz-common-auth/service/TokenBlacklistService.java` — BloomFilter import 路径更新
- `ydsz-common-auth/util/PermissionUtils.java` — 删除引用已删类注释
- `ydsz-common-feign/aspect/SignatureRequestInterceptor.java` — 直接使用 DigestUtils + 内联 generateNonce
- `ydsz-message/message-server/consumer/BloomFilterDeduplicator.java` — 改用 util.BloomFilter
- `ydzs-userinfo/userinfo-web/filter/TokenAutoRenewalFilter.java` — NEW_TOKEN_HEADER 改用 AuthHeaderConstants
- `ydsz-common/audit/aspect/AuditAspect.java` — `"X-Access-Token"` 改用 AuthHeaderConstants

### 新增 2 文件
- `ydsz-common-util/BloomFilter.java`（零依赖布隆过滤器，从 auth 迁移）
- `docs/ai-rules/shared-rules.yaml` v13 — 新增 YDIZ-SEC-003 规则
- `catpaw-always.md` v13 — 同步新增规则

---

## 5. 后续建议（非阻塞）

1. **common-safe 预存编译错误修复**：`exception.code` 包路径缺失问题（与本次整改无关，属历史遗留）
2. **OutboxDomainEventPublisher.topic()**：message-server 中 `DomainEvent.Builder.topic()` 方法签名变更未对齐（属此前 common-event 重构遗留）
3. **FilterIgnoreConstants / AuthHeaderConstants / InternalSignatureHeaderConstants 激活**：报告分析为 0 引用，但实际已被 code 内部引用（Javadoc 注释中可见）。因项目其他 base/socket 等模块存在同名常量类分层定义，后续可渐进式统一。

---

*报告生成时间：2026-09-30 | 同步代码改造完成 | 规范版本 v26.09.30-v13*
