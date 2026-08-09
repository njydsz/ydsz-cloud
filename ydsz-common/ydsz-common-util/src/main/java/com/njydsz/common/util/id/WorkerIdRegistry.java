package com.njydsz.common.util.id;

/**
 * WorkerId 注册中心 SPI（简化版）。
 *
 * <p>用于分布式场景下 WorkerId 唯一分配，避免多实例 ID 冲突。
 * 实现可对接 Redis SETNX、Zookeeper 临时节点、ETCD、Nacos 等。
 *
 * <h2>设计变更（2.0.0 简化）</h2>
 * <ul>
 *   <li>移除 heartbeat/release 方法：容器化部署场景下，Pod IP 哈希或 StatefulSet 序号更可靠</li>
 *   <li>移除 startHeartbeat 默认方法：应用层心跳增加复杂度且无明确收益</li>
 *   <li>仅保留 acquire 核心方法：获取一个可用的 WorkerId</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * &#64;Component
 * public class RedisWorkerIdRegistry implements WorkerIdRegistry {
 *     &#64;Override
 *     public long acquire(String nodeId) {
 *         // 基于 Redis SETNX 或 Pod Index 获取 workerId
 *         return resolvedWorkerId;
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface WorkerIdRegistry {

    /**
     * 获取一个可用的 WorkerId。
     *
     * <p>调用方需保证同一 nodeId 多次调用返回相同 ID（幂等性）。
     * 推荐实现：基于 nodeId 哈希取模、分布式锁 + 自增序号、或容器序号直接映射。
     *
     * @param nodeId 节点标识（通常为 Pod 名、IP 或主机名）
     * @return WorkerId（0-31）
     * @throws IllegalStateException 当 WorkerId 资源耗尽或分配失败时
     */
    long acquire(String nodeId);

    /**
     * 注册中心类型标识。
     *
     * @return 类型名称（如 "Redis"、"Zookeeper"、"ETCD"、"Nacos"）
     */
    default String type() {
        return getClass().getSimpleName();
    }
}
