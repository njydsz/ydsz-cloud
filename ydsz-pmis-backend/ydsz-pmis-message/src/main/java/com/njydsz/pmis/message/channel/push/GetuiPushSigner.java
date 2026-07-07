package com.njydsz.pmis.message.channel.push;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 个推（GeTui）V2 API 签名工具。
 *
 * <p>签名算法：{@code SHA-256(appKey + timestamp + masterSecret)} 的十六进制小写串。
 * 纯静态方法，可独立单元测试，零外部依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class GetuiPushSigner {

    private GetuiPushSigner() {
    }

    /**
     * 计算个推鉴权签名。
     *
     * @param appKey       个推 AppKey
     * @param timestamp    时间戳（毫秒）
     * @param masterSecret MasterSecret
     * @return SHA-256 十六进制签名
     */
    public static String sign(String appKey, String timestamp, String masterSecret) {
        String raw = appKey + timestamp + masterSecret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("个推签名计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 字节数组转十六进制小写串。
     *
     * @param bytes 字节数组
     * @return 十六进制串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
