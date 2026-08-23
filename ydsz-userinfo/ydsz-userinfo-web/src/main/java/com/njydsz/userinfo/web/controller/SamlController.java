package com.njydsz.userinfo.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.server.auth.SamlService;
import com.njydsz.userinfo.server.service.SamlIdpConfigService;

/**
 * SAML 2.0 Service Provider 端点 Controller（P2-1 多租户 IdP 路由）。
 *
 * <p>实现 SAML 2.0 SP 角色的核心端点，包括：
 *
 * <ul>
 *   <li>{@code GET /saml/metadata} — SP 元数据端点（供 IdP 导入）
 *   <li>{@code GET /saml/sso} — SSO 发起端点（重定向至默认 IdP）
 *   <li>{@code GET /saml2/sso/{idpEntityId}} — 多租户 SSO 发起（动态路由到指定 IdP）
 *   <li>{@code POST /saml/acs} — Assertion Consumer Service（IdP 回调）
 *   <li>{@code POST /saml2/acs/{idpEntityId}} — 多租户 ACS 回调（使用指定 IdP 证书验证）
 *   <li>{@code GET /saml/idp-list} — 查询可用 IdP 列表（供登录页展示）
 * </ul>
 *
 * <p><b>多租户 SSO 流程：</b>
 *
 * <ol>
 *   <li>前端从 {@code /saml/idp-list} 获取可用 IdP 列表</li>
 *   <li>用户选择 IdP，前端重定向至 {@code /saml2/sso/{idpEntityId}}</li>
 *   <li>SP 从 DB 查找 IdP 配置，生成 AuthnRequest 并重定向至对应 IdP</li>
 *   <li>用户在 IdP 认证后，IdP POST SAML Response 至 {@code /saml2/acs/{idpEntityId}}</li>
 *   <li>SP 使用 IdP 对应证书验证签名，提取用户身份</li>
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
@ApiVersion("1")
public class SamlController {

  /** SAML 2.0 SP 服务 */
  private final SamlService samlService;

  /** SAML IdP 配置服务（P2-1 多租户） */
  private final SamlIdpConfigService samlIdpConfigService;

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

  // ==================== P2-1 多租户 IdP 路由端点 ====================

  /**
   * 多租户 SSO 发起端点（P2-1）。
   *
   * <p>根据 IdP Entity ID 从 DB 配置中查找对应 IdP 的 SSO 端点，生成 AuthnRequest 并重定向。
   * 支持不同企业使用不同的 SAML IdP（如企业微信 SAML、飞书 SAML、ADFS）。
   *
   * @param idpEntityId IdP Entity ID（URL 编码）
   * @param response    HTTP 响应对象（用于重定向）
   */
  @GetMapping("/sso/{idpEntityId}")
  @Operation(
      summary = "多租户 SSO 发起",
      description = "根据 IdP Entity ID 动态路由到指定企业的 SAML IdP")
  public void initiateSsoByEntityId(@PathVariable String idpEntityId, HttpServletResponse response) {
    try {
      String decodedEntityId = URLDecoder.decode(idpEntityId, StandardCharsets.UTF_8);
      String redirectUrl = samlService.buildAuthnRequestUrlByEntityId(decodedEntityId);
      log.info("多租户 SAML SSO 发起: idpEntityId={}", decodedEntityId);
      response.sendRedirect(redirectUrl);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("多租户 SAML SSO 发起失败: idpEntityId={}", idpEntityId, e);
      throw new BusinessException(UserInfoExceptionCode.SAML_SSO_INIT_FAILED);
    }
  }

  /**
   * 多租户 ACS 回调端点（P2-1）。
   *
   * <p>接收指定 IdP 的 SAML Response，使用 DB 中该 IdP 的证书验证签名。
   *
   * @param samlResponse Base64 编码的 SAML Response
   * @param idpEntityId  IdP Entity ID
   * @param relayState   重定向状态
   * @return 用户身份信息
   */
  @Audit(
      module = "SAML",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'多租户 SAML SSO 登录: idpEntityId=' + #idpEntityId")
  @RateLimit(resource = "userinfo.saml.acs.multi", threshold = 30)
  @PostMapping(value = "/acs/{idpEntityId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "多租户 ACS 回调",
      description = "使用指定 IdP 的证书验证 SAML Response")
  public YdszResponse<Map<String, String>> assertionConsumerServiceByEntityId(
      @RequestParam("SAMLResponse") String samlResponse,
      @PathVariable String idpEntityId,
      @RequestParam(value = "RelayState", required = false) String relayState) {

    if (samlResponse == null || samlResponse.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }

    String decodedEntityId = URLDecoder.decode(idpEntityId, StandardCharsets.UTF_8);
    Map<String, String> userAttributes = samlService.processSamlResponseWithEntityId(
        samlResponse, decodedEntityId);

    log.info("多租户 SAML ACS 处理成功: nameId={}, idpEntityId={}",
        userAttributes.get("nameId"), decodedEntityId);

    return YdszResponse.success(userAttributes);
  }

  /**
   * 查询可用 IdP 列表（P2-1）。
   *
   * <p>供前端登录页展示可用的 SAML IdP 列表，用户可选择对应的 IdP 进行 SSO 登录。
   *
   * @return 可用 IdP 列表
   */
  @GetMapping("/idp-list")
  @Operation(
      summary = "查询可用 IdP 列表",
      description = "返回所有已启用的 SAML IdP 配置（供登录页展示选择）")
  public YdszResponse<List<SamlIdpConfigVO>> listIdps() {
    return YdszResponse.success(samlIdpConfigService.findEnabled());
  }
}
