package com.njydsz.pmis.common.redis.config;

import com.njydsz.pmis.common.util.classloader.ClassUtils;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 杩炴帴宸ュ巶閰嶇疆鍣?
 *
 * <p>鏍规嵁 {@link RedisClientProperties} 涓殑 clientType 閰嶇疆锛?
 * 鑷姩閫夋嫨骞跺垱寤哄搴旂殑杩炴帴宸ュ巶锛圝edis 鎴?Lettuce锛夈€?
 *
 * <p>鏀寔鐨勫姛鑳斤細
 * <ul>
 *   <li>鏍规嵁瀹㈡埛绔被鍨嬭嚜鍔ㄩ€夋嫨 JedisConnectionFactory 鎴?LettuceConnectionFactory</li>
 *   <li>瀹㈡埛绔敮涓€鎬ф牎楠岋細鍚姩鏃舵娴?classpath 涓槸鍚﹀瓨鍦ㄥ涓?Redis 瀹㈡埛绔紝閬垮厤鍐茬獊</li>
 *   <li>缁熶竴鐨勮繛鎺ユ睜閰嶇疆锛坈ommons-pool2锛?/li>
 *   <li>SSL 閰嶇疆鏀寔</li>
 *   <li>闆嗙兢鎷撴墤鑷€傚簲鍒锋柊锛圠ettuce锛?/li>
 *   <li>瓒呮椂閰嶇疆</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
public class RedisConnectionFactoryConfigurer {

    private static final long DEFAULT_TOPOLOGY_REFRESH_SECONDS = 30;

    private static final String LETTUCE_CLIENT_CLASS = "io.lettuce.core.RedisClient";
    private static final String JEDIS_CLIENT_CLASS = "redis.clients.jedis.Jedis";

    /**
     * 瀹㈡埛绔敮涓€鎬ф牎楠屾爣璁帮紝纭繚鍙墽琛屼竴娆?
     */
    private static final AtomicBoolean CLIENT_VALIDATED = new AtomicBoolean(false);

    /**
     * 鏍规嵁瀹㈡埛绔被鍨嬪垱寤鸿繛鎺ュ伐鍘?
     *
     * <p>鍒涘缓鍓嶄細鎵ц瀹㈡埛绔敮涓€鎬ф牎楠岋紝纭繚 classpath 涓笉浼氬悓鏃跺瓨鍦?
     * Lettuce 鍜?Jedis 涓や釜瀹㈡埛绔簱锛岄伩鍏嶈繍琛屾椂鍐茬獊銆?
     *
     * @param properties       Redis 閰嶇疆灞炴€?
     * @param clientProperties 瀹㈡埛绔厤缃睘鎬?
     * @return RedisConnectionFactory 瀹炰緥
     * @throws IllegalStateException 濡傛灉妫€娴嬪埌澶氫釜 Redis 瀹㈡埛绔?
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
     * 鏍￠獙 Redis 瀹㈡埛绔敮涓€鎬?
     *
     * <p>妫€娴?classpath 涓槸鍚﹀悓鏃跺瓨鍦?Lettuce 鍜?Jedis 涓や釜瀹㈡埛绔簱銆?
     * 濡傛灉鍚屾椂瀛樺湪锛屾姏鍑哄紓甯告彁绀虹敤鎴锋帓闄ゅ叾涓竴涓紝閬垮厤杩愯鏃跺啿绐併€?
     *
     * <p>鏍￠獙閫昏緫锛?
     * <ul>
     *   <li>浣跨敤 CAS 纭繚鍙墽琛屼竴娆℃牎楠?/li>
     *   <li>閫氳繃 ClassUtils.isPresent 妫€娴嬬被鏄惁瀛樺湪</li>
     *   <li>鍚屾椂瀛樺湪鏃舵姏鍑?IllegalStateException</li>
     * </ul>
     *
     * @throws IllegalStateException 濡傛灉鍚屾椂妫€娴嬪埌 Lettuce 鍜?Jedis
     */
    private void validateClientUniqueness() {
        if (CLIENT_VALIDATED.compareAndSet(false, true)) {
            boolean lettucePresent = ClassUtils.isPresent(LETTUCE_CLIENT_CLASS);
            boolean jedisPresent = ClassUtils.isPresent(JEDIS_CLIENT_CLASS);

            if (lettucePresent && jedisPresent) {
                throw new IllegalStateException(
                        "妫€娴嬪埌 Redis 瀹㈡埛绔啿绐侊細classpath 涓悓鏃跺瓨鍦?Lettuce 鍜?Jedis銆? +
                        "璇峰湪 pom.xml 涓帓闄ゅ叾涓竴涓紝渚嬪锛? +
                        " <exclusion><groupId>redis.clients</groupId><artifactId>jedis</artifactId></exclusion>"
                );
            }
        }
    }

    /**
     * 鍒涘缓 Jedis 杩炴帴宸ュ巶
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
     * 鍒涘缓 Lettuce 杩炴帴宸ュ巶
     */
    private LettuceConnectionFactory createLettuceConnectionFactory(RedisProperties properties,
                                                                     RedisClientProperties clientProperties,
                                                                     Duration timeout) {
        LettuceClientConfiguration clientConfig = buildLettuceClientConfiguration(properties, clientProperties, timeout);

        if (properties.getCluster() != null && properties.getCluster().getNodes() != null
                && !properties.getCluster().getNodes().isEmpty()) {
            RedisClusterConfiguration clusterConfig =
                    new RedisClusterConfiguration(new java.util.ArrayList<>(properties.getCluster().getNodes()));
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
     * 鏋勫缓 Jedis 瀹㈡埛绔厤缃?
     */
    @SuppressWarnings("rawtypes")
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
     * 鏋勫缓 Lettuce 瀹㈡埛绔厤缃?
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
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
     * 灏嗛厤缃枃浠剁殑璇荤瓥鐣ユ槧灏勪负 Lettuce 鐨?ReadFrom
     */
    private ReadFrom resolveReadFrom(RedisClientProperties clientProperties) {
        if (clientProperties == null || clientProperties.getReadFrom() == null) {
            return null;
        }
        RedisClientProperties.ReadFrom configured = clientProperties.getReadFrom();
        return switch (configured) {
            case MASTER, UPSTREAM -> null; // Lettuce 榛樿灏辨槸 UPSTREAM锛屾棤闇€鏄惧紡璁剧疆
            case MASTER_PREFERRED, UPSTREAM_PREFERRED -> ReadFrom.UPSTREAM_PREFERRED;
            case REPLICA_PREFERRED -> ReadFrom.REPLICA_PREFERRED;
            case REPLICA -> ReadFrom.REPLICA;
            case NEAREST -> ReadFrom.LOWEST_LATENCY;
            default -> null;
        };
    }

    /**
     * 鏋勫缓閫氱敤杩炴帴姹犻厤缃?
     */
    @SuppressWarnings("rawtypes")
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
     * 鏋勫缓 Lettuce 瀹㈡埛绔€夐」锛氳嚜鍔ㄩ噸杩?+ 闆嗙兢鎷撴墤鍒锋柊
     */
    private ClientOptions buildLettuceClientOptions(RedisProperties properties) {
        if (properties.getCluster() != null && properties.getCluster().getNodes() != null
                && !properties.getCluster().getNodes().isEmpty()) {
            // Lettuce 7.x: 鑷€傚簲鍒锋柊瑙﹀彂鍣ㄩ粯璁ゅ叏閮ㄥ惎鐢紙DEFAULT_ADAPTIVE_REFRESH_TRIGGERS锛夛紝
            // 鏃犻渶鏄惧紡璋冪敤 enableAdaptiveRefreshTrigger锛堝凡寮冪敤锛夛紝浠呴渶閰嶇疆鍛ㄦ湡鍒锋柊
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
     * 鍒ゆ柇鏄惁鍚敤 SSL
     */
    private boolean isSslEnabled(RedisClientProperties clientProperties) {
        if (clientProperties != null && clientProperties.getSsl() != null) {
            return clientProperties.getSsl().isEnabled();
        }
        return false;
    }

    /**
     * 鏋勫缓鍗曟満妯″紡閰嶇疆
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
     * 鏋勫缓闆嗙兢妯″紡閰嶇疆
     */
    private RedisClusterConfiguration buildClusterConfig(RedisProperties properties) {
        if (properties.getCluster() == null || properties.getCluster().getNodes() == null
                || properties.getCluster().getNodes().isEmpty()) {
            return null;
        }
        RedisClusterConfiguration config =
                new RedisClusterConfiguration(new java.util.ArrayList<>(properties.getCluster().getNodes()));
        config.setPassword(properties.getPassword());
        config.setMaxRedirects(properties.getCluster().getMaxRedirects());
        return config;
    }

    /**
     * 鏋勫缓鍝ㄥ叺妯″紡閰嶇疆
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
     * 鏋勫缓 Redis 鑺傜偣鍒楄〃
     */
    private java.util.List<RedisNode> buildRedisNodes(java.util.Collection<String> nodes) {
        java.util.List<RedisNode> redisNodes = new java.util.ArrayList<>();
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
