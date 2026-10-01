package com.njydsz.common.auth.security;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.util.security.DigestUtils;

/**
 * 内部请求头签名工具（P0-3 由 ydsz-gateway 下沉至 ydsz-common-auth）。
 *
 * <p>使用 HMAC-SHA256 对网关注入的内部头进行签名，防止客户端伪造。
 * 网关（AuthGlobalFilter / WebSocketAuthFilter）负责签名，下游服务
 * （ydsz-common-web 的 {@code InternalSignatureFilter}）负责验签，双方共用本工具与密钥。
 *
 * <p>归属说明：ydsz-gateway 为 reactive 栈禁止依赖 ydsz-common-web，而下游 Servlet 服务
 * 无法依赖网关模块，故签名算法必须收敛在双方共同依赖的 ydsz-common-auth 中。
 *
 * <p>签名 payload 拼接顺序：traceId|userId|username|roles|permissions
 *
 * <p>底层委托 {@link DigestUtils#hmacSha256Hex}（YDIZ-COMMON-054）。
 *
 * @since 26.10.01
 * @author ydsz-team
 */
public final class InternalHeaderSigner {

  private InternalHeaderSigner() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 验证内部头签名（供下游服务调用）。
   *
   * <p>用相同密钥重新计算 HMAC-SHA256，对比签名是否一致（恒定时间比较）。
   *
   * @param secret 签名密钥（与网关相同）
   * @param traceId 链路追踪 ID
   * @param userId 用户 ID
   * @param username 用户名
   * @param roles 角色（CSV）
   * @param permissions 权限（CSV）
   * @param receivedSig 收到的签名
   * @return true=签名有效；false=签名无效
   */
  public static boolean verify(
      String secret,
      String traceId,
      String userId,
      String username,
      String roles,
      String permissions,
      String receivedSig) {
    String expectedSig = sign(secret, traceId, userId, username, roles, permissions);
    // YDIZ-COMMON-054: 委托 DigestUtils.constantTimeEquals 进行恒定时间比较
    return DigestUtils.constantTimeEquals(expectedSig, receivedSig);
  }

  /**
   * 生成内部头签名。
   *
   * <p>payload 拼接顺序：traceId|userId|username|roles|permissions
   *
   * @param secret 签名密钥
   * @param traceId 链路追踪 ID
   * @param userId 用户 ID
   * @param username 用户名
   * @param roles 角色（CSV）
   * @param permissions 权限（CSV）
   * @return HMAC-SHA256 签名（十六进制）
   */
  public static String sign(
      String secret,
      String traceId,
      String userId,
      String username,
      String roles,
      String permissions) {
    String payload =
        String.join("|",
            traceId != null ? traceId : "",
            userId != null ? userId : "",
            username != null ? username : "",
            roles != null ? roles : "",
            permissions != null ? permissions : "");
    // YDIZ-COMMON-054: 委托 DigestUtils.hmacSha256Hex 计算 HMAC-SHA256
    return DigestUtils.hmacSha256Hex(payload, secret);
  }
}
