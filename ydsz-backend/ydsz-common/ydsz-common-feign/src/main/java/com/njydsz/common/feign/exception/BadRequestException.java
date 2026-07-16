package com.njydsz.common.feign.exception;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 请求参数异常。
 *
 * <p>当 Feign 客户端收到 HTTP 400（Bad Request）状态码时抛出此异常，
 * 表示下游服务认为请求参数有误或请求体格式不正确。
 *
 * <p>适用场景：
 * <ul>
 *   <li>请求参数校验失败（如必填参数缺失、格式错误）</li>
 *   <li>请求体 JSON 格式错误</li>
 *   <li>参数值超出有效范围</li>
 * </ul>
 *
 * <p>错误消息示例：
 * <pre>
 * Feign 调用失败, method: UserService#getUser, request: GET http://user-service/api/user/123,
 * status: 400, reason: Bad Request, body: {"code":"100001","msg":"用户ID格式不正确"}
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Getter
@NoArgsConstructor
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private String code = "400";

    /**
     * 附加数据。
     */
    private transient Object data;

    /**
     * 创建请求参数异常。
     *
     * @param message 异常消息
     */
    public BadRequestException(String message) {
        super(message);
        this.code = "400";
    }

    /**
     * 创建请求参数异常。
     *
     * @param message 异常消息
     * @param cause   原因异常
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.code = "400";
    }

    /**
     * 设置错误码。
     *
     * @param code 错误码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 设置附加数据。
     *
     * @param data 附加数据
     */
    public void setData(Object data) {
        this.data = data;
    }
}
