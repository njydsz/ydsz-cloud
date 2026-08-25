# ydsz-userinfo

> 用户信息中心（User Info Center）— 登录认证 / RBAC / 组织架构 / OAuth2 / OIDC / SAML / CAS / SCIM / WebAuthn

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动，双入口架构） |
| **端口** | **9002**（Web 端，按构建顺序 3/10）/ **9003**（App 端） |
| **服务名** | `ydsz-userinfo`（Web）/ `ydsz-userinfo-app`（App） |
| **构建顺序** | 3/10 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis、Gateway |
| **公共依赖** | common-core / common-web / common-auth / common-redis / common-safe / common-jdbc / common-audit / common-exception / common-domain / common-event / common-json / common-util / common-lock / common-search / common-cache / common-excel / common-sentry / common-thread / common-feign / common-app |

> **端口提示**：本模块移动端入口 `ydsz-userinfo-app` 默认 `9003`，与 `ydsz-nextwiki-web`（网盘 Web 控制台，同为 `9003`）默认端口相同。两者通常不会同机部署（移动端入口 vs Web 控制台），若需同机运行，须通过 Nacos `ydsz-userinfo-{env}.yaml` / `ydsz-nextwiki-{env}.yaml` 将其中一个改为其他端口。

## 核心职责

| 业务域 | 说明 |
|---|---|
| **登录认证** | 账号密码 + 图形验证码 + LDAP/ADFS 域认证 + 社交登录（钉钉/企微/飞书） |
| **Token 管理** | JWT 签发 / 刷新 / 失效（common-auth TokenService）+ Token 自动续签 |
| **账号安全** | 登录失败计数 + 自动锁定（5 次失败锁 30 分钟）+ 密码策略校验 + 弱口令字典 + 风险评分 + 密码历史（防重用）|
| **RBAC** | 用户 / 角色 / 权限 6 要素（用户-角色-权限关联表精确查询） |
| **组织架构** | 部门树形结构 + 公司 + 岗位 |
| **菜单权限** | 菜单树 + 按钮/API 权限码分类 |
| **OAuth2** | 授权码模式（authorize + token 端点）+ PKCE + 应用注册管理 |
| **OIDC** | OpenID Connect Discovery 1.0 标准端点（/.well-known/openid-configuration + JWKS） |
| **SAML 2.0** | Service Provider 端点 + IdP 配置管理（多租户 IdP 路由） |
| **CAS** | CAS 协议端点（单点登录/登出） |
| **SCIM 2.0** | 用户供给协议（RFC 7643/7644），支持 HR 系统用户数据同步 |
| **WebAuthn** | 无密码认证（Passkey / FIDO2） |
| **登录历史** | 登录/密码变更历史（`ydsz_user_login_history` / `ydsz_user_password_history`） |
| **安全告警** | 暴力破解/密码喷洒检测 + 安全事件告警 |
| **国际化** | 语言 CRUD（`ydsz_language`） |
| **全局搜索** | `UserinfoSearchController` 基于用户/部门/角色维度 |

## DDD 分层结构

```
ydsz-userinfo/
├── pom.xml
├── ydsz-userinfo-api/                 # API 层：Feign Client + Fallback + Assembler
│   └── src/main/java/com/njydsz/userinfo/api/
│       ├── client/                    # OrgQueryClient（15 个方法）
│       ├── fallback/                  # Feign 降级实现
│       └── assembler/                 # UserInfoNameAssembler（跨模块 VO 富化）
├── ydsz-userinfo-domain/              # 领域层：Repository 接口 + DTO + VO + Query + Event + Enum
│   └── src/main/java/com/njydsz/userinfo/domain/
│       ├── repository/                # Repository 接口（19 个，DDD 仓储契约，返回 VO）
│       ├── dto/                       # 数据传输对象（33 个，含 LoginDTO / UserAccountDTO / ChangePasswordDTO 等）
│       ├── query/                     # 分页查询对象（10 个）
│       ├── vo/                        # 视图对象（40 个，含 TreeVO / SecurityDashboardVO / UserSessionStatistics 等）
│       ├── enums/                     # 领域枚举（6 个：EnableStatusEnum / BanType / DeviceType / IdentityProviderType / UserInfoExceptionCode / UserLifecycleStatusEnum）
│       ├── event/                     # 领域事件
│       │   ├── UserDomainEvent / UserDomainEventType / UserAuthEventListener
│       │   └── auth/                  # 认证事件（12 个：LoginSuccessEvent / LoginFailedEvent / AccountLockedEvent / MfaTriggeredEvent 等）
│       ├── alert/                     # 安全告警（SecurityAlert + SecurityAlertRepository）
│       ├── auth/                      # UserIdentityProvider
│       ├── config/                    # MfaSecretEncryptor / SocialAuthProperties
│       ├── oauth2/                    # OAuth2Application + OAuth2ApplicationRepository
│       ├── scim/                      # SCIM 2.0 协议对象（ScimUser / ScimPatchOp / ScimListResponse 等）
│       └── social/                    # SocialAuthProvider / SocialUserInfo / SocialAccessToken
├── ydsz-userinfo-infra/               # 基础设施层：Mapper + Repository 实现 + 实体 DO + Converter + Social
│   └── src/main/java/com/njydsz/userinfo/infra/
│       ├── entity/                    # 持久化实体 DO（21 个，DO 后缀，对应 ydsz_* 表）
│       ├── mapper/                    # MyBatis-Plus Mapper（21 个）
│       ├── repository/                # Repository 实现（Converter DO↔VO 转换）
│       ├── converter/                 # MapStruct 转换器（9 个）
│       ├── config/                    # AesMfaSecretEncryptor / PlainMfaSecretEncryptor
│       └── social/                    # 社交登录提供者（Abstract + DingTalk + EnterpriseWechat + Feishu + JustAuthHttpClient）
├── ydsz-userinfo-server/              # 应用层：Service + Auth + Config + Aspect + Metrics + Trace + Alert + Device + SSE + OAuth2
│   └── src/main/java/com/njydsz/userinfo/server/
│       ├── auth/                      # 认证/风控（30+ 个类）
│       ├── config/                    # 配置类（20+ 个）
│       ├── service/                   # 19 个 Service 接口 + impl（14 个实现类）
│       ├── alert/                     # 安全告警（SecurityAlertService + AggregationTask + NotificationChannel）
│       ├── aspect/                    # 切面（SecondaryAuthAspect / SensitiveOperationAspect）
│       ├── device/                    # 设备会话（DeviceSessionService + DeviceSessionVO）
│       ├── dto/                       # UserImportDTO
│       ├── event/                     # 事件分发（Dispatcher + Publisher + listener/）
│       ├── health/                   # UserInfoHealthIndicator
│       ├── metrics/                  # UserInfoMetrics（SentryMetricsAdapter）
│       ├── oauth2/                   # OAuth2 应用服务（OAuth2ApplicationService + OAuthCodeContext）
│       ├── search/                   # UserinfoSearchProvider
│       ├── social/                   # 社交登录提供者注册表（AutoConfiguration + Registry）
│       ├── sse/                      # SSE 实时推送（SseAuthEventListener + SseEmitterRegistry）
│       └── trace/                    # TraceContext（轻量链路追踪）
├── ydsz-userinfo-web/                 # Web 层：Controller + Filter + Bootstrap（端口 9002）
│   └── src/main/java/com/njydsz/userinfo/web/
│       ├── UserInfoApplication.java
│       ├── annotation/                # RequireInternal（内部接口标记注解）
│       ├── aspect/                    # RequireInternalAspect（内部调用校验切面）
│       ├── dto/                       # RefreshRequest / SecondaryAuthRequest
│       ├── vo/                        # JwksEndpoint / OidcDiscoveryEndpoint
│       ├── filter/                    # 7 个过滤器
│       └── controller/                # 32 个 Controller
└── ydsz-userinfo-app/                 # App 层：移动端/应用端 API（端口 9003）
    └── src/main/java/com/njydsz/userinfo/app/
        ├── UserInfoAppApplication.java
        ├── config/                    # AppAutoConfiguration / ConditionalOnPlatform / PlatformCondition / UserInfoAppAutoConfiguration
        ├── health/                   # AppHealthIndicator
        └── openapi/                  # AppOpenApiConfiguration / UserInfoAppOpenApiConfiguration
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/auth/login` `/logout` `/refresh` | 登录/登出/Token 刷新 |
| `/api/v1/user` | 用户 CRUD + 分页 + 密码管理 + 角色分配 + 批量操作 + Excel 导入导出 |
| `/api/v1/user/{userId}/login-history` | 用户登录历史查询 |
| `/api/v1/profile` | 用户个人信息更新 |
| `/api/v1/Role` | 角色 CRUD + 权限分配 |
| `/api/v1/dept` | 部门 CRUD + 树形结构 |
| `/api/v1/Menu` | 菜单 CRUD + 树形结构 |
| `/api/v1/Company` | 公司 CRUD |
| `/api/v1/Post` | 岗位 CRUD |
| `/api/v1/Language` | 语言 CRUD |
| `/api/v1/captcha` | 图形验证码生成/校验 |
| `/api/v1/oauth2/authorize` `/token` | OAuth2 授权码模式（支持 PKCE + scope 细粒度） |
| `/api/v1/admin/oauth2/applications` | OAuth2 应用注册管理 |
| `/.well-known/openid-configuration` | OIDC Discovery 元数据文档 |
| `/.well-known/jwks.json` | JWKS 公钥集合 |
| `/saml` | SAML 2.0 Service Provider 端点 |
| `/api/v1/saml-idp-config` | SAML IdP 配置管理 |
| `/cas` | CAS 协议端点（单点登录/登出） |
| `/scim/v2` | SCIM 2.0 用户供给（RFC 7643/7644） |
| `/api/v1/webauthn` | WebAuthn/Passkey 无密码认证 |
| `/api/v1/profile/social` | 社交账号关联管理 |
| `/api/v1/social-client-config` | 社交登录客户端配置 |
| `/api/v1/auth-policy` | 认证策略管理 |
| `/api/v1/admin/security/alerts` | 安全告警管理 |
| `/api/v1/admin/security` | 安全仪表盘 |
| `/api/v1/admin` | 会话管理 + 账号封禁 |
| `/api/v1/devices` | 设备会话管理 |
| `/api/v1/sso/metrics` | SSO 指标（CAS/OAuth2/SAML/OIDC/社交登录/WebAuthn） |
| `/api/v1/sso` | 跨域 SSO 令牌交换 |
| `/api/v1/admin/ldap/sync` | LDAP 同步管理 |
| `/api/v1/userinfo/search` | 用户/部门/角色搜索 |
| `/api/v1/self-service` | 自助注册/找回密码/发送验证码（图形验证码 + IP 限流 + 幂等防护） |
| `/api/v1/auth/events` | SSE 认证事件实时推送（登录/MFA/会话驱逐/锁定等） |
| `/api/internal/*` | 内部 Feign 调用接口（15 个端点，`@RequireInternal` 服务端二次校验） |

## 数据库表设计

| 表名 | 说明 |
|---|---|
| `ydsz_user_account` | 用户账号（含登录失败计数/锁定时间/最后登录信息，realName AES-256-GCM 加密） |
| `ydsz_role` | 角色定义 |
| `ydsz_menu` | 菜单/按钮/API 权限定义 |
| `ydsz_user_role` | 用户-角色关联 |
| `ydsz_role_permission` | 角色-权限关联 |
| `ydsz_department` | 部门（树形结构） |
| `ydsz_company` | 公司 |
| `ydsz_company_dept` | 公司-部门关联 |
| `ydsz_post` | 岗位 |
| `ydsz_user_dept` | 用户-部门关联 |
| `ydsz_user_post` | 用户-岗位关联 |
| `ydsz_user_login_history` | 登录历史 |
| `ydsz_user_password_history` | 密码变更历史 |
| `ydsz_language` | 语言 |
| `ydsz_auth_policy` | 认证策略 |
| `ydsz_oauth2_application` | OAuth2 应用注册 |
| `ydsz_saml_idp_config` | SAML IdP 配置 |
| `ydsz_security_alert` | 安全告警 |
| `ydsz_social_account` | 社交账号关联 |
| `ydsz_social_client` | 社交登录客户端配置 |
| `ydsz_user_credential` | WebAuthn 凭证 |
| `sys_audit_log` | 审计日志（物理表在 common-audit，写操作经 @Audit 记录） |
| `ydsz_user_session` | 用户会话（Redis 存储，`userinfo:session:user:{userId}`） |

## 安全特性

| 特性 | 说明 |
|---|---|
| **密码加密** | BCrypt（PasswordEncoder，强度可配置，默认 10） |
| **密码策略** | 最少 8 位 + 大小写/数字/特殊字符 3 选 4 + 禁止连续重复 + 禁止键盘序列/连续字母序 + 禁止包含用户名 + 弱口令字典校验 + 密码历史（防近期重用，默认保留 5 条） |
| **账号锁定** | 5 次密码错误自动锁定 30 分钟，登录成功自动解锁（原子 SQL 计数，无并发竞态） |
| **双因素认证（MFA）** | TOTP（RFC 6238，兼容 Google/Microsoft Authenticator）+ 短信验证码降级；登录风险 HIGH 时强制校验；MFA 密钥可选 AES-256-GCM 加密存储 |
| **动态认证策略** | 风险 MEDIUM 强制图形验证码，HIGH 追加 MFA，CRITICAL 拒绝登录 |
| **JWT 黑名单** | 登出后 access_token + refresh_token 一并加入 Redis 黑名单（SHA-256 摘要 + 分布式锁，common-auth TokenBlacklistService） |
| **refresh_token 重用检测** | 已轮换的 refresh_token 再次使用触发链式撤销（RFC 6819） |
| **Token 自动续签** | access_token 剩余有效期低于阈值时自动签发新 Token（默认 10%，可通过 `ydsz.userinfo.token-auto-renewal-threshold-percent` 配置） |
| **OAuth2** | 授权码模式 + PKCE + refresh_token 轮换 + revoke（RFC 7009）+ introspect（RFC 7662）+ userinfo + scope 细粒度授权；授权码 256 位随机、GETDEL 原子消费；OAuth2 应用注册管理（CRUD） |
| **OIDC** | OpenID Connect Discovery 1.0 标准端点（/.well-known/openid-configuration + JWKS），JwksEndpoint + OidcDiscoveryEndpoint |
| **SAML 2.0** | Service Provider 端点 + IdP 配置管理（多租户 IdP 路由，@ConfigurationProperties 注入） |
| **CAS** | CAS 协议端点（单点登录/登出），CasService + CasProperties 配置 |
| **SCIM 2.0** | RFC 7643/7644 标准端点，支持用户 CRUD + PATCH 部分更新 + 部门/角色列表查询 + 服务提供者配置发现；Bearer Token 认证（ScimAuthFilter） |
| **WebAuthn** | 无密码认证（Passkey / FIDO2），通行 Key 增强 |
| **社交登录** | 钉钉 / 企微 / 飞书（JustAuth + 自定义 Provider），SocialAuthProviderRegistry 自动注册 |
| **验证码** | 4 位字母数字混合，Base64 PNG 图片，5 分钟有效（`ydsz.userinfo.captcha-ttl-seconds`） |
| **LDAP** | 可选 LDAP/ADFS 域认证（@ConfigurationProperties 配置注入），支持组织架构同步（LdapOrgSyncService） |
| **登录风控** | 风险评分（RiskScoringService，权重/时段/阈值可配置）+ Redis 计数器统一采集 |
| **权限缓存失效** | 菜单/角色变更发布 PermissionChangedEvent（common-auth）+ 角色权限 DB 结果缓存主动失效 + RBAC 本地二级缓存 |
| **内部接口鉴权** | `/api/internal/**` 服务端二次校验（@RequireInternal，网关白名单之外的最后防线） |
| **敏感操作分级** | 二次认证按 CRITICAL/HIGH/MEDIUM 分级，CRITICAL 短时效 |
| **并发写保护** | 角色分配分布式锁 + 用户更新乐观锁 revision |
| **单设备登录限制** | 单用户最大并发会话数可配置（默认 5，0=不限制），超限自动踢出；支持分端限制（web/app/api） |
| **链路追踪** | X-Trace-Id 全链路贯穿（日志 MDC + 指标标签 + 响应头） |
| **字段加密** | realName 使用 AES-256-GCM 加密存储，密钥经环境变量注入（YDSZ_FIELD_ENC_KEY_V1） |
| **指标埋点** | Micrometer 计数器/计时器（登录成功/失败/登出/认证耗时/在线会话数） |
| **健康检查** | Redis + JWT + 数据库连通性 + 用户/角色计数 |
| **安全告警** | 暴力破解/密码喷洒检测，支持自动 IP 封禁（common-safe IpAccessService） |
| **可信代理** | `ydsz.userinfo.trusted-proxies` 配置，为空时不信任转发头防止 IP 伪造 |
| **API 签名** | ApiSignatureFilter + ApiSignatureUtil，防止请求篡改 |
| **跨域 SSO** | CrossDomainSsoFilter + CrossDomainTokenService + TokenExchangeController（`/api/v1/sso`） |
| **记住我** | RememberMeFilter + RememberMeService + RememberMeProperties |

## 外部依赖

| 依赖 | 用途 |
|---|---|
| **mybatis-plus-spring-boot4-starter** | ORM 框架（Mapper 接口继承 BaseMapper） |
| **mapstruct** | DO/VO/DTO 对象转换（编译期生成，零反射） |
| **spring-security-crypto** | BCrypt PasswordEncoder 密码加密 |
| **easy-captcha** | 图形验证码生成（4 位字母数字混合） |
| **webauthn4j-core 0.21.0** | FIDO2 WebAuthn 密码学原语 |
| **jjwt (api + impl)** | JWT 签发/解析（OidcConfiguration 使用 Keys 工具） |
| **spring-boot-starter-data-ldap** | LDAP/ADFS 域认证（LdapOrgSyncService） |
| **dynamic-datasource-spring-boot3-starter** | 动态数据源（多数据源切换） |
| **micrometer-registry-prometheus** | Prometheus 指标导出 |
| **spring-boot-starter-actuator** | 健康检查/指标端点 |
| **spring-boot-health** | HealthIndicator 接口 |
| **spring-cloud-starter-bootstrap** | Bootstrap 配置上下文 |
| **spring-cloud-starter-alibaba-nacos-discovery** | Nacos 服务注册 |
| **spring-cloud-starter-alibaba-nacos-config** | Nacos 配置中心 |
| **springdoc-openapi-starter-webmvc-ui** | OpenAPI 3.0 / Swagger UI |

## Feign 接口

| 客户端 | 方法 | 返回类型 |
|---|---|---|
| `OrgQueryClient` | `queryUserById(userId)` | `YdszResponse<UserAccountVO>` |
| `OrgQueryClient` | `getDeptTree()` | `YdszResponse<List<DepartmentTreeVO>>` |
| `OrgQueryClient` | `getDeptList()` | `YdszResponse<List<DepartmentVO>>` |
| `OrgQueryClient` | `listUserIdsByRoleCode(roleCode)` | `YdszResponse<List<String>>` |
| `OrgQueryClient` | `listRoleCodesByUserId(userId)` | `YdszResponse<List<String>>` |
| `OrgQueryClient` | `listDeptIdsByUserId(userId)` | `YdszResponse<List<String>>` |
| `OrgQueryClient` | `getLeaderByUserId(userId)` | `YdszResponse<String>` |
| `OrgQueryClient` | `listUserIdsByPositionCode(positionCode)` | `YdszResponse<List<String>>` |
| `OrgQueryClient` | `getDeptLeaderByDeptId(deptId)` | `YdszResponse<String>` |
| `OrgQueryClient` | `getDeptLeaderByDeptCode(deptCode)` | `YdszResponse<String>` |
| `OrgQueryClient` | `batchUserNames(userIds)` | `YdszResponse<Map<String, String>>` |
| `OrgQueryClient` | `batchDeptNames(deptIds)` | `YdszResponse<Map<String, String>>` |
| `OrgQueryClient` | `batchRoleNames(roleIds)` | `YdszResponse<Map<String, String>>` |
| `OrgQueryClient` | `batchPostNames(postIds)` | `YdszResponse<Map<String, String>>` |
| `OrgQueryClient` | `batchCompanyNames(companyIds)` | `YdszResponse<Map<String, String>>` |

> `UserInfoNameAssembler`（`ydsz-userinfo-api`）通过 `OrgQueryClient` 批量名称富化接口，在一次 Feign 往返中解析 ID → 名称映射，避免 N+1 调用。

## 启动顺序

依赖 `common` + `nacos`，**应在 `gateway` 之后**启动。

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
# Web 端（端口 9002）
mvn -pl ydsz-userinfo-web spring-boot:run
# App 端（端口 9003，双入口架构）
mvn -pl ydsz-userinfo-app spring-boot:run
```

## 配置

```yaml
ydsz:
  userinfo:
    health-enabled: true
    token-ttl-seconds: 7200
    max-login-fail-count: 5
    lock-duration-minutes: 30
    captcha-enabled: true
    captcha-ttl-seconds: 300
    password-min-length: 8
    password-max-length: 64
    password-min-category-count: 3
    bcrypt-strength: 10
    oauth2-clients:
      - client-id: "demo"
        client-secret: "..."
    password-history-count: 5
    max-sessions-per-user: 5
    token-auto-renewal-enabled: true
    token-auto-renewal-threshold-percent: 10
    mfa-encryption-key: ${MFA_ENCRYPTION_KEY:}
    risk-ip-weight: 30
    risk-time-weight: 20
    risk-device-weight: 25
    risk-frequency-weight: 25
    internal-call:
      enabled: false
      header-name: X-Internal-Call
  auth:
    token:
      enabled: true
      secret-key: "your-jwt-secret-key-at-least-32-chars"
      access-token-expire-seconds: 7200
      refresh-token-expire-seconds: 604800
    ldap:
      enabled: false
      host: 10.248.3.56
      port: 389
      domain: "@ydszsoft"
```

### 配置项（`ydsz.userinfo.*`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.userinfo.health-enabled` | `true` | 是否启用健康检查 |
| `ydsz.userinfo.token-ttl-seconds` | `7200` | 会话 Token 有效期（秒） |
| `ydsz.userinfo.max-login-fail-count` | `5` | 连续失败锁定阈值 |
| `ydsz.userinfo.lock-duration-minutes` | `30` | 锁定时长（分钟） |
| `ydsz.userinfo.captcha-enabled` | `true` | 是否启用图形验证码 |
| `ydsz.userinfo.captcha-ttl-seconds` | `300` | 图形验证码有效期（秒） |
| `ydsz.userinfo.password-min-length` | `8` | 密码最小长度 |
| `ydsz.userinfo.password-max-length` | `64` | 密码最大长度（BCrypt 72 字节截断限制） |
| `ydsz.userinfo.password-min-category-count` | `3` | 密码字符类别数下限 |
| `ydsz.userinfo.password-history-count` | `5` | 禁止重用的历史密码条数 |
| `ydsz.userinfo.bcrypt-strength` | `10` | BCrypt 强度（4-31） |
| `ydsz.userinfo.oauth2-clients` | `{}` | OAuth2 客户端注册（Map，clientId → 客户端配置） |
| `ydsz.userinfo.max-sessions-per-user` | `5` | 单用户最大并发会话数（0=不限制） |
| `ydsz.userinfo.max-sessions-per-device-type` | `{}` | 分端会话限制（web/app/api） |
| `ydsz.userinfo.mfa-encryption-key` | — | MFA TOTP 密钥加密密钥（AES-256-GCM，Base64 编码） |
| `ydsz.userinfo.token-auto-renewal-enabled` | `true` | Token 自动续签开关 |
| `ydsz.userinfo.token-auto-renewal-threshold-percent` | `10` | Token 续签阈值百分比 |
| `ydsz.userinfo.batch-size-limit` | `500` | 批量查询上限 |
| `ydsz.userinfo.permission-cache-ttl-seconds` | `600` | 角色权限缓存 TTL（秒） |
| `ydsz.userinfo.risk-window-seconds` | `300` | 登录风险因子采集窗口（秒） |
| `ydsz.userinfo.mfa-risk-threshold` | `60` | 风险等级触发 MFA 的评分阈值 |
| `ydsz.userinfo.risk-ip-weight` | `30` | 风控：IP 风险权重 |
| `ydsz.userinfo.risk-time-weight` | `20` | 风控：时间异常权重 |
| `ydsz.userinfo.risk-device-weight` | `25` | 风控：设备异常权重 |
| `ydsz.userinfo.risk-frequency-weight` | `25` | 风控：频率异常权重 |
| `ydsz.userinfo.risk-anomaly-start-hour` | `0` | 风控：异常时段起始小时 |
| `ydsz.userinfo.risk-anomaly-end-hour` | `6` | 风控：异常时段结束小时 |
| `ydsz.userinfo.risk-frequency-window-minutes` | `5` | 风控：频率窗口（分钟） |
| `ydsz.userinfo.risk-frequency-threshold` | `3` | 风控：频率阈值 |
| `ydsz.userinfo.trusted-proxies` | `[]` | 可信代理 IP 列表（为空时不信任转发头） |
| `ydsz.userinfo.auth-exclude-paths` | `["/actuator/**", ...]` | 不需要鉴权的路径列表（Ant 风格） |
| `ydsz.userinfo.internal-call.enabled` | `false` | 内部接口服务端二次校验开关 |
| `ydsz.userinfo.scim.base-path` | `/scim/v2` | SCIM 端点基础路径 |
| `ydsz.tenant.enabled` | `false` | 多租户隔离开关（开启后 SQL 自动追加 tenant_id） |
| `ydsz.safe.field-encryption.keys.1` | `${YDSZ_FIELD_ENC_KEY_V1}` | 字段加密密钥（环境变量注入） |
| `ydsz.auth.token.secret-key` | （必填） | JWT 签名密钥（至少 32 字符） |
| `ydsz.auth.token.access-token-expire-seconds` | `7200` | Access Token 有效期（秒） |
| `ydsz.auth.token.refresh-token-expire-seconds` | `604800` | Refresh Token 有效期（秒） |
| `ydsz.auth.ldap.enabled` | `false` | 是否启用 LDAP/ADFS 域认证 |
| `ydsz.auth.ldap.host` | — | LDAP 服务器地址 |
| `ydsz.auth.ldap.port` | `389` | LDAP 端口 |
| `ydsz.auth.ldap.domain` | — | LDAP 域后缀（如 `@ydszsoft`） |
| `ydsz.security.alert.dedup-ttl-seconds` | `300` | 安全告警去重时间窗口（秒） |
| `ydsz.security.alert.ip-dedup-ttl-seconds` | `180` | 安全告警 IP 维度去重窗口（秒） |
| `ydsz.security.alert.brute-force-threshold` | `10` | 暴力破解检测阈值 |
| `ydsz.security.alert.password-spray-threshold` | `5` | 密码喷洒检测阈值 |

## 常见问题

### Q1：登录失败 "账号或密码错误" 但密码正确

1. 检查 `ydsz_user_account` 中 `status = 'ENABLED'` 且 `deleted = 0`
2. 检查 `lock_time` 是否在未来（账号被锁定）
3. 检查 `password` 字段是否为 BCrypt 哈希值

### Q2：JWT Token 刷新失败

1. Refresh Token 已过期（默认 7 天）
2. Token 已加入黑名单（用户已登出）
3. `ydsz.auth.token.secret-key` 被修改过

### Q3：LDAP 认证失败

1. 检查 `ydsz.auth.ldap.enabled=true`
2. 检查 LDAP 服务器连通性
3. 用户名需去掉域后缀（系统自动拼接 `domain`）

### Q4：菜单树不显示

1. 检查 `ydsz_menu` 表是否有数据且 `status = 'ENABLED'`
2. 检查用户角色是否关联了对应菜单权限
3. 菜单树按 `parent_id` 递归构建，根节点 `parent_id = 0`

### Q5：MFA 密钥安全存储

生产环境必须配置 `ydsz.userinfo.mfa-encryption-key`（Base64 编码的 32 字节密钥），否则 MFA TOTP 密钥将以明文存储在 Redis 中。
生成方式：`openssl rand -base64 32`

### Q6：社交登录配置

社交登录通过 `SocialAuthProviderRegistry` 自动注册，支持钉钉/企微/飞书。需在 `SocialAuthProperties` 中配置各平台的 clientId/clientSecret。

### Q7：SCIM 端点认证

SCIM 端点使用 Bearer Token 认证（`ScimAuthFilter`），需在请求头携带 `Authorization: Bearer <token>`。

---

> 本模块是 YDSZ 的**身份认证与组织架构中心**，所有业务模块通过 Feign 调用获取用户/部门信息。
> 严禁在业务模块中重复实现用户/权限查询逻辑。
