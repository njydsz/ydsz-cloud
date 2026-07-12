package com.njydsz.pmis.common.exception.enums;

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
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public enum UserExceptionCode implements ExceptionCode {
 *     USER_NOT_FOUND("U10001", "user.not.found"),
 *     USER_ALREADY_EXISTS("U10002", "user.already.exists");
 *
 *     private final String code;
 *     private final String key;
 *
 *     public String getCode() { return code; }
 *     public String getKey() { return key; }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see com.njydsz.pmis.common.exception.code.UnifiedExceptionCode
 */
public interface ExceptionCode {

    /**
     * 获取异常码
     *
     * <p>返回业务错误码，用于标识具体的异常类型。
     * 建议格式：模块前缀(2位) + 业务码(3位)，如 "A01001"
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
        ExceptionCode result = ExceptionCodeRegistry.lookup(code);
        if (result == null) {
            throw new IllegalStateException("No ExceptionCode registered for code: " + code);
        }
        return result;
    }
}
