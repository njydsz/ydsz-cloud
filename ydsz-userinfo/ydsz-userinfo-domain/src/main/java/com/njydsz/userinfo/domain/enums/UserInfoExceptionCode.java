package com.njydsz.userinfo.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 用户信息中心模块异常码枚举。
 *
 * <p>P0-6: 已从旧版 {@code ResultCode} 体系迁移到 {@code common-exception} 的 {@link ExceptionCode}
 * 体系，获得以下增强能力：
 *
 * <ul>
 *   <li>统一异常码注册：自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}，支持反查与文档生成
 *   <li>HTTP 状态码：每个异常码携带精确的 HTTP 状态码（404/401/403/400）
 *   <li>异常分类：由 {@link ExceptionCode#getCategory()} 按首字母推断（B → SYSTEM）
 *   <li>i18n 支持：{@link #getKey()} 返回国际化消息键，由 {@code I18nConfiguration} 解析
 * </ul>
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B30xxx 用户/认证
 *   <li>B31xxx 组织架构
 *   <li>B32xxx RBAC（角色/权限/菜单/岗位/语言）
 *   <li>B33xxx 自助服务
 *   <li>B34xxx 社交认证（OAuth2 绑定/登录）
 *   <li>B35xxx LDAP 同步
 *   <li>A20xxx 安全认证（锁号/MFA/Token/Remember-Me，HTTP 401）
 * </ul>
 *
 * <p><b>稳定性</b>：错误码是业务契约，修改/废弃必须保留向前兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "userinfo", description = "用户中心")
public enum UserInfoExceptionCode implements ExceptionCode {

  // ==================== B30xxx 用户/认证 ====================
  /** 用户不存在 */
  USER_NOT_FOUND("B30001", "userinfo.user.not.found", 404),
  /** 密码错误 */
  PASSWORD_INCORRECT("B30002", "userinfo.password.incorrect"),
  /** 用户已停用 */
  USER_DISABLED("B30003", "userinfo.user.disabled", 403),
  /** 用户名已存在 */
  USERNAME_DUPLICATE("B30005", "userinfo.username.duplicate"),
  /** 账号已锁定，请稍后再试 */
  ACCOUNT_LOCKED("A20110", "userinfo.account.locked", 401),
  /** 验证码无效或已过期 */
  CAPTCHA_INVALID("B30007", "userinfo.captcha.invalid"),
  /** 请输入验证码 */
  CAPTCHA_REQUIRED("B30008", "userinfo.captcha.required"),
  /** 需要双因素认证 */
  MFA_REQUIRED("A20108", "userinfo.mfa.required", 401),
  /** 双因素认证码无效 */
  MFA_INVALID("A20109", "userinfo.mfa.invalid", 401),
  /** 用户未绑定双因素认证 */
  MFA_NOT_BOUND("B30009", "userinfo.mfa.not.bound"),
  /** 用户已绑定双因素认证，请勿重复绑定 */
  MFA_ALREADY_BOUND("B30014", "userinfo.mfa.already.bound"),
  /** 原密码错误 */
  OLD_PASSWORD_INCORRECT("B30010", "userinfo.password.old.incorrect"),
  /** 新密码不能与旧密码相同 */
  PASSWORD_SAME_AS_OLD("B30011", "userinfo.password.same.as.old"),
  /** 密码强度不足 */
  PASSWORD_TOO_WEAK("B30012", "userinfo.password.too.weak"),
  /** 不能使用最近使用过的密码 */
  PASSWORD_REUSED("B30013", "userinfo.password.reused"),
  /** IP 登录失败次数过多被临时封禁 */
  IP_BLOCKED("B30019", "userinfo.login.ip.blocked", 403),
  /** Token 无效 */
  TOKEN_INVALID("A20003", "userinfo.token.invalid", 401),
  /** 授权码无效或已过期 */
  OAUTH2_CODE_INVALID("B30016", "userinfo.oauth2.code.invalid", 401),
  /** 客户端 ID 无效 */
  OAUTH2_CLIENT_INVALID("B30017", "userinfo.oauth2.client.invalid", 401),
  /** 回调地址不匹配 */
  OAUTH2_REDIRECT_URI_MISMATCH("B30018", "userinfo.oauth2.redirect.uri.mismatch", 401),
  /** PKCE code_verifier 无效 */
  OAUTH2_PKCE_VERIFIER_INVALID("B30020", "userinfo.oauth2.pkce.verifier.invalid", 401),
  /** 导入文件为空 */
  IMPORT_FILE_EMPTY("B30021", "userinfo.import.file.empty"),
  /** 导入文件无数据 */
  IMPORT_DATA_EMPTY("B30022", "userinfo.import.data.empty"),
  /** 导入数量超过上限 */
  IMPORT_EXCEEDS_LIMIT("B30023", "userinfo.import.exceeds.limit"),
  /** 导入用户名为空 */
  IMPORT_USERNAME_EMPTY("B30024", "userinfo.import.username.empty"),
  /** 导入真实姓名为空 */
  IMPORT_REALNAME_EMPTY("B30025", "userinfo.import.realname.empty"),
  /** 导入密码为空 */
  IMPORT_PASSWORD_EMPTY("B30026", "userinfo.import.password.empty"),
  /** 导入用户名已存在 */
  IMPORT_USERNAME_DUPLICATE("B30027", "userinfo.import.username.duplicate"),
  /** 导入部门编码不存在 */
  IMPORT_DEPT_NOT_FOUND("B30028", "userinfo.import.dept.not.found"),
  /** 导入上级用户不存在 */
  IMPORT_LEADER_NOT_FOUND("B30029", "userinfo.import.leader.not.found"),
  /** 导入文件读取失败 */
  IMPORT_READ_FAILED("B30030", "userinfo.import.read.failed"),
  /** 内部接口访问被拒绝（缺少 X-Internal-Call 标记，P0-6） */
  INTERNAL_ACCESS_FORBIDDEN("B30031", "userinfo.internal.access.forbidden", 403),
  /** OAuth2 scope 超出客户端授权范围（P1-3） */
  OAUTH2_SCOPE_INVALID("B30032", "userinfo.oauth2.scope.invalid", 401),
  /** 数据已被其他用户修改，请刷新后重试（乐观锁冲突，P1-6） */
  USER_UPDATE_CONFLICT("B30033", "userinfo.user.update.conflict", 409),
  /** OAuth2 state 参数无效或已过期 */
  OAUTH2_STATE_INVALID("B30034", "userinfo.oauth2.state.invalid", 401),

  // ==================== B31xxx 组织架构 ====================
  /** 部门不存在 */
  DEPARTMENT_NOT_FOUND("B30101", "userinfo.DepartmentDO.not.found", 404),
  /** 该部门下存在子部门，无法删除 */
  DEPARTMENT_HAS_CHILDREN("B30102", "userinfo.DepartmentDO.has.children"),
  /** 该部门下存在人员，无法删除 */
  DEPARTMENT_HAS_USERS("B30103", "userinfo.DepartmentDO.has.users"),
  /** 部门编码已存在 */
  DEPARTMENT_CODE_DUPLICATE("B30104", "userinfo.DepartmentDO.code.duplicate"),
  /** 公司不存在 */
  COMPANY_NOT_FOUND("B30105", "userinfo.CompanyDO.not.found", 404),
  /** 公司编码已存在 */
  COMPANY_CODE_DUPLICATE("B30106", "userinfo.CompanyDO.code.duplicate"),
  /** 用户-部门关联不存在 */
  USER_DEPT_NOT_FOUND("B30107", "userinfo.UserDeptVO.not.found", 404),

  // ==================== B32xxx RBAC ====================
  /** 角色不存在 */
  ROLE_NOT_FOUND("B32001", "userinfo.RoleDO.not.found", 404),
  /** 角色编码已存在 */
  ROLE_CODE_DUPLICATE("B32002", "userinfo.RoleDO.code.duplicate"),
  /** 内置角色不允许删除 */
  ROLE_BUILTIN_CANNOT_DELETE("B32003", "userinfo.RoleDO.builtin.cannot.delete"),
  /** 该角色下存在用户，无法删除 */
  ROLE_HAS_USERS("B32004", "userinfo.RoleDO.has.users"),
  /** 权限不存在 */
  PERMISSION_NOT_FOUND("B32005", "userinfo.permission.not.found", 404),
  /** 菜单不存在 */
  MENU_NOT_FOUND("B32006", "userinfo.MenuDO.not.found", 404),
  /** 该菜单下存在子菜单，无法删除 */
  MENU_HAS_CHILDREN("B32011", "userinfo.MenuDO.has.children"),
  /** 岗位不存在 */
  POST_NOT_FOUND("B32007", "userinfo.PostDO.not.found", 404),
  /** 岗位编码已存在 */
  POST_CODE_DUPLICATE("B32008", "userinfo.PostDO.code.duplicate"),
  /** 语言不存在 */
  LANGUAGE_NOT_FOUND("B32009", "userinfo.LanguageDO.not.found", 404),
  /** 语言编码已存在 */
  LANGUAGE_CODE_DUPLICATE("B32010", "userinfo.LanguageDO.code.duplicate"),

  // ==================== A20xxx 安全认证（二次认证/会话控制） ====================
  /** 该设备类型会话数已达上限 */
  DEVICE_SESSION_LIMIT_EXCEEDED("A20128", "userinfo.device.session.limit.exceeded", 401),
  /** 敏感操作需要二次认证 */
  SENSITIVE_VERIFY_REQUIRED("A20120", "userinfo.sensitive.verify.required", 401),
  /** 二次认证已过期，请重新验证 */
  SENSITIVE_VERIFY_EXPIRED("A20121", "userinfo.sensitive.verify.expired", 401),
  /** 二次认证密码错误 */
  SENSITIVE_VERIFY_PASSWORD_INCORRECT("A20122", "userinfo.sensitive.verify.password.incorrect", 401),
  /** 需要二级认证 */
  SECONDARY_AUTH_REQUIRED("A20123", "userinfo.secondary.auth.required", 401),
  /** 二级认证已过期 */
  SECONDARY_AUTH_EXPIRED("A20124", "userinfo.secondary.auth.expired", 401),
  /** 账号未激活（用户已注册但未验证邮箱/手机） */
  USER_NOT_ACTIVATED("A20125", "userinfo.user.not.activated", 403),
  /** 账号已暂停（临时停用，可由管理员恢复） */
  USER_SUSPENDED("A20126", "userinfo.user.suspended", 403),
  /** 账号已离职（终态，不可再激活） */
  USER_RESIGNED("A20127", "userinfo.user.resigned", 403),

  // ==================== A20xxx Remember-Me ====================
  /** Remember-Me 已过期（超过最大续期天数） */
  REMEMBER_ME_EXPIRED("A20140", "userinfo.remember.me.expired", 401),
  /** Remember-Me 无效（Cookie 校验失败或会话不存在） */
  REMEMBER_ME_INVALID("A20141", "userinfo.remember.me.invalid", 401),

  // ==================== A20xxx 跨域 SSO ====================
  /** 不受信的跨域来源 */
  SSO_DOMAIN_NOT_TRUSTED("A20137", "userinfo.sso.domain.not.trusted", 403),
  /** 令牌交换失败 */
  SSO_TOKEN_EXCHANGE_FAILED("A20138", "userinfo.sso.token.exchange.failed", 401),
  /** CORS 预检失败 */
  CORS_PREFLIGHT_FAILED("A20139", "userinfo.cors.preflight.failed", 403),

  // ==================== B33xxx 自助服务 ====================
  /** 自助注册功能未开启 */
  SELF_REGISTRATION_DISABLED("B33001", "userinfo.self.registration.disabled"),
  /** 验证码已过期或无效 */
  VERIFY_CODE_INVALID("B33002", "userinfo.verify.code.invalid"),
  /** 验证码发送过于频繁 */
  VERIFY_CODE_RATE_LIMITED("B33003", "userinfo.verify.code.rate.limited"),
  /** 找回密码账号不存在 */
  FORGOT_PASSWORD_USER_NOT_FOUND("B33004", "userinfo.forgot.password.user.not.found"),
  /** 找回密码手机号与账号不匹配 */
  FORGOT_PASSWORD_PHONE_MISMATCH("B33005", "userinfo.forgot.password.phone.mismatch"),
  /** 账号未锁定，无需解锁 */
  ACCOUNT_NOT_LOCKED("B33006", "userinfo.account.not.locked"),
  /** 账号解锁失败，验证信息不匹配 */
  ACCOUNT_UNLOCK_FAILED("B33007", "userinfo.account.unlock.failed"),
  /** 账号解锁验证码已过期或无效 */
  ACCOUNT_UNLOCK_VERIFY_CODE_INVALID("B33008", "userinfo.account.unlock.verify.code.invalid"),

  // ==================== A20xxx 封禁治理（运营侧主动封禁与管理员会话治理） ====================
  /** 账号已被封禁 */
  USER_BANNED("A20133", "userinfo.user.banned", 403),
  /** 账号已被永久封禁 */
  USER_BANNED_PERMANENT("A20134", "userinfo.user.banned.permanent", 403),
  /** 不能封禁管理员 */
  CANNOT_BAN_ADMIN("A20135", "userinfo.cannot.ban.admin", 403),
  /** 不能封禁自己 */
  CANNOT_BAN_SELF("A20136", "userinfo.cannot.ban.self", 400),

  // ==================== A20xxx API 参数签名（P0-7） ====================
  /** 缺少签名参数（X-Timestamp/X-Nonce/X-Signature 任一缺失） */
  SIGNATURE_REQUIRED("A20129", "userinfo.signature.required", 401),
  /** 签名无效（签名值不匹配） */
  SIGNATURE_INVALID("A20130", "userinfo.signature.invalid", 401),
  /** 签名已过期（时间戳超出有效期窗口） */
  SIGNATURE_EXPIRED("A20131", "userinfo.signature.expired", 401),
  /** Nonce 已被使用（疑似重放攻击） */
  NONCE_REUSED("A20132", "userinfo.nonce.reused", 401),

  // ==================== B34xxx 社交认证 ====================
  /** 社交认证功能未开启 */
  SOCIAL_AUTH_DISABLED("B34001", "userinfo.social.auth.disabled"),
  /** 不支持的社交平台 */
  SOCIAL_PLATFORM_NOT_SUPPORTED("B34002", "userinfo.social.platform.not.supported"),
  /** 该社交账号已绑定到其他用户 */
  SOCIAL_BIND_EXISTS("B34003", "userinfo.social.bind.exists"),
  /** 社交账号未绑定 */
  SOCIAL_ACCOUNT_NOT_BOUND("B34004", "userinfo.social.account.not.bound"),
  /** 社交认证失败 */
  SOCIAL_AUTH_FAILED("B34005", "userinfo.social.auth.failed"),

  // ==================== B35xxx LDAP 同步 ====================
  /** LDAP 同步功能未开启 */
  LDAP_SYNC_DISABLED("B35001", "userinfo.ldap.sync.disabled"),
  /** LDAP 同步正在进行中 */
  LDAP_SYNC_IN_PROGRESS("B35002", "userinfo.ldap.sync.in.progress"),
  /** LDAP 同步失败 */
  LDAP_SYNC_FAILED("B35003", "userinfo.ldap.sync.failed"),
  /** LDAP 连接失败 */
  LDAP_CONNECTION_FAILED("B35004", "userinfo.ldap.connection.failed"),

  // ==================== B36xxx SCIM 2.0 用户供给 ====================
  /** SCIM 服务未开启 */
  SCIM_DISABLED("B36001", "userinfo.scim.disabled"),
  /** SCIM 认证失败（Bearer Token 无效） */
  SCIM_AUTH_FAILED("B36002", "userinfo.scim.auth.failed", 401),
  /** SCIM 用户不存在 */
  SCIM_USER_NOT_FOUND("B36003", "userinfo.scim.user.not.found", 404),
  /** SCIM 过滤表达式解析错误 */
  SCIM_FILTER_PARSE_ERROR("B36004", "userinfo.scim.filter.parse.error"),
  /** SCIM PATCH 操作无效 */
  SCIM_PATCH_INVALID("B36005", "userinfo.scim.patch.invalid"),

  // ==================== B37xxx SAML 2.0 ====================
  /** SAML 配置缺失（IdP 端点或证书未配置） */
  SAML_CONFIG_MISSING("B37001", "userinfo.saml.config.missing"),
  /** SAML Response 无效或解析失败 */
  SAML_RESPONSE_INVALID("B37002", "userinfo.saml.response.invalid"),
  /** SAML 签名缺失 */
  SAML_SIGNATURE_MISSING("B37003", "userinfo.saml.signature.missing"),
  /** SAML 签名验证失败 */
  SAML_SIGNATURE_INVALID("B37004", "userinfo.saml.signature.invalid"),
  /** SAML 断言已过期 */
  SAML_ASSERTION_EXPIRED("B37005", "userinfo.saml.assertion.expired"),
  /** SAML 断言尚未生效 */
  SAML_ASSERTION_NOT_YET_VALID("B37006", "userinfo.saml.assertion.not.yet.valid"),
  /** SAML Audience 不匹配 */
  SAML_AUDIENCE_MISMATCH("B37007", "userinfo.saml.audience.mismatch"),
  /** SAML SSO 发起失败 */
  SAML_SSO_INIT_FAILED("B37008", "userinfo.saml.sso.init.failed"),

  // ==================== B38xxx OIDC ====================
  /** OIDC 配置无效 */
  OIDC_CONFIG_INVALID("B38001", "userinfo.oidc.config.invalid"),
  /** OIDC nonce 无效或已使用 */
  OIDC_NONCE_INVALID("B38002", "userinfo.oidc.nonce.invalid"),
  /** OIDC ID Token 签发失败 */
  OIDC_ID_TOKEN_ISSUE_FAILED("B38003", "userinfo.oidc.id.token.issue.failed"),

  // ==================== B39xxx WebAuthn/Passkey ====================
  /** WebAuthn 功能未开启 */
  WEBAUTHN_DISABLED("B39001", "userinfo.webauthn.disabled"),
  /** WebAuthn 挑战码已过期 */
  WEBAUTHN_CHALLENGE_EXPIRED("B39002", "userinfo.webauthn.challenge.expired"),
  /** WebAuthn 挑战码类型不匹配 */
  WEBAUTHN_CHALLENGE_TYPE_MISMATCH("B39003", "userinfo.webauthn.challenge.type.mismatch"),
  /** WebAuthn 挑战码用户不匹配 */
  WEBAUTHN_CHALLENGE_USER_MISMATCH("B39004", "userinfo.webauthn.challenge.user.mismatch"),
  /** WebAuthn 客户端数据无效 */
  WEBAUTHN_CLIENT_DATA_INVALID("B39005", "userinfo.webauthn.client.data.invalid"),
  /** WebAuthn 签名验证失败 */
  WEBAUTHN_SIGNATURE_INVALID("B39006", "userinfo.webauthn.signature.invalid"),
  /** WebAuthn 凭证不存在 */
  WEBAUTHN_CREDENTIAL_NOT_FOUND("B39007", "userinfo.webauthn.credential.not.found"),
  /** WebAuthn 凭证已存在 */
  WEBAUTHN_CREDENTIAL_EXISTS("B39008", "userinfo.webauthn.credential.exists"),
  /** WebAuthn 凭证不属于当前用户 */
  WEBAUTHN_CREDENTIAL_NOT_BELONG_TO_USER("B39009", "userinfo.webauthn.credential.not.belong.to.user"),
  /** WebAuthn 凭证数已达上限 */
  WEBAUTHN_CREDENTIAL_LIMIT_REACHED("B39010", "userinfo.webauthn.credential.limit.reached");

  /** 错误码（业务契约，不应轻易变更） */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  UserInfoExceptionCode(String code, String key) {
    this(code, key, 400);
  }

  UserInfoExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
