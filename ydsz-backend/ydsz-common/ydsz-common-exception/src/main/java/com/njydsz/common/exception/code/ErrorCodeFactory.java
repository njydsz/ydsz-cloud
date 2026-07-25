package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;
import com.njydsz.common.exception.enums.SubErrorCode;

/**
 * 错误码工厂
 *
 * <p>提供错误码的查询、注册、构建能力，是 {@link ExceptionCodeRegistry} 的业务封装层。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>主错误码查询：按 code 字符串反查 ExceptionCode 枚举</li>
 *   <li>子错误码注册：业务模块可注册子错误码与国际化 key 的映射</li>
 *   <li>分类查询：按 ExceptionCategory 查找所有错误码</li>
 *   <li>错误码文档：生成 Markdown 错误码字典</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 主错误码查找
 * ExceptionCode ec = ErrorCodeFactory.lookup("A01001");
 *
 * // 子错误码注册
 * ErrorCodeFactory.registerSubCode("A01001", "0001", "user.not.found.password_incorrect");
 *
 * // 编码（含 traceId 嵌入）
 * String encoded = ErrorCodeFactory.encodeWithTraceId("A01001", "0001", "abc123");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ErrorCodeFactory {

    /** 子错误码注册表：主错误码 → (子错误码 → 国际化 key) */
    private static final Map<String, Map<String, String>> SUB_CODE_REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();

    /** 子错误码描述：主错误码 → (子错误码 → 中文描述) */
    private static final Map<String, Map<String, String>> SUB_CODE_DESCRIPTIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private ErrorCodeFactory() {
        // 工具类
    }

    /**
     * 查询主错误码
     *
     * @param code 主错误码字符串
     * @return ExceptionCode 枚举实例，未找到返回 null
     */
    public static ExceptionCode lookup(String code) {
        if (code == null) {
            return null;
        }
        // 兼容带子错误码格式
        int dashIdx = code.indexOf(ErrorCodeEncoder.SEPARATOR_SUB);
        if (dashIdx > 0) {
            code = code.substring(0, dashIdx);
        }
        return ExceptionCodeRegistry.lookup(code);
    }

    /**
     * 查询主错误码（必返回）
     *
     * @param code 主错误码字符串
     * @return ExceptionCode 枚举实例
     * @throws IllegalStateException 如果未找到
     */
    public static ExceptionCode require(String code) {
        ExceptionCode ec = lookup(code);
        if (ec == null) {
            throw new IllegalStateException("No ExceptionCode registered for: " + code);
        }
        return ec;
    }

    /**
     * 注册子错误码
     *
     * @param mainCode      主错误码
     * @param subCode       子错误码（4 位数字）
     * @param i18nKey       国际化 key
     * @param description   中文描述（用于错误码字典）
     */
    public static void registerSubCode(String mainCode, String subCode, String i18nKey, String description) {
        if (!SubErrorCode.isValid(subCode)) {
            throw new IllegalArgumentException("Invalid subCode: " + subCode);
        }
        SUB_CODE_REGISTRY
                .computeIfAbsent(mainCode, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(subCode, i18nKey);
        if (description != null) {
            SUB_CODE_DESCRIPTIONS
                    .computeIfAbsent(mainCode, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(subCode, description);
        }
    }

    /**
     * 获取子错误码对应的国际化 key
     *
     * @param mainCode 主错误码
     * @param subCode  子错误码
     * @return 国际化 key，未注册返回 null
     */
    public static String getSubCodeI18nKey(String mainCode, String subCode) {
        if (mainCode == null || subCode == null) {
            return null;
        }
        Map<String, String> subMap = SUB_CODE_REGISTRY.get(mainCode);
        if (subMap == null) {
            return null;
        }
        return subMap.get(subCode);
    }

    /**
     * 获取子错误码的中文描述
     *
     * @param mainCode 主错误码
     * @param subCode  子错误码
     * @return 中文描述，未注册返回 null
     */
    public static String getSubCodeDescription(String mainCode, String subCode) {
        if (mainCode == null || subCode == null) {
            return null;
        }
        Map<String, String> subMap = SUB_CODE_DESCRIPTIONS.get(mainCode);
        if (subMap == null) {
            return null;
        }
        return subMap.get(subCode);
    }

    /**
     * 获取主错误码下所有子错误码
     *
     * @param mainCode 主错误码
     * @return 子错误码 → 国际化 key 映射
     */
    public static Map<String, String> getSubCodes(String mainCode) {
        if (mainCode == null) {
            return Map.of();
        }
        Map<String, String> subMap = SUB_CODE_REGISTRY.get(mainCode);
        if (subMap == null) {
            return Map.of();
        }
        return Map.copyOf(subMap);
    }

    /**
     * 按分类查找所有错误码
     *
     * @param category 分类
     * @return 该分类下所有错误码映射
     */
    public static Map<String, ExceptionCode> findByCategory(ExceptionCategory category) {
        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        Map<String, ExceptionCode> result = new HashMap<>();
        for (Map.Entry<String, ExceptionCode> entry : all.entrySet()) {
            if (entry.getValue().getCategory() == category) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 编码（含 traceId 嵌入）
     *
     * @param mainCode 主错误码
     * @param subCode  子错误码（可为 null）
     * @param traceId  traceId（可为 null）
     * @return 编码后的错误码字符串
     */
    public static String encodeWithTraceId(String mainCode, String subCode, String traceId) {
        return ErrorCodeEncoder.encode(mainCode, subCode, traceId);
    }

    /**
     * 解码错误码
     *
     * @param encodedCode 编码后的错误码
     * @return 解码结果
     */
    public static ErrorCodeDecoder.ErrorCodeParts decode(String encodedCode) {
        return ErrorCodeDecoder.decode(encodedCode);
    }

    /**
     * 统计已注册错误码总数
     *
     * @return 错误码总数
     */
    public static int count() {
        return ExceptionCodeRegistry.allRegistered().size();
    }

    /**
     * 统计子错误码总数
     *
     * @return 子错误码总数
     */
    public static int countSubCodes() {
        int count = 0;
        for (Map<String, String> subMap : SUB_CODE_REGISTRY.values()) {
            count += subMap.size();
        }
        return count;
    }
}
