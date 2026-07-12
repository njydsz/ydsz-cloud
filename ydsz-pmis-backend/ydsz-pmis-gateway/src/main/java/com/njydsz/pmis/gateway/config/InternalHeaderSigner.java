package com.njydsz.pmis.gateway.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 内部请求头签名工具
 *
 * <p>使用 HMAC-SHA256 对网关注入的内部头进行签名，防止客户端伪造。
 * 下游服务可使用相同密钥验证签名。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
public final class InternalHeaderSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private InternalHeaderSigner() {
        throw new UnsupportedOperationException("Utility class");
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
