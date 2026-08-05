package com.remisoft.common.exception.code;

/**
 * 错误码接口。
 *
 * <p>统一业务错误码的访问方式，任何表示错误码的枚举都应实现此接口，
 * 以便通过一致的方式获取错误码、消息与 HTTP 状态码。
 *
 * <p><b>迁移说明：</b>本接口原定义于 {@code remi-common-core}（v2.1.0 精简核心时移除），
 * 因错误码体系属于异常处理能力，迁移至 {@code remi-common-exception} 模块维护。
 *
 * <h3>已弃用（since 当前版本）</h3>
 * <p>已被 {@link com.remisoft.common.core.code.ResultCode} 统一替代。
 * 历史作用是通过 {@link ExceptionCode} 桥接核心接口，现已由 {@link ExceptionCode}
 * 直接继承 {@code com.remisoft.common.core.code.ResultCode} 完成统一。
 * 本接口仅保留用于兼容尚未迁移的旧代码引用，<strong>新代码应直接使用</strong>
 * {@code com.remisoft.common.core.code.ResultCode}，不经过本接口。
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated 已被 {@link com.remisoft.common.core.code.ResultCode} 统一替代，
 *             请直接使用该接口
 * @see ExceptionCode
 * @see com.remisoft.common.core.code.ResultCode
 */
@Deprecated
public interface ResultCode {

    /**
     * 获取业务错误码。
     *
     * @return 错误码字符串，如 "A01001"
     */
    String getCode();

    /**
     * 获取错误消息（i18n 解析失败或未设置解析器时的回退消息）。
     *
     * @return 错误消息
     */
    String getMsg();

    /**
     * 获取国际化消息 key（默认使用枚举名拼接）。
     *
     * @return 国际化消息 key
     */
    default String getMessageKey() {
        return "error." + ((Enum<?>) this).name();
    }

    /**
     * 获取对应的 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    int getHttpStatusCode();
}
