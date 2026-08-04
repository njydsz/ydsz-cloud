package com.remisoft.common.exception.enums;

import com.remisoft.common.core.code.ResultCode;

/**
 * 异常码接口
 *
 * <p>继承 {@link ResultCode}，统一双轨错误码体系。
 * 所有业务异常码枚举都应实现此接口，以保证统一的访问方式。
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
 * <h3>与 ResultCode 的桥接（v1.1.0 统一双轨体系）</h3>
 * <ul>
 *   <li>{@link #getMsg()} → 委托给 {@link #getKey()}，无 i18n 时回退显示 key</li>
 *   <li>{@link #getMessageKey()} → 委托给 {@link #getKey()}，用于 BaseResponse 的 i18n 链路</li>
 *   <li>{@link #getHttpStatusCode()} → 委托给 {@link #getHttpStatus()}，桥接命名差异</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 定义错误码（同时兼容 ExceptionCode 和 ResultCode）
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
 *
 * // 2. 作为异常抛出（原有方式）
 * throw new BusinessException(UserExceptionCode.USER_NOT_FOUND);
 *
 * // 3. 作为响应直接返回（v1.1.0 新增能力）
 * return BaseResponse.error(UserExceptionCode.USER_NOT_FOUND);
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ResultCode
 * @see UnifiedExceptionCode
 */
public interface ExceptionCode extends ResultCode {

    /**
     * 获取异常码
     *
     * <p>返回业务错误码，用于标识具体的异常类型。
     * 建议格式：分类(1位) + 模块(2位) + 业务码(3位)，如 "A01001"
     *
     * @return 异常码字符串
     */
    @Override
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
     * 获取默认结果消息
     *
     * <p>委托给 {@link #getKey()}，作为 i18n 解析失败或未设置解析器时的回退消息。
     * 业务枚举可通过覆盖此方法来提供更友好的默认文案。
     *
     * @return 默认消息（通常为 i18n key）
     */
    @Override
    default String getMsg() {
        return getKey();
    }

    /**
     * 获取国际化消息 key
     *
     * <p>委托给 {@link #getKey()}，确保 BaseResponse 的 i18n 解析链路
     * 能正确解析业务模块的错误码国际化消息。
     *
     * @return 国际化消息键
     */
    @Override
    default String getMessageKey() {
        return getKey();
    }

    /**
     * 获取对应的 HTTP 状态码
     *
     * <p>桥接至 {@link #getHttpStatus()}，统一双轨体系的 HTTP 状态码获取方式。
     * 默认返回 400（Bad Request），实现类可按需覆盖 {@link #getHttpStatus()}。
     *
     * @return HTTP 状态码
     */
    @Override
    default int getHttpStatusCode() {
        return getHttpStatus();
    }

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
            case 'F':
                return ExceptionCategory.INFRASTRUCTURE;
            case 'G':
            case 'H':
            case 'W':
                return ExceptionCategory.BUSINESS;
            default:
                return ExceptionCategory.BUSINESS;
        }
    }
}
