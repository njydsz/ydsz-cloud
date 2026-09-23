package com.njydsz.common.auth.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

/**
 * JWKS 公钥集数据（RFC 7517 / RFC 7518 JSON Web Key Set）
 *
 * <p>封装签名密钥的 JWKS 公钥 JSON 文档（{@code {"keys":[...]}}），供 OIDC Discovery 定时暴露给依赖方（RP），支持：
 *
 * <ul>
 *   <li>HMAC 对称密钥：{@code kty=oct, alg=HS256}（内部服务间使用，短效）</li>
 *   <li>RSA 公钥：{@code kty=RSA, alg=RS256, use=sig}（对外 OIDC 依赖方联邦，长效）</li>
 * </ul>
 *
 * <p>当前实现为静态时间点快照，密钥轮换需重新构造实例（配合重启 / 重载）。
 *
 * <p>JSON 文档构造采用原始 StringBuilder 拼接，避免引入第三方 JSON 依赖（符合 JSON 生态红线：
 * 业务代码禁止 import Jackson/Fastjson/Gson）。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public class JwksEndpoint {

  /** JWKS 文档 JSON 字符串 */
  private final String jwksJson;

  /** JWKS 文档 JSON 字节数组（UTF-8） */
  private final byte[] jwksBytes;

  /**
   * 构造 JWKS 端点数据
   *
   * <p>同时包含 HMAC oct 密钥（供内网 RP 使用）和可选 RSA 公钥（供外网 OIDC 依赖方联邦使用）。
   *
   * @param hmacKeyBytes HMAC 签名密钥内部字节数组（来自
   *        {@link javax.crypto.SecretKey#getEncoded()}），不可为 null
   * @param publicKeyPem RSA 公钥 PEM（可选；为 null 或空时仅暴露 oct 密钥）
   */
  public JwksEndpoint(byte[] hmacKeyBytes, String publicKeyPem) {
    Objects.requireNonNull(hmacKeyBytes, "HMAC 密钥字节数组不可为 null");
    this.jwksJson = buildJwksJson(hmacKeyBytes, publicKeyPem);
    this.jwksBytes = jwksJson.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 构造 JWKS 端点数据（仅 RSA 公钥，无 HMAC 密钥）
   *
   * <p>外网暴露专用方式：仅暴露 RSA 公钥，内网 RP 通过另一个端点获取包含 oct 密钥的 JWKS。
   *
   * @param publicKeyPem RSA 公钥 PEM 字符串，不可为 null 或空
   */
  public JwksEndpoint(String publicKeyPem) {
    Objects.requireNonNull(publicKeyPem, "RSA 公钥 PEM 不可为 null");
    if (publicKeyPem.isBlank()) {
      throw new IllegalArgumentException("RSA 公钥 PEM 不可为空");
    }
    this.jwksJson = buildRsaOnlyJwksJson(publicKeyPem);
    this.jwksBytes = jwksJson.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 构建完整 JWKS JSON 字符串
   *
   * @param hmacKeyBytes    HMAC 密钥字节数组
   * @param publicKeyPem    RSA 公钥 PEM（可选）
   * @return 序列化后的 JWKS JSON
   */
  private String buildJwksJson(byte[] hmacKeyBytes, String publicKeyPem) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"keys\":[");
    // OCT 对称密钥
    sb.append("{\"kty\":\"oct\",\"use\":\"sig\",\"alg\":\"HS256\",");
    sb.append("\"kid\":\"").append(computeKidBase64url(hmacKeyBytes)).append("\",");
    sb.append("\"k\":\"").append(Base64.getUrlEncoder().withoutPadding().encodeToString(hmacKeyBytes)).append("\"}");
    // RSA 公钥（可选）
    if (publicKeyPem != null && !publicKeyPem.isBlank()) {
      sb.append(",");
      sb.append("{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",");
      sb.append("\"kid\":\"").append(computeKidBase64url(publicKeyPem.getBytes(StandardCharsets.UTF_8))).append("\",");
      sb.append("\"n\":\"").append(escapeJson(stripPemHeader(publicKeyPem))).append("\",");
      sb.append("\"e\":\"AQAB\"}");
    }
    sb.append("]}");
    return sb.toString();
  }

  /**
   * 构建仅含 RSA 公钥的 JWKS JSON
   *
   * @param publicKeyPem RSA 公钥 PEM
   * @return 序列化后的 JWKS JSON
   */
  private String buildRsaOnlyJwksJson(String publicKeyPem) {
    StringBuilder sb = new StringBuilder(192);
    sb.append("{\"keys\":[{");
    sb.append("\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",");
    sb.append("\"kid\":\"").append(computeKidBase64url(publicKeyPem.getBytes(StandardCharsets.UTF_8))).append("\",");
    sb.append("\"n\":\"").append(escapeJson(stripPemHeader(publicKeyPem))).append("\",");
    sb.append("\"e\":\"AQAB\"}]}");
    return sb.toString();
  }

  /**
   * 计算密钥指纹（SHA-256 截断 80 bit，做 kid）
   *
   * @param content 原始字节
   * @return Base64URL 编码的 kid（不含 padding）
   */
  private String computeKidBase64url(byte[] content) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(content);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 MessageDigest 初始化失败", e);
    }
  }

  /**
   * 剥离 PEM 头尾和空白字符
   *
   * @param pem PEM 字符串
   * @return Base64 裸数据
   */
  private String stripPemHeader(String pem) {
    return pem.replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
        .replace("-----END RSA PUBLIC KEY-----", "")
        .replaceAll("\\s", "");
  }

  /**
   * 转义 JSON 特殊字符（仅处理 Base64 安全字符之外的引号和反斜杠）
   *
   * @param input Base64 字符串
   * @return 安全 JSON 字符串值
   */
  private String escapeJson(String input) {
    return input.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * 获取 JWKS JSON 字符串
   *
   * @return JWKS JSON 文档
   */
  public String getJwks() {
    return jwksJson;
  }

  /**
   * 获取 JWKS 字节数组（UTF-8）
   *
   * @return 字节数组
   */
  public byte[] getJwksBytes() {
    return jwksBytes;
  }

  /**
   * 获取 HTTP Content-Type 头值
   *
   * @return 固定值 "application/jwk-set+json"
   */
  public String getContentType() {
    return "application/jwk-set+json";
  }
}
