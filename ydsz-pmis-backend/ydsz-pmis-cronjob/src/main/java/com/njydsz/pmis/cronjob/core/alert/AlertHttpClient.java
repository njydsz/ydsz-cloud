package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.config.AlertProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 告警 HTTP 客户端工具（P5 告警 + 监控）。
 *
 * <p>封装 {@link java.net.http.HttpClient}，为各通道通知器提供统一的 HTTP POST 能力。
 * 使用 JDK 内置 HttpClient，避免引入 OkHttp/Apache HttpClient 等第三方依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class AlertHttpClient {

    private final AlertProperties properties;
    private final HttpClient httpClient;

    public AlertHttpClient(AlertProperties properties) {
        this.properties = properties;
        Duration timeout = properties.getHttpTimeout() != null
                ? properties.getHttpTimeout()
                : Duration.ofSeconds(5);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * 发送 JSON POST 请求。
     *
     * @param url     目标 URL
     * @param jsonBody JSON 请求体
     * @param headers 额外请求头（可为 null）
     * @return HTTP 响应体字符串
     * @throws AlertSendException 请求失败、超时或非 2xx 响应时抛出
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers) throws AlertSendException {
        if (url == null || url.isBlank()) {
            throw new AlertSendException("目标 URL 为空");
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(properties.getHttpTimeout() != null
                            ? properties.getHttpTimeout()
                            : Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new AlertSendException("HTTP " + status + ": " + truncate(response.body()));
            }
            return response.body();
        } catch (AlertSendException e) {
            throw e;
        } catch (Exception e) {
            throw new AlertSendException("HTTP 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 截断字符串，避免日志过长。
     */
    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
