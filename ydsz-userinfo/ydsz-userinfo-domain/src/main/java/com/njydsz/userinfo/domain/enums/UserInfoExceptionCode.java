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
 *   <li>A20xxx 安全认证（锁号/MFA/Token，HTTP 401）
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

  // ==================== A20xxx 安全认证（二次认证） ====================
  /** 敏感操作需要二次认证 */
  SENSITIVE_VERIFY_REQUIRED("A20120", "userinfo.sensitive.verify.required", 401),
  /** 二次认证已过期，请重新验证 */
  SENSITIVE_VERIFY_EXPIRED("A20121", "userinfo.sensitive.verify.expired", 401),
  /** 二次认证密码错误 */
  SENSITIVE_VERIFY_PASSWORD_INCORRECT("A20122", "userinfo.sensitive.verify.password.incorrect", 401),

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
  FORGOT_PASSWORD_PHONE_MISMATCH("B33005", "userinfo.forgot.password.phone.mismatch");

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
