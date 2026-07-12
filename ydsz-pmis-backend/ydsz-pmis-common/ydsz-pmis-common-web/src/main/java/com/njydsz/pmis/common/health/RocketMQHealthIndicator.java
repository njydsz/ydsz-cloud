package com.njydsz.pmis.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * RocketMQ 健康检查指示器（P1-8）
 *
 * <p>通过 TCP 端口连通性检测 RocketMQ NameServer 可用性。
 * 检查结果暴露在 {@code /actuator/health/rocketmq} 端点。
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@code org.apache.rocketmq.client.producer.DefaultMQProducer}</li>
 *   <li>{@code pmis.health.rocketmq.enabled=true}（默认 true）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.rocketmq.client.producer.DefaultMQProducer")
@ConditionalOnProperty(prefix = "pmis.health.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMQHealthIndicator implements HealthIndicator {

    /** 健康检查 TCP 超时时间（ms） */
    private static final int TCP_TIMEOUT_MS = 2000;

    /** RocketMQ NameServer 地址，格式 host:port */
    @Value("${rocketmq.name-server:}")
    private String nameServer;

    @Override
    public Health health() {
        if (nameServer == null || nameServer.isBlank()) {
            return Health.unknown()
                    .withDetail("error", "rocketmq.name-server not configured")
                    .build();
        }

        String[] parts = nameServer.split(":");
        if (parts.length < 2) {
            return Health.down()
                    .withDetail("error", "invalid name-server format: " + nameServer)
                    .build();
        }

        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return Health.down()
                    .withDetail("error", "invalid port in name-server: " + nameServer)
                    .build();
        }

        long startTime = System.currentTimeMillis();
        boolean connected = checkTcpConnection(host, port);
        long latency = System.currentTimeMillis() - startTime;

        if (connected) {
            return Health.up()
                    .withDetail("nameServer", nameServer)
                    .withDetail("latency_ms", latency)
                    .build();
        } else {
            log.warn("[HealthCheck] RocketMQ NameServer 连接失败: {}", nameServer);
            return Health.down()
                    .withDetail("nameServer", nameServer)
                    .withDetail("error", "cannot connect to RocketMQ NameServer")
                    .build();
        }
    }

    /**
     * TCP 端口连通性检查
     *
     * @param host 主机地址
     * @param port 端口
     * @return true 表示连接成功
     */
    private boolean checkTcpConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TCP_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            log.debug("[HealthCheck] RocketMQ TCP 连通性检查失败: host={}, port={}", host, port, e);
            return false;
        }
    }
}
