package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.domain.event.RuleConfigRefreshEvent;

/**
 * 规则配置广播器（分布式热加载 SPI）
 *
 * <p>用于在多实例部署环境下广播规则变更事件，确保所有节点的规则缓存一致。
 * 消费方（如 execution 模块）提供基于 Redis Pub/Sub、Nacos、MQ 等的实现。
 *
 * <p>典型流程：
 * <pre>
 *   节点A: RuleAdminService.save() → broadcaster.broadcast(event)
 *                                       ↓ (Redis Pub/Sub)
 *   节点B: broadcaster.onMessage(event) → publishEvent(local) → RuleHotReloader
 * </pre>
 *
 * <p>防止广播风暴：广播消息携带 sourceNodeId，接收方忽略本节点发出的消息。
 *
 * @since 1.0.0
 */
public interface RuleConfigBroadcaster {

    /**
     * 广播规则变更事件到所有节点
     *
     * @param event    规则变更事件
     * @param sourceId 发送节点标识（用于接收方忽略自身消息，防止循环）
     */
    void broadcast(RuleConfigRefreshEvent event, String sourceId);

    /**
     * 是否已启用广播
     *
     * @return true=已启用分布式广播
     */
    default boolean isAvailable() {
        return true;
    }
}
