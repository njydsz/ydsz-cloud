# ydsz-userinfo 模块优化分析报告

> 对标业界主流 IAM（Google IAM / 阿里 RAM / Spring Authorization Server / Keycloak / Casbin）
> 基线为 ydsz-cloud 仓库 ydsz-userinfo 最新代码（截至 2026-08-19）
> 遵循《云顶编码规范》：禁止第三方 JSON/Caffeine/POI；公共层 L1 为四个 utility 模块；优先 ydsz-common-* 自研组件

---

## 一、模块画像（基于代码事实）

### 1.1 规模统计

| 维度 | 数量 | 备注 |
|---|---|---|
| 子模块 | 6 | api / app / domain / infra / server / web |
| Controller | 14 | README 文档声明 12，实际 14（doc drift） |
| Service 接口 | 15 | 含 impl 子目录，共 13 个 Impl |
| Mapper | 14 | MyBatis-Plus 风格 |
| 实体 DO | 14 | 全部位于 `infra/entity`（README 描述为 `domain/entity` 无 DO 后缀，doc drift） |
| 领域 Repository | 14 | domain/repository 下，DDD 风格 |
| DTO/VO/Query | 17+13+5 | domain 层 |
| Domain Event | 2 | UserDomainEvent + UserDomainEventType |
| 启动端口 | 9002 | 构建顺序 3/10 |
| 公共依赖 | 5 | common-web / common-auth / common-redis / common-safe / common-jdbc |

### 1.2 核心能力清单

- **认证**：账号密码 + 图形验证码 + LDAP/ADFS + TOTP MFA + 短信 MFA 降级
- **会话**：Redis Hash（accessToken → 详情）+ Redis Set（userId → accessToken 列表）+ 黑名单
- **风控**：RiskScoringService 4 维度评分（IP / 时间 / 设备 / 频率）+ 4 等级（SAFE/MEDIUM/HIGH/CRITICAL）
- **OAuth2**：授权码 + PKCE + refresh 轮换 + revoke（RFC 7009）+ introspect（RFC 7662）+ userinfo
- **密码策略**：长度 / 字符类别 / 连续重复 / 用户名包含 / 弱口令字典 / 历史密码
- **审计**：`@Audit` 注解 + `SensitiveOperationAspect` 二次认证切面
- **可观测**：UserInfoMetrics（继承 SentryMetricsAdapter）+ Redis 跨实例在线会话计数
- **数据安全**：BCrypt（password）+ AES-256-GCM（realName，`@EncryptField`）+ SQL 防火墙 + 字段脱敏
- **横切**：`@DataScope` 数据权限、`@Idempotent` 幂等、`@RateLimit` 限流、`@ApiVersion` 版本

### 1.3 与业界大厂对标（雷达图维度评分）

| 维度 | ydsz | 大厂标准 | 差距 | 评级 |
|---|---|---|---|---|
| 认证 | 85 | 95 | -10 | B+ |
| 鉴权 | 80 | 90 | -10 | B+ |
| 加密 | 90 | 95 | -5 | A- |
| 风控 | 65 | 90 | **-25** | C+ |
| 审计 | 75 | 90 | -15 | B |
| 可观测 | 80 | 90 | -10 | B+ |
| OAuth2 | 85 | 95 | -10 | B+ |
| 密码策略 | 80 | 90 | -10 | B+ |
| 会话治理 | 85 | 90 | -5 | B+ |
| 多租户 | 50 | 85 | **-35** | D+ |

**重点收口**：风控（差距 25）、多租户（差距 35）、审计（差距 15）。

---

## 二、五维度分析

### 2.1 架构维度（评分 8/10）

**优势**：
1. **DDD 五层清晰**：api（Feign Client + Fallback）/ app（OpenAPI 配置）/ domain（Repository 接口 + DTO/VO/Query/Event）/ infra（DO + Mapper）/ server（Service + Auth + Config + Aspect + Metrics + Event）/ web（Controller + Filter）。
2. **富领域模型**：`UserAccountDO` 封装了 `enable()` / `disable()` / `unlock()` / `isLocked()` / `canAuthenticate()` / `recordLoginFailure()` / `recordLoginSuccess()` 等领域行为，避免贫血模型。
3. **P0-5 拆分到位**：`AuthServiceImpl` 仅做编排，单点职责委托 `AccountStatusGuard` / `CredentialVerifier` / `SessionManager` / `RoleCacheService` / `RiskScoringService` / `LoginAttemptCounterService`，可单测可替换。
4. **事件驱动 + Outbox**：`UserDomainEventPublisher` 在用户 CRUD / 角色变更 / 登录成功等节点发布领域事件，配合 `SearchIndexEventBridge` 同步搜索索引。
5. **公共模块复用**：`@EnableYdszAudit` / `@EnableYdszAuth` / `@EnableYdszSafe` / `@EnableYdszFeign` 注解式装配，符合云顶规范。

**短板**：
1. **Controller 直接注入 `WorkflowApproverCacheService`**：`InternalApiController` 第 81、140、186、202、218、234 行直接调用缓存服务，违反分层——Controller 不应感知缓存存在，应通过 `UserAccountService` / `DepartmentService` 等 Service 层封装。
2. **`AuthServiceImpl` 注入 14 个 bean**：协作图庞大，建议按子域聚合（认证子域 / 会话子域 / 风控子域 / 审计子域），或拆为 `LoginOrchestrator` + `SessionOrchestrator` + `RefreshOrchestrator`。
3. **缺少 Anti-Corruption Layer（ACL）**：domain 层 VO 直接被 Feign Client 返回（`OrgQueryClient` 返回 `UserAccountVO`），下游服务强耦合 ydsz-userinfo 的 domain 包；应通过 api 层 Assembler 转换为 `OpenApiUserVO` 等公共契约。
4. **`UserInfoRbacService` 缺版本兼容**：从 Redis Hash 反序列化 UserInfo 没有 `schemaVersion` 字段，将来字段变更难以平滑迁移。

### 2.2 功能维度（评分 7/10）

**优势**：OAuth2 端点齐全（authorize/token/revoke/introspect/userinfo）；MFA 双通道（TOTP + 短信）；密码历史防重用；登录失败原子锁定。

**短板**：
1. **风控维度单薄**：仅 IP / 时间 / 设备（基于 UA hash）/ 频率 4 维度，权重硬编码（30/20/25/25）。业界主流（Google、阿里）会引入：地理位置异常（异地登录）、用户行为基线（常用 IP 段、常用时段、常用设备指纹）、机器人检测（鼠标轨迹 / 行为流）、Tor/VPN/Proxy 出口 IP 识别、设备指纹（不仅是 UA，还含 canvas / 字体 / WebGL 指纹）。
2. **多租户仅逻辑隔离**：`UserAccountDO.tenantId` 字段存在，但代码中未见到租户上下文传播（ThreadLocal / Request Scope）、未见到 schema 隔离、未见到资源配额。`UserAccountServiceImpl.create()` 第 157-159 行默认 `tenantId = "1"`，等同于单租户。
3. **OAuth2 scope 固定**：`OAuth2Controller.token()` 第 318、373 行返回 `"scope": "read write"` 写死，没有按客户端注册的 scope 范围校验，没有 audience 字段，无法做细粒度授权（对标 Spring Authorization Server / Keycloak 的 scope-per-client）。
4. **refresh_token 缺重用检测**：`AuthServiceImpl.refresh()` 第 349 行仅把旧 refresh_token 加黑名单，未做 RFC 6819 §5.2.2.3 建议的"重用检测 → 链式撤销"——攻击者重用旧 token 时应自动撤销该用户全部 refresh_token 链。
5. **自助服务安全弱**：`SelfServiceController` 的 `/register` / `/forgot-password` 仅靠 SMS 验证码 + 全局 5 QPS 限流，缺：图形验证码 / 滑块、IP 维度限流、`@Idempotent` 幂等保护、邮件验证码通道。
6. **单设备登录限制缺失**：会话索引 `userinfo:session:user:{userId}` 存储所有活跃 token，但没有上限控制，同一用户可无限多端登录。对标钉钉 / 飞书有"最多 N 端在线"策略。
7. **`SensitiveOperationAspect` 无分级**：所有 `@SensitiveOperation` 一视同仁要求二次认证，未区分 CRITICAL（改密 / 删除用户）/ HIGH（角色分配）/ MEDIUM（更新个人信息）三档。

### 2.3 性能维度（评分 6/10）

**优势**：
1. `UserInfoMetrics` 使用 Redis 原子计数器 `userinfo:session:total` 维护跨实例在线会话数，避免单节点 AtomicLong。
2. `LoginAttemptCounterService` 将失败计数从 DB count 查询改为 Redis 计数器，消除登录主路径 DB 往返。
3. `batchUserNames` 支持分批（超 `batchSizeLimit` 自动切分），避免 in 查询击穿 SQL 限制。
4. `RoleCacheService` / `WorkflowApproverCacheService` 提供角色 / 审批人展开的 Redis 缓存。

**短板**：
1. **批量操作 N+1 严重**：`UserAccountServiceImpl.batchRemoveByIds()` / `batchEnable()` / `batchDisable()` 第 488-497、513-525、541-554 行用 for 循环单条调用 `findById` + `update` + `indexUpsert` + `eventPublish`。批量 100 个用户会触发 100×4 = 400 次方法调用，且每次 `indexUpsert` 同步调 SearchIndexEventBridge。应改用 `UPDATE ... WHERE id IN (...)` 批量 SQL + 异步索引同步。
2. **`update` 缺乐观锁**：`UserAccountServiceImpl.update()` 第 181-194 行先 `findById` 取 `existing`（结果未使用）再 `update`，存在 lost update 风险。`UserAccountDO` 未声明 `@Version` 字段。并发更新场景下后写覆盖前写。
3. **`assignRoles` 全量覆盖无并发保护**：`UserAccountServiceImpl.assignRoles()` 第 292-322 行 `deleteByUserId` + `batchInsert`，无分布式锁或乐观版本号。同一用户并发 assignRoles 会丢角色。
4. **`kickOutOtherSessions` 串行**：`AuthController.kickOutOtherSessions()` 第 277-282 行 for 循环逐个 `authService.logout(token)`，会话多时阻塞。应批量 `sessionManager.revokeSessions(Set<String>)`。
5. **Redis DECR 下溢**：`UserInfoMetrics.recordLogout()` 第 105 行 `redisStringOps.decr(SESSION_TOTAL_KEY, 1L)`，若计数器为 0 或不存在仍会 decr 导致 -1，破坏 Gauge 准确性。应用 Lua 脚本保证 ≥0。
6. **`UserInfoRbacService` 无本地二级缓存**：每次鉴权（网关层每条请求都查）都 Redis `hGetAll`，对 Redis 是热路径。可加 Caffeine 二级缓存（TTL 5-10s，遵循云顶规范——使用 `ydsz-common-cache`，禁止直接 Caffeine）。
7. **`getDeptTree` 全量加载 + 内存构建**：`InternalApiController.getDeptTree()` 第 110-112 行返回全量部门并在内存递归建树，部门数 >1000 时性能差。应预计算树结构缓存于 Redis（`ydsz-common-cache`）。
8. **`evaluateLoginRisk` 串行 Redis 往返**：`AuthServiceImpl.evaluateLoginRisk()` 第 200-201 行先 `getRecentFailCount` 再 `checkIfNewDevice`，两次 Redis RTT。可合并为 pipeline 或 Lua 脚本。

### 2.4 体验维度（评分 7/10）

**优势**：
1. OpenAPI 3 注解完整（`@Tag` / `@Operation`），Swagger 文档自动生成。
2. i18n 资源齐备（`userinfo-messages_zh_CN.properties` / `_en_US.properties`）。
3. `BaseResponse<T>` 统一响应格式 + `PageResponse<T>` 分页结构。
4. `@ApiVersion("1")` + `X-API-Version` header + sunset headers 支持版本演进。
5. `@Audit` 注解的 `excludeParams` 排除敏感字段（password / verifyCode / newPassword）。

**短板**：
1. **OAuth2Controller 缺 `@RateLimit` + `@Audit`**：authorize / token / revoke / introspect / userinfo 五个端点均无限流无审计，与 AuthController 形成对照（AuthController 全部端点都有）。攻击者可暴力枚举授权码或 DoS。
2. **`SensitiveOperationAspect` 仅 `log.warn`**：敏感操作被拦截未落审计表，事后追溯困难。应在切面内调用 `AuditService.record()` 落 `sys_audit_log`。
3. **内部 API 路径不规范**：`/user/list-by-RoleDO`、`/RoleDO/batch-names`、`/PostDO/batch-names`、`/CompanyDO/batch-names`、`/user/RoleDO-codes` —— 用 DO 后缀的实体名直接做 URL 路径，违反 RESTful 命名（应为 `/users/by-role`、`/roles/batch-names`、`/posts/batch-names`、`/companies/batch-names`）。
4. **`extractClientIp` 信任代理头**：`AuthController.extractClientIp()` 第 179-201 行直接信任 `X-Forwarded-For` / `X-Real-IP` / `Proxy-Client-IP` / `WL-Proxy-Client-IP`，未配置 trusted proxy 白名单。直连场景下恶意客户端可伪造 IP 绕过 IP 风控。
5. **错误码粒度不足**：`UserInfoExceptionCode.PASSWORD_TOO_WEAK` 同时承载"长度不足 / 字符类别不足 / 连续重复 / 含用户名 / 弱字典"5 种错误，前端无法精确提示。应拆为 5 个细分错误码。
6. **logout 无计数**：`UserInfoMetrics.recordLogout()` 仅 decr 会话数，未累加 `logouts_total` 计数器，缺失登出行为分析。

### 2.5 过度设计维度（评分 8/10）

总体而言，ydsz-userinfo **未发现明显过度设计**，组件化与抽象层次基本合理。以下两点需复评：

1. **`UserAccountDO` 双重状态访问器**：同时提供 `getStatus()` / `setStatus(String)` 与 `getStatusEnum()` / `setStatusEnum(EnableStatusEnum)`，外加 `IntegerStringTypeHandler` 做 DB 整数与业务字符串转换。这是为兼容历史 DB 列（0/1）的合理设计，但应在文档中明确"新代码禁止直接用 String status，必须用 Enum 版本"。
2. **`UserInfoMetrics` 通用方法冗余**：`recordCacheResult` / `recordHttpCount` / `recordTimer` 三个通用方法（第 150-173 行）是否已被父类 `SentryMetricsAdapter` 提供？若已提供则为重复抽象。建议核对父类 API 后删除冗余。
3. **`AuthService` 接口的 `kickOutUser` 与 `evictAllSessions`**：注释明确"内部逻辑相同，语义上区分"，但实际实现完全一致（`AuthServiceImpl` 第 371-374、384-386 行均调用 `sessionManager.evictAllSessions`）。语义区分若未在审计日志中体现，则属于"伪抽象"，建议合并或真正实现差异化（如 `kickOutUser` 写管理员审计、`evictAllSessions` 写系统审计）。

---

## 三、P0 立即收口清单（10 项，预计 1-2 周完成）

> **判定标准**：高安全/业务影响 + 低工作量 + 已具备修复条件

### P0-1. bootstrap.yml 硬编码 AES 密钥泄露

- **定位**：`ydsz-userinfo-web/src/main/resources/bootstrap.yml` 第 87 行
  ```yaml
  keys:
    1: "fbQujnuiM1o99Ds4mK8VqyeLgRyI25gtL8e+irNglLM="
  ```
- **对标**：阿里 / 腾讯 / 华为云均要求密钥通过 KMS 或 K8s Secret 注入；Spring Cloud Config 也支持加密配置。
- **建议**：改为 `${YDSZ_FIELD_ENC_KEY_V1:}` 占位，由 Nacos 加密配置或环境变量注入；并在 `UserInfoProperties` 启动校验密钥非空且长度 ≥ 32 字符。同时该 YAML 第 81 行 `field-encryption` 缩进混乱（与 `auto-block` 同级但 keys 子项缩进多一级），需修正层级。
- **影响**：当前密钥已暴露在版本库，应立即轮换并强制刷新所有已加密 realName 字段（重新加密一遍）。

### P0-2. OAuth2 redirect_uri 空白名单绕过

- **定位**：`OAuth2Controller.authorize()` 第 166-170 行
  ```java
  if (clientConfig.getRedirectUris() != null
      && !clientConfig.getRedirectUris().isEmpty()
      && !clientConfig.getRedirectUris().contains(redirectUri)) {
    throw new BusinessException(UserInfoExceptionCode.OAUTH2_REDIRECT_URI_MISMATCH);
  }
  ```
- **对标**：RFC 6749 §3.1.2.3 强制要求 redirect_uri 必须命中注册白名单；Spring Authorization Server、Keycloak 均在白名单为空时拒绝。
- **建议**：白名单为 null 或空时直接抛 `OAUTH2_REDIRECT_URI_MISMATCH`，禁止跳过校验。
- **影响**：开放重定向漏洞，可被钓鱼攻击利用。

### P0-3. OAuth2 授权码并发重放

- **定位**：`OAuth2Controller.authorizationCodeGrant()` 第 250-293 行
  先 `redisStringOps.get(CODE_KEY_PREFIX + code, String.class)` 取上下文，做完 PKCE / clientId 校验后第 293 行才 `redisStringOps.del`。
- **对标**：RFC 6749 §4.1.2 要求授权码一次性使用；业界实现均用 `GETDEL`（Redis 6.2+）或 Lua 脚本原子取删。
- **建议**：将第 250 行改为 Lua 脚本 `local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v`，保证取与删原子；或升级 RedisStringOps 提供 `getAndDelete` 原子方法。
- **影响**：当前实现下两个请求并发使用同一授权码都能签发 token，构成重放漏洞。

### P0-4. 雪花 ID 作 OAuth2 授权码强度不足

- **定位**：`OAuth2Controller.authorize()` 第 173 行
  ```java
  String code = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
  ```
- **对标**：RFC 6749 §10.10 推荐授权码至少 128 位随机熵；OAuth2 安全最佳实践要求 SecureRandom 生成。
- **建议**：改用 `SecureRandom` 生成 32 字节随机数 + Base64URL 编码，长度 ≥ 43 字符（与 PKCE code_verifier 长度对齐）。
- **影响**：雪花 ID 64 位且具时间序，攻击者可枚举短窗口内的可能 ID 暴力命中。

### P0-5. 自助注册 / 找回密码安全弱

- **定位**：`SelfServiceController.register()` 第 80-85 行、`forgotPassword()` 第 101-106 行
- **对标**：Google / GitHub / 阿里 注册均强制图形验证码 + IP 限流 + 设备指纹 + 邮件双通道。
- **建议**：1) 在 `/register` / `/forgot-password` / `/send-verify-code` 前置强制图形验证码（复用 `CaptchaService`）；2) 限流改为按 IP 维度（`@RateLimit(resource="userinfo.selfservice.register", key="#request.remoteAddr", threshold=3, window=300)`）；3) 加 `@Idempotent(key="ydsz:userinfo:selfservice:register", ttlSeconds=10)`；4) 增加邮件验证码通道作为手机号备份。
- **影响**：当前可被分布式代理池暴力注册或撞库找回密码。

### P0-6. InternalApiController 缺内部接口鉴权标记

- **定位**：`InternalApiController` 类第 69-74 行，仅靠网关白名单"口头约束"
- **对标**：Spring Security 推荐 `@PreAuthorize("hasRole('INTERNAL')")` 或自定义 `@RequireInternal` 注解 + 拦截器；Spring Cloud Gateway 推荐在网关外额外加服务端二次校验。
- **建议**：1) 定义 `@RequireInternal` 注解 + `RequireInternalAspect`，校验请求头 `X-Internal-Call: true` + 共享密钥签名；2) `InternalApiController` 类级加 `@RequireInternal`；3) 网关层白名单 + 服务层注解双重防护。
- **影响**：网关配置错误时所有内部端点暴露给公网。

### P0-7. batch-names Controller 层缺 @Size 校验

- **定位**：`InternalApiController.batchUserNames()` 第 251 行 `@RequestBody List<String> userIds`、`batchDeptNames()` / `batchRoleNames()` / `batchPostNames()` / `batchCompanyNames()` 同样问题
- **对标**：JSR 380（Bean Validation 2.0）规范，Spring MVC 推荐用 `@Size(max=500)` 在 Controller 入口拦截。
- **建议**：改为 `@RequestBody @Size(max = 500) List<String> userIds`，超限直接 400 Bad Request。
- **影响**：当前依赖 Service 层截断，攻击者传 10 万 ID 会先全量入内存再被分批，OOM 风险。

### P0-8. UserInfoMetrics Redis DECR 下溢

- **定位**：`UserInfoMetrics.recordLogout()` 第 103-108 行
  ```java
  public void recordLogout() {
    try {
      redisStringOps.decr(SESSION_TOTAL_KEY, 1L);
    } catch (Exception e) { ... }
  }
  ```
- **对标**：业界计数器均用 Lua 脚本保证 `if v > 0 then decr end`。
- **建议**：改为 Lua 脚本 `local v = tonumber(redis.call('GET', KEYS[1]) or '0'); if v > 0 then return redis.call('DECR', KEYS[1]) else return 0 end`；同时在 `recordLogout` 内补 `incrementCounter("logouts_total", "result", "success")`。
- **影响**：登出计数失真，Gauge 出现负值。

### P0-9. UserAccountServiceImpl 批量操作 N+1

- **定位**：`UserAccountServiceImpl.batchRemoveByIds()` 第 483-499、`batchEnable()` 508-527、`batchDisable()` 536-557 行
- **对标**：MyBatis-Plus 提供 `updateBatchById` / 自定义 `UPDATE ... SET status = ? WHERE id IN (...)` 批量 SQL。
- **建议**：1) `batchEnable` / `batchDisable` 改为单条 `UPDATE ydsz_user_account SET status = ? WHERE id IN (...)`；2) `batchRemoveByIds` 改为 `UPDATE ... SET deleted = 1 WHERE id IN (...)`；3) 索引同步改为异步（事件总线批量提交）；4) 事件发布改为聚合（一次发布 `UserBatchUpdatedEvent` 含 ID 列表）。
- **影响**：批量 100 用户当前 400 次方法调用，优化后 1 次 SQL + 1 次事件。

### P0-10. AuthServiceImpl 风险 HIGH 时 captcha + MFA 双触发

- **定位**：`AuthServiceImpl.login()` 第 108-109 行
  ```java
  validateCaptchaIfEnabled(loginDTO, risk);
  validateMfaIfRequired(user, loginDTO, risk);
  ```
  `validateCaptchaIfEnabled` 第 160-161 行：`forceCaptcha = risk != null && risk.requiresAdditionalVerification() && !risk.shouldReject()`，而 `requiresAdditionalVerification()` 含 MEDIUM/HIGH，因此 HIGH 时既要求 captcha 又要求 MFA。
- **对标**：Google / 阿里 风险 HIGH 时仅触发 MFA（更强因素替代弱因素）。
- **建议**：`validateCaptchaIfEnabled` 修改判定为 `risk.level() == MEDIUM`，HIGH 直接走 MFA 路径不再要求 captcha。
- **影响**：HIGH 风险下用户体验差，正常异地登录被双重拦截。

---

## 四、P1 规划交付清单（10 项，预计 2-3 个月）

### P1-1. 风控扩维（差距 -25 分）

- **当前**：`RiskScoringService` 4 维度（IP 30% / 时间 20% / 设备 25% / 频率 25%），权重硬编码。
- **对标**：Google Risk Engine 含 100+ 维度；阿里人机识别含设备指纹 + 行为流；腾讯天御含异地 + 设备首次 + 异常时段组合。
- **建议**：1) 引入 IP 地理位置（GeoIP 数据库或 `ydsz-common-geo` 自研封装，禁止第三方 GeoIP API）；2) 用户行为基线（常用 IP 段、常用 6 小时窗口、常用设备指纹）；3) 设备指纹增强（canvas + 字体 + WebGL，自研 `DeviceFingerprintService`）；4) 权重配置化（`ydsz.userinfo.risk.weights.*`）；5) Tor/VPN 出口 IP 库（可自建离线库，禁止外网 API）。
- **影响**：差距 25 → 10。

### P1-2. 多租户物理隔离（差距 -35 分）

- **当前**：`UserAccountDO.tenantId` 字段存在但无上下文传播、无 schema 隔离。
- **对标**：阿里 RAM 多租户含租户管理员、资源配额、操作审计按租户隔离；SaaS 多租户标准含 schema-per-tenant 或 row-level + RLS。
- **建议**：1) 自研 `TenantContext`（ThreadLocal + Request Scope 传播）；2) 网关注入 `X-Tenant-Id` header；3) `UserAccountMapper` 加 `@TenantFilter` 拦截 SQL 自动追加 `WHERE tenant_id = ?`；4) `UserAccountDO` 增 `tenantId` 唯一索引（`uk_username_tenant`）；5) 关键表（`ydsz_user_account` / `ydsz_role` / `ydsz_menu`）按租户隔离；6) 租户管理员角色（`TENANT_ADMIN`）只能管本租户用户。
- **影响**：差距 35 → 15（先做逻辑 + 上下文传播，schema 隔离作为后续 P1 升级）。

### P1-3. OAuth2 scope / audience 细粒度

- **当前**：`OAuth2Controller.token()` 第 318 行写死 `"scope": "read write"`。
- **对标**：Spring Authorization Server 支持 scope-per-client 注册 + audience 校验；Keycloak 支持 scope-to-role 映射。
- **建议**：1) `UserInfoProperties.OAuth2Client` 增 `allowedScopes: Set<String>` 与 `allowedAudiences: Set<String>`；2) `/authorize` 接收 `scope` 参数并校验；3) `/token` 响应中 `scope` 反映实际授权范围；4) JWT claims 增 `scope` / `aud` 字段；5) 网关 `@PreAuthorize("hasAuthority('OP_USER_READ')")` 细粒度鉴权。
- **影响**：差距 10 → 3。

### P1-4. refresh_token 重用检测 + 链式撤销

- **当前**：`AuthServiceImpl.refresh()` 第 349 行仅 addToBlacklist 旧 token。
- **对标**：RFC 6819 §5.2.2.3 强烈建议检测重用并撤销整个 token 家族。
- **建议**：1) Redis 维护 `refresh_token_family:{familyId}` Set，记录该家族所有 refresh_token；2) refresh 时检测旧 token 是否已在黑名单——若是则判定为重用，立即撤销该家族全部 token；3) 通知用户（邮件 / 短信）。
- **影响**：差距 -10 → -3。

### P1-5. RBAC 本地二级缓存

- **当前**：`UserInfoRbacService.loadUserInfo()` 第 52 行每次 `redisHashOps.hGetAll`。
- **对标**：网关层每条请求都查 UserInfo，Redis 热点；业界主流用 Caffeine 二级缓存（短 TTL）。
- **建议**：使用 `ydsz-common-cache`（云顶规范禁止直接 Caffeine），配置 `caffeine二级缓存 TTL=5s, maxSize=10000`；登出 / 角色变更时 evict。
- **影响**：Redis QPS 降 60-80%。

### P1-6. UserAccountDO 增乐观锁 @Version

- **当前**：`UserAccountServiceImpl.update()` 第 181-194 行 lost update 风险。
- **对标**：MyBatis-Plus `@Version` 标准实践；Hibernate Optimistic Lock。
- **建议**：1) `UserAccountDO` 增 `@Version private Integer version;`；2) DB 加 `version INT NOT NULL DEFAULT 0` 列；3) `UserAccountRepository.update` 走 MP `updateById` 自动带 version；4) 失败抛 `OptimisticLockException`，前端提示重试。
- **影响**：消除 lost update。

### P1-7. assignRoles 并发覆盖

- **当前**：`UserAccountServiceImpl.assignRoles()` 第 292-322 行 delete + batchInsert 无锁。
- **对标**：分布式锁（Redisson）+ CAS 是业界标准。
- **建议**：使用 `ydsz-common-lock`（自研分布式锁，禁止直接 Redisson）的 `@DistributedLock(key = "'assignRoles:' + #userId", waitTime = 3, leaseTime = 10)` 注解。
- **影响**：消除并发丢角色。

### P1-8. SensitiveOperationAspect 分级 + 审计落盘

- **当前**：`SensitiveOperationAspect` 第 47-60 行所有 `@SensitiveOperation` 一视同仁，仅 `log.warn`。
- **对标**：分级（CRITICAL/HIGH/MEDIUM）+ 审计落盘是金融级标准。
- **建议**：1) `@SensitiveOperation` 增 `level() default SensitiveLevel.HIGH`；2) 切面按级别差异化校验（CRITICAL 要求 MFA + 邮件二次确认，HIGH 仅 MFA，MEDIUM 仅密码二次确认）；3) 拦截 / 通过均调用 `AuditService.record()` 落 `sys_audit_log`。
- **影响**：用户体验 + 安全审计闭环。

### P1-9. 单设备登录限制

- **当前**：`userinfo:session:user:{userId}` 无上限 Set。
- **对标**：钉钉 / 飞书 / 企业微信 均限制最多 N 端同时在线（默认 3-5）。
- **建议**：1) 配置 `ydsz.userinfo.max-sessions-per-user = 5`；2) `SessionManager.createSession` 创建时检查 Set 大小，超限自动踢出最旧会话；3) 前端提示"您的账号在其他设备登录，本设备已下线"。
- **影响**：会话治理差距 -5 → 0。

### P1-10. 链路追踪 traceId 贯穿

- **当前**：`UserInfoMetrics` 有指标但无 trace 关联。
- **对标**：OpenTelemetry / Spring Cloud Sleuth 标准。
- **建议**：使用 `ydsz-common-trace`（自研 trace 组件，禁止直接 Sleuth），在审计 / 日志 / 响应头 `X-Trace-Id` 全链路贯穿；UserInfoMetrics 的 tags 增 `traceId` 标签。
- **影响**：可观测差距 -10 → 0。

---

## 五、P2 顺手收口清单（8 项，预计 1-2 周）

| # | 定位 | 建议 |
|---|---|---|
| P2-1 | `OAuth2Controller` 5 端点 | 全部加 `@RateLimit(resource="userinfo.oauth2.xxx", threshold=20)` + `@Audit` |
| P2-2 | `InternalApiController` 路径 | `/RoleDO/batch-names` → `/roles/batch-names`，`/PostDO/batch-names` → `/posts/batch-names`，`/CompanyDO/batch-names` → `/companies/batch-names`，`/user/list-by-RoleDO` → `/users/by-role`，`/user/RoleDO-codes` → `/users/role-codes` |
| P2-3 | `UserInfoMetrics.recordLogout` | 增 `incrementCounter("logouts_total", "result", "success")` |
| P2-4 | `PasswordPolicyValidator` | 增键盘序列检测（qwert / asdf / 12345 / abcd）+ 常见字典词检测（自研离线词库，禁止外网 HIBP） |
| P2-5 | `UserInfoRbacService.loadUserInfoMap` | Redis Hash 增 `schemaVersion` 字段，反序列化时校验兼容性 |
| P2-6 | `AuthController.extractClientIp` | 增 trusted proxy 白名单配置，未配置代理时不读取 `X-Forwarded-For` |
| P2-7 | `InternalApiController` | 各端点加 `@RateLimit(threshold=200)` 宽松限流，防单服务雪崩 |
| P2-8 | README doc drift 修正 | 见下一节清单 |

---

## 六、文档漂移清单（doc drift）

> 基于 README 与最新代码交叉核对

| README 描述 | 实际代码 | 处理 |
|---|---|---|
| `ydsz-userinfo-domain/entity/` 14 个实体无 DO 后缀 | `ydsz-userinfo-infra/entity/` 14 个 *DO.java（DO 后缀） | 修正 README 第 42-58 行分层结构图，反映 infra/entity 真实位置 |
| Controller 12 个 | Controller 14 个（多了 `UserProfileController`、`SelfServiceController`） | README 第 75-87 行 Controller 清单补 `UserProfileController.java`（`/api/v1/user/profile`）与 `SelfServiceController.java`（`/api/v1/self-service`） |
| `MfaService` 未在 README 提及 | `server/auth/MfaService.java` 存在（TOTP + 短信 MFA） | README 第 64-71 行 server 子目录补 `MfaService` / `SensitiveVerifyService` / `CredentialVerifier` / `AccountStatusGuard` / `SessionManager` / `RoleCacheService` / `DbRolePermissionLoader` 等拆分组件 |
| README 第 70 行 server 子目录 `search/` `event/` `health/` | 实际 search/event 子目录存在文件，但 Glob 未列出 health/ | 核实 health/ 是否真实存在；若不存在应删除 README 描述 |
| README 第 200-219 行配置项表 | bootstrap.yml 实际还有 `ydsz.userinfo.riskWindowSeconds` / `ydsz.userinfo.batchSizeLimit` / `ydsz.safe.ip-access.*` / `ydsz.safe.auto-block.*` / `ydsz.safe.field-encryption.*` / `ydsz.web.api-version.*` / `ydsz.json.*` / `ydsz.jdbc.*` | 补全配置项表 |
| README 第 39 行 `OrgQueryClient 15 个方法` | 实际方法数未核对 | 需读取 `OrgQueryClient.java` 确认 |
| README 第 142 行 `Redis 计数器统一采集（P1-2/P1-5）` | 实际已实现 | 删除"待补"语义 |

---

## 七、暂缓项（依赖外网或重基础设施）

| # | 项 | 暂缓理由 |
|---|---|---|
| -1 | HIBP API 集成 | 外网依赖，与"最小依赖 + 绝对可控"理念冲突；可改为内网部署 Pwned Passwords 离线索引 |
| -2 | 字段级加密 → KMS 集成 | 内网 KMS 基础设施未就绪前，先按 P0-1 改 Nacos 加密配置 |
| -3 | ML 异常检测（行为基线建模） | 需要训练数据 + 模型服务，工作量大；先用 P1-1 规则扩维过渡 |
| -4 | 短信验证码多供应商容灾 | 需引入第二家短信通道，与最小依赖理念需评估 |

---

## 八、落地路线图

```
S1（1-2 周）  P0-1 ~ P0-10 立即收口（10 项）
              ├─ 第 1 周：P0-1/2/3/4（OAuth2 + 加密密钥，安全高危）
              ├─ 第 1 周：P0-5/6/7（自助服务 + InternalApi 鉴权 + batch Size）
              └─ 第 2 周：P0-8/9/10（Metrics + 批量 N+1 + 风险判定）

S2（2-3 月）  P1-1 ~ P1-10 规划交付（10 项）
              ├─ 第 1 月：P1-5/6/7/9（缓存 + 乐观锁 + assignRoles 锁 + 单设备）
              ├─ 第 2 月：P1-1/2（风控扩维 + 多租户上下文）
              └─ 第 3 月：P1-3/4/8/10（OAuth2 scope + 重用检测 + 敏感分级 + trace）

S3（1-2 周）  P2-1 ~ P2-8 顺手收口（8 项）

S4（视情况）  暂缓项按基础设施就绪情况启动
```

---

## 九、云顶规范符合性核查

| 规范条款 | ydsz-userinfo 现状 | 结论 |
|---|---|---|
| 禁止第三方 JSON，必须 `ydsz-common-json` | `OAuth2Controller` 使用 `YdszJson.toJson` / `YdszJson.parseMap` | ✅ 合规 |
| 禁止直接 Caffeine，必须 `ydsz-common-cache` | 暂未引入二级缓存，P1-5 已规划走 `ydsz-common-cache` | ✅ 合规（待补） |
| 禁止直接 POI，必须自研 Excel 组件 | `UserExcelService` 存在，需核实是否走自研 POI 组件 | 待核实 |
| 公共层 L1 为四个 utility 模块 | `UserAccountDO` 使用 `MpBaseEntity`（来自 `ydsz-common-jdbc`） | ✅ 合规 |
| 鉴权走 `common-auth` | `UserInfoApplication` 加 `@EnableYdszAuth`；`UserInfoRbacService` 实现 `RbacUserInfoService` SPI | ✅ 合规 |
| 审计走 `common-audit` | `UserInfoApplication` 加 `@EnableYdszAudit`；Controller 用 `@Audit` 注解 | ✅ 合规 |
| 限流走 `common-safe` | `UserInfoApplication` 加 `@EnableYdszSafe`；用 `@RateLimit` 注解 | ✅ 合规 |
| 雪花 ID 走 `common-util` | `OAuth2Controller` 注入 `SnowflakeIdGenerator` | ✅ 合规 |
| 字段级加密走 `common-safe` | `UserAccountDO.realName` 用 `@EncryptField` + `EncryptTypeHandler` | ✅ 合规 |

---

## 十、结语

ydsz-userinfo 整体设计水准在国内自研 IAM 中属于**上游水平**——DDD 五层清晰、富领域模型、P0-5 拆分到位、字段级加密 + 风险评分 + OAuth2 全套端点均落地，且严格遵循云顶规范（自研组件优先、最小依赖）。

**核心短板集中在三个方向**：
1. **风控维度单薄**（差距 25 分）：仅 4 维度硬编码，需扩维 + 配置化 + 行为基线。
2. **多租户仅逻辑隔离**（差距 35 分）：tenantId 字段存在但无上下文传播与隔离。
3. **OAuth2 安全细节漏点**（差距 10 分）：redirect_uri 空白名单绕过、授权码并发重放、雪花 ID 强度不足、scope 写死——这 4 项是 P0 安全高危，应立即收口。

按 P0 → P1 → P2 顺序推进，预计 3-4 个月可将 ydsz-userinfo 从 78 分（综合评分）提升至 88+ 分，达到业界大厂 IAM 主流水平。

---

> 报告基于 ydsz-cloud 仓库 ydsz-userinfo 模块最新代码生成，所有问题定位均附文件路径与行号，可直接据此提交修复。
> 生成时间：2026-08-19
