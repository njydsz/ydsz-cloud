package com.njydsz.common.queue.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.queue.enums.QueueType;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.queue.MessageQueueFactory;
import com.njydsz.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.common.queue.topology.TopologyType;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列配置类
 *
 * <p>支持通过 Nacos 进行动态配置，配置前缀为 {@code ydsz.queue}。
 * 支持通过 {@link MessageQueueFactory} 创建消息队列实例，复用 ydsz-common-redis 连接。
 *
 * <p><b>连接复用：</b>
 * <p>当提供 RedisTemplate 时，Redis 队列
 * 优先复用 ydsz-common-redis 的连接，避免重复创建 JedisPool。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "ydsz.queue")
public class QueueProperties {

    /**
     * 队列是否启用
     */
    private boolean enabled = true;

    /**
     * 队列类型（支持通过枚举名称或 value 自动识别）
     * <p>支持的值：LIST, PUBSUB, STREAM, KAFKA, ROCKET, RABBIT, ACTIVE
     */
    private QueueType type;

    /**
     * 队列类型字符串（用于兼容旧配置）
     * <p>该字段仅用于配置解析，实际使用应通过 getType() 获取枚举值。
     */
    private String typeStr;

    /**
     * Redis 服务器地址（也用于非 Redis 队列的通用主机配置）
     */
    private String host = "127.0.0.1";

    /**
     * Redis 服务器端口（也用于非 Redis 队列的通用端口配置）
     */
    private int port = 6379;

    /**
     * Redis 密码（也用于非 Redis 队列的通用密码配置）
     */
    private String password;

    /**
     * Redis 用户名（用于非 Redis 队列的通用用户名配置）
     */
    private String username;

    /**
     * 超时时间（毫秒）
     */
    @Min(1)
    private int timeout = 3000;

    /**
     * List 队列阻塞超时时间（秒）
     */
    private long listBlockTimeoutSeconds = 5;

    /**
     * Stream 队列消费者组
     */
    private String streamGroup = "group-1";

    /**
     * Stream 队列消费者名称
     */
    private String streamConsumer = "consumer-1";

    /**
     * Stream 队列重试最大次数
     */
    private int streamRetryMax = 3;

    /**
     * Stream 队列阻塞时间（毫秒）
     */
    private long streamBlockMillis = 2000;

    /**
     * Stream 队列批量拉取大小
     */
    private int streamBatchSize = 10;

    /**
     * Stream 队列死信队列后缀
     */
    private String streamDeadLetterSuffix = ":dlq";

    /**
     * 消费者限流速率（每秒允许处理的消息数，默认 0 表示不限流）
     */
    private int consumerRateLimitPerSecond = 0;

    /**
     * 是否启用死信队列自动重试（默认true）
     */
    private boolean deadLetterRetryEnabled = true;

    /**
     * 死信队列最大重试次数（默认3）
     */
    @Min(1)
    private int deadLetterMaxRetries = 3;

    /**
     * 死信队列重试间隔毫秒（默认60000）
     */
    private long deadLetterRetryInterval = 60000;

    /**
     * 死信队列重试抖动百分比（默认30，范围 0-100，0=无抖动）
     *
     * <p>多实例部署时，各实例在基础延迟上附加 [0, interval * jitterPercent / 100] 的随机抖动，
     * 避免所有实例同时扫描死信队列造成惊群。
     */
    private int deadLetterRetryJitterPercent = 30;

    /**
     * 是否启用消息去重（默认 false，分布式场景推荐使用 RedisMessageDeduplicator）
     */
    private boolean dedupEnabled = false;

    /**
     * 消息去重窗口（毫秒，默认 300000 = 5 分钟）
     */
    private long dedupWindowMillis = 300_000L;

    /**
     * 熔断器配置
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * 消息去重解析后的窗口
     */
    public long resolvedDedupWindowMillis() {
        return dedupWindowMillis > 0 ? dedupWindowMillis : 300_000L;
    }

    /**
     * 解析后的队列类型
     *
     * <p>优先返回 {@link #type} 枚举值；若为 null 则惰性解析 {@link #typeStr}。
     * 本方法不修改 {@link #type} 字段，保证幂等性。
     */
    public QueueType resolvedType() {
        if (type != null) {
            return type;
        }
        if (typeStr != null && !typeStr.trim().isEmpty()) {
            return QueueType.fromValue(typeStr);
        }
        throw new IllegalStateException("队列类型不能为空");
    }

    /**
     * 解析后的主机地址
     */
    public String resolvedHost() {
        return host != null ? host : "127.0.0.1";
    }

    /**
     * 解析后的端口
     */
    public int resolvedPort() {
        return port > 0 ? port : 6379;
    }

    /**
     * 解析后的超时时间
     */
    public int resolvedTimeout() {
        return timeout > 0 ? timeout : 3000;
    }

    /**
     * 解析后的用户名
     */
    public String resolvedUsername() {
        return username;
    }

    /**
     * 解析后的密码
     */
    public String resolvedPassword() {
        return password;
    }

    /**
     * 解析后的 List 阻塞超时时间
     */
    public long resolvedListBlockTimeoutSeconds() {
        return listBlockTimeoutSeconds > 0 ? listBlockTimeoutSeconds : 5;
    }

    /**
     * 解析后的 Stream 消费者组
     */
    public String resolvedStreamGroup() {
        return streamGroup != null && !streamGroup.isEmpty() ? streamGroup : "group-1";
    }

    /**
     * 解析后的 Stream 消费者名称
     */
    public String resolvedStreamConsumer() {
        return streamConsumer != null && !streamConsumer.isEmpty() ? streamConsumer : "consumer-1";
    }

    /**
     * 解析后的 Stream 重试最大次数
     */
    public int resolvedStreamRetryMax() {
        return streamRetryMax >= 0 ? streamRetryMax : 3;
    }

    /**
     * 解析后的 Stream 阻塞时间
     */
    public long resolvedStreamBlockMillis() {
        return streamBlockMillis > 0 ? streamBlockMillis : 2000;
    }

    /**
     * 解析后的 Stream 批量拉取大小
     */
    public int resolvedStreamBatchSize() {
        return streamBatchSize > 0 ? streamBatchSize : 10;
    }

    /**
     * 解析后的 Stream 死信队列后缀
     */
    public String resolvedStreamDeadLetterSuffix() {
        return streamDeadLetterSuffix != null && !streamDeadLetterSuffix.isEmpty()
                ? streamDeadLetterSuffix : ":dlq";
    }

    /**
     * 解析后的死信队列自动重试开关
     */
    public boolean resolvedDeadLetterRetryEnabled() {
        return deadLetterRetryEnabled;
    }

    /**
     * 解析后的死信队列最大重试次数
     */
    public int resolvedDeadLetterMaxRetries() {
        return deadLetterMaxRetries > 0 ? deadLetterMaxRetries : 3;
    }

    /**
     * 解析后的死信队列重试间隔
     */
    public long resolvedDeadLetterRetryInterval() {
        return deadLetterRetryInterval > 0 ? deadLetterRetryInterval : 60000;
    }

    /**
     * 解析后的死信队列重试抖动百分比
     */
    public int getDeadLetterRetryJitterPercent() {
        return deadLetterRetryJitterPercent;
    }

    /**
     * 解析后的消费者限流速率
     */
    public int resolvedConsumerRateLimitPerSecond() {
        return consumerRateLimitPerSecond > 0 ? consumerRateLimitPerSecond : 0;
    }

    /**
     * 创建消费者限流器实例
     *
     * @return 限流器实例，当 consumerRateLimitPerSecond <= 0 时返回空操作限流器
     */
    public ConsumerRateLimiter createRateLimiter() {
        return new ConsumerRateLimiter(resolvedConsumerRateLimitPerSecond());
    }

    /**
     * 异步消费者线程池配置
     */
    private ExecutorConfig consumerExecutor = new ExecutorConfig();

    /**
     * 熔断器配置项
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * 是否启用熔断器（默认 true）
         */
        private boolean enabled = true;

        /**
         * 连续失败阈值（默认 10 次）
         */
        private int failureThreshold = 10;

        /**
         * 熔断开启后的恢复等待时间（毫秒，默认 60000 = 1 分钟）
         */
        private long openStateTimeoutMillis = 60_000L;
    }

    /**
     * 异步消费者线程池配置项
     */
    @Data
    public static class ExecutorConfig {
        /**
         * 核心线程数（默认 2）
         */
        private int coreSize = 2;

        /**
         * 最大线程数（默认 16）
         */
        private int maxSize = 16;

        /**
         * 任务队列容量（默认 256）
         */
        private int queueCapacity = 256;

        /**
         * 线程名前缀
         */
        private String threadNamePrefix = "ydsz-queue-consumer-";

        /**
         * 优雅停机等待秒数（默认 30）
         */
        private int awaitTerminationSeconds = 30;
    }

    /**
     * 解析后的消费者线程池配置
     */
    public ExecutorConfig resolvedConsumerExecutor() {
        return consumerExecutor != null ? consumerExecutor : new ExecutorConfig();
    }

    /**
     * 多 MQ 组合拓扑配置
     */
    private MultiTopologyConfig multiTopology = new MultiTopologyConfig();

    /**
     * 消息轨迹配置
     */
    private TraceConfig trace = new TraceConfig();

    /**
     * 消息轨迹配置项
     */
    @Data
    public static class TraceConfig {
        /**
         * 是否启用消息轨迹（默认false）
         */
        private boolean enabled = false;

        /**
         * 轨迹存储后端类型（memory/redis，默认memory）
         */
        private String backend = "memory";

        /**
         * 轨迹过期时间（分钟，默认30）
         */
        private int ttlMinutes = 30;

        /**
         * 内存后端最大缓存条目数（默认1000）
         */
        private int maxCapacity = 1000;

        /**
         * 解析后的后端类型
         */
        public String resolvedBackend() {
            return backend != null && !backend.isEmpty() ? backend : "memory";
        }
    }

    /**
     * 多 MQ 组合拓扑配置项
     */
    @Data
    public static class MultiTopologyConfig {
        /**
         * 是否启用多 MQ 拓扑（默认 false）
         */
        private boolean enabled = false;

        /**
         * 拓扑类型（primary-backup / fan-out / aggregation）
         */
        private String type = "primary-backup";

        /**
         * 参与拓扑的 MQ 类型列表（逗号分隔, 如 "STREAM,KAFKA"）
         */
        private String participants = "";

        /**
         * 拓扑名称（用于日志和监控标识）
         */
        private String topologyName = "default";

        /**
         * 解析后的拓扑类型
         *
         * @return 拓扑类型枚举
         */
        public TopologyType resolvedType() {
            return TopologyType.fromValue(type);
        }

        /**
         * 解析后的参与者 MQ 类型列表
         *
         * @return MQ 类型枚举列表
         */
        public List<QueueType> resolvedParticipants() {
            if (participants == null || participants.trim().isEmpty()) {
                return List.of();
            }
            return Arrays.stream(participants.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(QueueType::fromValue)
                    .toList();
        }
    }

    /**
     * 解析队列配置项
     *
     * @param configStr 格式: "KAFKA:host:9092:topicName" 或 "STREAM:127.0.0.1:6379:myGroup"
     * @return 解析后的配置对象
     */
    public static QueueProperties parse(String configStr) {
        QueueProperties properties = new QueueProperties();
        properties.setEnabled(true);

        if (configStr == null || configStr.isEmpty()) {
            throw new IllegalArgumentException("队列配置字符串不能为空");
        }

        String[] parts = configStr.split(":", 4);
        if (parts.length < 2) {
            throw new IllegalArgumentException("队列配置格式错误，应为: TYPE:HOST:PORT:...");
        }

        properties.setType(QueueType.valueOf(parts[0].toUpperCase()));
        if (parts.length >= 2) {
            properties.setHost(parts[1]);
        }
        if (parts.length >= 3) {
            properties.setPort(Integer.parseInt(parts[2]));
        }
        if (parts.length >= 4) {
            Map<String, String> args = parseKeyValueArgs(parts[3]);
            applyArgs(properties, args);
        }

        return properties;
    }

    private static Map<String, String> parseKeyValueArgs(String argsStr) {
        return Arrays.stream(argsStr.split("&"))
                .map(s -> s.split("="))
                .filter(a -> a.length == 2)
                .collect(Collectors.toMap(
                        a -> a[0].trim(),
                        a -> a[1].trim()
                ));
    }

    private static void applyArgs(QueueProperties properties, Map<String, String> args) {
        if (args.containsKey("group")) {
            properties.setStreamGroup(args.get("group"));
        }
        if (args.containsKey("consumer")) {
            properties.setStreamConsumer(args.get("consumer"));
        }
        if (args.containsKey("retryMax")) {
            properties.setStreamRetryMax(Integer.parseInt(args.get("retryMax")));
        }
        if (args.containsKey("blockMillis")) {
            properties.setStreamBlockMillis(Long.parseLong(args.get("blockMillis")));
        }
        if (args.containsKey("batchSize")) {
            properties.setStreamBatchSize(Integer.parseInt(args.get("batchSize")));
        }
        if (args.containsKey("password")) {
            properties.setPassword(args.get("password"));
        }
        if (args.containsKey("timeout")) {
            properties.setTimeout(Integer.parseInt(args.get("timeout")));
        }
    }

    /**
     * 构建消息队列工厂实例（复用 ydsz-common-redis 连接，推荐）
     *
     * @param redisTemplate    Redis 模板实例
     * @param consumerExecutor 异步消费者线程池
     * @return 消息队列工厂
     */
    public IMessageQueueProvider buildFactory(RedisTemplate<String, Object> redisTemplate,
                                              ExecutorService consumerExecutor) {
        log.info("构建消息队列工厂（复用 ydsz-common-redis 连接）");
        return new MessageQueueFactory(this, redisTemplate, consumerExecutor);
    }

    @Override
    public String toString() {
        String typeStr = type != null ? type.getValue() : "null";
        return "QueueProperties{type=" + typeStr +
                ", host='" + resolvedHost() + '\'' +
                ", port=" + resolvedPort() +
                ", timeout=" + resolvedTimeout() +
                (username != null ? ", username='" + username + '\'' : "") +
                (password != null ? ", password=***" : "") +
                "}";
    }

    /**
     * 打印多队列配置调试信息
     *
     * @param multiTypes 队列类型列表
     * @param multiConfigs 队列配置映射
     */
    public void printDebugInfo(List<QueueType> multiTypes,
                               Map<String, QueueProperties> multiConfigs) {
        log.debug("=== QueueProperties 调试信息 ===");
        log.debug("当前实例: {}", this);
        log.debug("多队列类型: {}", multiTypes);
        log.debug("多队列配置: {}", multiConfigs);
        if (multiConfigs != null) {
            multiConfigs.forEach((key, config) ->
                    log.debug("配置[{}]: {}", key, config)
            );
        }
        log.debug("=== 调试信息结束 ===");
    }

    /**
     * 从 YAML 配置构建 QueueProperties 列表
     *
     * @param yamlConfigs 配置项列表
     * @return QueueProperties 列表
     */
    public static List<QueueProperties> fromYamlConfigs(List<?> yamlConfigs) {
        if (yamlConfigs == null || yamlConfigs.isEmpty()) {
            return List.of();
        }

        return yamlConfigs.stream()
                .map(obj -> {
                    if (obj instanceof Map<?, ?> rawMap) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        rawMap.forEach((k, v) -> map.put(String.valueOf(k), v));
                        return fromMap(map);
                    }
                    throw new IllegalArgumentException("配置项必须是 Map 类型: " + obj);
                })
                .toList();
    }

    private static QueueProperties fromMap(Map<String, Object> map) {
        QueueProperties properties = new QueueProperties();
        properties.setEnabled(true);

        Object typeObj = map.get("type");
        if (typeObj != null) {
            properties.setType(QueueType.valueOf(String.valueOf(typeObj).toUpperCase()));
        }

        Object host = map.get("host");
        if (host != null) {
            properties.setHost(String.valueOf(host));
        }

        Object port = map.get("port");
        if (port != null) {
            properties.setPort(Integer.parseInt(String.valueOf(port)));
        }

        Object group = map.get("group");
        if (group != null) {
            properties.setStreamGroup(String.valueOf(group));
        }

        Object consumer = map.get("consumer");
        if (consumer != null) {
            properties.setStreamConsumer(String.valueOf(consumer));
        }

        Object password = map.get("password");
        if (password != null) {
            properties.setPassword(String.valueOf(password));
        }

        Object timeout = map.get("timeout");
        if (timeout != null) {
            properties.setTimeout(Integer.parseInt(String.valueOf(timeout)));
        }

        Object batch = map.get("batchSize");
        if (batch != null) {
            properties.setStreamBatchSize(Integer.parseInt(String.valueOf(batch)));
        }

        Object block = map.get("blockMillis");
        if (block != null) {
            properties.setStreamBlockMillis(Long.parseLong(String.valueOf(block)));
        }

        return properties;
    }
}
