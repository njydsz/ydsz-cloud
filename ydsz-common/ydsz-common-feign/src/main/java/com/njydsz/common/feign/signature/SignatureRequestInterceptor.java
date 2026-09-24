package com.njydsz.common.feign.signature;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign 请求签名拦截器（开放平台场景预留）。
 *
 * <p>读取 {@link FeignRequestSignature} 注解配置，对请求进行 HMAC-SHA256 签名， 并将签名结果写入 {@code X-Signature} /
 * {@code X-Timestamp} / {@code X-Nonce} 请求头。
 *
 * <p><b>预留说明：</b>当前为骨架实现，未注册为 Spring Bean。 启用时需要在 {@code FeignConfiguration} 中注册此拦截器 Bean（建议通过
 * {@code @ConditionalOnProperty} 条件开关控制）。
 *
 * <p>使用示例（需要时取消注释）：
 *
 * <pre>{@code
 * @Bean
 * @ConditionalOnProperty(prefix = "ydsz.feign.signature", name = "enabled", havingValue = "true")
 * public RequestInterceptor signatureInterceptor() {
 *     return new SignatureRequestInterceptor();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see FeignRequestSignature
 * @see SignatureUtils
 */
public class SignatureRequestInterceptor implements RequestInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(SignatureRequestInterceptor.class);

  /** 签名头名称 */
  public static final String HEADER_SIGNATURE = "X-Signature";
  /** 时间戳头名称 */
  public static final String HEADER_TIMESTAMP = "X-Timestamp";
  /** Nonce 头名称 */
  public static final String HEADER_NONCE = "X-Nonce";

  /** 签名密钥（实际使用应通过配置注入） */
  private final String secretKey;

  public SignatureRequestInterceptor(String secretKey) {
    this.secretKey = secretKey;
  }

  /**
   * 使用默认密钥构造（仅用于演示）。
   *
   * <p>实际使用应通过配置注入密钥。
   */
  public SignatureRequestInterceptor() {
    this("default-secret-key-replace-me");
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    // 1. 生成时间戳和 Nonce
    String timestamp = String.valueOf(System.currentTimeMillis());
    String nonce = SignatureUtils.generateNonce();

    // 2. 构建待签名字符串
    String requestPath = requestTemplate.path();
    String method = requestTemplate.method();
    String body = resolveRequestBody(requestTemplate);

    String stringToSign =
        method + "\n" + requestPath + "\n" + timestamp + "\n" + nonce + "\n" + body;

    // 3. 计算签名
    String signature = SignatureUtils.hmacSha256(stringToSign, secretKey);

    // 4. 写入请求头
    requestTemplate.header(HEADER_SIGNATURE, signature);
    requestTemplate.header(HEADER_TIMESTAMP, timestamp);
    requestTemplate.header(HEADER_NONCE, nonce);

    LOG.debug(
        "Feign 请求签名已生成 | method={} | path={} | timestamp={} | nonce={}",
        method,
        requestPath,
        timestamp,
        nonce);
  }

  /**
   * 解析请求体为字符串。
   *
   * @param requestTemplate Feign 请求模板
   * @return 请求体字符串，无法解析时返回空字符串
   */
  private String resolveRequestBody(RequestTemplate requestTemplate) {
    if (requestTemplate.body() == null) {
      return "";
    }
    // 优先使用 body 的 toString（已编码为字节数组的新_BODY 不适用）
    byte[] body = requestTemplate.body();
    if (body != null && body.length > 0) {
      return new String(body, StandardCharsets.UTF_8);
    }
    return "";
  }

  /**
   * 从 RequestTemplate 获取 Content-Type 值。
   *
   * @param requestTemplate Feign 请求模板
   * @return Content-Type 值，不存在时返回 null
   */
  // YDIZ-WARN-001 允许保留：签名头泛型集合擦除，由 HTTP 层强转为 String
  @SuppressWarnings("unchecked")
  private String getContentType(RequestTemplate requestTemplate) {
    Map<String, Collection<String>> headers = requestTemplate.headers();
    Collection<String> values = headers.get("Content-Type");
    if (values != null && !values.isEmpty()) {
      return values.iterator().next();
    }
    return null;
  }
}
