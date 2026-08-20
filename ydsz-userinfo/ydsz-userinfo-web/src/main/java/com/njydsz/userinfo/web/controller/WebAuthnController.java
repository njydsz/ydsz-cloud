package com.njydsz.userinfo.web.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.server.auth.RoleCacheService;
import com.njydsz.userinfo.server.auth.WebAuthnService;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * WebAuthn/Passkey 无密码认证 Controller（P3-2 通行 Key 增强）。
 *
 * <p>实现 FIDO2 WebAuthn 协议的注册与认证端点：
 *
 * <ul>
 *   <li>注册流程：获取选项 → 浏览器生成密钥 → 验证并存储凭证</li>
 *   <li>认证流程：获取选项 → 浏览器签名 → 验证签名并建立会话</li>
 *   <li>Passkey 流程：无用户名登录 → 浏览器自动发现 Passkey → 验证并建立会话</li>
 * </ul>
 *
 * <p><b>P3-2 Passkey 流程（无用户名登录）：</b>
 *
 * <ol>
 *   <li>前端访问 {@code /passkey/options} 获取认证选项（空 allowCredentials）</li>
 *   <li>浏览器自动弹出 Passkey 选择器（需配合 {@code mediation: "conditional"}）</li>
 *   <li>用户选择 Passkey 后，前端提交签名至 {@code /passkey/verify}</li>
 *   <li>验证通过后签发 JWT Token（access_token + refresh_token），用户无需输入密码完成登录</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webauthn")
@RequiredArgsConstructor
@Tag(name = "WebAuthn", description = "FIDO2 Passkey 无密码认证")
@ApiVersion("1")
public class WebAuthnController {

  /** WebAuthn 服务 */
  private final WebAuthnService webAuthnService;

  /** Token 服务（签发 access/refresh token） */
  private final TokenService tokenService;

  /** 角色缓存服务（加载用户角色用于 Token 声明） */
  private final RoleCacheService roleCacheService;

  // ==================== P3-2 Passkey 通行 Key 专用端点 ====================

  /**
   * 获取 Passkey 注册选项（P3-2 Discoverable Credential）。
   *
   * <p>与普通注册选项区别：{@code residentKey: "required"} 强制存储可发现凭证，
   * 使该 Passkey 可在后续无用户名认证时被浏览器自动发现。
   *
   * @param username    用户名（可选，默认取当前登录用户）
   * @param displayName 显示名称（可选）
   * @return Passkey 注册选项 Map
   */
  @GetMapping("/passkey/registration/options")
  @Operation(
      summary = "获取 Passkey 注册选项（可发现凭证）",
      description = "生成带有 residentKey=required 的注册选项，注册后的 Passkey 支持无用户名登录")
  public YdszResponse<Map<String, Object>> getPasskeyRegistrationOptions(
      String username,
      String displayName) {
    String userId = AuthContextUtils.getUserId();
    if (username == null || username.isBlank()) {
      username = AuthContextUtils.getUsername();
    }
    if (displayName == null || displayName.isBlank()) {
      displayName = username;
    }

    Map<String, Object> options = webAuthnService.generatePasskeyRegistrationOptions(
        userId, username, displayName);
    return YdszResponse.success(options);
  }

  /**
   * 获取 Passkey 认证选项（P3-2 无用户名登录）。
   *
   * <p>返回空 {@code allowCredentials} 列表，浏览器自动发现可用的 Passkey。
   * 前端配合 {@code mediation: "conditional"} 触发浏览器原生 Passkey 选择器。
   *
   * @return Passkey 认证选项 Map
   */
  @GetMapping("/passkey/options")
  @Operation(
      summary = "获取 Passkey 认证选项（无用户名登录）",
      description = "返回空 allowCredentials，浏览器自动发现并弹出 Passkey 选择器")
  public YdszResponse<Map<String, Object>> getPasskeyAuthenticationOptions() {
    Map<String, Object> options = webAuthnService.generatePasskeyAuthenticationOptions();
    return YdszResponse.success(options);
  }

  /**
   * 验证 Passkey 认证响应（P3-2 无用户名登录）。
   *
   * <p>验证 Passkey 签名后签发 JWT Token（access_token + refresh_token），
   * 用户无需输入密码完成登录。签名验证使用 webauthn4j 进行真实的 ECDSA/RSA 密码学验证。
   *
   * @param assertion 认证断言数据（challenge、credentialId、clientDataJSON、authenticatorData、signature）
   * @return 登录结果（accessToken、refreshToken、tokenType）
   */
  @Audit(
      module = "WebAuthn",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'Passkey 无用户名登录: credentialId=' + #assertion.credentialId")
  @RateLimit(resource = "userinfo.webauthn.passkeyAuth", threshold = 20)
  @PostMapping("/passkey/verify")
  @Operation(
      summary = "验证 Passkey 认证响应（无用户名登录）",
      description = "验证 Passkey 签名，通过后签发 JWT Token 完成登录")
  public YdszResponse<LoginVO> verifyPasskeyAuthentication(@RequestBody Map<String, Object> assertion) {
    String challenge = (String) assertion.get("challenge");
    String credentialId = (String) assertion.get("credentialId");
    String clientDataJSON = (String) assertion.get("clientDataJSON");
    String authenticatorData = (String) assertion.get("authenticatorData");
    String signature = (String) assertion.get("signature");

    String userId = webAuthnService.verifyPasskeyAuthentication(
        challenge, credentialId, clientDataJSON, authenticatorData, signature);

    // 根据 userId 查询用户信息并签发 JWT Token
    LoginVO loginVO = issueTokensForUser(userId);

    return YdszResponse.success(loginVO);
  }

  // ==================== 原有 WebAuthn 端点 ====================

  /**
   * 获取注册选项
   *
   * <p>生成 WebAuthn 注册参数（挑战码、用户信息、凭证参数），
   * 供前端调用 navigator.credentials.create() 生成密钥对。
   *
   * @param username 用户名（可选，默认取当前登录用户）
   * @param displayName 显示名称（可选）
   * @return 注册选项 Map
   */
  @GetMapping("/registration/options")
  @Operation(
      summary = "获取注册选项",
      description = "生成 WebAuthn 注册参数供浏览器生成密钥对")
  public YdszResponse<Map<String, Object>> getRegistrationOptions(
      String username,
      String displayName) {
    String userId = AuthContextUtils.getUserId();
    if (username == null || username.isBlank()) {
      username = AuthContextUtils.getUsername();
    }
    if (displayName == null || displayName.isBlank()) {
      displayName = username;
    }

    Map<String, Object> options = webAuthnService.generateRegistrationOptions(
        userId, username, displayName);
    return YdszResponse.success(options);
  }

  /**
   * 验证并存储注册凭证
   *
   * <p>验证浏览器提交的注册响应（attestation），验证通过后存储公钥凭证。
   *
   * @param credential 注册凭证数据（challenge、credentialId、publicKey 等）
   * @return 操作结果
   */
  @Audit(
      module = "WebAuthn",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'Passkey 注册: userId=' + #credential.userId")
  @RateLimit(resource = "userinfo.webauthn.register", threshold = 10)
  @PostMapping("/registration/verify")
  @Operation(
      summary = "验证并存储注册凭证",
      description = "验证浏览器提交的公钥凭证并持久化存储")
  public YdszResponse<Void> verifyRegistration(@RequestBody Map<String, Object> credential) {
    String userId = AuthContextUtils.getUserId();
    String challenge = (String) credential.get("challenge");
    String credentialId = (String) credential.get("credentialId");
    String publicKey = (String) credential.get("publicKey");
    String aaguid = (String) credential.get("aaguid");
    String clientDataJSON = (String) credential.get("clientDataJSON");

    webAuthnService.verifyAndStoreCredential(userId, challenge, credentialId,
        publicKey, aaguid, clientDataJSON);
    return YdszResponse.success();
  }

  /**
   * 获取认证选项
   *
   * <p>生成 WebAuthn 认证参数（挑战码、允许凭证列表），
   * 供前端调用 navigator.credentials.get() 进行签名。
   *
   * @param userId 用户 ID（可选，不提供时返回通用认证选项，用于 Passkey 发现）
   * @return 认证选项 Map
   */
  @GetMapping("/authentication/options")
  @Operation(
      summary = "获取认证选项",
      description = "生成 WebAuthn 认证参数供浏览器进行签名认证")
  public YdszResponse<Map<String, Object>> getAuthenticationOptions(String userId) {
    Map<String, Object> options = webAuthnService.generateAuthenticationOptions(userId);
    return YdszResponse.success(options);
  }

  /**
   * 验证认证响应
   *
   * <p>使用 webauthn4j 对浏览器提交的认证响应（assertion）进行真实的 ECDSA/RSA 密码学验证。
   * 验证成功后签发 JWT Token（access_token + refresh_token）完成登录。
   *
   * @param assertion 认证断言数据
   * @return 登录结果（accessToken、refreshToken、tokenType）
   */
  @Audit(
      module = "WebAuthn",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'Passkey 认证断言验证'")
  @RateLimit(resource = "userinfo.webauthn.authenticate", threshold = 20)
  @PostMapping("/authentication/verify")
  @Operation(
      summary = "验证认证响应",
      description = "验证浏览器提交的 Passkey 签名，通过后签发 JWT Token 完成登录")
  public YdszResponse<LoginVO> verifyAuthentication(@RequestBody Map<String, Object> assertion) {
    String challenge = (String) assertion.get("challenge");
    String credentialId = (String) assertion.get("credentialId");
    String clientDataJSON = (String) assertion.get("clientDataJSON");
    String authenticatorData = (String) assertion.get("authenticatorData");
    String signature = (String) assertion.get("signature");

    String userId = webAuthnService.verifyAuthenticationResponse(
        challenge, credentialId, clientDataJSON, authenticatorData, signature);

    // 根据 userId 查询用户信息并签发 JWT Token
    LoginVO loginVO = issueTokensForUser(userId);

    return YdszResponse.success(loginVO);
  }

  /**
   * 查询当前用户的 Passkey 列表
   *
   * @return Passkey 凭证列表
   */
  @GetMapping("/credentials")
  @Operation(
      summary = "查询 Passkey 列表",
      description = "返回当前用户注册的所有 Passkey 凭证")
  public YdszResponse<List<WebAuthnCredentialVO>> listCredentials() {
    String userId = AuthContextUtils.getUserId();
    List<WebAuthnCredentialVO> credentials = webAuthnService.listCredentials(userId);
    return YdszResponse.success(credentials);
  }

  /**
   * 删除指定的 Passkey
   *
   * @param credentialId 凭证 ID
   * @return 操作结果
   */
  @Audit(
      module = "WebAuthn",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'Passkey 删除: credentialId=' + #credentialId")
  @DeleteMapping("/credentials/{credentialId}")
  @Operation(
      summary = "删除 Passkey",
      description = "删除指定的 Passkey 凭证")
  public YdszResponse<Void> deleteCredential(@PathVariable String credentialId) {
    String userId = AuthContextUtils.getUserId();
    webAuthnService.deleteCredential(userId, credentialId);
    return YdszResponse.success();
  }

  // ==================== 私有方法 ====================

  /**
   * 根据用户 ID 查询用户信息并签发 JWT Token（access_token + refresh_token）
   *
   * <p>流程：
   * <ol>
   *   <li>根据 userId 查询用户账号信息</li>
   *   <li>加载用户角色列表</li>
   *   <li>构建 UserInfo 并签发 Token</li>
   *   <li>组装 LoginVO 返回</li>
   * </ol>
   *
   * @param userId 用户 ID
   * @return 登录结果 VO（含 accessToken、refreshToken）
   */
  private LoginVO issueTokensForUser(String userId) {
    // 查询用户账号信息
    var userAccount = webAuthnService.findUserById(userId);

    // 加载用户角色
    List<RoleVO> roles = roleCacheService.loadUserRoles(userId);
    String roleCodes = roles.stream().map(RoleVO::getRoleCode).reduce((a, b) -> a + "," + b).orElse("");
    String roleNames = roles.stream().map(RoleVO::getRoleName).reduce((a, b) -> a + "," + b).orElse("");

    // 构建 UserInfo 用于 Token 签发
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(userAccount.getId());
    userInfo.setUsername(userAccount.getUsername());
    userInfo.setRoleCode(roleCodes);
    userInfo.setRoleName(roleNames);
    userInfo.setTenantId(userAccount.getTenantId());

    // 签发 access_token 和 refresh_token
    String accessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);

    // 组装登录结果
    LoginVO loginVO = new LoginVO();
    loginVO.setAccessToken(accessToken);
    loginVO.setRefreshToken(refreshToken);
    loginVO.setTokenType("Bearer");

    return loginVO;
  }
}
