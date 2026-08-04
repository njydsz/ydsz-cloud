package com.njydsz.common.core.response;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.ProblemDetail;
import com.njydsz.common.json.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.slf4j.MDC;

import java.io.Serializable;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
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
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see IResponse
 * @see PageResponse
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuperBuilder
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp"})
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    public static final String SUCCESS = BaseResultCode.SUCCESS.getCode();

    /**
     * 失败状态码（复用 {@link BaseResultCode#UNKNOWN}，与错误码体系保持一致）。
     */
    public static final String ERROR = BaseResultCode.UNKNOWN.getCode();

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
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), null);
    }

    /**
     * 返回成功数据
     *
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success(T data) {
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), data);
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
        return of(ERROR, resolveMessage(MSG_OPERATION_FAIL, "操作失败"), null);
    }

    /**
     * 返回失败消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String msg) {
        return of(ERROR, msg, null);
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
     * 返回失败消息
     *
     * @param code 错误码
     * @param msg 消息内容
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String code, String msg, T data) {
        return of(code, msg, data);
    }

    /**
     * 国际化消息解析器接口
     */
    @FunctionalInterface
    public interface MessageResolver {
        /**
         * 解析国际化消息
         *
         * @param key 国际化消息 key
         * @param defaultValue 默认消息文本
         * @return 解析后的消息内容
         */
        String resolve(String key, String defaultValue);
    }

    /**
     * 国际化消息解析器实例（AtomicReference 保证线程安全和一次性设置）。
     *
     * <p>采用一次性设置语义：启动时由 {@code CoreAutoConfiguration} 注入，
     * 后续不可修改，消除全局可变状态的线程安全隐患。</p>
     */
    private static final AtomicReference<MessageResolver> RESOLVER = new AtomicReference<>();

    /**
     * 一次性设置全局消息解析器（仅首次调用生效）。
     *
     * <p>由 {@code CoreAutoConfiguration} 在应用启动时调用。
     * 由于采用一次性设置语义，重复调用不会覆盖已有解析器，
     * 确保 i18n 解析行为在应用生命周期内保持一致。</p>
     *
     * @param resolver 消息解析器实现
     * @return true=设置成功（首次），false=已存在解析器（忽略）
     * @since 1.6.0
     */
    public static boolean setResolverIfAbsent(MessageResolver resolver) {
        return RESOLVER.compareAndSet(null, resolver);
    }

    /**
     * 设置全局消息解析器（已废弃）。
     *
     * <p>仅为向后兼容保留，新代码请使用 {@link #setResolverIfAbsent(MessageResolver)}。
     * 注意：此方法会覆盖已有解析器，可能导致 i18n 行为在运行期发生变化。</p>
     *
     * @param resolver 消息解析器实现
     * @deprecated 使用 {@link #setResolverIfAbsent(MessageResolver)} 替代
     */
    @Deprecated
    public static void setResolver(MessageResolver resolver) {
        RESOLVER.set(resolver);
    }

    /**
     * 解析国际化消息，若未设置解析器则返回默认值
     *
     * @param key 国际化消息 key
     * @param defaultValue 默认消息文本
     * @return 解析后的消息内容
     */
    protected static String resolveMessage(String key, String defaultValue) {
        MessageResolver currentResolver = RESOLVER.get();
        if (currentResolver != null) {
            String result = currentResolver.resolve(key, defaultValue);
            return result != null ? result : defaultValue;
        }
        return defaultValue;
    }

    /**
     * 检查国际化消息解析器是否已注册
     *
     * @return 已注册返回 true，否则返回 false
     */
    public static boolean isResolverRegistered() {
        return RESOLVER.get() != null;
    }

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
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), null);
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
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
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
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息（泛型兼容版本）。
     *
     * <p>仅为向后兼容旧版 API 保留，新代码请使用 {@link #errorWithDetail(ResultCode, String)}。
     * 此方法仅在类型匹配时工作，否则抛出 ClassCastException。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情（实例特定信息）
     * @param type       期望的返回数据类型（必须为 ProblemDetail.class）
     * @param <T>        数据类型
     * @return 携带 ProblemDetail 的失败消息
     * @deprecated 使用 {@link #errorWithDetail(ResultCode, String)} 替代，返回类型更明确
     * @since 1.1.0
     */
    @Deprecated
    public static <T> BaseResponse<T> errorWithDetail(ResultCode resultCode, String detail, Class<T> type) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail);
        if (!type.isInstance(problem)) {
            throw new ClassCastException("Expected " + type.getName() + " but got " + problem.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        BaseResponse<T> response = (BaseResponse<T>) of(
                resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
        return response;
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息（含请求路径，泛型兼容版本）。
     *
     * <p>仅为向后兼容保留，新代码请使用 {@link #errorWithDetail(ResultCode, String, URI)}。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @param instance   请求路径 URI
     * @param type       期望的返回数据类型（必须为 ProblemDetail.class）
     * @param <T>        数据类型
     * @return 携带 ProblemDetail 的失败消息
     * @deprecated 使用 {@link #errorWithDetail(ResultCode, String, URI)} 替代，返回类型更明确
     * @since 1.1.0
     */
    @Deprecated
    public static <T> BaseResponse<T> errorWithDetail(ResultCode resultCode, String detail, URI instance, Class<T> type) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
        if (!type.isInstance(problem)) {
            throw new ClassCastException("Expected " + type.getName() + " but got " + problem.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        BaseResponse<T> response = (BaseResponse<T>) of(
                resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
        return response;
    }

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

}
