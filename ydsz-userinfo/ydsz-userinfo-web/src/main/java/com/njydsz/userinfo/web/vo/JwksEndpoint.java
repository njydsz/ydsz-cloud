package com.njydsz.userinfo.web.vo;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import lombok.extern.slf4j.Slf4j;

/**
 * JWKS（JSON Web Key Set）公钥端点（本地实现，避免 common-auth 依赖编译问题）。
 *
 * <p>生成符合 RFC 7517 标准的 JWKS 格式公钥集合。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JwksEndpoint {

  private static final String KTY_OCT = "oct";
  private static final String KTY_RSA = "RSA";
  private static final String USE_SIG = "sig";
  private static final String ALG_HS256 = "HS256";
  private static final String ALG_RS256 = "RS256";
  private static final String PEM_RSA_HEADER = "-----BEGIN PUBLIC KEY-----";
  private static final String PEM_RSA_FOOTER = "-----END PUBLIC KEY-----";

  private final byte[] hmacSecret;
  private final String publicKeyPem;

  public JwksEndpoint(byte[] hmacSecret, String publicKeyPem) {
    this.hmacSecret = hmacSecret;
    this.publicKeyPem = publicKeyPem;
  }

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

  private Map<String, Object> buildHmacKey(byte[] secretKey) {
    Map<String, Object> key = new LinkedHashMap<>();
    key.put("kty", KTY_OCT);
    key.put("use", USE_SIG);
    key.put("alg", ALG_HS256);
    key.put("k", Base64.getUrlEncoder().withoutPadding().encodeToString(secretKey));
    return key;
  }

  private Map<String, Object> buildRsaKey(String pem) throws GeneralSecurityException, IllegalArgumentException {
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
    key.put("n", Base64.getUrlEncoder().withoutPadding()
        .encodeToString(rsaPublicKey.getModulus().toByteArray()));
    key.put("e", Base64.getUrlEncoder().withoutPadding()
        .encodeToString(rsaPublicKey.getPublicExponent().toByteArray()));
    return key;
  }
}
