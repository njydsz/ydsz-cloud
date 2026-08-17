# ydsz-userinfo 模块代码审查与优化建议

> 审查日期：2026-08-17
> 审查范围：ydsz-userinfo 全部 5 个子模块（api / domain / infra / server / web）
> 对标对象：互联网大厂研发规范（阿里、字节、美团）、Spring Boot 官方最佳实践、OWASP 安全标准

---

## 一、架构优化

### A. 优点（值得保持的设计）

1. **DDD 分层清晰**：domain / infra / server / web / api 五层职责分明，domain 定义实体与枚举，infra 仅做 Mapper，server 编排业务，web 暴露 REST，api 发布 Feign Client。
2. **领域行为内聚**：`UserAccount` 实体内置 `recordLoginFailure` / `recordLoginSuccess` / `enable` / `disable` / `canAuthenticate` 等领域方法，符合 DDD 充血模型。
3. **异常码迁移到位**：`UserInfoExceptionCode` 已实现 `ExceptionCode` 接口并注册 `@YdszExceptionCode`，走全局 `ErrorCodeTable` 注册表，支持 i18n + HTTP 状态码精确控制。
4. **横切关注点统一**：审计 (`@Audit`)、限流 (`@RateLimit`)、幂等 (`@Idempotent`)、数据权限 (`@DataScope`) 全部注解化，无散落的硬编码。
5. **领域事件解耦**：`UserDomainEventPublisher` 封装事件发布，走 Outbox 模式投递到 common-event。

### B. 架构问题与优化建议

#### B-1. domain 模块依赖过重

**现状**：`ydsz-userinfo-domain` 引入了 `ydsz-common-jdbc`、`ydsz-common-excel`、`ydsz-common-domain`、`ydsz-common-safe`、`ydsz-common-event`、`ydsz-common-json`、`spring-security-crypto`、`mapstruct` 等 8+ 个依赖。其中 `ydsz-common-jdbc`（含 MpBaseEntity）和 `spring-security-crypto` 属于基础设施关注点，不应出现在纯 domain 层。

**对标**：阿里《Java 开发手册》规定 domain 层只做业务建模，不依赖框架级组件。

**建议**：
- 将 `MpBaseEntity` 下沉为 domain 层的独立 BaseEntity（仅含 id / deleted / revision / tenantId / createdBy / createdAt / updatedBy / updatedAt 字段），解除对 common-jdbc 的强依赖。
- `spring-security-crypto`（BCrypt）仅被 `PasswordPolicyValidator` 间接使用，可上移到 server 层。
- `mapstruct` 的 `@Mapping` 注解在 domain 实体上出现，建议将 MapStruct 接口集中到 server 层，domain 层仅保留纯 POJO + 领域方法。

#### B-2. server 层直接引用 Mapper（跳过 Repository 抽象）

**现状**：`UserAccountServiceImpl`、`RoleServiceImpl`、`DepartmentServiceImpl` 均直接注入 `XxxMapper`，Service 与 MyBatis Plus 持久化框架强耦合。

**对标**：DDD 经典模式（Eric Evans）建议在 Domain 层定义 Repository 接口、Infra 层提供实现。

**建议**：
- 在 domain 层定义 `UserRepository`、`RoleRepository`、`DepartmentRepository` 等接口。
- 在 infra 层提供 `UserRepositoryImpl`（内部委托 Mapper）。
- server 层仅依赖 Repository 接口，便于：
  - 单元测试 mock
  - 未来切换 JPA / MongoDB 等持久化实现
  - 消除 Service 对 MyBatis Plus `LambdaQueryWrapper` 的直接依赖

#### B-3. Controller 层缺少统一异常处理

**现状**：`UserAccountController.importUsers` 和 `exportUsers` 内部 try-catch 后返回 `BaseResponse.error("导入失败: " + e.getMessage())`，将异常细节暴露给前端。

**对标**：Spring `@RestControllerAdvice` + `BaseGlobalResponseAdvice`（ydsz-common-web 已提供）。

**建议**：
- 删除 Controller 内部的 try-catch，让异常直接抛到全局异常处理器。
- `BaseResponse.error(String message)` 方法应标记 `@Deprecated`，引导使用异常抛出方式。
- 全局异常处理器统一将 `BusinessException` 转换为标准 HTTP 响应，避免 `e.getMessage()` 泄露堆栈细节。

#### B-4. 缺少 API 版本管理策略落地

**现状**：`@ApiVersion("1")` 注解已标注，`bootstrap.yml` 配置了 `ydsz.web.api-version.strategy: HEADER`，但未见版本协商、弃用通知、多版本共存的完整策略。

**对标**：Google API Versioning Guide、Stripe API Versioning。

**建议**：
- 在 `sunset-headers` 已启用基础上，补充 `Deprecation` 响应头（RFC 8594）。
- 定义版本生命周期策略：Beta → GA → Deprecated → Sunset，每个阶段有明确的时间窗口。
- 在 OpenAPI 文档中通过 `@Deprecated` 注解标记即将下线的端点。

---

## 二、功能增强

### A. 已具备的优秀功能

1. **多因子认证预留**：`MFA_REQUIRED` / `MFA_INVALID` / `MFA_NOT_BOUND` 异常码已定义，`RiskScoringService` 的 `requiresAdditionalVerification()` 方法已识别 MEDIUM / HIGH 风险等级，为 MFA 接入预留了扩展点。
2. **OAuth2 + PKCE 完整实现**：`OAuth2Controller` 实现了 RFC 6749 授权码模式 + RFC 7636 PKCE 扩展，支持 confidential / public 双客户端类型。
3. **LDAP 集成**：`LdapAuthenticationProvider` 通过 `@ConditionalOnProperty` 按需启用，不影响主认证链路。
4. **风险评分**：`RiskScoringService` 从 IP / 时间 / 设备 / 频率四维度评估登录风险，CRITICAL 级别直接拒绝。
5. **弱密码字典**：`WeakPasswordDictionary` 加载 `weak-passwords.txt`，防止 Top 1000 常见密码。
6. **密码历史**：`UserPasswordHistoryService` 防止最近 N 次密码重复使用。

### B. 功能缺失与增强建议

#### B-1. 缺少账号自助注册与找回密码

**现状**：仅有管理员创建用户（`POST /api/v1/user`）和管理员重置密码（`POST /api/v1/user/reset-password`），无用户自助注册、邮箱/手机验证码找回密码流程。

**对标**：Auth0、Keycloak、Spring Security 标准流程。

**建议**：
- 新增 `POST /api/v1/auth/register`：邮箱/手机 + 验证码注册。
- 新增 `POST /api/v1/auth/forgot-password`：发送重置链接/验证码到邮箱或手机。
- 新增 `POST /api/v1/auth/reset-password-by-token`：通过 token 重置密码。
- 引入 `ydsz-common-notify` 模块发送邮件/短信通知。

#### B-2. 缺少会话管理与强制下线

**现状**：`AuthServiceImpl.kickOutUser` 已实现驱逐全部会话，但无"查询当前用户所有活跃会话"的接口。

**对标**：Spring Session、企业 IM 的"设备管理"功能。

**建议**：
- 新增 `GET /api/v1/auth/sessions`：返回当前用户所有活跃会话（设备、IP、登录时间）。
- 新增 `DELETE /api/v1/auth/sessions/{token}`：下线指定设备。
- 新增 `POST /api/v1/auth/kick-out/{userId}`：管理员强制下线某用户（已部分实现，需暴露为管理接口）。

#### B-3. 缺少操作日志查询

**现状**：`@Audit` 注解已记录操作日志，但无查询接口供管理员查看。

**建议**：
- 新增 `GET /api/v1/audit/logs`：按模块 / 用户 / 时间范围查询审计日志。
- 审计日志异步写入 Elasticsearch，支持全文检索。

#### B-4. 缺少数据导入事务一致性

**现状**：`UserExcelServiceImpl.importUsers` 逐行导入，单条失败不影响其他行（部分成功模式）。但导入过程无事务包裹，可能出现"部分用户已创建、部分失败"的中间状态，且已创建的用户无法自动回滚。

**对标**：Spring Batch、EasyExcel 的批量处理模式。

**建议**：
- 方案 A（推荐）：使用 Spring Batch 实现分块处理（chunk-size=50），每个 chunk 一个事务，失败时仅回滚当前 chunk。
- 方案 B：提供"预览导入"模式（dry-run），先校验全部数据，校验通过后再执行实际导入。
- 方案 C：导入前创建临时表，全部导入成功后一次性转存到正式表。
- 新增导入任务状态跟踪：`GET /api/v1/user/import/{taskId}/status`，支持异步导入大文件。

#### B-5. 缺少用户资料完整功能

**现状**：`UserAccount` 实体有 avatar 字段，但无上传头像接口；无用户偏好设置（语言、时区、主题）；无个人信息查看/编辑页面所需的接口。

**建议**：
- 新增 `POST /api/v1/user/avatar`：上传头像（集成 `ydsz-common-file` 到 OSS/MinIO）。
- 新增 `GET /api/v1/user/profile`：获取当前用户完整资料。
- 新增 `PUT /api/v1/user/profile`：修改当前用户资料（昵称、头像、语言偏好）。
- 新增 `PATCH /api/v1/user/settings`：用户偏好设置（时区、主题、通知偏好）。

#### B-6. 缺少批量操作异步化

**现状**：`batchRemoveByIds`、`batchEnable`、`batchDisable`、`importUsers`、`exportUsers` 均为同步执行，数据量大时可能超时。

**对标**：Spring Async、阿里云函数计算异步任务模式。

**建议**：
- 对大数据量操作（>100 条）改为异步任务模式，返回 taskId。
- 新增 `GET /api/v1/tasks/{taskId}`：查询任务进度（进度百分比、成功/失败数）。
- 引入 `ydsz-common-thread` 提供的 `ThreadPoolTaskExecutor` 统一管理异步线程池。

---

## 三、性能提升

### A. 已有的性能优化

1. **批查询替代 N+1**：`UserExcelServiceImpl` 预加载用户名/部门/上级映射，`NameAssembler` 一次 Feign 批量解析。
2. **多级缓存**：`DepartmentServiceImpl.tree()` 使用 L1（Caffeine）+ L2（Redis）两级缓存；`RoleServiceImpl.getRolePermissionIds` 使用 Redis 缓存。
3. **搜索索引同步**：通过 `SearchIndexEventBridge` 异步同步到搜索引擎（ES / OpenSearch）。
4. **MapStruct 编译期生成**：避免反射拷贝的性能开销。

### B. 性能瓶颈与优化建议

#### B-1. 用户分页查询未走缓存

**现状**：`UserAccountServiceImpl.page` 每次都查数据库构建 `Page<UserAccountVO>`，无缓存层。当用户量 > 10 万且分页查询频繁时，DB 压力较大。

**建议**：
- 对"全部用户列表"（`list()` 接口）增加 Redis 缓存，TTL 5 分钟，用户变更时主动失效。
- 对分页查询的"总记录数"缓存 30 秒，避免每次 `COUNT(*)`。
- 考虑引入 CQRS 模式：写模型走 DB，读模型走 ES（已通过 `SearchIndexEventBridge` 部分实现）。

#### B-2. 角色权限校验可能全量加载

**现状**：`AuthServiceImpl.loadUserRoles` 每次登录时执行 2 条 SQL（查 user_role + 查 role），无缓存。

**建议**：
- 将用户角色信息写入 Redis Hash（key: `userinfo:user:roles:{userId}`），TTL 与 token 对齐。
- 角色变更时通过领域事件 `publishRoleChanged` 主动失效缓存（已有事件发布，但未见缓存失效逻辑）。

#### B-3. `batchUserNames` 未限制批量大小

**现状**：`UserAccountServiceImpl.batchUserNames` 接受任意大小集合，无上限校验。若调用方传入万个 ID，将生成巨型 `IN (...)` 查询。

**建议**：
- 在 Service 层限制单次批量查询上限（建议 500），超出时自动分批执行。
- 对 Java 入口添加 `@Size(max = 500)` 校验。

#### B-4. 缺少数据库连接池与慢 SQL 监控集成

**现状**：`bootstrap.yml` 已配置 `ydsz.jdbc.slow-sql.threshold-millis: 500`，但未与 `UserInfoMetrics` 集成（慢 SQL 仅打 warn 日志，无 Micrometer 指标）。

**建议**：
- 在 `UserInfoMetrics` 新增 `recordSlowSql(sql, duration)` 方法，将慢 SQL 耗时分布纳入 Prometheus。
- 与 `ydsz-common-sentry` 集成，慢 SQL 超阈值时发送告警。

#### B-5. 缺少热点数据本地缓存

**现状**：部门树有 L1 + L2 缓存，但用户基本信息、角色信息、岗位信息等高频查询仅走 Redis。

**建议**：
- 对用户基本信息（`getById`）增加 Caffeine L1 缓存，TTL 2 分钟。
- 对角色列表、岗位列表等变更频率低的数据增加 L1 缓存。
- 通过 `UserInfoNameAssembler.evict()` 在数据变更时主动失效 L1。

---

## 四、体验改善

### A. 已有的良好体验

1. **OpenAPI 文档完善**：所有 Controller 方法都有 `@Operation` 注解，字段有 Javadoc 注释。
2. **统一的响应格式**：`BaseResponse<T>` / `PageResponse<T>` 全局统一。
3. **详细的 Javadoc**：类级别说明接口路径、安全特性、使用场景；方法级别说明业务流程、安全特性、参数含义。
4. **错误码分类清晰**：B30xxx（用户/认证）、B31xxx（组织架构）、B32xxx（RBAC）、A20xxx（安全认证），便于问题定位。

### B. 体验问题与改善建议

#### B-1. 错误消息未完全国际化

**现状**：`PasswordPolicyValidator` 直接抛出中文消息（"密码长度不能少于 8 个字符"），`UserExcelServiceImpl` 的 import 校验也使用中文硬编码（"用户名不能为空"）。

**对标**：Spring MessageSource + i18n properties 文件。

**建议**：
- 所有面向用户的消息必须走 `MessageSource`，禁止硬编码中文。
- 完善 `i18n/messages_zh_CN.properties` 和 `i18n/messages_en_US.properties`，覆盖所有异常码。
- 前端根据 Accept-Language 头自动切换语言。

#### B-2. OpenAPI 文档缺少示例与枚举说明

**现状**：`@Operation` 仅有 summary / description，缺少 `examples`；枚举字段（如 status）在 Swagger UI 中显示为 String，无下拉选项。

**建议**：
- 为关键字段（status、userType）添加 `@Schema(allowableValues = {"ENABLED", "DISABLED"})`。
- 为复杂 DTO 添加 `@Schema(example = "{...}")`。
- 在 `application.yml` 配置 `springdoc.swagger-ui.tags-sorter: alpha` 优化文档展示顺序。

#### B-3. 登录响应未返回 refreshToken

**现状**：`AuthServiceImpl.buildLoginResult` 中 `result.setRefreshToken(null)` 注释为 TODO，`LoginVO` 实现了 refresh token 的返回，但实际构建时被置空。

**建议**：
- 构建登录结果时传入 `refreshToken`：`result.setRefreshToken(refreshToken)`。
- 确保 `issueTokensAndCreateSession` 返回的 `refreshToken` 被正确传递。

#### B-4. 缺少接口耗时与 SLA 监控 Dashboard

**现状**：`UserInfoMetrics` 已记录 `auth_duration_ms` 的 P50/P90/P99，但无对应的 Grafana Dashboard 模板。

**建议**：
- 提供 `docs/grafana-userinfo-dashboard.json`（参考 `ydsz-workflow` 的 `grafana_dashboard.json`）。
- Dashboard 包含面板：登录 QPS、认证耗时分布、在线会话数、登录失败率、慢 SQL 数。

#### B-5. 缺少接口幂等键的前缀隔离

**现状**：`@Idempotent(key = "ydsz:userinfo:AuthController:login:lock")` 使用完整类名 + 方法名，导致 Redis Key 过长。且不同环境的 key 未隔离。

**建议**：
- 使用简化的业务语义 key（如 `idempotent:auth:login`）。
- 引入 profile 前缀（`dev:` / `sit:` / `prod:`）实现环境隔离。

---

## 五、过度设计

### A. 值得商榷的设计选择

#### A-1. 风险评分服务过于简化

**现状**：`RiskScoringService` 基于简单加权求和（IP 风险 + 时间异常 + 设备异常 + 频率异常 = 总分），阈值硬编码（30/60/80），且：
- "凌晨 0-6 点视为异常" 对全球化团队不合理（时区问题）
- "新设备" 判断依赖 User-Agent，容易误判（浏览器升级、隐私模式）
- 规则无法动态调整（无配置中心支持）

**建议**：
- 保留现有简单实现（作为 MVP），但预留接口未来接入 ML 模型。
- 至少将阈值和权重参数化到 `UserInfoProperties`。
- 时间异常判断应基于用户历史登录模式（而非固定时段）。

#### A-2. 验证码服务过度封装

**现状**：`CaptchaController` 和 `CaptchaService` 对 `easy-captcha` 库做了完整封装，支持图形验证码生成。但当前实现未支持：
- 行为验证码（滑块、点选）—— 仅在注释中提到"可选"
- 短信验证码
- 邮箱验证码

**建议**：
- 如果仅使用图形验证码，当前封装足够。
- 如需支持行为验证码，建议引入专业 SDK（如极验、腾讯防水），而非自研。
- 避免过度封装第三方库——如果只是透传调用，可直接使用 `easy-captcha` 的 API。

#### A-3. OAuth2 客户端配置存储在 YML

**现状**：`UserInfoProperties.OAuth2Client` 的 `clientSecret` 明文存储在 `bootstrap.yml`（`${OAUTH2_CLIENT_SECRET:default-secret}` 有默认值兜底）。

**对标**：Spring Authorization Server、HashiCorp Vault、AWS Secrets Manager。

**建议**：
- 生产环境必须通过 Vault / K8s Secret 注入 `clientSecret`，禁止 YML 默认值。
- 客户端元数据（clientSecret、redirectUris）建议持久化到数据库，支持运行时动态注册。

#### A-4. NameAssembler 多级缓存粒度过细

**现状**：`UserInfoNameAssembler` 为每个 ID 单独缓存（L1 + L2），缓存 key 数量 = 实体数 × 5 种类型。在 10 万用户场景下，缓存 key 可能达 50 万个。

**建议**：
- 坚持当前设计（实体级缓存）作为默认策略。
- 新增"批量预热"接口，在服务启动时预热热点数据（最近活跃用户）。
- 考虑使用 Redis Hash 结构（`userinfo:name:USER` → hash），将同一类型的名称聚合存储，减少 key 数量。

#### A-5. 领域事件可能过度使用

**现状**：`UserDomainEventPublisher` 为每个操作都发事件（create/update/delete/login/roleChanged/departmentChanged），但未见明确的消费者列表。如果事件无人消费，会造成不必要的开销。

**建议**：
- 梳理事件消费者清单，确认每个事件至少有一个消费者。
- 考虑合并细粒度事件为粗粒度事件（如 `USER_DATA_CHANGED`），减少事件类型数量。
- 事件数据负载最小化（当前实现较好，仅携带必要字段）。

---

## 六、安全加固

### A. 已有的安全措施

1. **密码 BCrypt 加密**（cost=10 可配置）
2. **字段级加密**（realName 使用 AES-256-GCM）
3. **账号锁定**（N 次失败后自动锁定 30 分钟，数据库原子自增）
4. **IP 封禁**（BRUTE_FORCE 事件驱动自动封禁）
5. **登录风险评估**（多维度评分 + CRITICAL 拒绝）
6. **OAuth2 PKCE 支持**（防授权码拦截）
7. **Token 轮换**（refresh token 一次性使用）
8. **会话驱逐**（改密/禁用时清理所有 token）

### B. 安全强化建议

#### B-1. 缺少密码喷洒（Password Spraying）防护

**现状**：当前仅防护单账号暴力破解（N 次失败锁定），未防护"用同一密码尝试多个账号"的喷洒攻击。

**对标**：OWASP Authentication Cheat Sheet、Azure AD Smart Lockout。

**建议**：
- 在 `RiskScoringService` 新增维度：检测同一 IP 对不同用户名使用相同密码的尝试模式。
- 引入"全局失败计数"：同一 IP 在 5 分钟内累计失败 20 次（不限账号），触发 IP 临时封禁。

#### B-2. JWT 无密钥轮换策略

**现状**：`TokenService` 未见密钥轮换配置。若 JWT 签名密钥泄露，需重启所有实例才能更换。

**建议**：
- 实现密钥版本管理（JWKS），支持 graceful rotation。
- 密钥存储走 Vault / Nacos 加密配置，禁止硬编码。

#### B-3. 敏感操作缺少二次认证

**现状**：管理员重置密码、删除用户、禁用账号等高危操作仅依赖登录 token，无二次确认。

**建议**：
- 对敏感操作（resetPassword / removeById / batchDisable）增加 TOTP / 短信验证码二次确认。
- 或至少要求重新验证当前密码（re-authentication）。

#### B-4. Token 黑名单持久化缺失

**现状**：Token 黑名单存储在 Redis，依赖 TTL 过期。若 Redis 重启，所有黑名单 token 失效，已登出的 token 可能被重新使用。

**建议**：
- 对高安全场景，黑名单持久化到 DB（`ydsz_token_blacklist` 表）。
- 或 Redis 开启 AOF 持久化。

#### B-5. 缺少安全响应头配置

**现状**：`bootstrap.yml` 未见安全响应头配置（X-Content-Type-Options、X-Frame-Options、Content-Security-Policy、Strict-Transport-Security）。

**建议**：
- 配置 Spring Security 的 `SecurityFilterChain` 添加安全头。
- 或使用 Gateway 层统一添加（推荐）。

---

## 七、可落地优化优先级矩阵

| 优先级 | 优化项 | 工作量 | 影响面 | 建议时间 |
|--------|--------|--------|--------|----------|
| **P0** | 修复 loginResult.refreshToken 未返回 | 0.5d | 高 | 本周 |
| **P0** | Controller 统一异常处理（删除内部 try-catch） | 1d | 高 | 本周 |
| **P0** | Token 黑名单持久化 / AOF | 0.5d | 高 | 本周 |
| **P1** | 接口耗时 Micrometer 指标完善 | 1d | 中 | 下周 |
| **P1** | batchUserNames 批量上限校验 | 0.5d | 中 | 下周 |
| **P1** | 用户角色信息 Redis 缓存 | 1d | 中 | 下周 |
| **P1** | 敏感操作二次认证 | 2d | 高 | 下周 |
| **P1** | 错误消息完整国际化 | 2d | 中 | 两周内 |
| **P2** | Repository 抽象层引入 | 3d | 中 | 下月 |
| **P2** | domain 模块依赖瘦身 | 2d | 低 | 下月 |
| **P2** | 自助注册 / 找回密码 | 3d | 高 | 下月 |
| **P2** | 会话管理接口 | 2d | 中 | 下月 |
| **P2** | 批量操作异步化 | 3d | 中 | 两月内 |
| **P3** | 密码喷洒防护 | 2d | 中 | 两月内 |
| **P3** | JWT 密钥轮换 | 2d | 低 | 两月内 |
| **P3** | API 版本生命周期管理 | 1d | 低 | 按需 |
| **P3** | Grafana Dashboard 模板 | 1d | 低 | 按需 |

---

## 八、总结

ydsz-userinfo 模块在 DDD 分层、安全设计、风险防控、RPC 暴露等方面已经做得相当扎实，代码质量、注释完整度、异常处理规范性均达到生产级水准。

**核心优势**：
- 安全纵深防御体系完整（BCrypt + 字段加密 + IP 封禁 + 风险评分 + 验证码）
- DDD 分层清晰，领域行为内聚
- 横切关注点注解化，无散落逻辑
- Feign 回退、多级缓存、异步搜索同步等性能优化到位

**主要改进方向**：
- 补充用户端自助功能（注册、找回密码、个人中心）
- 引入 Repository 抽象降低层间耦合
- 完善安全二次认证与密钥轮换
- 批量操作异步化与任务进度跟踪
- 接口指标可观测性与 SLA 监控</longcat_think>
