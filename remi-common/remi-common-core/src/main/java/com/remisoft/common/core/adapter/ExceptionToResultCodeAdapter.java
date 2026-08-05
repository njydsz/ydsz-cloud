package com.remisoft.common.core.adapter;

import java.net.URI;
import java.util.Optional;

import org.slf4j.MDC;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.core.code.IExceptionResultCode;
import com.remisoft.common.core.code.ResultCode;
import com.remisoft.common.core.config.MessageResolverHolder;
import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.core.context.ProblemDetail;
import com.remisoft.common.core.response.BaseResponse;

/**
 * 异常到统一错误码响应的适配器。
 *
 * <p>将任意 {@link Throwable} 适配为 {@link BaseResponse}，负责：
 * <ul>
 *   <li>从异常中提取 {@link ResultCode}（接口桥接方式，无反射开销）</li>
 *   <li>构建 RFC 7807 {@link ProblemDetail} 响应体</li>
 *   <li>自动注入 MDC 中的 traceId/requestId 用于链路追踪</li>
 *   <li>国际化消息解析</li>
 * </ul>
 *
 * <p>支持两种异常类型（按优先级）：
 * <ol>
 *   <li>异常本身实现了 {@link ResultCode} 接口 —— 直接强转</li>
 *   <li>异常实现了 {@link IExceptionResultCode} 接口 —— 调用 {@link IExceptionResultCode#resultCode()}</li>
 * </ol>
 *
 * <p>未实现上述接口的异常统一映射为 {@link BaseResultCode#UNKNOWN}。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * try {
 *     riskyOperation();
 * } catch (Exception e) {
 *     return ExceptionToResultCodeAdapter.toErrorResponse(e, httpRequest.getURI());
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 2.1.0
 * @see BaseResponse#error(Throwable)
 * @see BaseResponse#error(Throwable, URI)
 * @see ResultCode
 * @see IExceptionResultCode
 */
public final class ExceptionToResultCodeAdapter {

    private ExceptionToResultCodeAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将异常适配为标准化错误响应（便捷重载，不携带 instance）。
     *
     * @param throwable 异常对象
     * @return 携带 {@link ProblemDetail} 的错误响应
     */
    public static BaseResponse<ProblemDetail> toErrorResponse(Throwable throwable) {
        return toErrorResponse(throwable, null);
    }

    /**
     * 将异常适配为标准化错误响应（含请求路径）。
     *
     * @param throwable 异常对象
     * @param instance  请求路径 URI（可为 null）
     * @return 携带 {@link ProblemDetail} 的错误响应（traceId 已自动从 MDC 注入）
     */
    public static BaseResponse<ProblemDetail> toErrorResponse(Throwable throwable, URI instance) {
        if (throwable == null) {
            return BaseResponse.unknownErrorResponse("未知错误", instance);
        }

        String detail = Optional.ofNullable(throwable.getMessage())
                .filter(msg -> !msg.isEmpty())
                .orElseGet(() -> throwable.getClass().getSimpleName());

        ResultCode resultCode = resolveResultCode(throwable);
        ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
        fillTraceContext(problem);

        return BaseResponse.of(resultCode.getCode(),
                MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()),
                problem);
    }

    /**
     * 从异常中解析 ResultCode（接口桥接方式，无反射开销）。
     *
     * <p>对未实现上述接口的异常返回 {@link BaseResultCode#UNKNOWN}，
     * 调用方可据此判断"未识别的异常类型"。</p>
     *
     * @param throwable 异常对象
     * @return 解析到的 ResultCode；无法解析时返回 UNKNOWN
     */
    public static ResultCode resolveResultCode(Throwable throwable) {
        if (throwable instanceof ResultCode resultCode) {
            return resultCode;
        }
        if (throwable instanceof IExceptionResultCode exceptionWithCode) {
            return exceptionWithCode.resultCode();
        }
        return BaseResultCode.UNKNOWN;
    }

    /**
     * 从 MDC 中提取当前线程的 traceId 和 requestId 并注入 ProblemDetail。
     *
     * <p>若 MDC 中无有效值，则不修改 ProblemDetail 对应字段，避免覆盖工厂方法设置的值。
     * traceId 用于贯通多个服务的链路追踪，requestId 用于标识单次入口请求。</p>
     *
     * @param problem 待注入的 ProblemDetail 实例（不可为 null）
     */
    private static void fillTraceContext(ProblemDetail problem) {
        // 自动注入 traceId
        String traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            problem.setTraceId(traceId);
        }
        // 自动注入 requestId（v2.0 新增）
        String requestId = MDC.get(HeaderConstants.MDC_REQUEST_ID_KEY);
        if (requestId != null && !requestId.isBlank()) {
            problem.setRequestId(requestId);
        }
    }
}
