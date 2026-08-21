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
│       ├── enums/                     # 领域枚举（EnableStatusEnum / BanType / DeviceType / IdentityProviderType / UserLifecycleStatusEnum 等）
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
│       │   ├── UserAccountDO.java          # 用户账号（含 AES-256-GCM 字段加密 realName）
│       │   ├── RoleDO.java                 # 角色
│       │   ├── RolePermissionDO.java       # 角色-权限关联
│       │   ├── UserRoleDO.java             # 用户-角色关联
│       │   ├── MenuDO.java                 # 菜单/按钮/API 权限定义
│       │   ├── DepartmentDO.java           # 部门（树形）
│       │   ├── CompanyDO.java              # 公司
│       │   ├── CompanyDeptDO.java          # 公司-部门关联
│       │   ├── PostDO.java                 # 岗位
│       │   ├── UserDeptDO.java             # 用户-部门关联
│       │   ├── UserPostDO.java             # 用户-岗位关联
│       │   ├── UserLoginHistoryDO.java     # 登录历史
│       │   ├── UserPasswordHistoryDO.java  # 密码变更历史
│       │   ├── LanguageDO.java             # 语言
│       │   ├── AuthPolicyDO.java           # 认证策略
│       │   ├── OAuth2ApplicationDO.java    # OAuth2 应用注册
│       │   ├── SamlIdpConfigDO.java        # SAML IdP 配置
│       │   ├── SecurityAlertDO.java        # 安全告警
│       │   ├── SocialAccountDO.java        # 社交账号关联
│       │   ├── SocialClientDO.java         # 社交登录客户端配置
│       │   └── WebAuthnCredentialDO.java   # WebAuthn 凭证
│       ├── mapper/                    # MyBatis-Plus Mapper（21 个）
│       ├── repository/                # Repository 实现（Converter DO↔VO 转换）
│       ├── converter/                 # UserInfoConverter / AuthPolicyConverter / ScimConverter 等（MapStruct）
│       ├── config/                    # AesMfaSecretEncryptor / PlainMfaSecretEncryptor
│       └── social/                    # 社交登录提供者
│           ├── AbstractSocialAuthProvider.java
│           ├── DingTalkAuthProvider.java
│           ├── EnterpriseWechatAuthProvider.java
│           ├── FeishuAuthProvider.java
│           └── JustAuthHttpClient.java
├── ydsz-userinfo-server/              # 应用层：Service + Auth + Config + Aspect + Metrics + Trace + Alert + Device + SSE + OAuth2
│   └── src/main/java/com/njydsz/userinfo/server/
│       ├── auth/                      # 认证/风控（30+ 个类）
│       │   ├── AuthService / AuthServiceImpl    # 登录认证核心
│       │   ├── AccountStatusGuard               # 账号状态守卫
│       │   ├── ApiSignatureUtil                 # API 签名工具
│       │   ├── CaptchaService                   # 图形验证码
│       │   ├── CasService                       # CAS 协议服务
│       │   ├── CredentialVerifier               # 凭证校验
│       │   ├── CrossDomainTokenService          # 跨域 Token 服务
│       │   ├── DbRolePermissionLoader           # 角色权限 DB 加载器
│       │   ├── GeoIpService                     # GeoIP 定位
│       │   ├── LdapAuthenticationProvider      # LDAP 认证提供者
│       │   ├── LdapOrgSyncService               # LDAP 组织架构同步
│       │   ├── LdapSyncTask                    # LDAP 同步定时任务
│       │   ├── LocalUserIdentityProvider        # 本地用户身份提供者
│       │   ├── LoginAttemptCounterService       # 登录尝试计数器
│       │   ├── MfaService                       # MFA 双因素认证
│       │   ├── PasswordPolicyValidator          # 密码策略校验
│       │   ├── PathExcludeService               # 路径排除服务
│       │   ├── RememberMeService                # 记住我服务
│       │   ├── RiskScoringService               # 风险评分
│       │   ├── RoleCacheService                 # 角色缓存服务
│       │   ├── SamlService                      # SAML 服务
│       │   ├── ScimPatchHandler                 # SCIM PATCH 处理
│       │   ├── SecondaryAuthService             # 二次认证服务
│       │   ├── SecurityDashboardService         # 安全仪表盘服务
│       │   ├── SensitiveVerifyService           # 敏感操作验证
│       │   ├── SessionActivityService           # 会话活跃度服务
│       │   ├── SessionManager                   # 会话管理器
│       │   ├── SocialAuthService                # 社交登录服务
│       │   ├── UserBanService                   # 账号封禁服务
│       │   ├── UserIdentityProviderFactory      # 用户身份提供者工厂
│       │   ├── UserInfoRbacService              # RBAC 服务
│       │   ├── UserLifecycleTask                # 用户生命周期任务
│       │   ├── UserPasswordHistoryService       # 密码历史服务
│       │   ├── UserSessionAdminService          # 用户会话管理服务
│       │   ├── VerifyCodeService                # 验证码服务
│       │   ├── WeakPasswordDictionary           # 弱口令字典
│       │   └── WebAuthnService                  # WebAuthn 服务
│       ├── config/                    # 配置类（20+ 个）
│       │   ├── UserInfoProperties / UserInfoConfiguration
│       │   ├── CasProperties / CasConfiguration
│       │   ├── CrossDomainSsoProperties
│       │   ├── GeoIpProperties
│       │   ├── InternalCallProperties
│       │   ├── LdapProperties / LdapSyncProperties
│       │   ├── OidcProperties / OidcConfiguration
│       │   ├── RememberMeProperties
│       │   ├── SamlProperties / SamlConfiguration
│       │   ├── ScimProperties
│       │   ├── WebAuthnProperties / WebAuthnConfiguration
│       │   ├── UserInfoMessageSourceConfiguration
│       │   ├── ApiSignatureProperties
│       │   ├── UserLoginRiskProperties
│       │   ├── UserSecurityProperties
│       │   └── UserTokenProperties
│       ├── service/                   # 19 个 Service 接口 + impl（14 个实现类）
│       │   ├── AuthPolicyService
│       │   ├── CompanyDeptService
│       │   ├── CompanyService
│       │   ├── DepartmentService
│       │   ├── LanguageService
│       │   ├── LoginAttemptContext
│       │   ├── LoginHistoryService
│       │   ├── MenuService
│       │   ├── PostService
│       │   ├── RoleService
│       │   ├── SamlIdpConfigService
│       │   ├── SelfServiceService
│       │   ├── SocialClientConfigService
│       │   ├── UserAccountService
│       │   ├── UserDeptService
│       │   ├── UserExcelService
│       │   ├── UserLifecycleService
│       │   ├── UserPostService
│       │   ├── WorkflowApproverCacheService
│       │   └── impl/
│       ├── alert/                     # 安全告警
│       │   ├── SecurityAlertService
│       │   ├── SecurityAlertAggregationTask
│       │   ├── AlertNotificationChannel
│       │   └── LogAlertNotificationChannel
│       ├── aspect/                    # 切面
│       │   ├── SecondaryAuthAspect            # 二次认证切面
│       │   └── SensitiveOperationAspect       # 敏感操作切面
│       ├── device/                    # 设备会话
│       │   ├── DeviceSessionService
│       │   └── DeviceSessionVO
│       ├── dto/                       # 导入 DTO
│       │   └── UserImportDTO
│       ├── event/                     # 事件分发
│       │   ├── UserAuthEventDispatcher
│       │   ├── UserDomainEventPublisher
│       │   └── listener/
│       ├── health/                   # UserInfoHealthIndicator
│       ├── metrics/                  # UserInfoMetrics（SentryMetricsAdapter）
│       ├── oauth2/                   # OAuth2 应用服务
│       │   ├── OAuth2ApplicationService
│       │   └── OAuthCodeContext
│       ├── search/                   # UserinfoSearchProvider
│       ├── social/                   # 社交登录提供者注册表
│       │   ├── SocialAuthProviderAutoConfiguration
│       │   └── SocialAuthProviderRegistry
│       ├── sse/                      # SSE 实时推送
│       │   ├── SseAuthEventListener
│       │   └── SseEmitterRegistry
│       └── trace/                    # TraceContext（轻量链路追踪）
├── ydsz-userinfo-web/                 # Web 层：Controller + Filter + Bootstrap（端口 9002）
│   └── src/main/java/com/njydsz/userinfo/web/
│       ├── UserInfoApplication.java
│       ├── annotation/                # RequireInternal（内部接口标记注解）
│       ├── aspect/                    # RequireInternalAspect（内部调用校验切面）
│       ├── dto/                       # RefreshRequest / SecondaryAuthRequest
│       ├── vo/                        # JwksEndpoint / OidcDiscoveryEndpoint
│       ├── filter/                    # 7 个过滤器
│       │   ├── ApiSignatureFilter          # API 签名校验
│       │   ├── CrossDomainSsoFilter        # 跨域 SSO 过滤
│       │   ├── RememberMeFilter            # 记住我过滤
│       │   ├── ScimAuthFilter              # SCIM 认证过滤
│       │   ├── TokenAutoRenewalFilter      # Token 自动续签过滤
│       │   ├── TraceIdFilter               # 链路追踪 ID 过滤
│       │   └── UserInfoMetricsFilter       # 指标采集过滤
│       └── controller/                # 32 个 Controller
│           ├── AuthController.java              # /api/v1/auth
│           ├── AuthPolicyController.java        # /api/v1/auth-policy
│           ├── CaptchaController.java           # /api/v1/captcha
│           ├── CasController.java               # /cas
│           ├── CompanyController.java           # /api/v1/CompanyDO
│           ├── DepartmentController.java        # /api/v1/dept
│           ├── InternalApiController.java       # /api/internal（Feign 内部调用，@RequireInternal）
│           ├── LanguageController.java          # /api/v1/LanguageDO
│           ├── LdapSyncController.java          # /api/v1/admin/ldap/sync
│           ├── MenuController.java              # /api/v1/MenuDO
│           ├── OAuth2ApplicationController.java # /api/v1/admin/oauth2/applications
│           ├── OAuth2Controller.java            # /api/v1/oauth2
│           ├── OidcController.java              # /.well-known（OIDC 发现 + JWKS）
│           ├── PostController.java              # /api/v1/PostDO
│           ├── RoleController.java              # /api/v1/RoleDO
│           ├── SamlController.java              # /saml
│           ├── SamlIdpConfigController.java     # /api/v1/saml-idp-config
│           ├── ScimController.java              # /scim/v2（SCIM 2.0 用户供给）
│           ├── SecurityAlertController.java     # /api/v1/admin/security/alerts
│           ├── SecurityDashboardController.java # /api/v1/admin/security
│           ├── SocialAccountController.java     # /api/v1/profile/social
│           ├── SocialClientConfigController.java # /api/v1/social-client-config
│           ├── SsoMetricsController.java        # /api/v1/sso/metrics
│           ├── TokenExchangeController.java     # /api/v1/sso
│           ├── UserAccountController.java       # /api/v1/user
│           ├── UserinfoSearchController.java    # /api/v1/userinfo/search
│           ├── UserProfileController.java       # /api/v1/profile
│           ├── WebAuthnController.java          # /api/v1/webauthn
│           ├── AdminSessionController.java      # /api/v1/admin（会话管理 + 账号封禁）
│           ├── device/
│           │   └── DeviceSessionController.java # /api/v1/devices（设备会话管理）
│           ├── selfservice/
│           │   └── SelfServiceController.java   # /api/v1/self-service
│           └── sse/
│               └── AuthEventSseController.java  # /api/v1/auth/events（SSE 认证事件推送）
└── ydsz-userinfo-app/                 # App 层：移动端/应用端 API（端口 9003）
    └── src/main/java/com/njydsz/userinfo/app/
        ├── UserInfoAppApplication.java
        ├── config/
        │   ├── AppAutoConfiguration.java
        │   ├── ConditionalOnPlatform.java
        │   ├── PlatformCondition.java
        │   └── UserInfoAppAutoConfiguration.java
        ├── health/
        │   └── AppHealthIndicator.java
        └── openapi/
            ├── AppOpenApiConfiguration.java
            └── UserInfoAppOpenApiConfiguration.java
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/auth/login` `/logout` `/refresh` | 登录/登出/Token 刷新 |
| `/api/v1/user` | 用户 CRUD + 分页 + 密码管理 + 角色分配 + 批量操作 + Excel 导入导出 |
| `/api/v1/user/{userId}/login-history` | 用户登录历史查询 |
| `/api/v1/profile` | 用户个人信息更新 |
| `/api/v1/RoleDO` | 角色 CRUD + 权限分配 |
| `/api/v1/dept` | 部门 CRUD + 树形结构 |
| `/api/v1/MenuDO` | 菜单 CRUD + 树形结构 |
| `/api/v1/CompanyDO` | 公司 CRUD |
| `/api/v1/PostDO` | 岗位 CRUD |
| `/api/v1/LanguageDO` | 语言 CRUD |
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
| `ydsz.userinfo.mfa-encryption-key` | — | MFA TOTP 密钥加密密钥（AES-2</longcat_think>
