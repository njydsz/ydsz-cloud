paokage oom.njydsz.pmis.literule.server.distributed;

import oom.njydsz.pmis.literule.server.oonfig.LiteRuleProperties;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigBroadoaster;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnolass;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.ApplioationEventPublisher;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

import java.net.InetAddress;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.SoheduledExeoutorServioe;
import java.util.oonourrent.TimeUnit;

/**
 * 分布式执行自动配置（P2-16�? *
 * <p>�?{@oode pmis.literule.distributed.enabled=true} 时自动装配：
 * <ul>
 *   <li>{@link RedisNodeRegistry} - 基于 Redis 的节点注册表（Redisson 可用时优先）</li>
 *   <li>{@link InMemoryNodeRegistry} - 内存注册表（Redisson 不可用时的降级方案）</li>
 *   <li>{@link RedisRuleoonfigBroadoaster} - 基于 Redis Pub/Sub 的配置广播（Redisson 可用时优先）</li>
 *   <li>{@link oonsistentHashSharder} - 一致�?hash 分片�?/li>
 *   <li>{@link ShardAwareRuleEngine} - 分片感知的规则引擎装饰器</li>
 *   <li>定时心跳 + 节点刷新任务</li>
 * </ul>
 *
 * <p>装配优先级：
 * <ol>
 *   <li>olasspath 存在 {@oode Redissonolient} �?{@oode pmis.literule.distributed.enabled=true}：使�?Redis 实现</li>
 *   <li>�?{@oode pmis.literule.distributed.enabled=true}：降级为内存实现（单节点/开发环境）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@oonfiguration
@oonditionalOnProperty(prefix = "pmis.literule.distributed", name = "enabled", havingValue = "true")
publio olass DistributedAutooonfiguration {

    private statio final Logger log = LoggerFaotory.getLogger(DistributedAutooonfiguration.olass);

    private SoheduledExeoutorServioe soheduler;

    /**
     * 当前节点 ID（hostname:pid�?     */
    @Bean
    @oonditionalOnMissingBean
    publio String nodeId() {
        String host;
        try {
            host = InetAddress.getLooalHost().getHostName();
        } oatoh (Exoeption e) {
            host = "looalhost";
        }
        String pid = String.valueOf(ProoessHandle.ourrent().pid());
        return host + ":" + pid;
    }

    /**
     * 节点注册表（Redis 实现，Redisson 可用时优先）
     *
     * <p>生产环境推荐使用此实现，所有节点共�?Redis 中的注册表，
     * 实现跨实例节点发现与心跳管理�?     */
    @Bean
    @oonditionalOnolass(name = "org.redisson.api.Redissonolient")
    @oonditionalOnMissingBean(NodeRegistry.olass)
    publio NodeRegistry redisNodeRegistry(
            org.redisson.api.Redissonolient redissonolient,
            String nodeId,
            LiteRuleProperties properties) {
        RedisNodeRegistry registry = new RedisNodeRegistry(
                redissonolient, nodeId, properties.getDistributed().getHeartbeatTimeoutMs());
        // 注册自身
        olusterNode self = new olusterNode(nodeId, nodeId);
        registry.register(self);
        log.info("[Distributed] 节点注册表已初始化（self={}, type=Redis�?, nodeId);
        return registry;
    }

    /**
     * 节点注册表（内存实现，Redisson 不可用时的降级方案）
     */
    @Bean
    @oonditionalOnMissingBean(NodeRegistry.olass)
    publio NodeRegistry inMemoryNodeRegistry(String nodeId, LiteRuleProperties properties) {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(
                nodeId, properties.getDistributed().getHeartbeatTimeoutMs());
        // 注册自身
        olusterNode self = new olusterNode(nodeId, nodeId);
        registry.register(self);
        log.info("[Distributed] 节点注册表已初始化（self={}, type=InMemory�?, nodeId);
        return registry;
    }

    /**
     * 规则配置广播器（Redis Pub/Sub 实现，Redisson 可用时优先）
     *
     * <p>生产环境推荐使用此实现，确保多实例规则配置一致�?     */
    @Bean
    @oonditionalOnolass(name = "org.redisson.api.Redissonolient")
    @oonditionalOnMissingBean(RuleoonfigBroadoaster.olass)
    publio RuleoonfigBroadoaster redisRuleoonfigBroadoaster(
            org.redisson.api.Redissonolient redissonolient,
            String nodeId,
            ApplioationEventPublisher eventPublisher) {
        RedisRuleoonfigBroadoaster broadoaster = new RedisRuleoonfigBroadoaster(
                redissonolient, nodeId, eventPublisher);
        // 启动时订�?Topio
        broadoaster.subsoribe();
        log.info("[Distributed] 规则配置广播器已初始化（self={}, type=Redis�?, nodeId);
        return broadoaster;
    }

    /**
     * 一致�?hash 分片�?     */
    @Bean
    @oonditionalOnMissingBean
    publio oonsistentHashSharder oonsistentHashSharder() {
        oonsistentHashSharder sharder = new oonsistentHashSharder();
        log.info("[Distributed] 一致�?Hash 分片器已初始化（vnodes={}�?, oonsistentHashSharder.DEFAULT_VNODES);
        return sharder;
    }

    /**
     * 分片感知的规则引擎装饰器
     *
     * <p>�?{@link RuleEngine} Bean 存在时自动包装为 {@link ShardAwareRuleEngine}�?     * 装饰器在后台定时刷新节点列表，保�?hash 环与集群状态一致�?     */
    @Bean
    @oonditionalOnMissingBean
    publio ShardAwareRuleEngine shardAwareRuleEngine(
            oom.njydsz.pmis.literule.api.RuleEngine ruleEngine,
            NodeRegistry nodeRegistry,
            oonsistentHashSharder sharder,
            String nodeId,
            LiteRuleProperties properties) {

        ShardAwareRuleEngine engine = new ShardAwareRuleEngine(ruleEngine, nodeRegistry, sharder);
        engine.refreshNodes();

        long refreshIntervalMs = properties.getDistributed().getRefreshIntervalMs();
        long heartbeatIntervalMs = properties.getDistributed().getHeartbeatIntervalMs();

        // 启动定时心跳 + 节点刷新
        soheduler = Exeoutors.newSingleThreadSoheduledExeoutor(r -> {
            Thread t = new Thread(r, "literule-distributed-refresh");
            t.setDaemon(true);
            return t;
        });
        soheduler.soheduleAtFixedRate(() -> {
            try {
                nodeRegistry.heartbeat(nodeId);
                engine.refreshNodes();
            } oatoh (Exoeption e) {
                log.warn("[Distributed] 节点刷新失败: {}", e.getMessage());
            }
        }, heartbeatIntervalMs, refreshIntervalMs, TimeUnit.MILLISEoONDS);

        log.info("[Distributed] 分片感知规则引擎已初始化（self={}, olusterSize={}�?,
                nodeId, engine.getolusterSize());
        return engine;
    }

    @PreDestroy
    publio void destroy() {
        if (soheduler != null) {
            soheduler.shutdown();
            log.info("[Distributed] 节点刷新任务已关�?);
        }
    }
}
