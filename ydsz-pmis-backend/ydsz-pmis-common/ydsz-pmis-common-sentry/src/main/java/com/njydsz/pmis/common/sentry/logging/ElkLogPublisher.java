package com.njydsz.pmis.common.sentry.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.njydsz.pmis.common.sentry.domain.LogEvent;
import com.njydsz.pmis.common.sentry.spi.LogPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * ELK + Logstash 日志发布器
 *
 * <p>通过 TCP/UDP 将结构化 JSON 日志推送到 Logstash。
 * Logstash 解析后写入 Elasticsearch，由 Kibana 展示。
 *
 * <p>降级策略：
 * <ul>
 *   <li>连接失败时自动降级到本地文件（由 Logback fallback appender 处理）</li>
 *   <li>连续失败超过阈值时触发熔断，暂停推送</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class ElkLogPublisher implements LogPublisher {

    private final String host;
    private final int port;
    private final String protocol;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxRetryAttempts;

    /** 连续失败次数 */
    private volatile int consecutiveFailures = 0;
    /** 熔断阈值 */
    private final int circuitBreakerThreshold;

    public ElkLogPublisher(String host, int port, String protocol,
                           int connectTimeoutMillis, int readTimeoutMillis,
                           int maxRetryAttempts, int circuitBreakerThreshold) {
        this.host = host;
        this.port = port;
        this.protocol = protocol != null ? protocol.toLowerCase() : "tcp";
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxRetryAttempts = maxRetryAttempts;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        log.info("[Sentry] ElkLogPublisher 初始化: {}://{}:{}", this.protocol, host, port);
    }

    @Override
    public boolean publish(LogEvent event) {
        if (!isAvailable()) {
            return false;
        }
        String json = LogEventSerializer.toJson(event);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                if ("udp".equals(protocol)) {
                    sendUdp(bytes);
                } else {
                    sendTcp(bytes);
                }
                consecutiveFailures = 0;
                return true;
            } catch (Exception e) {
                log.debug("[Sentry] ELK 日志发布失败 (attempt {}/{}): {}", attempt, maxRetryAttempts, e.getMessage());
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        consecutiveFailures++;
        if (consecutiveFailures >= circuitBreakerThreshold) {
            log.warn("[Sentry] ELK 日志发布连续失败 {} 次, 触发熔断", consecutiveFailures);
        }
        return false;
    }

    private void sendTcp(byte[] bytes) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            OutputStream os = socket.getOutputStream();
            os.write(bytes);
            os.write('\n');
            os.flush();
        }
    }

    private void sendUdp(byte[] bytes) throws IOException {
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            java.net.DatagramPacket packet = new java.net.DatagramPacket(
                    bytes, bytes.length, new InetSocketAddress(host, port));
            socket.send(packet);
        }
    }

    @Override
    public boolean isAvailable() {
        return consecutiveFailures < circuitBreakerThreshold;
    }

    @Override
    public String getName() {
        return "elk-logstash";
    }

    @Override
    public String getScheme() {
        return "elk";
    }

    /**
     * 重置熔断状态
     */
    public void resetCircuitBreaker() {
        consecutiveFailures = 0;
        log.info("[Sentry] ELK 日志发布器熔断状态已重置");
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
