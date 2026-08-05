package com.remisoft.common.core.response;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 统一 API 响应体。
 *
 * @param <T> 数据类型
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "timestamp"})
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SUCCESS_CODE = "A00000";

    private String code;
    private String msg;
    private T data;
    private Long timestamp;

    /**
     * 便捷构造器：code + msg + data。
     *
     * <p>用于子类（如 {@link PageResponse}）调用 {@code super(code, msg, data)}
     * 的场景，timestamp 保持 null（由 {@code @JsonInclude(NON_NULL)} 决定是否序列化）。</p>
     *
     * @param code 业务响应码
     * @param msg  响应消息
     * @param data 响应数据
     */
    public BaseResponse(String code, String msg, T data) {
        this(code, msg, data, null);
    }

    public static <T> BaseResponse<T> success() {
        return BaseResponse.<T>builder()
                .code(SUCCESS_CODE)
                .msg("ok")
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .code(SUCCESS_CODE)
                .msg("ok")
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> BaseResponse<T> success(String msg, T data) {
        return BaseResponse.<T>builder()
                .code(SUCCESS_CODE)
                .msg(msg)
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> BaseResponse<T> error(String code, String msg) {
        return BaseResponse.<T>builder()
                .code(code)
                .msg(msg)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> BaseResponse<T> error(BaseResultCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMsg());
    }

    public static <T> BaseResponse<T> error(BaseResultCode resultCode, String msg) {
        return error(resultCode.getCode(), msg);
    }

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(this.code);
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis();
    }
}
