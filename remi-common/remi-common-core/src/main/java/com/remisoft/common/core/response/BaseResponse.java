package com.remisoft.common.core.response;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.core.code.IExceptionResultCode;
import com.remisoft.common.core.code.ResultCode;
import com.remisoft.common.core.config.MessageResolverHolder;
import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.core.context.ProblemDetail;
import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.slf4j.MDC;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一API返回结果封装类
 *
 * <p>用于前后端交互的标准返回格式，封装了响应码、消息、数据和时间戳。
 *
 * <p><b>响应结构：</b>
 * <ul>
 *   <li>code: 响应码，A00000表示成功，其他表示失败</li>
 *   <li>msg: 响应消息</li>
 *   <li>data: 响应数据</li>
 *   <li>timestamp: 响应时间戳</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 返回成功
 * return BaseResponse.success(user);
 *
 * // 返回失败（走 i18n）
 * return BaseResponse.error(BaseResultCode.NOT_FOUND);
 * }</pre>
 *
 * @param <T> 数据泛型
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see IResponse
 * @see PageResponse
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp", "extensions"})
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    public static final String SUCCESS = BaseResultCode.SUCCESS.getCode();

    /**
     * 未知错误状态码（复用 {@link BaseResultCode#UNKNOWN}，与错误码体系保持一致）。
     *
     * <p>该常量表示系统未知错误（C99999），用于与 {@link #SUCCESS} 进行反向校验场景。
     *
     * @since 1.7.0
     */
    public static final String UNKNOWN_CODE = BaseResultCode.UNKNOWN.getCode();

    /**
     * 失败状态码（复用 {@link BaseResultCode#UNKNOWN}，与错误码体系保持一致）。
     *
     * @deprecated 使用 {@link #UNKNOWN_CODE} 替代，语义更明确
     */
    @Deprecated
    public static final String ERROR = UNKNOWN_CODE;

    /**
     * 国际化消息 key
     */
    public static final String MSG_OPERATION_SUCCESS = "response.success";

    /**
     * 操作失败国际化消息 key
     */
    public static final String MSG_OPERATION_FAIL = "response.error";

    /**
     * 返回编码
     */
    @EqualsAndHashCode.Include
    private String code;

    /**
     * 返回信息
     */
    private String msg;

    /**
     * 返回数据
     *
     * <p>泛型类型 T 无法限定为 Serializable（API 响应可携带任意类型数据），
     * Java 序列化非主要序列化方式（项目使用 Jackson JSON），此处抑制编译器警告。
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 扩展字段（可选）。
     *
     * <p>用于携带额外的上下文信息，如 requestId、debugInfo、cost 等。
     * 为 {@code null} 时不序列化（通过 {@code @JsonInclude(NON_NULL)} 控制）。</p>
     *
     * @since 1.6.0
     */
    private Map<String, Object> extensions;

    /**
     * 默认构造函数
     */
    public BaseResponse() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }

    /**
     * 全参数构造函数
     *
     * @param code 响应码
     * @param msg 响应消息
     * @param data 响应数据
     */
    public BaseResponse(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }

    /**
     * 创建BaseResponse实例
     *
     * @param code 状态码
     * @param msg 消息
     * @param data 数据
     * @param <T> 数据类型
     * @return BaseResponse实例
     */
    public static <T> BaseResponse<T> of(String code, String msg, T data) {
        return new BaseResponse<>(code, msg, data);
    }

    /**
     * 返回成功消息
     *
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success() {
        return of(SUCCESS, MessageResolverHolder.resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), null);
    }

    /**
     * 返回成功数据
     *
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success(T data) {
        return of(SUCCESS, MessageResolverHolder.resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), data);
    }

    /**
     * 返回成功消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> successMsg(String msg) {
        BaseResponse<T> response = new BaseResponse<>();
        response.code = SUCCESS;
        response.msg = msg;
        return response;
    }

    /**
     * 返回成功消息
     *
     * @param msg 消息内容
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success(String msg, T data) {
        return of(SUCCESS, msg, data);
    }

    /**
     * 返回失败消息
     *
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error() {
        return of(UNKNOWN_CODE, MessageResolverHolder.resolveMessage(MSG_OPERATION_FAIL, "操作失败"), null);
    }

    /**
     * 返回失败消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String msg) {
        return of(UNKNOWN_CODE, msg, null);
    }

    /**
     * 返回失败消息
     *
     * @param code 错误码
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String code, String msg) {
        return of(code, msg, null);
    }

    /**
     * 返回失败消息（自定义数据）。
     *
     * <p><b>注意：</b>此方法已废弃，建议使用 {@link #of(String, String, Object)} 替代。
     *
     * @param code 错误码
     * @param msg  消息内容
     * @param data 数据内容
     * @param <T>  数据类型
     * @return 失败消息
     * @since 1.0.0
     * @deprecated 使用 {@link #of(String, String, Object)} 替代，语义更明确
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> BaseResponse<T> error(String code, String msg, T data) {
        return of(code, msg, data);
    }

    // ======================== 国际化 API ========================

    /**
     * 检查国际化消息解析器是否已注册。
     *
     * <p>委托给 {@link MessageResolverHolder#isResolverRegistered()} 实现。
     *
     * @return 已注册返回 true，否则返回 false
     * @since 2.0.0
     */
    public static boolean isResolverRegistered() {
        return MessageResolverHolder.isResolverRegistered();
    }

    // ======================== 错误码响应构建 ========================

    /**
     * 返回失败消息
     *
     * <p>自动走 i18n 链路：使用 {@link ResultCode#getMessageKey()} 作为
     * 国际化 key 解析消息，解析失败时回退到 {@link ResultCode#getMsg()}。</p>
     *
     * @param resultCode 结果码
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode) {
        return of(resultCode.getCode(),
                MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), null);
    }

    /**
     * 返回失败消息
     *
     * @param resultCode 结果码
     * @param msg 自定义消息（覆盖 ResultCode 默认消息，不走 i18n）
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, String msg) {
        return of(resultCode.getCode(), msg, null);
    }

    /**
     * 从 Throwable 构建标准化错误响应（RFC 7807 Problem Details）。
     *
     * <p>提供与异常体系的轻量级桥接，允许在 core 模块中将任意异常转换为
     * 符合 RFC 7807 规范的 {@link ProblemDetail} 响应。</p>
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>如果异常实现了 {@link ResultCode} 接口，直接使用其错误码和 HTTP 状态码</li>
     *   <li>如果异常实现了 {@link IExceptionResultCode} 接口，通过 {@link IExceptionResultCode#resultCode()} 获取</li>
     *   <li>如果以上都不满足，回退到 {@link BaseResultCode#UNKNOWN} 错误码</li>
     *   <li>异常消息作为 {@code ProblemDetail.detail} 返回；请求路径作为 {@code instance}</li>
     *   <li>自动从 MDC 中注入当前线程的 {@code traceId}，保证错误响应可被链路追踪系统关联</li>
     * </ol>
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * try {
     *     // ... 业务逻辑
     * } catch (BusinessException e) {
     *     return BaseResponse.error(e, URI.create("/api/v1/orders"));
     * } catch (Exception e) {
     *     return BaseResponse.error(e);
     * }
     * }</pre>
     *
     * @param throwable 异常对象
     * @param instance  请求路径 URI（可为 null）
     * @return 携带 {@link ProblemDetail} 的错误响应（traceId 已自动从 MDC 注入）
     * @since 1.6.0
     * @see ProblemDetail
     * @see IExceptionResultCode
     */
    public static BaseResponse<ProblemDetail> error(Throwable throwable, URI instance) {
        if (throwable == null) {
            return errorWithDetail(BaseResultCode.UNKNOWN, "未知错误", instance);
        }

        String detail = throwable.getMessage();
        if (detail == null || detail.isEmpty()) {
            detail = throwable.getClass().getSimpleName();
        }

        // 尝试从异常中提取 ResultCode（优先 ResultCode 接口，其次 IExceptionResultCode 桥接）
        ResultCode resultCode = extractResultCode(throwable);
        if (resultCode != null) {
            // 使用 ProblemDetail.of() 工厂方法，减少模板代码并确保字段填充一致
            ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
            autoFillTraceIdFromMdc(problem);
            return of(resultCode.getCode(),
                    MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
        }

        // 默认使用 UNKNOWN
        return errorWithDetail(BaseResultCode.UNKNOWN, detail, instance);
    }

    /**
     * 从 Throwable 构建标准化错误响应（便捷重载，不携带 instance）。
     *
     * @param throwable 异常对象
     * @return 携带 {@link ProblemDetail} 的错误响应
     * @since 1.6.0
     * @see #error(Throwable, URI)
     */
    public static BaseResponse<ProblemDetail> error(Throwable throwable) {
        return error(throwable, null);
    }

    /**
     * 从异常中提取 ResultCode（接口桥接方式，无反射开销）。
     *
     * <p>支持以下场景（按优先级）：
     * <ol>
     *   <li>异常本身实现了 {@link ResultCode} 接口 —— 直接强转</li>
     *   <li>异常实现了 {@link IExceptionResultCode} 接口 —— 调用 {@link IExceptionResultCode#resultCode()}</li>
     * </ol>
     *
     * <p>对未实现上述接口的异常返回 {@code null}，调用方应回退到 UNKNOWN。
     *
     * @param throwable 异常对象
     * @return 提取到的 ResultCode；无法提取时返回 null
     * @since 1.7.0
     */
    private static ResultCode extractResultCode(Throwable throwable) {
        if (throwable instanceof ResultCode resultCode) {
            return resultCode;
        }
        if (throwable instanceof IExceptionResultCode exceptionWithCode) {
            return exceptionWithCode.resultCode();
        }
        return null;
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息
     *
     * <p>将标准化的错误详情封装到 {@link ProblemDetail} 中作为 data 返回，
     * 便于前端和第三方系统按 RFC 7807 规范处理错误。
     * 返回类型明确为 {@code BaseResponse<ProblemDetail>}，无需类型强转。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情（实例特定信息）
     * @return 携带 {@link ProblemDetail} 的失败消息
     * @since 1.6.0
     * @see ProblemDetail
     */
    public static BaseResponse<ProblemDetail> errorWithDetail(ResultCode resultCode, String detail) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail);
        autoFillTraceIdFromMdc(problem);
        return of(resultCode.getCode(),
                MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息（含请求路径）
     *
     * <p>返回类型明确为 {@code BaseResponse<ProblemDetail>}，调用方无需强转。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @param instance   请求路径 URI
     * @return 携带 {@link ProblemDetail} 的失败消息
     * @since 1.6.0
     */
    public static BaseResponse<ProblemDetail> errorWithDetail(ResultCode resultCode, String detail, URI instance) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
        autoFillTraceIdFromMdc(problem);
        return of(resultCode.getCode(),
                MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
    }

    /**
     * 从 MDC 中提取当前线程的 traceId 和 requestId 并注入 ProblemDetail。
     *
     * <p>若 MDC 中无有效值，则不修改 ProblemDetail 对应字段，避免覆盖 Builder 设置的值。
     * traceId 用于贯通多个服务的链路追踪，requestId 用于标识单次入口请求。</p>
     *
     * @param problem 待注入的 ProblemDetail 实例（不可为 null）
     * @since 2.0.0
     */
    private static void autoFillTraceIdFromMdc(ProblemDetail problem) {
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

    // ======================== 状态判断 ========================

    /**
     * 判断是否成功
     *
     * @return 成功返回 true，否则返回 false
     */
    @Override
    public boolean isSuccess() {
        return SUCCESS.equals(this.code);
    }

    /**
     * 判断是否失败
     *
     * @return 失败返回true，否则返回false
     */
    public boolean isFailed() {
        return !isSuccess();
    }

    // ======================== 扩展字段操作 ========================

    /**
     * 添加扩展字段（链式调用）。
     *
     * <p>用于携带额外的上下文信息，如 requestId、debugInfo、cost 等。
     * 示例：{@code return BaseResponse.success(data).putExtension("requestId", "req-123");}</p>
     *
     * @param key   扩展键
     * @param value 扩展值
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> putExtension(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("extension key must not be null or empty");
        }
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * 批量添加扩展字段（链式调用）。
     *
     * @param exts 扩展键值对（可为 null）
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> putExtensions(Map<String, Object> exts) {
        if (exts != null && !exts.isEmpty()) {
            if (this.extensions == null) {
                this.extensions = new HashMap<>();
            }
            this.extensions.putAll(exts);
        }
        return this;
    }

    /**
     * 获取扩展字段。
     *
     * @param key 扩展键
     * @return 扩展值；不存在返回 null
     * @since 1.6.0
     */
    public Object getExtension(String key) {
        return this.extensions != null ? this.extensions.get(key) : null;
    }

    /**
     * 移除扩展字段（链式调用）。
     *
     * @param key 扩展键
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> removeExtension(String key) {
        if (this.extensions != null) {
            this.extensions.remove(key);
            // 如果移除后为 null，清理 Map 以节省内存并避免序列化
            if (this.extensions.isEmpty()) {
                this.extensions = null;
            }
        }
        return this;
    }

    /**
     * 判断是否有任何扩展字段。
     *
     * @return 存在扩展字段返回 true；否则返回 false
     * @since 1.6.0
     */
    public boolean hasExtensions() {
        return this.extensions != null && !this.extensions.isEmpty();
    }
}
