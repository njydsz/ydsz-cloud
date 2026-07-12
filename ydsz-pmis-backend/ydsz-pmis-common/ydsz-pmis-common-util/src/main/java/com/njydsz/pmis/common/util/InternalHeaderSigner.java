package com.njydsz.pmis.common.util;

import com.njydsz.pmis.common.constant.CommonConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 内部头签名工具（P0-C5）
 *
 * <p>网关在透传 {@code X-User-*} 系列头时，必须同时注入
 * {@link CommonConstants#HEADER_INTERNAL_SIG} + {@link CommonConstants#HEADER_INTERNAL_TS}，
 * 下游服务通过 {@link #verify} 校验签名，拦截外部伪造的内部头。
 *
 * <h3>签名算法</h3>
 * <pre>
 *   payload = traceId + "|" + userId + "|" + username + "|" + roles + "|" + permissions + "|" + ts
 *   sig     = Base64Url(HMAC-SHA256(secret, payload))
 * </pre>
 *
 * <h3>防重放</h3>
 * <p>时间戳 {@code ts}（秒级）参与签名，下游校验时同时验证时间戳在
 * {@link CommonConstants#INTERNAL_SIG_TTL_SECONDS} 窗口内。
 *
 * <h3>密钥来源</h3>
 * <p>复用 {@code pmis.jwt.secret}（生产环境已强制校验为强密钥），避免新增配置项。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class InternalHeaderSigner {

    private InternalHeaderSigner() {
    }

    /**
     * 生成内部头 HMAC-SHA256 签名。
     *
     * @param secret      共享密钥（与 JWT 同一密钥，至少 32 字节）
     * @param traceId     链路追踪 ID
     * @param userId      用户 ID
     * @param username    用户名
     * @param roles       角色列表（逗号分隔字符串）
     * @param permissions 权限列表（逗号分隔字符串）
     * @param tsSeconds   签名时间戳（秒）
     * @return Base64Url 编码的签名
     */
    public static String sign(String secret, String traceId, String userId,
                              String username, String roles, String permissions,
                              long tsSeconds) {
        String payload = buildPayload(traceId, userId, username, roles, permissions, tsSeconds);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("内部头签名失败", e);
        }
    }

    /**
     * 校验内部头签名 + 时间戳窗口。
     *
     * @param secret           共享密钥
     * @param traceId          链路追踪 ID
     * @param userId           用户 ID
     * @param username         用户名
     * @param roles            角色列表（逗号分隔字符串）
     * @param permissions      权限列表（逗号分隔字符串）
     * @param sig              待校验的签名（来自请求头）
     * @param tsSeconds        签名时间戳（来自请求头，秒）
     * @param currentTsSeconds 当前时间戳（秒）
     * @return true 表示签名合法且时间戳在窗口内
     */
    public static boolean verify(String secret, String traceId, String userId,
                                 String username, String roles, String permissions,
                                 String sig, long tsSeconds, long currentTsSeconds) {
        if (sig == null || sig.isBlank()) {
            return false;
        }
        // 时间戳窗口校验（防重放）
        if (Math.abs(currentTsSeconds - tsSeconds) > CommonConstants.INTERNAL_SIG_TTL_SECONDS) {
            return false;
        }
        String expected = sign(secret, traceId, userId, username, roles, permissions, tsSeconds);
        return CryptoUtil.constantTimeEquals(expected, sig);
    }

    /**
     * 构造签名 payload（保持 sign/verify 一致）。
     */
    private static String buildPayload(String traceId, String userId, String username,
                                       String roles, String permissions, long tsSeconds) {
        return safe(traceId) + "|" + safe(userId) + "|" + safe(username) + "|"
                + safe(roles) + "|" + safe(permissions) + "|" + tsSeconds;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
