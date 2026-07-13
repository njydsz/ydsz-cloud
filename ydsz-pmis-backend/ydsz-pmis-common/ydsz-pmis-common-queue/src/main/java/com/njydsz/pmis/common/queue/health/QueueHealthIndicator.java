package com.njydsz.pmis.common.queue.health;

import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.enums.QueueType;
import com.njydsz.pmis.common.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 消息队列健康检查
 *
 * <p>根据实际队列类型（Kafka/RabbitMQ/RocketMQ/Redis）检查对应中间件连通性。
 * Redis 类型复用 ydsz-pmis-common-redis 连接进行检查，非 Redis 类型通过 TCP 端口连通性检查。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnProperty(prefix = "ydsz.queue", name = "enabled", havingValue = "true", matchIfMissing = false)
public class QueueHealthIndicator implements HealthIndicator {

    private static final int HEALTH_CHECK_TIMEOUT_MS = 2000;

    private final RedisService redisService;
    private final QueueProperties queueProperties;

    public QueueHealthIndicator(QueueProperties queueProperties,
                                ObjectProvider<RedisService> redisServiceProvider) {
        this.queueProperties = queueProperties;
        this.redisService = redisServiceProvider.getIfAvailable();
    }

    @Override
    public Health health() {
        QueueType type = resolveQueueType();
        if (type == null) {
            return Health.unknown()
                    .withDetail("error", "队列类型未配置")
                    .build();
        }

        try {
            Health.Builder builder;
            if (isRedisType(type)) {
                builder = checkRedisHealth();
            } else {
                builder = checkMqConnectivity(type);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("消息队列健康检查失败, type={}", type, e);
            return Health.down()
                    .withDetail("mqType", type.getValue())
                    .withDetail("host", queueProperties.resolvedHost())
                    .withDetail("port", resolvePort(type))
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * 解析队列类型
     */
    private QueueType resolveQueueType() {
        try {
            return queueProperties.resolvedType();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查 Redis 队列健康状态
     */
    private Health.Builder checkRedisHealth() {
        if (redisService == null) {
            return Health.unknown()
                    .withDetail("mqType", "redis")
                    .withDetail("detail", "RedisService 未提供，无法执行健康检查");
        }

        long startTime = System.currentTimeMillis();
        redisService.hasKey("__health_check__");
        long responseTime = System.currentTimeMillis() - startTime;

        return Health.up()
                .withDetail("mqType", "redis")
                .withDetail("host", queueProperties.resolvedHost())
                .withDetail("port", queueProperties.resolvedPort())
                .withDetail("responseTimeMs", responseTime);
    }

    /**
     * 检查非 Redis 中间件的 TCP 连通性
     */
    private Health.Builder checkMqConnectivity(QueueType type) {
        String host = queueProperties.resolvedHost();
        int port = resolvePort(type);

        long startTime = System.currentTimeMillis();
        boolean connected = checkTcpConnection(host, port);
        long responseTime = System.currentTimeMillis() - startTime;

        Map<String, Object> details = new HashMap<>();
        details.put("mqType", type.getValue());
        details.put("host", host);
        details.put("port", port);
        details.put("responseTimeMs", responseTime);
        details.put("connected", connected);

        if (connected) {
            return Health.up().withDetails(details);
        } else {
            return Health.down()
                    .withDetails(details)
                    .withDetail("error", "无法连接到 " + type.getValue() + " 服务 " + host + ":" + port);
        }
    }

    /**
     * 根据队列类型解析对应的端口
     */
    private int resolvePort(QueueType type) {
        if (type == QueueType.KAFKA) {
            return queueProperties.resolvedPort() != 6379 ? queueProperties.resolvedPort() : 9092;
        } else if (type == QueueType.RABBIT) {
            return queueProperties.resolvedPort() != 6379 ? queueProperties.resolvedPort() : 5672;
        } else if (type == QueueType.ROCKET) {
            return queueProperties.resolvedPort() != 6379 ? queueProperties.resolvedPort() : 9876;
        } else if (type == QueueType.ACTIVE) {
            return queueProperties.resolvedPort() != 6379 ? queueProperties.resolvedPort() : 61616;
        }
        return queueProperties.resolvedPort();
    }

    /**
     * 检查 TCP 端口连通性
     */
    private boolean checkTcpConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), HEALTH_CHECK_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            log.debug("TCP 连通性检查失败, host={}, port={}", host, port, e);
            return false;
        }
    }

    private boolean isRedisType(QueueType type) {
        return type == QueueType.LIST || type == QueueType.PUBSUB || type == QueueType.STREAM;
    }
}
