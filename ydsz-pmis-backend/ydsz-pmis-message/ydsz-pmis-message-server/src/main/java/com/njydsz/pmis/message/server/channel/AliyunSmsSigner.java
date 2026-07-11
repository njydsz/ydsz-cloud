package com.njydsz.pmis.message.server.channel.sms;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 阿里云 SMS Common RPC V1.0 签名工具。
 *
 * <p>实现阿里云 {@code HMAC-SHA1} 签名算法，纯静态方法，可独立单元测试。
 * 签名步骤：
 * <ol>
 *   <li>所有请求参数按 key 字典序排序，URL encode 后拼接成 canonical query</li>
 *   <li>构造签名字符串 {@code GET&%2F&<percentEncode(canonicalQuery)>}</li>
 *   <li>HMAC-SHA1(signString, accessKeySecret + "&") → Base64 → Signature</li>
 * </ol>
 *
 * <p>零外部 SDK 依赖，仅用 JDK 标准库，符合自研轻量化风格。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class AliyunSmsSigner {

    private AliyunSmsSigner() {
    }

    /**
     * 计算阿里云 RPC 签名。
     *
     * @param params           请求参数（不含 Signature）
     * @param accessKeySecret  AccessKey Secret
     * @return Base64 编码的签名值
     */
    public static String sign(Map<String, String> params, String accessKeySecret) {
        String canonical = buildCanonicalQuery(params);
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonical);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("阿里云签名计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造规范化查询串（按 key 字典序排序 + percentEncode）。
     *
     * @param params 请求参数
     * @return 规范化查询串
     */
    public static String buildCanonicalQuery(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(percentEncode(e.getKey()))
                    .append("=")
                    .append(percentEncode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 构造请求 URL 查询串（保持原始顺序，含 Signature）。
     *
     * @param params 请求参数（含 Signature）
     * @return URL 查询串
     */
    public static String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(percentEncode(e.getKey()))
                    .append("=")
                    .append(percentEncode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 阿里云 percentEncode：URL encode 后替换 + → %20、* → %2A、%7E → ~。
     *
     * @param value 原始值
     * @return 编码后的值
     */
    public static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
