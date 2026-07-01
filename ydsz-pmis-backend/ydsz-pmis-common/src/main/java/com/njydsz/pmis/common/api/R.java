package com.njydsz.pmis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应封装
 *
 * @param <T> 数据类型
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一响应")
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码: 0=成功 */
    public static final int CODE_SUCCESS = 0;

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

    /**
     * 构建成功响应（无数据载荷）
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 构建成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
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
    public static <T> R<T> ok(T data, String message) {
        R<T> r = ok(data);
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
    public static <T> R<T> failed(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    /**
     * 构建失败响应（基于业务错误码）
     *
     * @param errorCode 业务错误码
     * @param <T>       数据类型
     * @return 失败响应
     */
    public static <T> R<T> failed(BizErrorCode errorCode) {
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
    public static <T> R<T> failed(BizErrorCode errorCode, String message) {
        return failed(errorCode.getCode(), message);
    }

    /**
     * 判断当前响应是否为成功响应
     *
     * @return true 表示成功；false 表示失败
     */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }
}
