package com.njydsz.pmis.common.util;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * HTTP 工具类（P2-6 增强）
 *
 * <p>基于 Java 11+ HttpClient，提供完整的 HTTP 调用封装：
 * <ul>
 *   <li>GET / POST / PUT / DELETE / PATCH 全方法支持</li>
 *   <li>泛型类型感知的 JSON 反序列化（集成 {@link JsonUtils}）</li>
 *   <li>可配置的超时、重试（指数退避）</li>
 *   <li>文件下载</li>
 *   <li>异步请求（CompletableFuture）</li>
 *   <li>连接池复用（全局 HttpClient 单例）</li>
 * </ul>
 *
 * <p>所有同步方法在非 2xx 状态码时抛出 {@link HttpClientException}，
 * 调用方可使用 {@link HttpResult} 包装类获取完整响应信息。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class HttpUtils {

    /** 默认连接超时 */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 默认请求超时 */
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** 重试基础延迟（毫秒） */
    private static final long RETRY_BASE_DELAY_MS = 200;

    /** 全局共享 HttpClient（线程安全，连接池复用） */
    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .build();

    private HttpUtils() {
    }

    // ==================== GET ====================

    /**
     * GET 请求（返回原始字符串）
     *
     * @param url 请求地址
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String get(String url) {
        return get(url, null);
    }

    /**
     * GET 请求（带 Header，返回原始字符串）
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String get(String url, Map<String, String> headers) {
        HttpResult<String> result = getForResult(url, headers);
        if (!result.isSuccess()) {
            throw new HttpClientException(result.getStatusCode(), "GET failed: " + url, result.getBody());
        }
        return result.getBody();
    }

    /**
     * GET 请求（返回指定类型对象，自动 JSON 反序列化）
     *
     * @param url          请求地址
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 反序列化后的对象
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static <T> T getForObject(String url, Class<T> responseType) {
        return getForObject(url, null, responseType);
    }

    /**
     * GET 请求（带 Header，返回指定类型对象）
     *
     * @param url          请求地址
     * @param headers      请求头
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 反序列化后的对象
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static <T> T getForObject(String url, Map<String, String> headers, Class<T> responseType) {
        HttpResult<T> result = getForResult(url, headers, responseType);
        if (!result.isSuccess()) {
            throw new HttpClientException(result.getStatusCode(), "GET failed: " + url, result.getBody());
        }
        return result.getData();
    }

    /**
     * GET 请求（返回完整响应信息）
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return HttpResult 包含状态码、响应体、响应头
     */
    public static HttpResult<String> getForResult(String url, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "GET", null);
        HttpResponse<String> response = execute(request);
        return HttpResult.of(response.statusCode(), response.body(), response.headers());
    }

    /**
     * GET 请求（返回完整响应信息，带类型反序列化）
     *
     * @param url          请求地址
     * @param headers      请求头
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return HttpResult 包含状态码、响应体、响应头、反序列化数据
     */
    public static <T> HttpResult<T> getForResult(String url, Map<String, String> headers, Class<T> responseType) {
        HttpRequest request = buildRequest(url, headers, "GET", null);
        HttpResponse<String> response = execute(request);
        return HttpResult.of(response.statusCode(), response.body(), response.headers(), responseType);
    }

    // ==================== POST ====================

    /**
     * POST 请求（JSON Body，返回原始字符串）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, null);
    }

    /**
     * POST 请求（JSON Body + Headers，返回原始字符串）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @param headers  请求头
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String postJson(String url, String jsonBody, Map<String, String> headers) {
        HttpResult<String> result = postJsonForResult(url, jsonBody, headers);
        if (!result.isSuccess()) {
            throw new HttpClientException(result.getStatusCode(), "POST failed: " + url, result.getBody());
        }
        return result.getBody();
    }

    /**
     * POST 请求（对象自动序列化为 JSON，返回指定类型对象）
     *
     * @param url          请求地址
     * @param body         请求体对象（自动 JSON 序列化）
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 反序列化后的对象
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static <T> T postForObject(String url, Object body, Class<T> responseType) {
        return postForObject(url, body, null, responseType);
    }

    /**
     * POST 请求（对象自动序列化，带 Header，返回指定类型）
     *
     * @param url          请求地址
     * @param body         请求体对象
     * @param headers      请求头
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 反序列化后的对象
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static <T> T postForObject(String url, Object body, Map<String, String> headers, Class<T> responseType) {
        String json = body instanceof String s ? s : JsonUtils.toJson(body);
        HttpResult<T> result = postJsonForResult(url, json, headers, responseType);
        if (!result.isSuccess()) {
            throw new HttpClientException(result.getStatusCode(), "POST failed: " + url, result.getBody());
        }
        return result.getData();
    }

    /**
     * POST 请求（返回完整响应信息）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @param headers  请求头
     * @return HttpResult
     */
    public static HttpResult<String> postJsonForResult(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "POST", jsonBody);
        HttpResponse<String> response = execute(request);
        return HttpResult.of(response.statusCode(), response.body(), response.headers());
    }

    /**
     * POST 请求（返回完整响应信息，带类型反序列化）
     *
     * @param url          请求地址
     * @param jsonBody     JSON 请求体
     * @param headers      请求头
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return HttpResult
     */
    public static <T> HttpResult<T> postJsonForResult(String url, String jsonBody, Map<String, String> headers, Class<T> responseType) {
        HttpRequest request = buildRequest(url, headers, "POST", jsonBody);
        HttpResponse<String> response = execute(request);
        return HttpResult.of(response.statusCode(), response.body(), response.headers(), responseType);
    }

    // ==================== PUT / DELETE / PATCH ====================

    /**
     * PUT 请求（JSON Body）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @param headers  请求头
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String putJson(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "PUT", jsonBody);
        HttpResponse<String> response = execute(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpClientException(response.statusCode(), "PUT failed: " + url, response.body());
        }
        return response.body();
    }

    /**
     * DELETE 请求
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String delete(String url, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "DELETE", null);
        HttpResponse<String> response = execute(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpClientException(response.statusCode(), "DELETE failed: " + url, response.body());
        }
        return response.body();
    }

    /**
     * PATCH 请求（JSON Body）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @param headers  请求头
     * @return 响应体字符串
     * @throws HttpClientException 如果请求失败或状态码非 2xx
     */
    public static String patchJson(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "PATCH", jsonBody);
        HttpResponse<String> response = execute(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpClientException(response.statusCode(), "PATCH failed: " + url, response.body());
        }
        return response.body();
    }

    // ==================== 重试 ====================

    /**
     * 带重试的 GET 请求（指数退避）
     *
     * <p>遇到连接异常或 5xx 状态码时自动重试，
     * 4xx 状态码不重试（客户端错误不可恢复）。
     *
     * @param url        请求地址
     * @param headers    请求头
     * @param maxRetries 最大重试次数
     * @return 响应体字符串
     * @throws HttpClientException 如果重试耗尽后仍失败
     */
    public static String getWithRetry(String url, Map<String, String> headers, int maxRetries) {
        return executeWithRetry(() -> get(url, headers), maxRetries);
    }

    /**
     * 带重试的 POST 请求（指数退避）
     *
     * @param url        请求地址
     * @param jsonBody   JSON 请求体
     * @param headers    请求头
     * @param maxRetries 最大重试次数
     * @return 响应体字符串
     * @throws HttpClientException 如果重试耗尽后仍失败
     */
    public static String postWithRetry(String url, String jsonBody, Map<String, String> headers, int maxRetries) {
        return executeWithRetry(() -> postJson(url, jsonBody, headers), maxRetries);
    }

    // ==================== 文件下载 ====================

    /**
     * 文件下载
     *
     * @param url       下载地址
     * @param filePath  本地保存路径
     * @throws HttpClientException 如果下载失败
     */
    public static void downloadFile(String url, Path filePath) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DEFAULT_REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Path> response = DEFAULT_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofFile(filePath));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpClientException(response.statusCode(), "Download failed: " + url);
            }
        } catch (HttpClientException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpClientException("Download failed: " + url, e);
        }
    }

    // ==================== 异步请求 ====================

    /**
     * 异步 GET 请求
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return CompletableFuture（包含响应体字符串）
     */
    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "GET", null);
        return DEFAULT_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new HttpClientException(response.statusCode(),
                                "GET async failed: " + url, response.body());
                    }
                    return response.body();
                });
    }

    /**
     * 异步 POST 请求（JSON Body）
     *
     * @param url      请求地址
     * @param jsonBody JSON 请求体
     * @param headers  请求头
     * @return CompletableFuture（包含响应体字符串）
     */
    public static CompletableFuture<String> postAsync(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, headers, "POST", jsonBody);
        return DEFAULT_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new HttpClientException(response.statusCode(),
                                "POST async failed: " + url, response.body());
                    }
                    return response.body();
                });
    }

    // ==================== 工具方法 ====================

    /**
     * 构建查询参数字符串
     *
     * @param params 参数 Map
     * @return 查询参数字符串（如 key1=val1&amp;key2=val2）
     */
    public static String buildQueryParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * 构建带查询参数的 URL
     *
     * @param baseUrl 基础 URL
     * @param params  查询参数
     * @return 完整 URL
     */
    public static String buildUrl(String baseUrl, Map<String, String> params) {
        String query = buildQueryParams(params);
        if (query.isEmpty()) return baseUrl;
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 HttpRequest
     */
    private static HttpRequest buildRequest(String url, Map<String, String> headers,
                                             String method, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_REQUEST_TIMEOUT);

        if (headers != null) {
            headers.forEach(builder::header);
        }

        String body = jsonBody != null ? jsonBody : "";
        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> {
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(body));
            }
            case "PUT" -> {
                builder.header("Content-Type", "application/json");
                builder.PUT(HttpRequest.BodyPublishers.ofString(body));
            }
            case "DELETE" -> builder.DELETE();
            case "PATCH" -> {
                builder.header("Content-Type", "application/json");
                builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
            }
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        return builder.build();
    }

    /**
     * 执行 HTTP 请求
     */
    private static HttpResponse<String> execute(HttpRequest request) {
        try {
            return DEFAULT_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpClientException("HTTP request interrupted: " + request.uri(), e);
        } catch (Exception e) {
            throw new HttpClientException("HTTP request failed: " + request.uri(), e);
        }
    }

    /**
     * 带重试执行（指数退避）
     *
     * <p>仅对连接异常和 5xx 状态码重试，4xx 不重试。
     */
    private static String executeWithRetry(java.util.function.Supplier<String> action, int maxRetries) {
        int attempts = 0;
        HttpClientException lastException = null;
        while (attempts <= maxRetries) {
            try {
                return action.get();
            } catch (HttpClientException e) {
                lastException = e;
                // 4xx 客户端错误不重试
                if (e.isClientError()) {
                    throw e;
                }
                attempts++;
                if (attempts <= maxRetries) {
                    try {
                        long delay = RETRY_BASE_DELAY_MS * (1L << (attempts - 1));
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new HttpClientException("Retry interrupted", ie);
                    }
                }
            }
        }
        throw lastException;
    }
}
