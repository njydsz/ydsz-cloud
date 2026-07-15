package com.njydsz.pmis.literule.server.distributed;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.server.config.LiteRuleProperties;
import com.njydsz.pmis.literule.server.spi.RuleConfigBroadcaster;

/**
 * 分布式执行自动配置（P2-16）
 *
 * <p>当 {@code pmis.literule.distributed.enabled=true} 时自动装配：
 * <ul>
 *   <li>{@link RedisNodeRegistry} - 基于 Redis 的节点注册表（Redisson 可用时优先）</li>
 *   <li>{@link InMemoryNodeRegistry} - 内存注册表（Redisson 不可用时的降级方案）</li>
 *   <li>{@link RedisRuleConfigBroadcaster} - 基于 Redis Pub/Sub 的配置广播（Redisson 可用时优先）</li>
 *   <li>{@link ConsistentHashSharder} - 一致性 hash 分片器</li>
 *   <li>{@link ShardAwareRuleEngine} - 分片感知的规则引擎装饰器</li>
 *   <li>定时心跳 + 节点刷新任务</li>
 * </ul>
 *
 * <p>装配优先级：
 * <ol>
 *   <li>classpath 存在 {@code RedissonClient} 且 {@code pmis.literule.distributed.enabled=true}：使用 Redis 实现</li>
 *   <li>仅 {@code pmis.literule.distributed.enabled=true}：降级为内存实现（单节点/开发环境）</li>
 * </ol>
 *
 * @since 1.5.0
 */
@Configuration
@ConditionalOnProperty(prefix = "pmis.literule.distributed", name = "enabled", havingValue = "true")
public class DistributedAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DistributedAutoConfiguration.class);

    private ScheduledExecutorService scheduler;

    /**
     * 当前节点 ID（hostname:pid）
     */
    @Bean
    @ConditionalOnMissingBean
    public String nodeId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "localhost";
        }
        String pid = String.valueOf(ProcessHandle.current().pid());
        return host + ":" + pid;
    }

    /**
     * 节点注册表（Redis 实现，Redisson 可用时优先）
     *
     * <p>生产环境推荐使用此实现，所有节点共享 Redis 中的注册表，
     * 实现跨实例节点发现与心跳管理。
     */
    @Bean
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @ConditionalOnMissingBean(NodeRegistry.class)
    public NodeRegistry redisNodeRegistry(
            RedissonClient redissonClient,
            String nodeId,
            LiteRuleProperties properties) {
        RedisNodeRegistry registry = new RedisNodeRegistry(
                redissonClient, nodeId, properties.getDistributed().getHeartbeatTimeoutMs());
        // 注册自身
        ClusterNode self = new ClusterNode(nodeId, nodeId);
        registry.register(self);
        log.info("[Distributed] 节点注册表已初始化（self={}, type=Redis）", nodeId);
        return registry;
    }

    /**
     * 节点注册表（内存实现，Redisson 不可用时的降级方案）
     */
    @Bean
    @ConditionalOnMissingBean(NodeRegistry.class)
    public NodeRegistry inMemoryNodeRegistry(String nodeId, LiteRuleProperties properties) {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(
                nodeId, properties.getDistributed().getHeartbeatTimeoutMs());
        // 注册自身
        ClusterNode self = new ClusterNode(nodeId, nodeId);
        registry.register(self);
        log.info("[Distributed] 节点注册表已初始化（self={}, type=InMemory）", nodeId);
        return registry;
    }

    /**
     * 规则配置广播器（Redis Pub/Sub 实现，Redisson 可用时优先）
     *
     * <p>生产环境推荐使用此实现，确保多实例规则配置一致。
     */
    @Bean
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @ConditionalOnMissingBean(RuleConfigBroadcaster.class)
    public RuleConfigBroadcaster redisRuleConfigBroadcaster(
            RedissonClient redissonClient,
            String nodeId,
            ApplicationEventPublisher eventPublisher) {
        RedisRuleConfigBroadcaster broadcaster = new RedisRuleConfigBroadcaster(
                redissonClient, nodeId, eventPublisher);
        // 启动时订阅 Topic
        broadcaster.subscribe();
        log.info("[Distributed] 规则配置广播器已初始化（self={}, type=Redis）", nodeId);
        return broadcaster;
    }

    /**
     * 一致性 hash 分片器
     */
    @Bean
    @ConditionalOnMissingBean
    public ConsistentHashSharder consistentHashSharder() {
        ConsistentHashSharder sharder = new ConsistentHashSharder();
        log.info("[Distributed] 一致性 Hash 分片器已初始化（vnodes={}）", ConsistentHashSharder.DEFAULT_VNODES);
        return sharder;
    }

    /**
     * 分片感知的规则引擎装饰器
     *
     * <p>当 {@link RuleEngine} Bean 存在时自动包装为 {@link ShardAwareRuleEngine}。
     * 装饰器在后台定时刷新节点列表，保持 hash 环与集群状态一致。
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardAwareRuleEngine shardAwareRuleEngine(
            RuleEngine ruleEngine,
            NodeRegistry nodeRegistry,
            ConsistentHashSharder sharder,
            String nodeId,
            LiteRuleProperties properties) {

        ShardAwareRuleEngine engine = new ShardAwareRuleEngine(ruleEngine, nodeRegistry, sharder);
        engine.refreshNodes();

        long refreshIntervalMs = properties.getDistributed().getRefreshIntervalMs();
        long heartbeatIntervalMs = properties.getDistributed().getHeartbeatIntervalMs();

        // 启动定时心跳 + 节点刷新
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "literule-distributed-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                nodeRegistry.heartbeat(nodeId);
                engine.refreshNodes();
            } catch (Exception e) {
                log.warn("[Distributed] 节点刷新失败: {}", e.getMessage());
            }
        }, heartbeatIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);

        log.info("[Distributed] 分片感知规则引擎已初始化（self={}, clusterSize={}）",
                nodeId, engine.getClusterSize());
        return engine;
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[Distributed] 节点刷新任务已关闭");
        }
    }
}
