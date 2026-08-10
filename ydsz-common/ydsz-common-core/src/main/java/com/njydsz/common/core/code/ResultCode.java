package com.njydsz.common.core.code;

/**
 * 统一结果码接口。
 *
 * <p>定义标准化的错误码契约，参考阿里巴巴《Java开发手册》错误码规范设计。
 *
 * <p><b>编码规范：</b>
 * <ul>
 *   <li>A 开头：用户端错误（参数校验、权限等）</li>
 *   <li>B 开头：当前系统业务异常</li>
 *   <li>C 开头：第三方服务异常</li>
 * </ul>
 *
 * <p><b>迁移说明：</b>本接口已被 {@code com.njydsz.common.exception.enums.ExceptionCode} 继承。
 * 新增业务模块错误码请直接实现 {@code ExceptionCode} 接口并使用 {@code @YdszResultCode} 注解注册，
 * 不要再实现此接口。{@code BaseResponse.error(ResultCode)} 工厂方法仍兼容本接口，
 * 内部 {@link BaseResultCode} 仍然实现本接口 —— 但对外扩展请使用 {@code ExceptionCode}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see BaseResultCode
 * @see com.njydsz.common.core.code.ResultCode
 * @see com.njydsz.common.exception.enums.ExceptionCode
 */
public interface ResultCode {

    /**
     * 获取结果码
     *
     * @return 结果码字符串
     */
    String getCode();

    /**
     * 获取结果消息
     *
     * @return 结果消息描述
     */
    String getMsg();

    /**
     * 获取国际化消息 key
     *
     * <p>默认实现返回 {@code "error." + 枚举名称}。
     * 实现类可覆盖此方法以自定义 key 格式。
     *
     * @return 形如 "error.BAD_REQUEST" 的国际化 key
     */
    default String getMessageKey() {
        if (this instanceof Enum<?>) {
            return "error." + ((Enum<?>) this).name();
        }
        // 非枚举实现（如实现了 ResultCode 的普通类）同样安全，避免 ClassCastException
        return "error." + getClass().getSimpleName();
    }

    /**
     * 将结果码映射到合适的 HTTP 状态码。
     *
     * <p>实现类必须显式声明每个错误码对应的 HTTP 状态码，
     * 禁止使用前缀猜测推断（如 A 开头一律 400），因为相同前缀下的
     * 错误码可能需要不同的状态码（例如 A10301 限流应为 429 而非 400）。
     *
     * @return 对应的 HTTP 状态码
     */
    int getHttpStatusCode();
}
