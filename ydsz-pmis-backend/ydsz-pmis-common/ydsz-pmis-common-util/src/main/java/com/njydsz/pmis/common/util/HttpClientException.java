package com.njydsz.pmis.common.util;

/**
 * HTTP 客户端异常
 *
 * <p>封装 HTTP 调用过程中的各类异常，包含 HTTP 状态码与响应体。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class HttpClientException extends RuntimeException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** HTTP 状态码（-1 表示连接异常，非 HTTP 错误） */
    private final int statusCode;

    /** HTTP 响应体（可能为 null） */
    private final String responseBody;

    /**
     * 构造 HTTP 客户端异常
     *
     * @param statusCode HTTP 状态码
     * @param message    异常信息
     */
    public HttpClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = null;
    }

    /**
     * 构造 HTTP 客户端异常（带响应体）
     *
     * @param statusCode   HTTP 状态码
     * @param message      异常信息
     * @param responseBody HTTP 响应体
     */
    public HttpClientException(int statusCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * 构造 HTTP 客户端异常（带原因）
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码（-1 表示连接异常）
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取 HTTP 响应体
     *
     * @return HTTP 响应体（可能为 null）
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * 判断是否为客户端错误（4xx）
     *
     * @return true 表示 4xx 错误
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 判断是否为服务端错误（5xx）
     *
     * @return true 表示 5xx 错误
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}
