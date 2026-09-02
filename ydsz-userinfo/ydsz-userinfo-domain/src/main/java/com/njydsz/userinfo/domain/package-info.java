/**
 * 用户领域层，包含用户账户仓储、认证策略、OAuth2 应用、SCIM 等.
 *
 * <p>本模块定义了用户子系统的核心领域模型与仓储接口契约，覆盖用户账户、角色权限、组织架构、
 * 认证策略、SAML IdP 配置、OAuth2 应用、WebAuthn 凭证、安全告警、社交账号绑定等多个业务子域。
 * 同时提供 SCIM 2.0 协议相关的值对象定义，支撑企业身份自动化的用户生命周期管理。</p>
 *
 * <p>领域模型主要构成：</p>
 * <ul>
 *   <li>用户子域：{@code UserAccountRepository}、{@code UserRoleRepository}、{@code UserPostRepository}、
 *       {@code UserDeptRepository}、{@code UserLoginHistoryRepository}、{@code UserPasswordHistoryRepository}，
 *       管理用户账户维度的持久化</li>
 *   <li>组织子域：{@code CompanyRepository}、{@code DepartmentRepository}、{@code CompanyDeptRepository}、
 *       {@code PostRepository}、{@code MenuRepository}、{@code LanguageRepository}，
 *       定义组织架构与基础数据的仓储契约</li>
 *   <li>认证策略子域：{@code AuthPolicyRepository} / {@code SamlIdpConfigRepository} /
 *       {@code UserIdentityProvider} / {@code OAuth2ApplicationRepository} /
 *       {@code WebAuthnCredentialRepository}，对接多种认证协议</li>
 *   <li>安全子域：{@code SecurityAlertRepository} 管理告警记录；{@code UserDomainEvent} 事件体系
 *       定义登录成功/失败、锁定/解锁、MFA 触发等账户安全事件</li>
 *   <li>SCIM 子域：{@code ScimUser}、{@code ScimEmail}、{@code ScimPhone}、{@code ScimName}、
 *       {@code ScimListResponse}、{@code ScimPatchOp}、{@code ScimError} 等值对象实现 SCIM 2.0 协议模型</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li>仓储接口严格按业务子域拆分，避免大泥球式聚合</li>
 *   <li>DTO（如 {@code UserAccountDTO}、{@code SamlIdpDTO}）用于跨层数据传输</li>
 *   <li>枚举（{@code IdentityProviderType}、{@code UserLifecycleStatusEnum}、{@code BanType}）定义领域状态与类型</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.userinfo.domain;
