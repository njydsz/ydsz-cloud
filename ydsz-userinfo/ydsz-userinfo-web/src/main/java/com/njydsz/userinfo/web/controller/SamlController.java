package com.njydsz.userinfo.web.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.SamlService;

/**
 * SAML 2.0 Service Provider 端点 Controller
 *
 * <p>实现 SAML 2.0 SP 角色的核心端点，包括：
 *
 * <ul>
 *   <li>{@code GET /saml/metadata} — SP 元数据端点（供 IdP 导入）
 *   <li>{@code GET /saml/sso} — SSO 发起端点（重定向至 IdP）
 *   <li>{@code POST /saml/acs} — Assertion Consumer Service（IdP 回调）
 * </ul>
 *
 * <p><b>SSO 流程：</b>
 *
 * <ol>
 *   <li>用户访问 {@code /saml/sso}，SP 生成 AuthnRequest 并重定向至 IdP</li>
 *   <li>用户在 IdP 完成认证，IdP 将 SAML Response POST 至 {@code /saml/acs}</li>
 *   <li>SP 验证签名、时间戳、Audience，提取用户身份并建立会话</li>
 *   <li>重定向用户至业务系统主页</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/saml")
@RequiredArgsConstructor
@Tag(name = "SAML 2.0", description = "SAML Service Provider 标准端点")
public class SamlController {

  /** SAML 2.0 SP 服务 */
  private final SamlService samlService;

  /**
   * SAML SP Metadata 端点
   *
   * <p>返回标准 SAML 2.0 EntityDescriptor XML，供 IdP 管理员导入建立信任关系。
   * 包含 EntityID、ACS URL、公钥证书等信息。
   *
   * @return SP Metadata XML（Content-Type: application/xml）
   */
  @GetMapping(value = "/metadata", produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(
      summary = "SP Metadata",
      description = "返回 SAML 2.0 Service Provider 的元数据 XML，供 IdP 导入建立信任")
  public ResponseEntity<String> metadata() {
    String metadata = samlService.generateMetadata();
    return ResponseEntity.ok(metadata);
  }

  /**
   * SAML SSO 发起端点
   *
   * <p>生成 AuthnRequest 并重定向用户至 IdP SSO 端点。
   * 用户完成 IdP 认证后，IdP 将 SAML Response 发送至 ACS 端点。
   *
   * @param response HTTP 响应对象（用于重定向）
   */
  @GetMapping("/sso")
  @Operation(
      summary = "SSO 发起",
      description = "生成 SAML AuthnRequest 并重定向用户至 IdP 完成认证")
  public void initiateSso(HttpServletResponse response) {
    try {
      String redirectUrl = samlService.buildAuthnRequestUrl();
      log.info("SAML SSO 发起: redirectUrl={}", redirectUrl.substring(0, Math.min(100, redirectUrl.length())));
      response.sendRedirect(redirectUrl);
    } catch (Exception e) {
      log.error("SAML SSO 发起失败", e);
      throw new BusinessException(UserInfoExceptionCode.SAML_SSO_INIT_FAILED);
    }
  }

  /**
   * SAML Assertion Consumer Service (ACS) 端点
   *
   * <p>接收 IdP 通过 HTTP POST 发送的 SAML Response，验证签名和断言后提取用户身份，
   * 建立本地会话并重定向用户至业务系统。
   *
   * @param samlResponse Base64 编码的 SAML Response（IdP POST 参数）
   * @param relayState 重定向状态（IdP 返回的原始地址）
   * @return 用户身份信息（实际生产环境应重定向至前端）
   */
  @Audit(
      module = "SAML",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'SAML SSO 登录成功: nameId=' + #samlResponse.substring(0, Math.min(20, #samlResponse.length()))")
  @RateLimit(resource = "userinfo.saml.acs", threshold = 30)
  @PostMapping(value = "/acs", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Assertion Consumer Service",
      description = "接收 IdP 的 SAML Response，验证后建立本地会话")
  public YdszResponse<Map<String, String>> assertionConsumerService(
      @RequestParam("SAMLResponse") String samlResponse,
      @RequestParam(value = "RelayState", required = false) String relayState) {

    if (samlResponse == null || samlResponse.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }

    // 验证 SAML Response 并提取用户属性
    Map<String, String> userAttributes = samlService.processSamlResponse(samlResponse);

    log.info("SAML ACS 处理成功: nameId={}, relayState={}",
        userAttributes.get("nameId"), relayState);

    // TODO: 根据 nameId 查找或创建本地用户，签发 JWT Token
    // 实际生产环境应将 token 写入 cookie/重定向至前端

    return YdszResponse.success(userAttributes);
  }
}
