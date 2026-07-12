package com.njydsz.pmis.common.util;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.http.HttpHeaders;
import java.util.Optional;

/**
 * HTTP 响应包装类
 *
 * <p>封装 HTTP 响应的状态码、响应体、响应头，并提供便捷的 JSON 反序列化方法。
 *
 * @param <T> 响应体类型
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class HttpResult<T> {

    /** HTTP 状态码 */
    private final int statusCode;

    /** 响应体（原始字符串） */
    private final String body;

    /** 响应头 */
    private final HttpHeaders headers;

    /** 反序列化后的响应体 */
    private final T data;

    /**
     * 构造 HTTP 响应包装
     *
     * @param statusCode HTTP 状态码
     * @param body       响应体（原始字符串）
     * @param headers    响应头
     * @param data       反序列化后的响应体（可能为 null）
     */
    private HttpResult(int statusCode, String body, HttpHeaders headers, T data) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
        this.data = data;
    }

    /**
     * 构建原始响应（不反序列化）
     *
     * @param statusCode HTTP 状态码
     * @param body       响应体
     * @param headers    响应头
     * @return HttpResult 实例
     */
    public static HttpResult<String> of(int statusCode, String body, HttpHeaders headers) {
        return new HttpResult<>(statusCode, body, headers, body);
    }

    /**
     * 构建带类型的响应（反序列化为指定类型）
     *
     * @param statusCode HTTP 状态码
     * @param body       响应体
     * @param headers    响应头
     * @param type       目标类型
     * @param <T>        目标类型
     * @return HttpResult 实例
     */
    public static <T> HttpResult<T> of(int statusCode, String body, HttpHeaders headers, Class<T> type) {
        T data = JsonUtils.parseObject(body, type);
        return new HttpResult<>(statusCode, body, headers, data);
    }

    /**
     * 构建带泛型类型的响应（反序列化为复杂泛型类型）
     *
     * @param statusCode HTTP 状态码
     * @param body       响应体
     * @param headers    响应头
     * @param type       目标类型 TypeReference
     * @param <T>        目标类型
     * @return HttpResult 实例
     */
    public static <T> HttpResult<T> of(int statusCode, String body, HttpHeaders headers, TypeReference<T> type) {
        T data = JsonUtils.parseObject(body, type);
        return new HttpResult<>(statusCode, body, headers, data);
    }

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取原始响应体
     *
     * @return 响应体字符串
     */
    public String getBody() {
        return body;
    }

    /**
     * 获取响应头
     *
     * @return 响应头
     */
    public HttpHeaders getHeaders() {
        return headers;
    }

    /**
     * 获取反序列化后的数据
     *
     * @return 反序列化后的数据
     */
    public T getData() {
        return data;
    }

    /**
     * 获取数据（Optional 包装）
     *
     * @return Optional 包装的数据
     */
    public Optional<T> getDataOptional() {
        return Optional.ofNullable(data);
    }

    /**
     * 判断是否为成功响应（2xx）
     *
     * @return true 表示成功
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 判断是否为客户端错误（4xx）
     *
     * @return true 表示客户端错误
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 判断是否为服务端错误（5xx）
     *
     * @return true 表示服务端错误
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * 成功时返回数据，失败时抛出 HttpClientException
     *
     * @return 反序列化后的数据
     * @throws HttpClientException 如果 HTTP 状态码非 2xx
     */
    public T orElseThrow() {
        if (!isSuccess()) {
            throw new HttpClientException(statusCode, "HTTP request failed: " + statusCode, body);
        }
        return data;
    }

    /**
     * 获取指定响应头的值
     *
     * @param name 头名称
     * @return 头值（可能为 null）
     */
    public String getHeader(String name) {
        if (headers == null) {
            return null;
        }
        return headers.firstValue(name).orElse(null);
    }
}
