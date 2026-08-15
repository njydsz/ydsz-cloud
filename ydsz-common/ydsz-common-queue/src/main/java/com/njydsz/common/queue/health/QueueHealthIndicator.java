package com.njydsz.common.queue.health;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列健康检查
 *
 * <p>根据实际队列类型（Kafka/RabbitMQ/RocketMQ/Redis）检查对应中间件连通性。
 * <ul>
 *   <li>Redis 类型：复用 ydsz-common-redis 连接执行 PING 命令</li>
 *   <li>非 Redis 类型：通过 TCP 端口连通性检查（使用各 MQ 默认端口）</li>
 * </ul>
 *
 * <p><b>端口解析逻辑：</b>
 * <ul>
 *   <li>如果用户显式配置了端口（{@code ydsz.queue.port != 0}），则始终使用用户配置</li>
 *   <li>如果端口为 0（未配置），则回退到各 MQ 类型的默认端口</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class QueueHealthIndicator implements HealthIndicator {

    private static final int HEALTH_CHECK_TIMEOUT_MS = 2000;

    /** 各 MQ 类型的默认端口（仅当用户未配置端口时使用） */
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int DEFAULT_KAFKA_PORT = 9092;
    private static final int DEFAULT_RABBIT_PORT = 5672;
    private static final int DEFAULT_ROCKET_PORT = 9876;

    private final RedisStringOps redisStringOps;
    private final QueueProperties queueProperties;

    public QueueHealthIndicator(QueueProperties queueProperties,
                                ObjectProvider<RedisStringOps> redisStringOpsProvider) {
        this.queueProperties = queueProperties;
        this.redisStringOps = redisStringOpsProvider.getIfAvailable();
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
     * 检查 Redis 队列健康状态（协议级 PING）
     */
    private Health.Builder checkRedisHealth() {
        if (redisStringOps == null) {
            return Health.unknown()
                    .withDetail("mqType", "redis")
                    .withDetail("detail", "RedisStringOps 未提供，无法执行健康检查");
        }

        long startTime = System.currentTimeMillis();
        redisStringOps.hasKey("__health_check__");
        long responseTime = System.currentTimeMillis() - startTime;

        return Health.up()
                .withDetail("mqType", "redis")
                .withDetail("host", queueProperties.resolvedHost())
                .withDetail("port", queueProperties.resolvedPort())
                .withDetail("responseTimeMs", responseTime)
                .withDetail("checkMethod", "redis-ping");
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

        if (connected) {
            return Health.up()
                    .withDetail("mqType", type.getValue())
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .withDetail("responseTimeMs", responseTime)
                    .withDetail("checkMethod", "tcp-port");
        } else {
            return Health.down()
                    .withDetail("mqType", type.getValue())
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .withDetail("responseTimeMs", responseTime)
                    .withDetail("checkMethod", "tcp-port")
                    .withDetail("error", "无法连接到 " + type.getValue() + " 服务 " + host + ":" + port);
        }
    }

    /**
     * 根据队列类型解析对应的端口
     *
     * <p>逻辑：
     * <ul>
     *   <li>如果用户显式配置了端口（{@code port != 0}），则使用用户配置</li>
     *   <li>如果端口为 0（未配置），则使用各 MQ 类型的默认端口</li>
     * </ul>
     */
    private int resolvePort(QueueType type) {
        int configuredPort = queueProperties.getPort();
        if (configuredPort > 0) {
            return configuredPort;
        }
        // 用户未配置端口，使用各 MQ 类型的默认端口
        if (type == QueueType.KAFKA) {
            return DEFAULT_KAFKA_PORT;
        }
        if (type == QueueType.RABBIT) {
            return DEFAULT_RABBIT_PORT;
        }
        if (type == QueueType.ROCKET) {
            return DEFAULT_ROCKET_PORT;
        }
        return DEFAULT_REDIS_PORT;
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
