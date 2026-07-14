package com.njydsz.pmis.common.core.response;

import java.io.Serializable;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.MDC;

import com.njydsz.pmis.common.json.annotation.JsonField;
import com.njydsz.pmis.common.json.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see IResponse
 * @see PageResponse
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuperBuilder
@JsonField(notWriteNullValue = true)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp"})
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 3L;

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
     * 时钟提供者 - 使用 AtomicReference 保证线程安全和性能
     * <p>相比 volatile 字段，AtomicReference 提供更好的内存可见性语义和更低的读取开销
     */
    private static final AtomicReference<Clock> CLOCK_HOLDER = 
        new AtomicReference<>(Clock.systemDefaultZone());

    /**
     * 默认构造函数
     */
    public BaseResponse() {
        this.timestamp = CLOCK_HOLDER.get().millis();
        this.traceId = MDC.get("traceId");
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
        this.timestamp = CLOCK_HOLDER.get().millis();
        this.traceId = MDC.get("traceId");
    }

    /**
     * 设置时钟（用于单元测试）
     *
     * @param clock 时钟实例
     */
    public static void setClock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }
        CLOCK_HOLDER.set(clock);
    }

    /**
     * 获取当前时钟（用于测试验证）
     *
     * @return 当前时钟实例
     */
    public static Clock getClock() {
        return CLOCK_HOLDER.get();
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
     * 判断是否成功
     *
     * @return 成功返回true，否则返回false
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

    /**
     * 获取消息（getMessage 别名，兼容旧代码调用）
     *
     * @return 响应消息
     */
    public String getMessage() {
        return msg;
    }

    // ==================== 工厂方法别名（兼容旧 Result API） ====================

    /**
     * 构建成功响应（无数据载荷）
     *
     * @param <T> 数据类型
     * @return 成功响应
     * @see #success()
     */
    public static <T> BaseResponse<T> ok() {
        return success();
    }

    /**
     * 构建成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     * @see #success(Object)
     */
    public static <T> BaseResponse<T> ok(T data) {
        return success(data);
    }

    /**
     * 构建成功响应（带数据与自定义提示信息）
     *
     * @param data    响应数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> BaseResponse<T> ok(T data, String message) {
        return of(SUCCESS, message, data);
    }

    /**
     * 失败响应（快捷别名，等价 error(message)）
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败结果
     * @see #error(String)
     */
    public static <T> BaseResponse<T> fail(String message) {
        return error(message);
    }

    /**
     * 构建失败响应（指定状态码与提示信息）
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败响应
     * @see #error(String, String)
     */
    public static <T> BaseResponse<T> failed(String code, String message) {
        return error(code, message);
    }

    /**
     * 失败响应（基于结果码）
     *
     * @param resultCode 结果码
     * @param <T>        数据类型
     * @return 失败结果
     * @see #error(ResultCode)
     */
    public static <T> BaseResponse<T> failed(ResultCode resultCode) {
        return error(resultCode);
    }

    /**
     * 构建失败响应（基于结果码并覆盖提示信息）
     *
     * @param resultCode 结果码
     * @param message    提示信息
     * @param <T>        数据类型
     * @return 失败响应
     * @see #error(ResultCode, String)
     */
    public static <T> BaseResponse<T> failed(ResultCode resultCode, String message) {
        return error(resultCode, message);
    }

    /**
     * 构建失败响应（基于异常）
     *
     * @param throwable 异常
     * @param <T>       数据类型
     * @return 失败响应
     */
    public static <T> BaseResponse<T> failed(Throwable throwable) {
        return error(throwable.getMessage());
    }
}
