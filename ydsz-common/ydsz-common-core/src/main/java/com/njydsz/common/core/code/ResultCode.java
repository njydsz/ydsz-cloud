package com.njydsz.common.core.code;

/**
 * 统一结果码接口 — 协议层最小契约。
 *
 * <p>定义 API 错误响应 envelope 中错误码的三要素：错误码字符串、默认兜底消息、对应 HTTP 状态码。
 * 本接口仅承载协议层结构，<b>不</b>包含国际化消息键（i18n key）等扩展语义。
 *
 * <p><b>继承体系：</b>
 * <pre>
 *   ResultCode（协议层：code + msg + httpStatus）
 *     ↑ 唯一直接实现：BaseResultCode
 *     ↑ 唯一子接口：ExceptionCode（异常层：+ key + category）
 *           ↑ 全部业务模块 *ExceptionCode 枚举
 * </pre>
 *
 * <p><b>扩展指引：</b>业务模块自定义错误码请直接实现 {@code ExceptionCode} 接口并使用
 * {@code @YdszExceptionCode} 注解注册，不要再直接实现此接口。
 * {@link com.njydsz.common.core.response.BaseResponse#error(ResultCode)} 工厂方法
 * 仍兼容本接口，但推荐传入 {@code ExceptionCode} 以享受完整 i18n 解析。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see BaseResultCode
 * @see com.njydsz.common.exception.enums.ExceptionCode
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
     * 获取默认兜底消息。
     *
     * <p>在国际化消息未配置或解析失败时，直接作为响应 message 返回。
     * 对于 {@link com.njydsz.common.exception.enums.ExceptionCode} 实现类，
     * 默认委托 {@link com.njydsz.common.exception.enums.ExceptionCode#getKey()}，
     * 由上层 {@code BaseResponse.error(ExceptionCode)} 优先使用 i18n 链路。
     *
     * @return 默认结果消息描述
     */
    String getMsg();

    /**
     * 将结果码映射到对应的 HTTP 状态码。
     *
     * <p>每个 ResultCode / ExceptionCode 必须显式声明其 HTTP 语义（数据驱动，无 switch），
     * 遵循 REST 语义。常见取值：200/400/401/403/404/409/429/500/503。
     *
     * @return 对应的 HTTP 状态码
     */
    int getHttpStatus();
}
