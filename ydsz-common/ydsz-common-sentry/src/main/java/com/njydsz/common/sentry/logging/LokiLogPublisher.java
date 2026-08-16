package com.njydsz.common.sentry.logging;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.sentry.domain.LogEvent;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.common.sentry.spi.LogPublisher;

/**
 * Loki 日志发布器
 *
 * <p>通过 HTTP 将结构化 JSON 日志推送到 Loki。
 * 作为 ELK 方案的备选/降级方案。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class LokiLogPublisher implements LogPublisher, AutoCloseable {

    private final String pushUrl;
    private final HttpClient httpClient;
    private final int maxRetryAttempts;
    private final CircuitBreaker circuitBreaker;
    private final int requestTimeoutSeconds;

    public LokiLogPublisher(String lokiUrl, int connectTimeoutSeconds,
                            int maxRetryAttempts, CircuitBreaker circuitBreaker) {
        this.pushUrl = lokiUrl.endsWith("/")
                ? lokiUrl + "loki/api/v1/push"
                : lokiUrl + "/loki/api/v1/push";
        this.maxRetryAttempts = maxRetryAttempts;
        this.circuitBreaker = circuitBreaker;
        this.requestTimeoutSeconds = connectTimeoutSeconds;
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
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        return circuitBreaker.execute(() -> {
            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(pushUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                            .build();

                    HttpResponse<Void> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.discarding());

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
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
            return false;
        }, () -> false);
    }

    /**
     * 构建 Loki 推送 payload
     *
     * <p>Loki push API 格式：
     * <pre>
     * {
     *   "streams": [{
     *     "stream": { "app": "ydsz", "level": "INFO" },
     *     "values": [[ "1620000000000000000", "{ \"message\": \"...\" }" ]]
     *   }]
     * }
     * </pre>
     */
    private String buildLokiPayload(LogEvent event) {
        String timestampNs = String.valueOf(event.getTimestamp().toEpochMilli() * 1_000_000);
        String logLine = LogEventSerializer.toJson(event);

        Map<String, String> streamLabels = new LinkedHashMap<>();
        streamLabels.put("app", event.getAppName() != null ? event.getAppName() : "ydsz");
        streamLabels.put("level", event.getLevel() != null ? event.getLevel().name() : "INFO");
        if (event.getProfile() != null) {
            streamLabels.put("env", event.getProfile());
        }
        if (event.getTraceId() != null) {
            streamLabels.put("traceId", event.getTraceId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("streams", new Object[]{
                Map.of(
                        "stream", streamLabels,
                        "values", new Object[][]{{timestampNs, logLine}}
                )
        });
        return YdszJson.toJson(payload);
    }

    @Override
    public boolean publishBatch(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        if (!isAvailable()) {
            return false;
        }
        String payload = buildLokiBatchPayload(events);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return circuitBreaker.execute(() -> {
            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(pushUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                            .build();
                    HttpResponse<Void> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return true;
                    }
                } catch (Exception e) {
                    // retry
                }
                if (attempt < maxRetryAttempts) {
                    try { Thread.sleep(200L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
            return false;
        }, () -> false);
    }

    private String buildLokiBatchPayload(List<LogEvent> events) {
        String appName = events.get(0).getAppName() != null ? events.get(0).getAppName() : "ydsz";
        List<Object[]> values = new ArrayList<>(events.size());
        for (LogEvent event : events) {
            String ts = String.valueOf(event.getTimestamp().toEpochMilli() * 1_000_000);
            String line = LogEventSerializer.toJson(event);
            values.add(new Object[]{ts, line});
        }
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", appName);
        labels.put("level", events.get(0).getLevel() != null ? events.get(0).getLevel().name() : "INFO");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("streams", new Object[]{ Map.of("stream", labels, "values", values) });
        return YdszJson.toJson(payload);
    }

        @Override
    public boolean isAvailable() {
        return circuitBreaker == null || circuitBreaker.getState() != CircuitBreaker.State.OPEN;
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
     * 获取熔断器状态
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreaker.State.CLOSED;
    }

    @Override
    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
                log.info("[Sentry] LokiLogPublisher HttpClient 已关闭");
            } catch (Exception e) {
                log.debug("[Sentry] HttpClient 关闭异常: {}", e.getMessage());
            }
        }
    }
}
