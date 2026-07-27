package com.njydsz.common.exception.code;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.njydsz.common.exception.enums.SubErrorCode;

import java.util.zip.CRC32;
/**
 * 错误码编码器
 *
 * <p>提供错误码的编码与解码能力，主要用于：
 * <ul>
 *   <li><b>TraceId 嵌入</b>：将 traceId 短哈希附加到错误码末尾，便于日志检索与人工排查</li>
 *   <li><b>子错误码扩展</b>：主错误码下细分具体场景（4 位数字）</li>
 *   <li><b>错误码压缩</b>：长 traceId（UUID 32 字符）压缩为 4 字符短哈希</li>
 * </ul>
 *
 * <p><b>编码格式：</b>
 * <pre>
 *     {主错误码}[-{子错误码}][#{traceIdShort}]
 *     示例: A01001-0001#a3f9
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 编码：主错误码 + 子错误码 + traceId
 * String encoded = ErrorCodeEncoder.encode(
 *     "A01001", SubErrorCode.normalize(1), "abc123def456");
 *
 * // 解码
 * ErrorCodeParts parts = ErrorCodeDecoder.decode(encoded);
 * parts.getMainCode();   // "A01001"
 * parts.getSubCode();    // "0001"
 * parts.getTraceIdShort(); // "a3f9"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ErrorCodeEncoder {

    /** TraceId 短哈希长度（4 字符 = 16 位 = 65536 种取值） */
    public static final int TRACE_ID_SHORT_LENGTH = 4;

    /** traceId 分隔符 */
    public static final char SEPARATOR_TRACE = '#';

    /** 子错误码分隔符 */
    public static final char SEPARATOR_SUB = '-';

    private ErrorCodeEncoder() {
        // 工具类
    }

    /**
     * 编码：主错误码 + 子错误码 + traceId 短哈希
     *
     * @param mainCode 主错误码（如 "A01001"）
     * @param subCode  子错误码（4 位数字，0000 表示无子错误码）
     * @param traceId  完整 traceId（可为 null）
     * @return 编码后的错误码字符串
     */
    public static String encode(String mainCode, String subCode, String traceId) {
        if (mainCode == null || mainCode.isEmpty()) {
            throw new IllegalArgumentException("mainCode cannot be null or empty");
        }
        StringBuilder sb = new StringBuilder(mainCode);
        if (subCode != null && !subCode.isEmpty() && !"0000".equals(subCode)) {
            sb.append(SEPARATOR_SUB).append(subCode);
        }
        if (traceId != null && !traceId.isEmpty()) {
            String shortHash = shortHash(traceId);
            sb.append(SEPARATOR_TRACE).append(shortHash);
        }
        return sb.toString();
    }

    /**
     * 编码：主错误码 + traceId 短哈希
     *
     * @param mainCode 主错误码
     * @param traceId  完整 traceId
     * @return 编码后的错误码字符串
     */
    public static String encode(String mainCode, String traceId) {
        return encode(mainCode, null, traceId);
    }

    /**
     * 编码：主错误码 + 子错误码
     *
     * @param mainCode 主错误码
     * @param subCode  子错误码
     * @return 编码后的错误码字符串
     */
    public static String encodeWithSub(String mainCode, String subCode) {
        return encode(mainCode, subCode, null);
    }

    /**
     * 计算 traceId 短哈希（Base36 编码 CRC32 前 4 字符）
     *
     * <p>实现：将 traceId 做 CRC32 哈希，取低 32 位用 Base36 编码后取前 4 字符。
     * 该算法稳定可靠，相同 traceId 永远得到相同短哈希，冲突率约 1/1679616。
     *
     * @param traceId 原始 traceId
     * @return 4 字符短哈希
     */
    public static String shortHash(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return "0000";
        }
        CRC32 crc = new CRC32();
        crc.update(traceId.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue();
        // 用 Base36 编码
        String base36 = Long.toString(value, 36).toLowerCase();
        // 补齐到 4 位
        if (base36.length() >= TRACE_ID_SHORT_LENGTH) {
            return base36.substring(0, TRACE_ID_SHORT_LENGTH);
        }
        StringBuilder sb = new StringBuilder(base36);
        while (sb.length() < TRACE_ID_SHORT_LENGTH) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }

    /**
     * 提取 traceId 短哈希（从编码后的错误码中）
     *
     * @param encodedCode 编码后的错误码
     * @return 4 字符短哈希，无则返回 null
     */
    public static String extractTraceIdShort(String encodedCode) {
        if (encodedCode == null) {
            return null;
        }
        int idx = encodedCode.lastIndexOf(SEPARATOR_TRACE);
        if (idx < 0 || idx == encodedCode.length() - 1) {
            return null;
        }
        return encodedCode.substring(idx + 1);
    }

    /**
     * Base64 短编码（用于将任意字符串压缩为短码）
     *
     * <p>如需将子错误码 + 短 traceId 合并为单一字符串，可使用此方法。
     *
     * @param input 原始字符串
     * @return Base64 URL 安全编码
     */
    public static String base64Short(String input) {
        if (input == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
