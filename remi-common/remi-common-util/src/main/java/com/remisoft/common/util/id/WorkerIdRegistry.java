package com.remisoft.common.util.id;

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
 * @author remi-team
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

    // ==================== 已废弃方法（v3.0 移除） ====================

    /**
     * 心跳续约。
     *
     * <p><b>已废弃：</b>自 2.0.0 起移除应用层心跳机制。
     * 容器化环境推荐使用 Pod Index 或 Downward API 直接获取序号，
     * 传统部署推荐使用 ZooKeeper 临时节点自动续约。
     *
     * @param workerId WorkerId
     * @param nodeId   节点 ID
     * @return 永远返回 true（空操作）
     * @deprecated 自 2.0.0 起废弃，v3.0 移除。请使用容器原生序号或分布式协调器临时节点。
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    default boolean heartbeat(long workerId, String nodeId) {
        // 空操作：应用层心跳已移除
        return true;
    }

    /**
     * 释放 WorkerId。
     *
     * <p><b>已废弃：</b>自 2.0.0 起移除显式释放逻辑。
     * 容器编排平台（Kubernetes）的 StatefulSet 序号天然回收，无需应用层释放。
     *
     * @param workerId WorkerId
     * @param nodeId   节点 ID
     * @deprecated 自 2.0.0 起废弃，v3.0 移除。容器平台自动回收。
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    default void release(long workerId, String nodeId) {
        // 空操作：应用层释放已移除
    }

    /**
     * 启动定时心跳续约任务。
     *
     * <p><b>已废弃：</b>自 2.0.0 起移除定时心跳。
     * 容器化场景推荐使用 Kubernetes StatefulSet 或 Downward API。
     *
     * @param workerId    WorkerId
     * @param nodeId      节点 ID
     * @param leaseMillis 租约时间（已忽略）
     * @return null（不再返回调度器）
     * @deprecated 自 2.0.0 起废弃，v3.0 移陲。心跳线程带来的复杂度大于收益。
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    default java.util.concurrent.ScheduledExecutorService startHeartbeat(
            long workerId, String nodeId, long leaseMillis) {
        // 空操作：心跳调度器已移除
        return null;
    }
}
