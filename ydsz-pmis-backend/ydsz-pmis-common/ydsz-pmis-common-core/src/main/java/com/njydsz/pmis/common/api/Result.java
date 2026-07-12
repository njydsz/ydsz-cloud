package com.njydsz.pmis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 统一响应封装
 *
 * <p>提供函数式链式操作能力，支持 map / flatMap / filter / recover / ifSuccess / ifFailure 等，
 * 适用于 Service 层多步校验与 Controller 层响应构建场景。
 *
 * @param <T> 数据类型
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一响应")
public class Result<T> implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码: 0=成功 */
    public static final int CODE_SUCCESS = 0;

    /** 状态码: 默认失败 */
    public static final int CODE_FAIL = -1;

    /** 状态码 */
    @Schema(description = "状态码：0=成功，其他=失败")
    private int code;

    /** 提示信息 */
    @Schema(description = "提示信息")
    private String message;

    /** 响应数据 */
    @Schema(description = "响应数据")
    private T data;

    /** 链路追踪 ID */
    @Schema(description = "链路追踪 ID")
    private String traceId;

    /** 服务器时间戳 */
    @Schema(description = "服务器时间戳")
    private long timestamp = System.currentTimeMillis();

    // ==================== 构造方法 ====================

    /**
     * 默认构造方法
     */
    public Result() {
        this.traceId = MDC.get("traceId");
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 构建成功响应（无数据载荷）
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构建成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(CODE_SUCCESS);
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    /**
     * 构建成功响应（带数据与自定义提示信息）
     *
     * @param data    响应数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data, String message) {
        Result<T> r = ok(data);
        r.setMessage(message);
        return r;
    }

    /**
     * 构建失败响应（指定状态码与提示信息）
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failed(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    /**
     * 失败响应（快捷别名，等价 failed(-1, message)）
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        return failed(CODE_FAIL, message);
    }

    /**
     * 失败响应（基于业务错误码）
     *
     * @param errorCode 业务错误码
     * @param <T>       数据类型
     * @return 失败结果
     */
    public static <T> Result<T> failed(BizErrorCode errorCode) {
        return failed(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 构建失败响应（基于业务错误码并覆盖提示信息）
     *
     * @param errorCode 业务错误码
     * @param message   提示信息
     * @param <T>       数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failed(BizErrorCode errorCode, String message) {
        return failed(errorCode.getCode(), message);
    }

    /**
     * 构建失败响应（基于异常）
     *
     * @param throwable 异常
     * @param <T>       数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failed(Throwable throwable) {
        return failed(CODE_FAIL, throwable.getMessage());
    }

    // ==================== 状态判断 ====================

    /**
     * 判断当前响应是否为成功响应
     *
     * @return true 表示成功；false 表示失败
     */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }

    /**
     * 判断当前响应是否为失败响应
     *
     * @return true 表示失败；false 表示成功
     */
    public boolean isFailed() {
        return code != CODE_SUCCESS;
    }

    // ==================== 函数式操作 ====================

    /**
     * 对成功响应的数据进行映射变换
     *
     * <p>若当前为失败响应，直接传递失败状态，不执行映射函数。
     *
     * @param mapper 映射函数
     * @param <R>    目标数据类型
     * @return 变换后的 Result
     */
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        if (isFailed()) {
            return Result.failed(this.code, this.message);
        }
        return Result.ok(mapper.apply(this.data));
    }

    /**
     * 对成功响应的数据进行 flatMap 变换（链式调用）
     *
     * <p>若当前为失败响应，直接传递失败状态，不执行变换函数。
     *
     * @param mapper 变换函数，返回新的 Result
     * @param <R>    目标数据类型
     * @return 变换后的 Result
     */
    public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        if (isFailed()) {
            return Result.failed(this.code, this.message);
        }
        Result<R> result = mapper.apply(this.data);
        if (result == null) {
            return Result.failed(CODE_FAIL, "flatMap returned null");
        }
        return result;
    }

    /**
     * 对成功响应的数据进行过滤
     *
     * <p>若过滤条件不满足，返回指定错误码的失败响应。
     * 若当前为失败响应，直接传递失败状态。
     *
     * @param predicate 过滤条件
     * @param errorCode 不满足条件时的错误码
     * @return 过滤后的 Result
     */
    public Result<T> filter(Predicate<? super T> predicate, BizErrorCode errorCode) {
        if (isFailed()) {
            return this;
        }
        if (predicate.test(this.data)) {
            return this;
        }
        return Result.failed(errorCode);
    }

    /**
     * 对成功响应的数据进行过滤
     *
     * <p>若过滤条件不满足，返回指定错误信息的失败响应。
     *
     * @param predicate 过滤条件
     * @param message   不满足条件时的错误信息
     * @return 过滤后的 Result
     */
    public Result<T> filter(Predicate<? super T> predicate, String message) {
        if (isFailed()) {
            return this;
        }
        if (predicate.test(this.data)) {
            return this;
        }
        return Result.failed(CODE_FAIL, message);
    }

    /**
     * 从失败响应中恢复
     *
     * <p>若当前为失败响应，执行恢复函数返回新的数据。
     * 若当前为成功响应，直接返回自身。
     *
     * @param recoverFunction 恢复函数，接收错误码与消息，返回恢复数据
     * @return 恢复后的 Result
     */
    public Result<T> recover(Function<RecoverContext, T> recoverFunction) {
        if (isSuccess()) {
            return this;
        }
        T recovered = recoverFunction.apply(new RecoverContext(this.code, this.message));
        return Result.ok(recovered);
    }

    /**
     * 成功时执行回调（不改变响应内容）
     *
     * @param action 回调函数
     * @return 当前 Result（用于链式调用）
     */
    public Result<T> ifSuccess(Consumer<? super T> action) {
        if (isSuccess() && data != null) {
            action.accept(data);
        }
        return this;
    }

    /**
     * 失败时执行回调（不改变响应内容）
     *
     * @param action 回调函数
     * @return 当前 Result（用于链式调用）
     */
    public Result<T> ifFailure(Consumer<RecoverContext> action) {
        if (isFailed()) {
            action.accept(new RecoverContext(this.code, this.message));
        }
        return this;
    }

    // ==================== 值获取 ====================

    /**
     * 返回成功数据，若失败则返回默认值
     *
     * @param defaultValue 默认值
     * @return 成功数据或默认值
     */
    public T orElse(T defaultValue) {
        return isSuccess() ? data : defaultValue;
    }

    /**
     * 返回成功数据，若失败则通过供应者获取默认值
     *
     * @param defaultValueSupplier 默认值供应者
     * @return 成功数据或供应者提供的默认值
     */
    public T orElseGet(Supplier<? extends T> defaultValueSupplier) {
        return isSuccess() ? data : defaultValueSupplier.get();
    }

    /**
     * 返回成功数据，若失败则抛出 IllegalStateException
     *
     * @return 成功数据
     * @throws IllegalStateException 如果当前为失败响应
     */
    public T orElseThrow() {
        if (isFailed()) {
            throw new IllegalStateException("result failed: code=" + code + ", message=" + message);
        }
        return data;
    }

    /**
     * 返回成功数据，若失败则抛出指定异常
     *
     * @param exceptionSupplier 异常供应者
     * @param <X>               异常类型
     * @return 成功数据
     * @throws X 如果当前为失败响应
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (isFailed()) {
            throw exceptionSupplier.get();
        }
        return data;
    }

    // ==================== 内部类 ====================

    /**
     * 恢复上下文，包含错误码与错误信息
     */
    public static final class RecoverContext implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int code;
        private final String message;

        /**
         * 构造恢复上下文
         *
         * @param code    错误码
         * @param message 错误信息
         */
        public RecoverContext(int code, String message) {
            this.code = code;
            this.message = message;
        }

        /**
         * 获取错误码
         *
         * @return 错误码
         */
        public int getCode() {
            return code;
        }

        /**
         * 获取错误信息
         *
         * @return 错误信息
         */
        public String getMessage() {
            return message;
        }
    }
}
