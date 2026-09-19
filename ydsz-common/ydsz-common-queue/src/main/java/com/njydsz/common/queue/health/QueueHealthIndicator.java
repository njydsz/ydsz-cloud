package com.njydsz.common.queue.health;

import java.net.InetSocketAddress;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 消息队列健康检查
 *
 * <p>根据实际队列类型（Kafka/RabbitMQ/RocketMQ/Redis）检查对应中间件连通性。
 *
 * <ul>
 *   <li>Redis 类型：复用 ydsz-common-redis 连接执行 PING 命令
 *   <li>非 Redis 类型：通过 TCP 端口连通性检查（使用各 MQ 默认端口）
 * </ul>
 *
 * <p><b>端口解析逻辑：</b>
 *
 * <ul>
 *   <li>如果用户显式配置了端口（{@code ydsz.queue.port != 0}），则始终使用用户配置
 *   <li>如果端口为 0（未配置），则回退到各 MQ 类型的默认端口
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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

  public QueueHealthIndicator(
      QueueProperties queueProperties, ObjectProvider<RedisStringOps> redisStringOpsProvider) {
    this.queueProperties = queueProperties;
    this.redisStringOps = redisStringOpsProvider.getIfAvailable();
  }

  @Override
  public Health health() {
    QueueType type = resolveQueueType();
    if (type == null) {
      return Health.unknown().withDetail("error", "队列类型未配置").build();
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
          .withDetail("host", queueProperties.getHost())
          .withDetail("port", resolvePort(type))
          .withDetail("error", e.getMessage())
          .build();
    }
  }

  /** 解析队列类型 */
  private QueueType resolveQueueType() {
    try {
      return queueProperties.getResolvedType();
    } catch (Exception e) {
      return null;
    }
  }

  /** 检查 Redis 队列健康状态（协议级 PING） */
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
        .withDetail("host", queueProperties.getHost())
        .withDetail("port", queueProperties.getPort())
        .withDetail("responseTimeMs", responseTime)
        .withDetail("checkMethod", "redis-ping");
  }

  /**
   * 检查非 Redis 中间件的连通性
   *
   * <p>按以下顺序降级：
   *
   * <ol>
   *   <li>Kafka：{@code AdminClient.describeCluster()} —— 协议级 Readiness（需要 kafka-clients）
   *   <li>RabbitMQ：{@code Channel.queueDeclarePassive(...)} —— 协议级 Readiness（需要 spring-rabbit）
   *   <li>兜底：TCP 端口连通性 —— Liveness 级别，仅表明进程存活
   * </ol>
   *
   * <p>协议级探测失败但 TCP 连通时上报 DEGRADED（broker 存活但 topic/认证异常）。
   */
  private Health.Builder checkMqConnectivity(QueueType type) {
    String host = queueProperties.getHost();
    int port = resolvePort(type);
    long startTime = System.currentTimeMillis();

    // 尝试协议级探测
    ProtocolCheckResult protocolResult = tryProtocolCheck(type);
    long responseTime = System.currentTimeMillis() - startTime;

    if (protocolResult != null && protocolResult.success) {
      return Health.up()
          .withDetail("mqType", type.getValue())
          .withDetail("host", host)
          .withDetail("port", port)
          .withDetail("responseTimeMs", responseTime)
          .withDetail("checkMethod", protocolResult.checkMethod);
    }

    // 兜底：TCP
    boolean tcpOk = checkTcpConnection(host, port);
    if (tcpOk) {
      Health.Builder degraded = Health.status("DEGRADED")
          .withDetail("mqType", type.getValue())
          .withDetail("host", host)
          .withDetail("port", port)
          .withDetail("responseTimeMs", responseTime)
          .withDetail("checkMethod", "tcp-port");
      if (protocolResult != null) {
        degraded.withDetail("protocolCheckError", protocolResult.errorMessage);
      }
      return degraded;
    }

    return Health.down()
        .withDetail("mqType", type.getValue())
        .withDetail("host", host)
        .withDetail("port", port)
        .withDetail("responseTimeMs", responseTime)
        .withDetail("checkMethod", "tcp-port")
        .withDetail("error", protocolResult != null
            ? "TCP 连接失败（协议级错误：" + protocolResult.errorMessage + "）"
            : "无法连接到 " + type.getValue() + " 服务 " + host + ":" + port);
  }

  /**
   * 尝试协议级 Readiness 探测。非堵塞，任一异常返回 failed 结果。
   *
   * @return null 表示当前 classpath 不支持协议探测
   */
  private ProtocolCheckResult tryProtocolCheck(QueueType type) {
    if (type == QueueType.KAFKA) {
      return tryKafkaProtocolCheck();
    }
    if (type == QueueType.RABBIT) {
      return tryRabbitProtocolCheck();
    }
    return null;
  }

  /** Kafka 协议级探测：AdminClient.describeCluster（验证连通 + 非空 controller）。 */
  private ProtocolCheckResult tryKafkaProtocolCheck() {
    try {
      // 通过反射加载，避免在 classpath 没有 kafka-clients 时触发 NoClassDefFoundError
      Class<?> adminClazz = Class.forName("org.apache.kafka.clients.admin.AdminClient");
      Class<?> configClazz = Class.forName("org.apache.kafka.clients.admin.AdminClientConfig");
      Class<?> producerConfigClazz = Class.forName("org.apache.kafka.clients.producer.ProducerConfig");
      Class<?> stringSerializerClazz =
          Class.forName("org.apache.kafka.common.serialization.StringSerializer");
      Object props = Class.forName("java.util.Properties").getDeclaredConstructor().newInstance();
      java.util.Properties p = (java.util.Properties) props;
      p.put(
          configClazz.getField("BOOTSTRAP_SERVERS_CONFIG").get(null),
          queueProperties.getKafkaBootstrapServers());
      p.put(
          producerConfigClazz.getField("KEY_SERIALIZER_CLASS_CONFIG").get(null),
          stringSerializerClazz.getName());
      p.put(
          producerConfigClazz.getField("VALUE_SERIALIZER_CLASS_CONFIG").get(null),
          stringSerializerClazz.getName());
      p.put(configClazz.getField("REQUEST_TIMEOUT_MS_CONFIG").get(null), 3000);
      p.put(configClazz.getField("DEFAULT_API_TIMEOUT_MS_CONFIG").get(null), 3000);

      Object admin = adminClazz.getMethod("create", java.util.Properties.class).invoke(null, p);
      try {
        Object cluster =
            adminClazz.getMethod("describeCluster").invoke(admin);
        Object controller =
            cluster.getClass().getMethod("controller").invoke(cluster).get()
                .orElse(null);
        boolean ok = controller != null;
        return new ProtocolCheckResult(ok, ok ? null : "broker controller 未就绪",
            "kafka-admin-describe-cluster");
      } finally {
        adminClazz.getMethod("close", long.class, java.util.concurrent.TimeUnit.class)
            .invoke(admin, 1L, java.util.concurrent.TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      return new ProtocolCheckResult(false, e.getMessage(), "kafka-admin-describe-cluster");
    }
  }

  /**
   * RabbitMQ 协议级探测：建立连接后对队列执行 passive declare（队列不存在时不创建，只校验连通）。
   */
  private ProtocolCheckResult tryRabbitProtocolCheck() {
    try {
      Class<?> factoryClazz = Class.forName("com.rabbitmq.client.ConnectionFactory");
      Object factory = factoryClazz.getDeclaredConstructor().newInstance();
      factoryClazz.getMethod("setHost", String.class).invoke(factory, queueProperties.getHost());
      factoryClazz.getMethod("setPort", int.class).invoke(factory, queueProperties.getPort());
      factoryClazz.getMethod("setUsername", String.class)
          .invoke(factory, queueProperties.getUsername() != null ? queueProperties.getUsername() : "guest");
      factoryClazz.getMethod("setPassword", String.class)
          .invoke(factory, queueProperties.getPassword() != null ? queueProperties.getPassword() : "");
      factoryClazz.getMethod("setVirtualHost", String.class)
          .invoke(factory, queueProperties.getVirtualHost() != null ? queueProperties.getVirtualHost() : "/");
      factoryClazz.getMethod("setConnectionTimeout", int.class).invoke(factory, 3000);

      Object connection =
          factoryClazz.getMethod("newConnection").invoke(factory);
      try {
        Object channel = connection.getClass().getMethod("createChannel").invoke(connection);
        try {
          channel.getClass().getMethod("queueDeclarePassive", String.class).invoke(channel, "");
          return new ProtocolCheckResult(true, null, "rabbitmq-queueDeclarePassive");
        } finally {
          channel.getClass().getMethod("close").invoke(channel);
        }
      } finally {
        connection.getClass().getMethod("close").invoke(connection);
      }
    } catch (Exception e) {
      return new ProtocolCheckResult(false, e.getMessage(), "rabbitmq-queueDeclarePassive");
    }
  }

  /** 协议级探测结果 */
  private static final class ProtocolCheckResult {
    final boolean success;
    final String errorMessage;
    final String checkMethod;

    ProtocolCheckResult(boolean success, String errorMessage, String checkMethod) {
      this.success = success;
      this.errorMessage = errorMessage;
      this.checkMethod = checkMethod;
    }
  }

  /**
   * 根据队列类型解析对应的端口
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>如果用户显式配置了端口（{@code port != 0}），则使用用户配置
   *   <li>如果端口为 0（未配置），则使用各 MQ 类型的默认端口
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

  /** 检查 TCP 端口连通性 */
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
