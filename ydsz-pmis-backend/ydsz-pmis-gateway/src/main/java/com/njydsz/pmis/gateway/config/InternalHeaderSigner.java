package com.njydsz.pmis.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 内部请求头签名工具
 *
 * <p>使用 HMAC-SHA256 对网关注入的内部头进行签名，防止客户端伪造。
 * 下游服务可使用相同密钥验证签名。
 *
 * <h3>P2-12 增强项</h3>
 * <ul>
 *   <li>{@code verify()}: 下游服务验证内部头签名的方法</li>
 *   <li>{@code validateTimestamp()}: 时间戳防重放（拒绝超过 N 秒的签名）</li>
 * </ul>
 *
 * @since 2.2.0
 */
public final class InternalHeaderSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /** P2-12: 时间戳防重放窗口（秒），超过此时间的签名将被拒绝 */
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 60L;

    private InternalHeaderSigner() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * P2-12: 验证内部头签名（供下游服务调用）
     *
     * <p>下游服务在接收到网关注入的内部头后，应调用此方法验证签名合法性。
     *
     * <p>验证流程：
     * <ol>
     *   <li>校验时间戳窗口（防重放）</li>
     *   <li>用相同密钥重新计算 HMAC-SHA256</li>
     *   <li>对比签名是否一致</li>
     * </ol>
 *
     * @param secret      签名密钥（与网关相同）
     * @param traceId     链路追踪 ID
     * @param userId      用户 ID
     * @param username    用户名
     * @param roles       角色（CSV）
     * @param permissions 权限（CSV）
     * @param tsSeconds   时间戳（秒）
     * @param receivedSig 收到的签名
     * @return true=签名有效；false=签名无效或时间戳过期
     */
    public static boolean verify(String secret, String traceId, String userId,
                                  String username, String roles, String permissions,
                                  long tsSeconds, String receivedSig) {
        // P2-12: 先校验时间戳窗口
        if (!validateTimestamp(tsSeconds)) {
            return false;
        }

        // 重新计算签名
        String expectedSig = sign(secret, traceId, userId, username, roles, permissions, tsSeconds);

        // 恒定时间比较，防计时攻击
        return slowEquals(expectedSig, receivedSig);
    }

    /**
     * P2-12: 验证时间戳是否在允许的窗口内（防重放攻击）
     *
     * <p>拒绝超过 {@code TIMESTAMP_TOLERANCE_SECONDS} 秒的签名。
     *
     * @param tsSeconds 签名时间戳（秒）
     * @return true=时间戳在窗口内；false=时间戳过期
     */
    public static boolean validateTimestamp(long tsSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long diff = Math.abs(nowSeconds - tsSeconds);
        return diff <= TIMESTAMP_TOLERANCE_SECONDS;
    }

    /**
     * P2-12: 恒定时间比较（防计时攻击）
     *
     * @param a 字符串 A
     * @param b 字符串 B
     * @return true=完全相等；false=不相等
     */
    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * 生成内部头签名
     *
     * @param secret      签名密钥
     * @param traceId     链路追踪 ID
     * @param userId      用户 ID
     * @param username    用户名
     * @param roles       角色（CSV）
     * @param permissions 权限（CSV）
     * @param tsSeconds   时间戳（秒）
     * @return HMAC-SHA256 签名（十六进制）
     */
    public static String sign(String secret, String traceId, String userId,
                              String username, String roles, String permissions,
                              long tsSeconds) {
        String payload = String.join("|",
                traceId != null ? traceId : "",
                userId != null ? userId : "",
                username != null ? username : "",
                roles != null ? roles : "",
                permissions != null ? permissions : "",
                String.valueOf(tsSeconds));

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("生成内部头签名失败", e);
        }
    }
}
