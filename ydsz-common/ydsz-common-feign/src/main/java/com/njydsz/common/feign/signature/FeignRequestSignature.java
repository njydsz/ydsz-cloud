package com.njydsz.common.feign.signature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.Order;

/**
 * Feign 请求签名注解（开放平台场景预留）。
 *
 * <p>在 FeignClient 接口或方法上添加此注解，启用 HMAC-SHA256 请求签名。 签名覆盖请求方法、路径、时间戳、nonce 和请求体，防止请求被篡改或重放。
 *
 * <p><b>签名算法：</b>
 *
 * <pre>
 * StringToSign = HTTPMethod + "\n"
 *              + RequestPath + "\n"
 *              + Timestamp + "\n"
 *              + Nonce + "\n"
 *              + Sha256(Body)
 * Signature = HMAC-SHA256(SecretKey, StringToSign)
 * </pre>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @FeignClient(name = "partner-service")
 * @FeignRequestSignature(algorithm = "HMAC-SHA256", secretKey = "${partner.secret-key}")
 * public interface PartnerClient {
 *
 *     @PostMapping("/api/order")
 *     CreateOrderResponse createOrder(@RequestBody CreateOrderRequest request);
 * }
 * }</pre>
 *
 * <p><b>预留说明：</b>当前为 API 预留阶段，未接入实际拦截器实现。 需要启用时再实现 {@link SignatureRequestInterceptor}
 * 并在 FeignConfiguration 中注册。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Order(0)
public @interface FeignRequestSignature {

  /**
   * 签名算法。
   *
   * @return 算法名称（仅支持 HMAC-SHA256）
   */
  String algorithm() default "HMAC-SHA256";

  /**
   * 签名密钥（支持 Spring 占位符 ${...} 解析）。
   *
   * @return 密钥
   */
  String secretKey() default "";

  /**
   * 是否包含请求体参与签名。
   *
   * <p>默认 true；对于 GET 等无 Body 的请求，会自动跳过 Body 部分。
   *
   * @return true 表示 Body 参与签名
   */
  boolean includeBody() default true;

  /**
   * 签名是否覆盖响应验证。
   *
   * <p>默认 false；开启后会对响应体进行签名校验，确保响应未被篡改。
   *
   * @return true 表示验证响应签名
   */
  boolean verifyResponse() default false;
}
