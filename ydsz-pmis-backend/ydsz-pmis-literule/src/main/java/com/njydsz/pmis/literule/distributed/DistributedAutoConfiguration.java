package com.njydsz.pmis.literule.distributed;

import com.njydsz.pmis.literule.config.LiteRuleProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 分布式执行自动配置（P2-16）
 *
 * <p>当 {@code pmis.literule.distributed.enabled=true} 时自动装配：
 * <ul>
 *   <li>{@link InMemoryNodeRegistry} - 默认内存注册表（生产环境可覆盖为 Redis 实现）</li>
 *   <li>{@link ConsistentHashSharder} - 一致性 hash 分片器</li>
 *   <li>{@link ShardAwareRuleEngine} - 分片感知的规则引擎装饰器</li>
 *   <li>定时心跳 + 节点刷新任务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
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
     * 节点注册表（默认内存实现）
     */
    @Bean
    @ConditionalOnMissingBean
    public NodeRegistry nodeRegistry(String nodeId,
                                     LiteRuleProperties properties) {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(nodeId);
        // 注册自身
        ClusterNode self = new ClusterNode(nodeId, nodeId);
        registry.register(self);
        log.info("[Distributed] 节点注册表已初始化（self={}, type=InMemory）", nodeId);
        return registry;
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
            com.njydsz.pmis.literule.api.RuleEngine ruleEngine,
            NodeRegistry nodeRegistry,
            ConsistentHashSharder sharder,
            String nodeId) {

        ShardAwareRuleEngine engine = new ShardAwareRuleEngine(ruleEngine, nodeRegistry, sharder, 10_000L);
        engine.refreshNodes();

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
        }, 5_000L, 10_000L, TimeUnit.MILLISECONDS);

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
