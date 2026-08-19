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
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.server.auth.WebAuthnService;

/**
 * WebAuthn/Passkey 无密码认证 Controller
 *
 * <p>实现 FIDO2 WebAuthn 协议的注册与认证端点：
 *
 * <ul>
 *   <li>注册流程：获取选项 → 浏览器生成密钥 → 验证并存储凭证</li>
 *   <li>认证流程：获取选项 → 浏览器签名 → 验证签名并建立会话</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webauthn")
@RequiredArgsConstructor
@Tag(name = "WebAuthn", description = "FIDO2 Passkey 无密码认证")
public class WebAuthnController {

  /** WebAuthn 服务 */
  private final WebAuthnService webAuthnService;

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
   * <p>验证浏览器提交的认证响应（assertion），验证成功后返回用户 ID（由上层签发 Token）。
   *
   * @param assertion 认证断言数据
   * @return 认证通过的用户 ID
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
      description = "验证浏览器提交的 Passkey 签名，成功后建立会话")
  public YdszResponse<String> verifyAuthentication(@RequestBody Map<String, Object> assertion) {
    String challenge = (String) assertion.get("challenge");
    String credentialId = (String) assertion.get("credentialId");
    String clientDataJSON = (String) assertion.get("clientDataJSON");
    String authenticatorData = (String) assertion.get("authenticatorData");
    String signature = (String) assertion.get("signature");

    String userId = webAuthnService.verifyAuthenticationResponse(
        challenge, credentialId, clientDataJSON, authenticatorData, signature);

    // TODO: 签发 JWT Token 或建立会话

    return YdszResponse.success(userId);
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
}
