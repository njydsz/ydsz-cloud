package com.remisoft.common.redis.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import com.remisoft.common.util.classloader.ClassUtils;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;

/**
 * Redis 连接工厂配置器
 *
 * <p>根据 {@link RedisClientProperties} 中的 clientType 配置，
 * 自动选择并创建对应的连接工厂（Jedis 或 Lettuce）。
 *
 * <p>支持的功能：
 * <ul>
 *   <li>根据客户端类型自动选择 JedisConnectionFactory 或 LettuceConnectionFactory</li>
 *   <li>客户端唯一性校验：启动时检测 classpath 中是否存在多个 Redis 客户端，避免冲突</li>
 *   <li>统一的连接池配置（commons-pool2）</li>
 *   <li>SSL 配置支持</li>
 *   <li>集群拓扑自适应刷新（Lettuce）</li>
 *   <li>超时配置</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class RedisConnectionFactoryConfigurer {

    private static final long DEFAULT_TOPOLOGY_REFRESH_SECONDS = 30;

    private static final String LETTUCE_CLIENT_CLASS = "io.lettuce.core.RedisClient";
    private static final String JEDIS_CLIENT_CLASS = "redis.clients.jedis.Jedis";

    /**
     * 客户端唯一性校验标记，确保只执行一次
     */
    private static final AtomicBoolean CLIENT_VALIDATED = new AtomicBoolean(false);

    /**
     * 根据客户端类型创建连接工厂
     *
     * <p>创建前会执行客户端唯一性校验，确保 classpath 中不会同时存在
     * Lettuce 和 Jedis 两个客户端库，避免运行时冲突。
     *
     * @param properties       Redis 配置属性
     * @param clientProperties 客户端配置属性
     * @return RedisConnectionFactory 实例
     * @throws IllegalStateException 如果检测到多个 Redis 客户端
     */
    public RedisConnectionFactory createConnectionFactory(RedisProperties properties,
                                                          RedisClientProperties clientProperties) {
        validateClientUniqueness();

        RedisClientType clientType = clientProperties != null
                ? clientProperties.getType()
                : RedisClientType.LETTUCE;

        Duration timeout = properties.getTimeoutDuration() != null
                ? properties.getTimeoutDuration()
                : Duration.ofMillis(properties.getTimeout());

        switch (clientType) {
            case JEDIS:
                return createJedisConnectionFactory(properties, clientProperties, timeout);
            case LETTUCE:
            default:
                return createLettuceConnectionFactory(properties, clientProperties, timeout);
        }
    }

    /**
     * 校验 Redis 客户端唯一性
     *
     * <p>检测 classpath 中是否同时存在 Lettuce 和 Jedis 两个客户端库。
     * 如果同时存在，抛出异常提示用户排除其中一个，避免运行时冲突。
     *
     * <p>校验逻辑：
     * <ul>
     *   <li>使用 CAS 确保只执行一次校验</li>
     *   <li>通过 ClassUtils.isPresent 检测类是否存在</li>
     *   <li>同时存在时抛出 IllegalStateException</li>
     * </ul>
     *
     * @throws IllegalStateException 如果同时检测到 Lettuce 和 Jedis
     */
    private void validateClientUniqueness() {
        if (CLIENT_VALIDATED.compareAndSet(false, true)) {
            boolean lettucePresent = ClassUtils.isPresent(LETTUCE_CLIENT_CLASS);
            boolean jedisPresent = ClassUtils.isPresent(JEDIS_CLIENT_CLASS);

            if (lettucePresent && jedisPresent) {
                throw new IllegalStateException(
                        "检测到 Redis 客户端冲突：classpath 中同时存在 Lettuce 和 Jedis。" +
                        "请在 pom.xml 中排除其中一个，例如：" +
                        " <exclusion><groupId>redis.clients</groupId><artifactId>jedis</artifactId></exclusion>"
                );
            }
        }
    }

    /**
     * 创建 Jedis 连接工厂
     */
    private JedisConnectionFactory createJedisConnectionFactory(RedisProperties properties,
                                                                 RedisClientProperties clientProperties,
                                                                 Duration timeout) {
        RedisStandaloneConfiguration standaloneConfig = buildStandaloneConfig(properties);
        RedisClusterConfiguration clusterConfig = buildClusterConfig(properties);
        RedisSentinelConfiguration sentinelConfig = buildSentinelConfig(properties);

        JedisClientConfiguration clientConfig = buildJedisClientConfiguration(clientProperties, timeout);

        JedisConnectionFactory factory;
        if (clusterConfig != null) {
            factory = new JedisConnectionFactory(clusterConfig, clientConfig);
        } else if (sentinelConfig != null) {
            factory = new JedisConnectionFactory(sentinelConfig, clientConfig);
        } else {
            factory = new JedisConnectionFactory(standaloneConfig, clientConfig);
        }

        return factory;
    }

    /**
     * 创建 Lettuce 连接工厂
     */
    private LettuceConnectionFactory createLettuceConnectionFactory(RedisProperties properties,
                                                                     RedisClientProperties clientProperties,
                                                                     Duration timeout) {
        LettuceClientConfiguration clientConfig = buildLettuceClientConfiguration(properties, clientProperties, timeout);

        if (properties.getCluster() != null && properties.getCluster().getNodes() != null
                && !properties.getCluster().getNodes().isEmpty()) {
            RedisClusterConfiguration clusterConfig =
                    new RedisClusterConfiguration(new ArrayList<>(properties.getCluster().getNodes()));
            clusterConfig.setPassword(properties.getPassword());
            clusterConfig.setMaxRedirects(properties.getCluster().getMaxRedirects());
            return new LettuceConnectionFactory(clusterConfig, clientConfig);
        }

        if (properties.getSentinel() != null && properties.getSentinel().getMaster() != null) {
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
            sentinelConfig.setMaster(properties.getSentinel().getMaster());
            sentinelConfig.setSentinels(buildRedisNodes(properties.getSentinel().getNodes()));
            sentinelConfig.setPassword(properties.getPassword());
            sentinelConfig.setSentinelPassword(properties.getSentinel().getPassword());
            return new LettuceConnectionFactory(sentinelConfig, clientConfig);
        }

        RedisStandaloneConfiguration standaloneConfig = buildStandaloneConfig(properties);
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    /**
     * 构建 Jedis 客户端配置
     */
    private JedisClientConfiguration buildJedisClientConfiguration(RedisClientProperties clientProperties,
                                                                    Duration timeout) {
        JedisClientConfiguration.JedisClientConfigurationBuilder builder =
                JedisClientConfiguration.builder();

        builder.connectTimeout(timeout);

        boolean sslEnabled = isSslEnabled(clientProperties);
        if (sslEnabled) {
            builder.useSsl();
        }

        if (clientProperties != null && clientProperties.getPool() != null
                && clientProperties.getPool().isEnabled()) {
            GenericObjectPoolConfig poolConfig = buildGenericPoolConfig(clientProperties.getPool());
            builder.usePooling().poolConfig(poolConfig);
        }

        return builder.build();
    }

    /**
     * 构建 Lettuce 客户端配置
     */
    private LettuceClientConfiguration buildLettuceClientConfiguration(RedisProperties properties,
                                                                        RedisClientProperties clientProperties,
                                                                        Duration timeout) {
        boolean usePool = clientProperties != null && clientProperties.getPool() != null
                && clientProperties.getPool().isEnabled();

        ReadFrom readFrom = resolveReadFrom(clientProperties);

        if (usePool) {
            GenericObjectPoolConfig poolConfig = buildGenericPoolConfig(clientProperties.getPool());
            LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                    LettucePoolingClientConfiguration.builder()
                            .commandTimeout(timeout)
                            .poolConfig(poolConfig);

            boolean sslEnabled = isSslEnabled(clientProperties);
            if (sslEnabled) {
                builder.useSsl();
            }

            builder.clientOptions(buildLettuceClientOptions(properties));
            if (readFrom != null) {
                builder.readFrom(readFrom);
            }
            return builder.build();
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(timeout);

        boolean sslEnabled = isSslEnabled(clientProperties);
        if (sslEnabled) {
            builder.useSsl();
        }

        builder.clientOptions(buildLettuceClientOptions(properties));
        if (readFrom != null) {
            builder.readFrom(readFrom);
        }
        return builder.build();
    }

    /**
     * 将配置文件的读策略映射为 Lettuce 的 ReadFrom
     */
    private ReadFrom resolveReadFrom(RedisClientProperties clientProperties) {
        if (clientProperties == null || clientProperties.getReadFrom() == null) {
            return null;
        }
        RedisClientProperties.ReadFrom configured = clientProperties.getReadFrom();
        return switch (configured) {
            case MASTER, UPSTREAM -> null; // Lettuce 默认就是 UPSTREAM，无需显式设置
            case MASTER_PREFERRED, UPSTREAM_PREFERRED -> ReadFrom.UPSTREAM_PREFERRED;
            case REPLICA_PREFERRED -> ReadFrom.REPLICA_PREFERRED;
            case REPLICA -> ReadFrom.REPLICA;
            case NEAREST -> ReadFrom.LOWEST_LATENCY;
            default -> null;
        };
    }

    /**
     * 构建通用连接池配置
     */
    private GenericObjectPoolConfig buildGenericPoolConfig(RedisClientProperties.Pool poolConfig) {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(poolConfig.getMaxActive());
        config.setMaxIdle(poolConfig.getMaxIdle());
        config.setMinIdle(poolConfig.getMinIdle());
        config.setMaxWait(Duration.ofMillis(poolConfig.getMaxWait()));
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        config.setMinEvictableIdleDuration(Duration.ofMinutes(5));
        config.setNumTestsPerEvictionRun(3);
        return config;
    }

    /**
     * 构建 Lettuce 客户端选项：自动重连 + 集群拓扑刷新
     */
    private ClientOptions buildLettuceClientOptions(RedisProperties properties) {
        if (properties.getCluster() != null && properties.getCluster().getNodes() != null
                && !properties.getCluster().getNodes().isEmpty()) {
            // Lettuce 7.x: 自适应刷新触发器默认全部启用（DEFAULT_ADAPTIVE_REFRESH_TRIGGERS），
            // 无需显式调用 enableAdaptiveRefreshTrigger（已弃用），仅需配置周期刷新
            ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                    .enablePeriodicRefresh(Duration.ofSeconds(DEFAULT_TOPOLOGY_REFRESH_SECONDS))
                    .build();
            return ClusterClientOptions.builder()
                    .autoReconnect(true)
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build();
        }

        return ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();
    }

    /**
     * 判断是否启用 SSL
     */
    private boolean isSslEnabled(RedisClientProperties clientProperties) {
        if (clientProperties != null && clientProperties.getSsl() != null) {
            return clientProperties.getSsl().isEnabled();
        }
        return false;
    }

    /**
     * 构建单机模式配置
     */
    private RedisStandaloneConfiguration buildStandaloneConfig(RedisProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getHost());
        config.setPort(properties.getPort());
        config.setPassword(properties.getPassword());
        config.setDatabase(properties.getDatabase());
        if (properties.getUser() != null) {
            config.setUsername(properties.getUser());
        }
        return config;
    }

    /**
     * 构建集群模式配置
     */
    private RedisClusterConfiguration buildClusterConfig(RedisProperties properties) {
        if (properties.getCluster() == null || properties.getCluster().getNodes() == null
                || properties.getCluster().getNodes().isEmpty()) {
            return null;
        }
        RedisClusterConfiguration config =
                new RedisClusterConfiguration(new ArrayList<>(properties.getCluster().getNodes()));
        config.setPassword(properties.getPassword());
        config.setMaxRedirects(properties.getCluster().getMaxRedirects());
        return config;
    }

    /**
     * 构建哨兵模式配置
     */
    private RedisSentinelConfiguration buildSentinelConfig(RedisProperties properties) {
        if (properties.getSentinel() == null || properties.getSentinel().getMaster() == null) {
            return null;
        }
        RedisSentinelConfiguration config = new RedisSentinelConfiguration();
        config.setMaster(properties.getSentinel().getMaster());
        config.setSentinels(buildRedisNodes(properties.getSentinel().getNodes()));
        config.setPassword(properties.getPassword());
        config.setSentinelPassword(properties.getSentinel().getPassword());
        return config;
    }

    /**
     * 构建 Redis 节点列表
     */
    private List<RedisNode> buildRedisNodes(Collection<String> nodes) {
        List<RedisNode> redisNodes = new ArrayList<>();
        if (nodes != null) {
            for (String node : nodes) {
                String[] parts = node.split(":");
                if (parts.length == 2) {
                    redisNodes.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
                }
            }
        }
        return redisNodes;
    }
}
