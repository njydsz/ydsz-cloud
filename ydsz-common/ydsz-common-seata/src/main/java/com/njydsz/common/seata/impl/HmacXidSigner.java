package com.njydsz.common.seata.impl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.seata.api.XidSigner;

/**
 * HMAC-SHA256 XID 签名器实现
 *
 * <p>使用 HMAC-SHA256 算法对 XID + 时间戳进行签名，格式为：
 * {@code base64(xid):base64(timestamp):base64(signature)}
 *
 * <p>验证时重新计算签名并比对，同时检查时间戳是否在有效窗口内（默认 5 分钟），
 * 防止重放攻击。
 *
 * <p><b>安全配置</b>：生产环境应通过 {@code ydsz.seata.xid.sign-key} 配置强密钥，
 * 建议使用 32 字节以上的随机字符串。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class HmacXidSigner implements XidSigner {

    private static final Logger log = LoggerFactory.getLogger(HmacXidSigner.class);

    /** HMAC-SHA256 算法名称 */
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /** 签名字段分隔符 */
    private static final String SEPARATOR = ":";

    /** 签名有效时间窗口（秒），超过此时间的签名被视为过期 */
    private static final long SIGNATURE_VALIDITY_SECONDS = 300L; // 5 分钟

    private final String signKey;

    /**
     * 构造 HMAC-SHA256 XID 签名器
     *
     * @param signKey 签名密钥（建议长度 >= 16 字符）
     */
    public HmacXidSigner(String signKey) {
        this.signKey = signKey;
    }

    /**
     * 生成带签名的 XID
     *
     * <p>格式：base64(xid) + ":" + base64(timestamp) + ":" + base64(signature)
     *
     * @param xid 原始 XID
     * @return 带签名的 XID
     */
    @Override
    public String sign(String xid) {
        if (xid == null || xid.isBlank()) {
            return null;
        }
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String payload = xid + SEPARATOR + timestamp;
            String signature = hmacSha256(payload);

            return Base64.getEncoder().encodeToString(xid.getBytes(StandardCharsets.UTF_8))
                    + SEPARATOR + Base64.getEncoder().encodeToString(timestamp.getBytes(StandardCharsets.UTF_8))
                    + SEPARATOR + Base64.getEncoder().encodeToString(signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to sign XID", e);
            return xid; // 签名失败时降级返回原值
        }
    }

    /**
     * 验证并解析带签名的 XID
     *
     * <p>步骤：
     * <ol>
     *   <li>解析格式，提取 xid/timestamp/signature</li>
     *   <li>验证签名有效性</li>
     *   <li>验证时间戳在有效窗口内</li>
     *   <li>验证通过返回原始 XID</li>
     * </ol>
     *
     * @param signedXid 带签名的 XID
     * @return 验证通过返回原始 XID，否则返回 null
     */
    @Override
    public String verify(String signedXid) {
        if (signedXid == null || signedXid.isBlank()) {
            return null;
        }
        try {
            // 快速判断：如果不包含分隔符，说明是未签名的旧格式，直接返回
            if (!signedXid.contains(SEPARATOR)) {
                log.debug("XID is in legacy unsigned format, accepting as-is");
                return signedXid.trim();
            }

            String[] parts = signedXid.split(SEPARATOR);
            if (parts.length != 3) {
                log.warn("Invalid signed XID format, expected 3 parts, got {}", parts.length);
                return null;
            }

            // 解码各部分
            String xid = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String timestampStr = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String signature = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);

            // 验证时间戳有效性（防止重放攻击）
            long timestamp = Long.parseLong(timestampStr);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > SIGNATURE_VALIDITY_SECONDS) {
                log.warn("XID signature expired: timestamp={}, now={}", timestamp, now);
                return null;
            }

            // 验证签名
            String payload = xid + SEPARATOR + timestampStr;
            String expectedSignature = hmacSha256(payload);
            if (!signature.equals(expectedSignature)) {
                log.warn("XID signature mismatch, rejecting");
                return null;
            }

            return xid;
        } catch (Exception e) {
            log.warn("Failed to verify XID signature", e);
            return null;
        }
    }

    /**
     * 计算 HMAC-SHA256 签名
     *
     * @param data 待签名数据
     * @return 十六进制格式的签名字符串
     */
    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 algorithm not available", e);
        }
    }
}
