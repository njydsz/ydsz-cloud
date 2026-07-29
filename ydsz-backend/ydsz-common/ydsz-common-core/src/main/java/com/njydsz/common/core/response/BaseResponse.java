package com.njydsz.common.core.response;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.constant.TraceConstants;
import com.njydsz.common.core.metrics.CoreMetrics;
import com.njydsz.common.json.annotation.YdszJsonField;
import com.njydsz.common.json.annotation.YdszJsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.slf4j.MDC;

import java.io.Serializable;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * // 返回成功带消息
 * return BaseResponse.success("操作成功", user);
 *
 * // 返回失败
 * return BaseResponse.error("参数错误");
 *
 * // 返回失败带错误码
 * return BaseResponse.error("A01002", "用户名已存在");
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
@YdszJsonField(notWriteNullValue = true)
@YdszJsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp"})
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    public static final String SUCCESS = "A00000";

    /**
     * 失败状态码
     */
    public static final String ERROR = "A01001";

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
        this.traceId = MDC.get(TraceConstants.MDC_TRACE_ID_KEY);
        recordMetrics();
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
        this.traceId = MDC.get(TraceConstants.MDC_TRACE_ID_KEY);
        recordMetrics();
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
     * @param msg 消息内容
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String msg, T data) {
        return of(ERROR, msg, data);
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
     * 消息解析器实例（volatile 保证多线程可见性）
     */
    private static volatile MessageResolver resolver;

    /**
     * 设置全局消息解析器（可覆盖）
     *
     * <p>由上层应用（如 Spring Boot 启动类或配置类）调用，注入国际化解析实现。
     * 后续调用将覆盖之前设置的解析器，以最后一次设置为准。
     *
     * @param resolver 消息解析器实现
     */
    public static void setResolver(MessageResolver resolver) {
        BaseResponse.resolver = resolver;
    }

    /**
     * 解析国际化消息，若未设置解析器则返回默认值
     *
     * @param key 国际化消息 key
     * @param defaultValue 默认消息文本
     * @return 解析后的消息内容
     */
    protected static String resolveMessage(String key, String defaultValue) {
        MessageResolver currentResolver = resolver;
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
        return resolver != null;
    }

    /**
     * 记录响应创建指标（委托到 CoreMetrics SPI）
     */
    private void recordMetrics() {
        CoreMetrics.recordResponseCreated(
                SUCCESS.equals(this.code), this.code);
    }

    /**
     * 返回失败消息
     *
     * @param resultCode 结果码
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /**
     * 返回失败消息
     *
     * @param resultCode 结果码
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, T data) {
        return of(resultCode.getCode(), resultCode.getMsg(), data);
    }

    /**
     * 返回失败消息
     *
     * @param resultCode 结果码
     * @param msg 自定义消息（覆盖 ResultCode 默认消息）
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, String msg) {
        return of(resultCode.getCode(), msg, null);
    }

    /**
     * 返回失败消息
     *
     * @param resultCode 结果码
     * @param throwable  异常对象（消息从 throwable.getMessage() 提取）
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, Throwable throwable) {
        String msg = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : resultCode.getMsg();
        return of(resultCode.getCode(), msg, null);
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息
     *
     * <p>将标准化的错误详情封装到 {@link ProblemDetail} 中作为 data 返回，
     * 便于前端和第三方系统按 RFC 7807 规范处理错误。
     *
     * @param resultCode 结果码
     * @param detail     错误详情（实例特定信息）
     * @param <T>        数据类型
     * @return 携带 ProblemDetail 的失败消息
     * @since 1.1.0
     * @see ProblemDetail
     */
    @SuppressWarnings("unchecked")
    public static <T> BaseResponse<T> errorWithDetail(ResultCode resultCode, String detail) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail);
        return of(resultCode.getCode(), resultCode.getMsg(), (T) problem);
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息（含请求路径）
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @param instance   请求路径 URI
     * @param <T>        数据类型
     * @return 携带 ProblemDetail 的失败消息
     * @since 1.1.0
     */
    @SuppressWarnings("unchecked")
    public static <T> BaseResponse<T> errorWithDetail(ResultCode resultCode, String detail, URI instance) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
        return of(resultCode.getCode(), resultCode.getMsg(), (T) problem);
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

    // ============================== 函数式 API（已废弃） ==============================

    /**
     * 返回数据，如果响应失败则返回默认值
     *
     * @param defaultValue 默认值
     * @return 成功时返回 data，失败时返回 defaultValue
     * @deprecated 项目未使用函数式风格处理响应，请直接使用 {@link #isSuccess()} + {@link #getData()} 判断。
     */
    @Deprecated
    public T orElse(T defaultValue) {
        return isSuccess() ? data : defaultValue;
    }

    /**
     * 返回数据，如果响应失败则抛出异常
     *
     * @return 成功时的 data
     * @throws IllegalStateException 如果响应失败
     * @deprecated 项目未使用函数式风格处理响应，请直接使用 {@link #isSuccess()} + {@link #getData()} 判断。
     */
    @Deprecated
    public T orElseThrow() {
        if (!isSuccess()) {
            throw new IllegalStateException("Response failed: code=" + code + ", msg=" + msg);
        }
        return data;
    }

    /**
     * 对成功的数据执行映射转换
     *
     * @param mapper 映射函数
     * @param <R>    目标类型
     * @return 包含映射后数据的新响应（失败时保持原样）
     * @deprecated 项目未使用函数式风格处理响应，请在业务层显式转换。
     */
    @Deprecated
    public <R> BaseResponse<R> map(Function<T, R> mapper) {
        if (isSuccess() && data != null) {
            return of(code, msg, mapper.apply(data));
        }
        return of(code, msg, null);
    }

    /**
     * 如果响应成功且数据非空，执行操作
     *
     * @param action 要执行的操作
     * @deprecated 项目未使用函数式风格处理响应，请直接使用 {@link #isSuccess()} + {@link #getData()} 判断。
     */
    @Deprecated
    public void ifSuccess(Consumer<T> action) {
        if (isSuccess() && data != null) {
            action.accept(data);
        }
    }

    /**
     * 如果响应失败，执行操作
     *
     * @param action 要执行的操作
     * @deprecated 项目未使用函数式风格处理响应，请直接使用 {@link #isFailed()} 判断。
     */
    @Deprecated
    public void ifFailed(Consumer<BaseResponse<T>> action) {
        if (!isSuccess()) {
            action.accept(this);
        }
    }

}
