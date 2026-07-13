package com.njydsz.pmis.common.sentry.logging;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.njydsz.pmis.common.sentry.domain.LogEvent;
import com.njydsz.pmis.common.sentry.spi.LogPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * Loki 日志发布器
 *
 * <p>通过 HTTP 将结构化 JSON 日志推送到 Loki。
 * 作为 ELK 方案的备选/降级方案。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class LokiLogPublisher implements LogPublisher {

    private final String pushUrl;
    private final HttpClient httpClient;
    private final int maxRetryAttempts;
    private final int circuitBreakerThreshold;

    private volatile int consecutiveFailures = 0;

    public LokiLogPublisher(String lokiUrl, int connectTimeoutSeconds,
                            int maxRetryAttempts, int circuitBreakerThreshold) {
        this.pushUrl = lokiUrl.endsWith("/")
                ? lokiUrl + "loki/api/v1/push"
                : lokiUrl + "/loki/api/v1/push";
        this.maxRetryAttempts = maxRetryAttempts;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        log.info("[Sentry] LokiLogPublisher 初始化: url={}", pushUrl);
    }

    @Override
    public boolean publish(LogEvent event) {
        if (!isAvailable()) {
            return false;
        }
        String payload = buildLokiPayload(event);
        byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(pushUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<Void> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    consecutiveFailures = 0;
                    return true;
                }
                log.debug("[Sentry] Loki 推送返回 {} (attempt {}/{})",
                        response.statusCode(), attempt, maxRetryAttempts);
            } catch (Exception e) {
                log.debug("[Sentry] Loki 日志发布失败 (attempt {}/{}): {}",
                        attempt, maxRetryAttempts, e.getMessage());
            }
            if (attempt < maxRetryAttempts) {
                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        consecutiveFailures++;
        if (consecutiveFailures >= circuitBreakerThreshold) {
            log.warn("[Sentry] Loki 日志发布连续失败 {} 次, 触发熔断", consecutiveFailures);
        }
        return false;
    }

    /**
     * 构建 Loki 推送 payload
     *
     * <p>Loki push API 格式：
     * <pre>
     * {
     *   "streams": [{
     *     "stream": { "app": "pmis", "level": "INFO" },
     *     "values": [[ "1620000000000000000", "{ \"message\": \"...\" }" ]]
     *   }]
     * }
     * </pre>
     */
    private String buildLokiPayload(LogEvent event) {
        String timestampNs = String.valueOf(event.getTimestamp().toEpochMilli() * 1_000_000);
        String logLine = LogEventSerializer.toJson(event);

        StringBuilder sb = new StringBuilder(256 + logLine.length());
        sb.append("{\"streams\":[{\"stream\":{");
        sb.append("\"app\":\"").append(escapeJson(event.getAppName())).append("\"");
        sb.append(",\"level\":\"").append(event.getLevel() != null ? event.getLevel().name() : "INFO").append("\"");
        if (event.getProfile() != null) {
            sb.append(",\"env\":\"").append(escapeJson(event.getProfile())).append("\"");
        }
        if (event.getTraceId() != null) {
            sb.append(",\"traceId\":\"").append(escapeJson(event.getTraceId())).append("\"");
        }
        sb.append("},\"values\":[[\"").append(timestampNs).append("\",")
                .append("\"").append(escapeJson(logLine)).append("\"]]}]}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isAvailable() {
        return consecutiveFailures < circuitBreakerThreshold;
    }

    @Override
    public String getName() {
        return "loki";
    }

    @Override
    public String getScheme() {
        return "loki";
    }

    /**
     * 重置熔断状态
     */
    public void resetCircuitBreaker() {
        consecutiveFailures = 0;
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
