package com.njydsz.common.exception.enums;

/**
 * 异常码接口
 *
 * <p>统一错误码体系：业务异常码枚举实现本接口后，既可用于异常抛出链路
 * （{@code throw new BusinessException(code)}），也可直接作为
 * {@code BaseResponse.error(code, msg)} 的响应码（v1.1.0 起支持）。
 * 设计为接口而非抽象类，可以让不同业务模块定义自己的异常码枚举，
 * 同时保持访问方式的一致性。
 *
 * <p><b>实现规范：</b>
 * <ul>
 *   <li>枚举类需要实现 getCode() 和 getKey() 方法</li>
 *   <li>code：业务错误码，字符串类型，如 "B93001"</li>
 *   <li>key：国际化消息键，对应 messages.properties 中的键</li>
 * </ul>
 *
 * <h3>与 {@code ResultCode} 的继承关系</h3>
 * <ul>
 *   <li>本接口直接继承 {@link com.njydsz.common.core.code.ResultCode}（核心模块），
 *       移除历史上通过 {@code com.njydsz.common.exception.code.ResultCode} 桥接的双轨设计</li>
 *   <li>{@link #getMsg()} 默认委托给 {@link #getKey()}，无 i18n 时回退显示 key</li>
 *   <li>{@link #getMessageKey()} 默认委托给 {@link #getKey()}，用于 BaseResponse 的 i18n 链路</li>
 *   <li>{@link #getHttpStatusCode()} 默认委托给 {@link #getHttpStatus()}，桥接命名差异</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 定义错误码（直接兼容 ExceptionCode 和 ResultCode）
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
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.core.code.ResultCode
 * @see com.njydsz.common.exception.code.CoreExceptionCode
 * @see com.njydsz.common.exception.code.SecurityExceptionCode
 * @see com.njydsz.common.exception.code.RateLimitExceptionCode
 */
public interface ExceptionCode extends com.njydsz.common.core.code.ResultCode {

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
     * <p>默认从主错误码首字母推断分类：
     * <ul>
     *     <li>{@code A} - 业务级错误（HTTP 4xx，业务参数、认证、权限、数据）</li>
     *     <li>{@code B} - 系统级错误（HTTP 5xx，基础设施故障）</li>
     *     <li>{@code C} - 安全级错误（HTTP 401/403）</li>
     * </ul>
     * 实现类可通过覆写此方法提供更精确的细分类别（如 rate-limit、external）。
     *
     * @return 错误码分类枚举；默认返回 {@link ExceptionCategory#BUSINESS}
     */
    default ExceptionCategory getCategory() {
        String code = getCode();
        if (code == null || code.isEmpty()) {
            return ExceptionCategory.BUSINESS;
        }
        char prefix = Character.toUpperCase(code.charAt(0));
        switch (prefix) {
            case 'B':
                return ExceptionCategory.SYSTEM;
            case 'C':
                return ExceptionCategory.SECURITY;
            default:
                // A / D / E 等暂归业务级，需要更细分类时由实现类覆写
                return ExceptionCategory.BUSINESS;
        }
    }
}
