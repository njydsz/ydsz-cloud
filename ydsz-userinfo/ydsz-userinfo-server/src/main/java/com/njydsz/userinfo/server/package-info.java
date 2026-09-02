/**
 * 用户核心服务层，提供认证、授权、MFA、会话管理、设备管理、LDAP 同步、安全告警等能力.
 *
 * <p>本模块是用户子系统的核心服务实现层，构建了从用户账户 CRUD、登录认证、多因子认证（MFA）、
 * 会话管理、角色权限体系，到 LDAP/SCIM/OAuth2/SAML/WebAuthn 等企业级身份协议对接，以及安全风控、
 * 设备管理、安全告警等纵深防御能力的完整身份与访问管理（IAM）解决方案。</p>
 *
 * <p>核心能力分层：</p>
 * <ul>
 *   <li>认证体系：{@code AuthServiceImpl} 统一编排认证流程，{@code UserIdentityProviderFactory}
 *       支持本地、LDAP、SAML、OAuth2、CAS、Social 等多提供者切换，
 *       {@code LdapAuthenticationProvider}、{@code SamlService}、{@code SocialAuthService}、
 *       {@code CasService}、{@code WebAuthnService} 分别对接对应协议</li>
 *   <li>多因子认证：{@code MfaService} 集成 TOTP/SMS/Email/硬件密钥等多种 MFA 方式，
 *       配合 {@code VerifyCodeService} 处理验证码发送与校验</li>
 *   <li>会话管理：{@code SessionManager} 负责会话创建、续期、驱逐与并发控制，
 *       {@code CrossDomainTokenService} 支持跨域单点登录，{@code RememberMeService} 实现记住我功能</li>
 *   <li>授权体系：{@code UserInfoRbacService} 基于 RBAC 模型，{@code RoleCacheService} 缓存角色权限，
 *       {@code DbRolePermissionLoader} 从数据库加载权限配置</li>
 *   <li>安全风控：{@code RiskScoringService} 对登录行为进行风险评分；{@code SecurityAlertService}
 *       聚合异常事件触发告警；{@code LoginAttemptCounterService} 防御暴力破解</li>
 *   <li>企业集成：{@code LdapOrgSyncService} 同步 LDAP 组织架构；{@code ScimPatchHandler}
 *       处理 SCIM 用户生命周期操作；{@code OAuth2ApplicationService} 管理 OAuth2 应用注册</li>
 * </ul>
 *
 * <h3>关键组件</h3>
 *
 * <ul>
 *   <li>{@code AuthServiceImpl} -- 认证服务主入口，编排多提供者认证链路</li>
 *   <li>{@code VerifyCodeService} -- 验证码生成与校验服务</li>
 *   <li>{@code MfaService} -- 多因子认证服务</li>
 *   <li>{@code SessionManager} -- 会话管理器</li>
 *   <li>{@code SocialAuthService} -- 社交登录服务</li>
 *   <li>{@code SamlService} -- SAML 身份提供者服务</li>
 *   <li>{@code WebAuthnService} -- FIDO2/WebAuthn 认证服务</li>
 *   <li>{@code LdapOrgSyncService} -- LDAP 组织架构同步服务</li>
 *   <li>{@code SecurityAlertService} -- 安全告警聚合服务</li>
 *   <li>{@code DeviceSessionService} -- 设备会话管理服务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.userinfo.server;
