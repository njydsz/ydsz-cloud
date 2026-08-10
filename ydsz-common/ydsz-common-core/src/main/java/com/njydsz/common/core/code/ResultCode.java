package com.njydsz.common.core.code;

/**
 * 统一结果码接口 — 三要素最小契约（i18n 属于 core 标配能力）。
 *
 * <p>定义 API 错误响应 envelope 中错误码的三要素：错误码字符串、国际化消息键、默认兜底消息。
 * HTTP 状态码等异常下沉语义定义在 {@code ExceptionCode} 子接口中。
 *
 * <p><b>继承体系：</b>
 * <pre>
 *   ResultCode（协议层：code + key + msg）
 *     ↑ 唯一直接实现：BaseResultCode（仅保留 SUCCESS / UNKNOWN）
 *     ↑ 唯一子接口：ExceptionCode（异常层：+ httpStatus + category）
 *           ↑ 全部业务模块 *ExceptionCode 枚举
 * </pre>
 *
 * <p><b>扩展指引：</b>业务模块自定义错误码请直接实现 {@code ExceptionCode} 接口并使用
 * {@code @YdszExceptionCode} 注解注册，不要再直接实现此接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see BaseResultCode
 * @see com.njydsz.common.core.code.BaseResultCode
 */
public interface ResultCode {

    /**
     * 获取结果码（如 "A10101"、"B70001"）。
     *
     * <p>前端/客户端通过该字符串识别错误类别并匹配展示文案。
     *
     * @return 结果码字符串
     */
    String getCode();

    /**
     * 获取国际化消息键（i18n key）。
     *
     * <p>默认值为 "core." + code，各模块可提供更精确的语义化 key。
     * 由上层 {@code BaseResponse.error(ResultCode)} i18n 链路使用，
     * 解析失败时回退到 {@link #getMsg()} 兜底。
     *
     * @return 国际化消息键
     */
    default String getKey() {
        return "core." + getCode();
    }

    /**
     * 获取默认兜底消息。
     *
     * <p>在国际化消息未配置或解析失败时，直接作为响应 message 返回。
     *
     * @return 默认结果消息描述
     */
    String getMsg();
}
