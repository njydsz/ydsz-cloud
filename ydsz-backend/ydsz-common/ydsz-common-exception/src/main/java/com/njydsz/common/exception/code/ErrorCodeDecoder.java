package com.njydsz.common.exception.code;

import com.njydsz.common.exception.enums.SubErrorCode;

/**
 * 错误码解码器
 *
 * <p>对 {@link ErrorCodeEncoder} 编码后的错误码进行解码，提取主错误码、子错误码、traceId 短哈希。
 *
 * <p><b>支持格式：</b>
 * <ul>
 *   <li>{@code A01001} - 仅主错误码</li>
 *   <li>{@code A01001-0001} - 主错误码 + 子错误码</li>
 *   <li>{@code A01001#a3f9} - 主错误码 + traceId 短哈希</li>
 *   <li>{@code A01001-0001#a3f9} - 主错误码 + 子错误码 + traceId 短哈希</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ErrorCodeDecoder {

    private ErrorCodeDecoder() {
        // 工具类
    }

    /**
     * 解码错误码字符串
     *
     * @param encodedCode 编码后的错误码
     * @return 解码结果
     */
    public static ErrorCodeParts decode(String encodedCode) {
        if (encodedCode == null || encodedCode.isEmpty()) {
            return ErrorCodeParts.empty();
        }

        String mainCode = encodedCode;
        String subCode = SubErrorCode.DEFAULT;
        String traceIdShort = null;

        // 1. 截取 traceId 短哈希部分（以 # 分隔）
        int traceIdx = encodedCode.indexOf(ErrorCodeEncoder.SEPARATOR_TRACE);
        if (traceIdx >= 0) {
            mainCode = encodedCode.substring(0, traceIdx);
            traceIdShort = encodedCode.substring(traceIdx + 1);
        }

        // 2. 截取子错误码部分（以 - 分隔）
        int subIdx = mainCode.indexOf(ErrorCodeEncoder.SEPARATOR_SUB);
        if (subIdx > 0) {
            String subPart = mainCode.substring(subIdx + 1);
            mainCode = mainCode.substring(0, subIdx);
            if (SubErrorCode.isValid(subPart)) {
                subCode = subPart;
            }
        }

        return new ErrorCodeParts(mainCode, subCode, traceIdShort);
    }

    /**
     * 仅提取主错误码
     *
     * @param encodedCode 编码后的错误码
     * @return 主错误码
     */
    public static String extractMainCode(String encodedCode) {
        if (encodedCode == null) {
            return null;
        }
        ErrorCodeParts parts = decode(encodedCode);
        return parts.getMainCode();
    }

    /**
     * 仅提取子错误码
     *
     * @param encodedCode 编码后的错误码
     * @return 4 位子错误码，无则返回 "0000"
     */
    public static String extractSubCode(String encodedCode) {
        if (encodedCode == null) {
            return SubErrorCode.DEFAULT;
        }
        ErrorCodeParts parts = decode(encodedCode);
        return parts.getSubCode();
    }

    /**
     * 错误码解码结果
     */
    public static class ErrorCodeParts {
        /** 主错误码 */
        private final String mainCode;
        /** 子错误码 */
        private final String subCode;
        /** traceId 短哈希 */
        private final String traceIdShort;

        /**
         * 构造解码结果
         *
         * @param mainCode     主错误码
         * @param subCode      子错误码
         * @param traceIdShort traceId 短哈希
         */
        public ErrorCodeParts(String mainCode, String subCode, String traceIdShort) {
            this.mainCode = mainCode;
            this.subCode = subCode == null ? SubErrorCode.DEFAULT : subCode;
            this.traceIdShort = traceIdShort;
        }

        public static ErrorCodeParts empty() {
            return new ErrorCodeParts(null, SubErrorCode.DEFAULT, null);
        }

        /**
         * 获取主错误码
         *
         * @return 主错误码
         */
        public String getMainCode() {
            return mainCode;
        }

        /**
         * 获取子错误码
         *
         * @return 子错误码
         */
        public String getSubCode() {
            return subCode;
        }

        /**
         * 获取 traceId 短哈希
         *
         * @return traceId 短哈希
         */
        public String getTraceIdShort() {
            return traceIdShort;
        }

        /**
         * 组合完整错误码（不含 traceId 短哈希）
         */
        public String getFullCode() {
            if (mainCode == null) {
                return null;
            }
            if (SubErrorCode.DEFAULT.equals(subCode)) {
                return mainCode;
            }
            return mainCode + ErrorCodeEncoder.SEPARATOR_SUB + subCode;
        }

        /**
         * 组合完整错误码（含 traceId 短哈希）
         */
        public String getEncodedCode() {
            if (mainCode == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(mainCode);
            if (!SubErrorCode.DEFAULT.equals(subCode)) {
                sb.append(ErrorCodeEncoder.SEPARATOR_SUB).append(subCode);
            }
            if (traceIdShort != null && !traceIdShort.isEmpty()) {
                sb.append(ErrorCodeEncoder.SEPARATOR_TRACE).append(traceIdShort);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return getEncodedCode();
        }
    }
}
