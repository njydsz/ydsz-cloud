package com.njydsz.common.core.response;

import com.njydsz.common.core.code.ResultCode;

/**
 * 统一响应结果门面（Results facade）。
 *
 * <p>对 {@link BaseResponse} / {@link PageResponse} 的常用工厂方法做一层语义化收敛，
 * 提供简短、类型明确的入口，减少 Controller 直接散用内部静态方法造成的 API 发散。
 * 本类仅为<b>委托（delegate）</b>，不引入新行为、不新增依赖。</p>
 *
 * <h3>设计取舍（避免过度设计）</h3>
 * <ul>
 *   <li>异常 → 响应的转换<b>不在此处实现</b>：核心 {@code BaseResponse.error(ResultCode)} 已是规范入口，
 *       全局 {@code @ControllerAdvice}（异常模块）应直接调用它，避免 core 反向耦合异常类型。</li>
 *   <li>仅暴露高频入口（ok / okWithObservability / page / fail），不穷举所有重载，避免与 {@link BaseResponse} 重复膨胀。</li>
 * </ul>
 *
 * <p><b>典型用法：</b></p>
 * <pre>{@code
 * return Results.ok(data);
 * return Results.ok("操作成功", data);
 * return Results.okWithObservability(data, requestId, spanId);
 * return Results.page(total, pageNum, pageSize, records);
 * return Results.fail(BaseResultCode.NOT_FOUND);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.9.1
 *
 * @see BaseResponse
 * @see PageResponse
 */
public final class Results {

    private Results() {
        throw new UnsupportedOperationException("Facade class");
    }

    /** 成功（无数据）。 */
    public static <T> BaseResponse<T> ok() {
        return BaseResponse.success();
    }

    /** 成功（带数据）。 */
    public static <T> BaseResponse<T> ok(T data) {
        return BaseResponse.success(data);
    }

    /** 成功（自定义消息 + 数据）。 */
    public static <T> BaseResponse<T> ok(String msg, T data) {
        return BaseResponse.success(msg, data);
    }

    /**
     * 成功（带可观测字段：requestId + spanId）。
     *
     * <p>适用于前端排障需要精确 trace→span 链路映射的场景；
     * 一般场景使用 {@link #ok(Object)} 即可（traceId 已在响应中自动填充）。</p>
     *
     * @param data      数据
     * @param requestId 请求 ID
     * @param spanId    Span ID
     * @param <T>       数据类型
     * @return 带可观测字段的成功响应
     * @since 1.10.0
     */
    public static <T> BaseResponse<T> okWithObservability(T data, String requestId, String spanId) {
        return new BaseResponse<>(BaseResponse.SUCCESS, resolveSuccessMsg(), data, requestId, spanId);
    }

    /** 分页成功响应（强类型信封 {@link PageResponse}）。 */
    public static <T> PageResponse<T> page(Long total, Long pageNum, Long pageSize, T data) {
        return PageResponse.success(total, pageNum, pageSize, data);
    }

    /** 分页失败响应（强类型信封 {@link PageResponse}）。 */
    public static <T> PageResponse<T> pageFail(ResultCode resultCode) {
        return PageResponse.error(resultCode);
    }

    /** 失败（未知错误）。 */
    public static <T> BaseResponse<T> fail() {
        return BaseResponse.error();
    }

    /** 失败（自定义消息）。 */
    public static <T> BaseResponse<T> fail(String msg) {
        return BaseResponse.error(msg);
    }

    /** 失败（错误码 + i18n 消息）。 */
    public static <T> BaseResponse<T> fail(ResultCode resultCode) {
        return BaseResponse.error(resultCode);
    }

    /** 失败（错误码 + 自定义消息）。 */
    public static <T> BaseResponse<T> fail(ResultCode resultCode, String msg) {
        return BaseResponse.error(resultCode, msg);
    }

    /** 解析成功消息（内部复用）。 */
    private static String resolveSuccessMsg() {
        return BaseResponse.resolveMessage(BaseResponse.MSG_OPERATION_SUCCESS, "操作成功");
    }
}
