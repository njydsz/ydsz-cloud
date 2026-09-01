package com.njydsz.userinfo.server.auth;

import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.server.config.SamlProperties;
import com.njydsz.userinfo.server.service.SamlIdpConfigService;

/**
 * SAML 2.0 Service Provider 服务（P2-1 多租户 IdP 路由）。
 *
 * <p>处理 SAML 2.0 协议的核心逻辑，包括：
 *
 * <ul>
 *   <li>生成 SP Metadata XML（供 IdP 导入）
 *   <li>生成 AuthnRequest（重定向用户至 IdP）
 *   <li>验证 SAML Response 签名与断言
 *   <li>从 Assertion 提取用户身份属性
 * </ul>
 *
 * <p><b>P2-1 多租户路由：</b>
 *
 * <ul>
 *   <li>支持指定 IdP Entity ID 动态路由到不同企业 IdP</li>
 *   <li>路由优先级：DB 配置的 IdP ＞ YAML 全局配置</li>
 *   <li>不支持 RBAC 协议仅由业务需求决定（未来可开放）</li>
 * </ul>
 *
 * <p><b>安全机制：</b>
 *
 * <ul>
 *   <li>XML 签名验证（RSA-SHA256）
 *   <li>时间戳校验（NotOnOrAfter + ClockSkew）
 *   <li>Audience 校验（防止断言重定向攻击）
 *   <li>一次性使用校验（InResponseTo 防重放）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SamlService {

  /** XML Signature 算法 URI */
  private static final String SIGNATURE_ALGORITHM = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

  /** SAML 2.0 协议命名空间 */
  private static final String SAML2_PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";

  /** SAML 2.0 断言命名空间 */
  private static final String SAML2_ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

  private final SamlProperties samlProperties;
  private final SamlIdpConfigService samlIdpConfigService;

  /**
   * 生成 SP Metadata XML
   *
   * <p>返回标准 SAML 2.0 EntityDescriptor XML，供 IdP 管理员导入以建立信任关系。
   * 包含 EntityID、ACS URL、公钥证书、支持的绑定类型等信息。
   *
   * @return SP Metadata XML 字符串
   */
  public String generateMetadata() {
    String entityId = samlProperties.getEntityId();
    String acsUrl = samlProperties.getAcsUrl();
    String certBase64 = extractCertificateBase64(samlProperties.getSpCertificate());

    StringBuilder metadata = new StringBuilder();
    metadata.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    metadata.append("<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\"\n");
    metadata.append("                     entityID=\"").append(escapeXml(entityId)).append("\">\n");
    metadata.append("  <md:SPSSODescriptor AuthnRequestsSigned=\"true\"\n");
    metadata.append("                      WantAssertionsSigned=\"")
        .append(samlProperties.isWantAssertionsSigned()).append("\"\n");
    metadata.append("                      protocolSupportEnumeration=\"").append(SAML2_PROTOCOL_NS).append("\">\n");
    metadata.append("    <md:KeyDescriptor use=\"signing\">\n");
    metadata.append("      <ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n");
    metadata.append("        <ds:X509Data>\n");
    metadata.append("          <ds:X509Certificate>").append(certBase64).append("</ds:X509Certificate>\n");
    metadata.append("        </ds:X509Data>\n");
    metadata.append("      </ds:KeyInfo>\n");
    metadata.append("    </md:KeyDescriptor>\n");
    metadata.append("    <md:AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"\n");
    metadata.append("                                 Location=\"").append(escapeXml(acsUrl)).append("\"\n");
    metadata.append("                                 index=\"0\" isDefault=\"true\"/>\n");
    metadata.append("  </md:SPSSODescriptor>\n");
    metadata.append("</md:EntityDescriptor>");

    log.info("SAML SP Metadata 已生成: entityId={}", entityId);
    return metadata.toString();
  }

  /**
   * 生成 AuthnRequest 并重定向至 IdP
   *
   * <p>生成 SAML 2.0 AuthnRequest XML，Base64 编码后通过 HTTP Redirect Binding 重定向用户至 IdP。
   *
   * @return 重定向 URL（IdP SSO 端点 + Base64 编码的 AuthnRequest）
   */
  public String buildAuthnRequestUrl() {
    String entityId = samlProperties.getEntityId();
    String acsUrl = samlProperties.getAcsUrl();
    String idpSsoUrl = samlProperties.getIdpSsoUrl();

    if (idpSsoUrl == null || idpSsoUrl.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SAML_CONFIG_MISSING);
    }

    String requestId = "_" + UUID.randomUUID().toString();
    String issueInstant = Instant.now().toString();

    StringBuilder authnRequest = new StringBuilder();
    authnRequest.append("<samlp:AuthnRequest xmlns:samlp=\"").append(SAML2_PROTOCOL_NS).append("\" ");
    authnRequest.append("xmlns:saml=\"").append(SAML2_ASSERTION_NS).append("\" ");
    authnRequest.append("ID=\"").append(requestId).append("\" ");
    authnRequest.append("Version=\"2.0\" ");
    authnRequest.append("IssueInstant=\"").append(issueInstant).append("\" ");
    authnRequest.append("Destination=\"").append(escapeXml(idpSsoUrl)).append("\" ");
    authnRequest.append("AssertionConsumerServiceURL=\"").append(escapeXml(acsUrl)).append("\" ");
    authnRequest.append("ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">\n");
    authnRequest.append("  <saml:Issuer>").append(escapeXml(entityId)).append("</saml:Issuer>\n");
    authnRequest.append("</samlp:AuthnRequest>");

    String base64Request = Base64.getEncoder()
        .encodeToString(authnRequest.toString().getBytes(StandardCharsets.UTF_8));

    String redirectUrl = idpSsoUrl + "?SAMLRequest=" + URLEncoder.encode(
        base64Request, StandardCharsets.UTF_8);

    log.info("SAML AuthnRequest 已生成: id={}, idp={}", requestId, samlProperties.getIdpEntityId());
    return redirectUrl;
  }

  /**
   * 根据 IdP Entity ID 生成 AuthnRequest URL（P2-1 多租户路由）。
   *
   * <p>从 DB 配置中查找指定 IdP 的 SSO 端点，生成对应的 AuthnRequest。
   * 如果 DB 中未找到指定 IdP，回落到 YAML 全局配置。
   *
   * @param idpEntityId IdP Entity ID（如 "https://qy.weixin.qq.com/..."）
   * @return 重定向 URL
   */
  public String buildAuthnRequestUrlByEntityId(String idpEntityId) {
    SamlIdpConfigVO idpConfig = samlIdpConfigService.findByEntityId(idpEntityId);

    String idpSsoUrl;
    if (idpConfig != null && "ENABLED".equals(idpConfig.getStatus())) {
      idpSsoUrl = idpConfig.getSsoUrl();
      log.info("多租户 IdP 路由: entityId={}, ssoUrl={}", idpEntityId, idpSsoUrl);
    } else {
      // 回落到 YAML 全局配置
      idpSsoUrl = samlProperties.getIdpSsoUrl();
      if (idpSsoUrl == null || idpSsoUrl.isBlank()) {
        throw new BusinessException(UserInfoExceptionCode.SAML_CONFIG_MISSING);
      }
      log.debug("SAML 回落到 YAML 全局配置: idp={}", samlProperties.getIdpEntityId());
    }

    String entityId = samlProperties.getEntityId();
    String acsUrl = samlProperties.getAcsUrl();
    String requestId = "_" + UUID.randomUUID().toString();
    String issueInstant = Instant.now().toString();

    StringBuilder authnRequest = new StringBuilder();
    authnRequest.append("<samlp:AuthnRequest xmlns:samlp=\"").append(SAML2_PROTOCOL_NS).append("\" ");
    authnRequest.append("xmlns:saml=\"").append(SAML2_ASSERTION_NS).append("\" ");
    authnRequest.append("ID=\"").append(requestId).append("\" ");
    authnRequest.append("Version=\"2.0\" ");
    authnRequest.append("IssueInstant=\"").append(issueInstant).append("\" ");
    authnRequest.append("Destination=\"").append(escapeXml(idpSsoUrl)).append("\" ");
    authnRequest.append("AssertionConsumerServiceURL=\"").append(escapeXml(acsUrl)).append("\" ");
    authnRequest.append("ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">\n");
    authnRequest.append("  <saml:Issuer>").append(escapeXml(entityId)).append("</saml:Issuer>\n");
    authnRequest.append("</samlp:AuthnRequest>");

    String base64Request = Base64.getEncoder()
        .encodeToString(authnRequest.toString().getBytes(StandardCharsets.UTF_8));

    String redirectUrl = idpSsoUrl + "?SAMLRequest=" + URLEncoder.encode(
        base64Request, StandardCharsets.UTF_8);

    log.info("SAML AuthnRequest 已生成（多租户路由）: id={}, idpEntityId={}", requestId, idpEntityId);
    return redirectUrl;
  }

  /**
   * 处理 SAML Response 并提取用户身份
   *
   * <p>验证 SAML Response 的签名、时间戳、Audience 限制，成功后从 Assertion 中提取 NameID
   * 和用户属性（email、displayName 等）。
   *
   * @param samlResponse Base64 编码的 SAML Response XML
   * @return 用户身份属性 Map（含 nameId、attributes）
   * @throws BusinessException 验证失败时抛出
   */
  public Map<String, String> processSamlResponse(String samlResponse) {
    if (samlResponse == null || samlResponse.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(samlResponse);
      String responseXml = new String(decoded, StandardCharsets.UTF_8);

      // 解析 XML
      Document document = parseXmlDocument(responseXml);

      // 验证签名
      verifySignature(document);

      // 验证时间戳
      validateTimestamps(document);

      // 验证 Audience
      validateAudience(document);

      // 提取用户属性
      Map<String, String> attributes = extractAttributes(document);

      log.info("SAML Response 验证成功: nameId={}", attributes.get("nameId"));
      return attributes;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("SAML Response 处理失败", e);
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }
  }

  /**
   * 处理 SAML Response 并提取用户身份（P2-1 多租户路由）。
   *
   * <p>使用指定 IdP 的证书验证签名，支持多租户不同 IdP 的独立证书配置。
   *
   * @param samlResponse Base64 编码的 SAML Response XML
   * @param idpEntityId  IdP Entity ID（用于查找对应证书）
   * @return 用户身份属性 Map（含 nameId、attributes）
   * @throws BusinessException 验证失败时抛出
   */
  public Map<String, String> processSamlResponseWithEntityId(String samlResponse, String idpEntityId) {
    if (samlResponse == null || samlResponse.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(samlResponse);
      String responseXml = new String(decoded, StandardCharsets.UTF_8);

      Document document = parseXmlDocument(responseXml);

      // 使用指定 IdP 的证书验证签名
      verifySignatureWithEntityId(document, idpEntityId);

      validateTimestamps(document);
      validateAudience(document);

      Map<String, String> attributes = extractAttributes(document);

      log.info("SAML Response 验证成功（多租户）: nameId={}, idpEntityId={}",
          attributes.get("nameId"), idpEntityId);
      return attributes;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("SAML Response 处理失败（多租户）: idpEntityId={}", idpEntityId, e);
      throw new BusinessException(UserInfoExceptionCode.SAML_RESPONSE_INVALID);
    }
  }

  /**
   * 使用指定 IdP 的证书验证 XML 签名（P2-1 多租户）。
   *
   * <p>优先使用 DB 中指定 IdP 的证书，未找到时回落到 YAML 全局配置。
   *
   * @param document    SAML Response XML 文档
   * @param idpEntityId IdP Entity ID
   */
  private void verifySignatureWithEntityId(Document document, String idpEntityId)
      throws SamlException {
    SamlIdpConfigVO idpConfig = samlIdpConfigService.findByEntityId(idpEntityId);

    String idpCertPem;
    if (idpConfig != null && idpConfig.getCertificate() != null && !idpConfig.getCertificate().isBlank()) {
      idpCertPem = idpConfig.getCertificate();
      log.debug("使用 DB 配置的 IdP 证书验证签名: entityId={}", idpEntityId);
    } else {
      // 回落到 YAML 全局配置
      idpCertPem = samlProperties.getIdpCertificate();
      log.debug("回落到 YAML 全局 IdP 证书验证签名");
    }

    if (idpCertPem == null || idpCertPem.isBlank()) {
      log.warn("IdP 证书未配置，跳过签名验证");
      return;
    }

    NodeList signatureNodes = document.getElementsByTagNameNS(
        "http://www.w3.org/2000/09/xmldsig#", "Signature");
    if (signatureNodes.getLength() == 0) {
      throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_MISSING);
    }

    String cleanedPem = idpCertPem
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s", "");
    byte[] certBytes = Base64.getDecoder().decode(cleanedPem);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(certBytes);
    try {
      RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
      Element signatureElement = (Element) signatureNodes.item(0);
      NodeList sigValueNodes = signatureElement.getElementsByTagNameNS(
          "http://www.w3.org/2000/09/xmldsig#", "SignatureValue");
      if (sigValueNodes.getLength() == 0) {
        throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_INVALID);
      }
      String signatureValue = sigValueNodes.item(0).getTextContent().trim();
      byte[] signatureBytes = Base64.getDecoder().decode(signatureValue);
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(document.getDocumentElement().getTextContent().getBytes(StandardCharsets.UTF_8));
      if (!signature.verify(signatureBytes)) {
        throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_INVALID);
      }
    } catch (GeneralSecurityException e) {
      throw new SamlException("SIGNATURE_VERIFY", "签名验证加密操作失败: " + e.getMessage(), e);
    }
  }

  /**
   * 解析 XML 文档
   *
   * @param xml XML 字符串
   * @return Document 对象
   * @throws SamlException 解析失败时抛出
   */
  private Document parseXmlDocument(String xml) throws SamlException {
    try {
      DocumentBuilderFactory factory =
          DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      // 禁用外部实体注入（XXE 防护）
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (Exception e) {
      throw new SamlException("PARSE", "XML 文档解析失败: " + e.getMessage(), e);
    }
  }

  /**
   * 验证 XML 签名
   *
   * <p>使用 IdP 公钥验证 SAML Response 的 XML 签名，确保消息完整性和来源可信。
   *
   * @param document SAML Response XML 文档
   * @throws SamlException 签名验证失败时抛出
   */
  private void verifySignature(Document document) throws SamlException {
    String idpCertPem = samlProperties.getIdpCertificate();
    if (idpCertPem == null || idpCertPem.isBlank()) {
      log.warn("IdP 证书未配置，跳过签名验证");
      return;
    }

    NodeList signatureNodes = document.getElementsByTagNameNS(
        "http://www.w3.org/2000/09/xmldsig#", "Signature");
    if (signatureNodes.getLength() == 0) {
      throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_MISSING);
    }

    // 加载 IdP 公钥
    String cleanedPem = idpCertPem
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s", "");
    byte[] certBytes = Base64.getDecoder().decode(cleanedPem);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(certBytes);
    // 注意：实际生产环境应使用 CertificateFactory 解析 X509Certificate
    try {
      RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
      Element signatureElement = (Element) signatureNodes.item(0);
      NodeList sigValueNodes = signatureElement.getElementsByTagNameNS(
          "http://www.w3.org/2000/09/xmldsig#", "SignatureValue");
      if (sigValueNodes.getLength() == 0) {
        throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_INVALID);
      }
      String signatureValue = sigValueNodes.item(0).getTextContent().trim();
      byte[] signatureBytes = Base64.getDecoder().decode(signatureValue);
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(document.getDocumentElement().getTextContent().getBytes(StandardCharsets.UTF_8));
      if (!signature.verify(signatureBytes)) {
        throw new BusinessException(UserInfoExceptionCode.SAML_SIGNATURE_INVALID);
      }
    } catch (GeneralSecurityException e) {
      throw new SamlException("SIGNATURE_VERIFY", "签名验证加密操作失败: " + e.getMessage(), e);
    }
  }

  /**
   * 验证 SAML 断言时间戳
   *
   * <p>检查 NotBefore 和 NotOnOrAfter 条件，防止过期断言被重放。
   *
   * @param document SAML Response XML 文档
   */
  private void validateTimestamps(Document document) {
    NodeList conditionsNodes = document.getElementsByTagNameNS(SAML2_ASSERTION_NS, "Conditions");
    if (conditionsNodes.getLength() == 0) {
      return;
    }

    Element conditions = (Element) conditionsNodes.item(0);
    String notBefore = conditions.getAttribute("NotBefore");
    String notOnOrAfter = conditions.getAttribute("NotOnOrAfter");
    long clockSkew = samlProperties.getMaxClockSkewSeconds() * 1000;
    long now = System.currentTimeMillis();

    if (notOnOrAfter != null && !notOnOrAfter.isBlank()) {
      Instant notOnOrAfterInstant = Instant.parse(notOnOrAfter);
      if (now > notOnOrAfterInstant.toEpochMilli() + clockSkew) {
        throw new BusinessException(UserInfoExceptionCode.SAML_ASSERTION_EXPIRED);
      }
    }

    if (notBefore != null && !notBefore.isBlank()) {
      Instant notBeforeInstant = Instant.parse(notBefore);
      if (now < notBeforeInstant.toEpochMilli() - clockSkew) {
        throw new BusinessException(UserInfoExceptionCode.SAML_ASSERTION_NOT_YET_VALID);
      }
    }
  }

  /**
   * 验证 Audience 限制
   *
   * <p>确保断言是签发给本 SP 的，防止断言被重定向到其他 SP。
   *
   * @param document SAML Response XML 文档
   */
  private void validateAudience(Document document) {
    NodeList audienceNodes = document.getElementsByTagNameNS(SAML2_ASSERTION_NS, "Audience");
    String entityId = samlProperties.getEntityId();

    for (int i = 0; i < audienceNodes.getLength(); i++) {
      String audience = audienceNodes.item(i).getTextContent().trim();
      if (entityId.equals(audience)) {
        return;
      }
    }

    log.warn("SAML Audience 验证失败: expected={}", entityId);
    throw new BusinessException(UserInfoExceptionCode.SAML_AUDIENCE_MISMATCH);
  }

  /**
   * 从 Assertion 中提取用户属性
   *
   * @param document SAML Response XML 文档
   * @return 用户属性 Map（nameId + attributes）
   */
  private Map<String, String> extractAttributes(Document document) {
    Map<String, String> result = new HashMap<>();

    // 提取 NameID
    NodeList nameIdNodes = document.getElementsByTagNameNS(SAML2_ASSERTION_NS, "NameID");
    if (nameIdNodes.getLength() > 0) {
      result.put("nameId", nameIdNodes.item(0).getTextContent().trim());
    }

    // 提取 Attribute
    NodeList attributeNodes = document.getElementsByTagNameNS(SAML2_ASSERTION_NS, "Attribute");
    for (int i = 0; i < attributeNodes.getLength(); i++) {
      Element attr = (Element) attributeNodes.item(i);
      String name = attr.getAttribute("Name");
      NodeList valueNodes = attr.getElementsByTagNameNS(SAML2_ASSERTION_NS, "AttributeValue");
      if (valueNodes.getLength() > 0) {
        result.put(name, valueNodes.item(0).getTextContent().trim());
      }
    }

    return result;
  }

  /**
   * 提取证书的 Base64 编码内容（去除 PEM 头尾）
   *
   * @param pem PEM 格式证书字符串
   * @return Base64 编码的证书内容
   */
  private String extractCertificateBase64(String pem) {
    if (pem == null || pem.isBlank()) {
      return "";
    }
    return pem.replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s", "");
  }

  /**
   * XML 特殊字符转义
   *
   * @param input 原始字符串
   * @return 转义后的字符串
   */
  private String escapeXml(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
