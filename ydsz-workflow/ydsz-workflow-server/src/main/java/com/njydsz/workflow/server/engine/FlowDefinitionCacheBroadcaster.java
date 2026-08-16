package com.njydsz.workflow.server.engine;


import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.njydsz.common.redis.service.ops.RedisPubSubOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 流程定义缓存集群广播器
 *
 * <p>P0-3: 解决 Caffeine 本地缓存在集群部署下的不一致问题。
 *
 * <p>当某个节点执行流程定义发布/停用/迁移/编辑等操作时，通过 Redis Pub/Sub 广播
 * 缓存失效消息到集群所有节点，各节点收到后清除本地缓存。
 *
 * <p>消息格式：{@code definitionId|sourceNodeId}
 * <ul>
 *   <li>{@code definitionId} — 要失效的流程定义 ID</li>
 *   <li>{@code sourceNodeId} — 发送方节点唯一标识，接收方忽略自身发出的消息</li>
 * </ul>
 *
 * <p>参考实现：auth 模块 PermissionChangeNotifier / PermissionChangeCacheInvalidator
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowDefinitionCacheBroadcaster {

    /** Redis Pub/Sub 频道 */
    private static final String CHANNEL = "flow:definition:cache:invalidate";

    private final RedisPubSubOps redisPubSubOps;
    /** @Lazy 避免 FlowDefinitionCacheService ↔ Broadcaster 循环依赖 */
    private final FlowDefinitionCacheService cacheService;
    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 本节点唯一标识，用于忽略自身发出的广播 */
    private final String sourceNodeId;


    public FlowDefinitionCacheBroadcaster(RedisPubSubOps redisPubSubOps,
                                           @Lazy FlowDefinitionCacheService cacheService,
            SnowflakeIdGenerator snowflakeIdGenerator) {
        this.redisPubSubOps = redisPubSubOps;
        this.cacheService = cacheService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.sourceNodeId = String.valueOf(snowflakeIdGenerator.nextId());
    }

    /**
     * 启动时订阅 Redis 频道
     */
    @PostConstruct
    void subscribe() {
        redisPubSubOps.subscribe(CHANNEL, message -> {
            String body = message.getBody(String.class);
            if (body == null || body.isBlank()) {
                return;
            }
            String[] parts = body.split("\\|", 2);
            if (parts.length < 2) {
                return;
            }
            String definitionId = parts[0];
            String source = parts[1];
            // 忽略自身发出的广播（本地 evict 已在 evict() 中执行）
            if (sourceNodeId.equals(source)) {
                return;
            }
            log.info("[FlowCache] 收到集群缓存失效广播: definitionId={} source={}",
                    definitionId, source);
            cacheService.evictLocal(definitionId);
        });
        log.info("[FlowCache] 流程定义缓存失效监听已启动: channel={} node={}", CHANNEL, sourceNodeId);
    }

    /**
     * 广播缓存失效消息到集群所有节点
     *
     * @param definitionId 要失效的流程定义 ID
     */
    public void broadcast(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            return;
        }
        try {
            String message = definitionId + "|" + sourceNodeId;
            redisPubSubOps.publish(CHANNEL, message);
        } catch (Exception e) {
            // 广播失败不影响本地操作，仅记录日志
            log.warn("[FlowCache] 缓存失效广播失败: definitionId={} err={}",
                    definitionId, e.getMessage());
        }
    }
}
