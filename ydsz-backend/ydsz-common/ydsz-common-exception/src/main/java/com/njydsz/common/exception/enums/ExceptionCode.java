package com.njydsz.common.exception.enums;

/**
 * 异常码接口
 *
 * <p>所有业务异常码枚举都应实现此接口，以保证统一的访问方式。
 * 设计为接口而非抽象类，可以让不同业务模块定义自己的异常码枚举，
 * 同时保持访问方式的一致性。
 *
 * <p><b>实现规范：</b>
 * <ul>
 *   <li>枚举类需要实现 getCode() 和 getKey() 方法</li>
 *   <li>code：业务错误码，字符串类型，如 "A01001"</li>
 *   <li>key：国际化消息键，对应 messages.properties 中的键</li>
 * </ul>
 *
 * <p><b>扩展能力（自 1.0.0 起）：</b>
 * <ul>
 *   <li>子错误码（subCode）：主错误码下细分的具体场景标识，4 位数字（如 0001 / 0002）</li>
 *   <li>TraceId 嵌入：将 traceId 编码到错误码末尾，便于日志关联与人工排查</li>
 *   <li>5 大分类（A/B/C/D/E）：业务、系统、安全、限流、外部</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public enum UserExceptionCode implements ExceptionCode {
 *     USER_NOT_FOUND("A01001", "user.not.found"),
 *     USER_ALREADY_EXISTS("A01002", "user.already.exists");
 *
 *     private final String code;
 *     private final String key;
 *
 *     public String getCode() { return code; }
 *     public String getKey() { return key; }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 */
public interface ExceptionCode {

    /**
     * 获取异常码
     *
     * <p>返回业务错误码，用于标识具体的异常类型。
     * 建议格式：分类(1位) + 模块(2位) + 业务码(3位)，如 "A01001"
     *
     * <p>JSON 序列化时输出 code 值而非枚举名称。
     *
     * @return 异常码字符串
     */
    String getCode();

    /**
     * 获取异常消息键
     *
     * <p>返回国际化消息的键，用于查找对应的本地化消息文本。
     * 键的格式建议采用点分隔的层级结构，如 "user.not.found"
     *
     * @return 异常消息键
     */
    String getKey();

    /**
     * 获取对应的 HTTP 状态码
     *
     * <p>返回该异常码对应的 HTTP 响应状态码，用于异常处理器自动设置响应状态。
     * 默认返回 400（Bad Request），实现类可按需覆盖。
     *
     * @return HTTP 状态码
     */
    default int getHttpStatus() {
        return 400;
    }

    /**
     * 获取子错误码（Sub Error Code）
     *
     * <p>用于在主错误码下细分具体场景，4 位数字字符串（如 "0001"）。
     * 默认返回 "0000"（无子错误码）。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>同一主错误码下区分不同的失败原因（如 USER_NOT_FOUND 在不同子错误码下表示不同原因）</li>
     *   <li>便于国际化文案精确化（messages_{subCode}.properties）</li>
     *   <li>便于客户端按子错误码做差异化提示</li>
     * </ul>
     *
     * @return 4 位数字子错误码字符串
     */
    default String getSubCode() {
        return "0000";
    }

    /**
     * 获取错误码分类
     *
     * <p>从主错误码首字母推断分类（A/B/C/D/E）。
     * 默认返回 {@link ExceptionCategory#BUSINESS}。
     *
     * @return 错误码分类枚举
     */
    default ExceptionCategory getCategory() {
        String code = getCode();
        if (code == null || code.isEmpty()) {
            return ExceptionCategory.BUSINESS;
        }
        char prefix = Character.toUpperCase(code.charAt(0));
        switch (prefix) {
            case 'A':
                return ExceptionCategory.BUSINESS;
            case 'B':
                return ExceptionCategory.SYSTEM;
            case 'C':
                return ExceptionCategory.SECURITY;
            case 'D':
                return ExceptionCategory.RATE_LIMIT;
            case 'E':
                return ExceptionCategory.EXTERNAL;
            case 'S':
                return ExceptionCategory.SYSTEM;
            case 'K':
                return ExceptionCategory.SECURITY;
            case 'V':
                return ExceptionCategory.VALIDATION;
            case 'I':
                return ExceptionCategory.INFRASTRUCTURE;
            case 'T':
                return ExceptionCategory.TIMEOUT;
            case 'R':
                return ExceptionCategory.RATE_LIMIT;
            default:
                return ExceptionCategory.BUSINESS;
        }
    }

    /**
     * 组合完整错误码（主错误码 + 子错误码）
     *
     * <p>格式：{@code {主错误码}-{子错误码}}，如 "A01001-0001"。
     *
     * @return 完整错误码字符串
     */
    default String getFullCode() {
        String sub = getSubCode();
        if (sub == null || sub.isEmpty() || "0000".equals(sub)) {
            return getCode();
        }
        return getCode() + "-" + sub;
    }

    /**
     * 根据 code 字符串查找已注册的 ExceptionCode
     *
     * <p>通过全局 {@link ExceptionCodeRegistry} 查找对应枚举实例。
     * 各模块的异常码枚举需在静态块中完成注册才能被此方法找到。
     *
     * @param code 异常码字符串，如 "A01001"
     * @return 对应的 ExceptionCode 枚举实例
     * @throws IllegalArgumentException 如果 code 为 null 或空字符串
     * @throws IllegalStateException 如果 code 未被任何模块注册
     */
    static ExceptionCode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Exception code cannot be null or empty");
        }
        // 兼容带子错误码的格式：截取主错误码部分
        String mainCode = code;
        int dashIdx = code.indexOf('-');
        if (dashIdx > 0) {
            mainCode = code.substring(0, dashIdx);
        }
        ExceptionCode result = ExceptionCodeRegistry.lookup(mainCode);
        if (result == null) {
            throw new IllegalStateException("No ExceptionCode registered for code: " + mainCode);
        }
        return result;
    }
}
