package com.njydsz.common.auth.oidc;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * JWKS（JSON Web Key Set）公钥端点
 *
 * <p>生成符合 RFC 7517 标准的 JWKS 格式公钥集合，支持两种密钥类型：
 *
 * <ul>
 *   <li><b>HMAC 对称密钥</b>（默认）：返回 {@code kty=oct} 格式的密钥，适用于 HS256 签名算法
 *   <li><b>RSA 公钥</b>（配置 {@code publicKeyPem} 后）：返回 {@code kty=RSA} 格式的密钥，适用于 RS256 签名算法
 * </ul>
 *
 * <p>返回的 key 均包含 {@code use=sig} 标识（仅用于签名验证）和对应的 {@code alg} 字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JwksEndpointVO {

  /** JWKS 密钥类型：八位字节序列（对称密钥） */
  private static final String KTY_OCT = "oct";

  /** JWKS 密钥类型：RSA 公钥 */
  private static final String KTY_RSA = "RSA";

  /** 密钥用途：签名 */
  private static final String USE_SIG = "sig";

  /** HMAC-SHA256 算法标识 */
  private static final String ALG_HS256 = "HS256";

  /** RSA-SHA256 算法标识 */
  private static final String ALG_RS256 = "RS256";

  /** RSA PEM 头标记 */
  private static final String PEM_RSA_HEADER = "-----BEGIN PUBLIC KEY-----";

  /** RSA PEM 尾标记 */
  private static final String PEM_RSA_FOOTER = "-----END PUBLIC KEY-----";

  /** HMAC 密钥字节数组（用于 oct 格式输出） */
  private final byte[] hmacSecret;

  /** RSA 公钥 PEM 格式字符串（可选） */
  private final String publicKeyPem;

  /**
   * 构造 JWKS 端点
   *
   * @param hmacSecret   HMAC 签名密钥字节数组
   * @param publicKeyPem RSA 公钥 PEM 字符串（可为 null 或空表示不使用 RSA）
   */
  public JwksEndpointVO(byte[] hmacSecret, String publicKeyPem) {
    this.hmacSecret = hmacSecret;
    this.publicKeyPem = publicKeyPem;
  }

  /**
   * 生成 JWKS（JSON Web Key Set）格式 JSON 字符串
   *
   * <p>根据配置返回对应格式的密钥：
   *
   * <ul>
   *   <li>配置了 RSA 公钥时，返回 {@code kty=RSA, alg=RS256} 格式的 key（含 n/e 参数）
   *   <li>未配置 RSA 公钥时，返回 {@code kty=oct, alg=HS256} 格式的 key
   * </ul>
   *
   * @return JWKS 标准 JSON 字符串，格式如 {"keys":[{...}]}
   */
  public String generateJwks() {
    List<Map<String, Object>> keys = new ArrayList<>();

    if (publicKeyPem != null && !publicKeyPem.isBlank()) {
      try {
        Map<String, Object> rsaKey = buildRsaKey(publicKeyPem);
        keys.add(rsaKey);
      } catch (Exception e) {
        log.warn("RSA 公钥解析失败，降级为 HMAC oct 格式输出: {}", e.getMessage());
        keys.add(buildHmacKey(hmacSecret));
      }
    } else {
      keys.add(buildHmacKey(hmacSecret));
    }

    Map<String, Object> jwks = new LinkedHashMap<>();
    jwks.put("keys", keys);
    return YdszJson.toJson(jwks);
  }

  /**
   * 构建 HMAC 对称密钥条目
   *
   * @param secretKey HMAC 密钥字节数组
   * @return JWK Map 结构
   */
  private Map<String, Object> buildHmacKey(byte[] secretKey) {
    Map<String, Object> key = new LinkedHashMap<>();
    key.put("kty", KTY_OCT);
    key.put("use", USE_SIG);
    key.put("alg", ALG_HS256);
    // Base64URL 编码密钥（不含 padding）
    key.put("k", Base64.getUrlEncoder().withoutPadding().encodeToString(secretKey));
    return key;
  }

  /**
   * 构建 RSA 公钥条目
   *
   * @param pem RSA 公钥 PEM 格式字符串
   * @return JWK Map 结构（含 n/e RSA 参数）
   * @throws NoSuchAlgorithmException 当 RSA 算法不可用时抛出
   * @throws InvalidKeySpecException 当 PEM 公钥解析失败时抛出
   */
  private Map<String, Object> buildRsaKey(String pem)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    String cleaned = pem.replace(PEM_RSA_HEADER, "")
        .replace(PEM_RSA_FOOTER, "")
        .replaceAll("\\s", "");
    byte[] encoded = Base64.getDecoder().decode(cleaned);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
    RSAPublicKey rsaPublicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);

    Map<String, Object> key = new LinkedHashMap<>();
    key.put("kty", KTY_RSA);
    key.put("use", USE_SIG);
    key.put("alg", ALG_RS256);
    // Base64URL 编码 RSA 模数 n 和指数 e（不含 padding）
    key.put(
        "n",
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rsaPublicKey.getModulus().toByteArray()));
    key.put(
        "e",
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rsaPublicKey.getPublicExponent().toByteArray()));
    return key;
  }
}
