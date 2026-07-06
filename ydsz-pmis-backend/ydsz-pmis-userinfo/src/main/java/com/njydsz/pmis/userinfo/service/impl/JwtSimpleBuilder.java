package com.njydsz.pmis.userinfo.service.impl;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 简化版 Token 构造器（仅用于测试/演示；生产环境建议使用 JwtTokenProvider）
 *
 * <p>格式：{@code base64(header).base64(payload).base64(hmacSig)}，与 JWT 兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
final class JwtSimpleBuilder {

    private static final Logger log = LoggerFactory.getLogger(JwtSimpleBuilder.class);

    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String SECRET = "pmis-user-module-jwt-secret-2026";

    private JwtSimpleBuilder() {
    }

    /**
     * 构造 JWT Token（兼容 JWT 格式，仅用于测试/演示）
     *
     * @param claims        载荷声明
     * @param expireSeconds 过期秒数
     * @return JWT Token 字符串
     */
    static String build(Map<String, Object> claims, int expireSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        claims.putIfAbsent("iat", now);
        claims.put("exp", now + expireSeconds);
        String headerB64 = b64(HEADER);
        String payloadB64 = b64(JSON.toJSONString(claims));
        String signature = hmac(headerB64 + "." + payloadB64);
        return headerB64 + "." + payloadB64 + "." + signature;
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String input) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            log.error("[JwtSimpleBuilder] HMAC 签名失败，返回空串（仅用于测试/演示）: {}", e.getMessage(), e);
            return "";
        }
    }
}