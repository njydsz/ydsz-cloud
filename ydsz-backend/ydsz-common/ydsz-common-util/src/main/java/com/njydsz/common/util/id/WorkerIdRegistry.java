package com.njydsz.common.util.id;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WorkerId 注册中心 SPI
 *
 * <p>用于分布式场景下 WorkerId 唯一分配，避免多实例 ID 冲突。
 * 实现可对接 Redis SETNX、Zookeeper 临时节点、ETCD、Nacos 等。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * WorkerIdRegistry registry = new RedisWorkerIdRegistry(redisTemplate, "ydsz:snowflake:workerId");
 * long workerId = registry.acquire("192.168.1.1", 300_000L);
 * ScheduledExecutorService heartbeat = registry.startHeartbeat(workerId, "192.168.1.1", 300_000L);
 * // ... 应用关闭时
 * heartbeat.shutdown();
 * registry.release(workerId, "192.168.1.1");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface WorkerIdRegistry {

    /**
     * 获取一个可用的 WorkerId
     *
     * @param nodeIp        节点 IP（用于标识）
     * @param leaseMillis   租约时间（毫秒）
     * @return WorkerId
     * @throws IllegalStateException 当 WorkerId 资源耗尽时
     */
    long acquire(String nodeIp, long leaseMillis);

    /**
     * 心跳续约
     *
     * @param workerId WorkerId
     * @param nodeIp   节点 IP
     * @return true 表示续约成功
     */
    boolean heartbeat(long workerId, String nodeIp);

    /**
     * 释放 WorkerId
     *
     * @param workerId WorkerId
     * @param nodeIp   节点 IP
     */
    void release(long workerId, String nodeIp);

    /**
     * 启动定时心跳续约任务
     *
     * <p>在租约到期前一半的时间点自动续约，避免 WorkerId 因租约过期被回收。
     * 调用方应持有返回的 ScheduledExecutorService 以便在应用关闭时停止。
     *
     * @param workerId    WorkerId
     * @param nodeIp      节点 IP
     * @param leaseMillis 租约时间（毫秒）
     * @return 管理心跳的 ScheduledExecutorService，调用方负责 shutdown
     * @since 1.1.0
     */
    default ScheduledExecutorService startHeartbeat(
            long workerId, String nodeIp, long leaseMillis) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r, "snowflake-heartbeat-" + workerId);
                    t.setDaemon(true);
                    return t;
                });
        long heartbeatInterval = leaseMillis / 2;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!heartbeat(workerId, nodeIp)) {
                    System.getLogger(WorkerIdRegistry.class.getName())
                            .log(System.Logger.Level.WARNING,
                                    "WorkerId heartbeat failed for {} on {}, lease may expire",
                                    workerId, nodeIp);
                }
            } catch (Exception e) {
                System.getLogger(WorkerIdRegistry.class.getName())
                        .log(System.Logger.Level.WARNING,
                                "WorkerId heartbeat error for {} on {}: {}",
                                workerId, nodeIp, e.getMessage());
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    /**
     * 注册中心类型
     */
    default String type() {
        return getClass().getSimpleName();
    }
}
