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
     * <p>心跳连续失败达到阈值（默认 3 次）时，记录 ERROR 日志并抛出
     * {@link RuntimeException} 终止心跳调度，避免租约过期后 WorkerId 被抢占导致 ID 重复。
     * 调用方应捕获并处理调度终止（如停止 ID 生成或重新获取 WorkerId）。
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
        // 心跳失败计数器：连续失败达到阈值后终止心跳调度，避免租约过期后 workerId 被抢占导致 ID 重复
        final AtomicInteger heartbeatFailCount = new AtomicInteger(0);
        final int heartbeatFailThreshold = 3;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (heartbeat(workerId, nodeIp)) {
                    heartbeatFailCount.set(0);
                } else {
                    int failCount = heartbeatFailCount.incrementAndGet();
                    System.getLogger(WorkerIdRegistry.class.getName())
                            .log(System.Logger.Level.WARNING,
                                    "WorkerId heartbeat failed for {} on {}, fail count: {}, lease may expire",
                                    workerId, nodeIp, failCount);
                    if (failCount >= heartbeatFailThreshold) {
                        System.getLogger(WorkerIdRegistry.class.getName())
                                .log(System.Logger.Level.ERROR,
                                        "WorkerId heartbeat failed {} consecutive times for {} on {}, terminating heartbeat to prevent ID duplication",
                                        failCount, workerId, nodeIp);
                        throw new RuntimeException("WorkerId heartbeat failed " + failCount
                                + " consecutive times for " + workerId + " on " + nodeIp
                                + ", terminating heartbeat to prevent ID duplication");
                    }
                }
            } catch (RuntimeException e) {
                // 重新抛出以终止 ScheduledExecutorService 调度
                throw e;
            } catch (Exception e) {
                int failCount = heartbeatFailCount.incrementAndGet();
                System.getLogger(WorkerIdRegistry.class.getName())
                        .log(System.Logger.Level.WARNING,
                                "WorkerId heartbeat error for {} on {}: {}, fail count: {}",
                                workerId, nodeIp, e.getMessage(), failCount);
                if (failCount >= heartbeatFailThreshold) {
                    System.getLogger(WorkerIdRegistry.class.getName())
                            .log(System.Logger.Level.ERROR,
                                    "WorkerId heartbeat failed {} consecutive times for {} on {}, terminating heartbeat to prevent ID duplication",
                                    failCount, workerId, nodeIp);
                    throw new RuntimeException("WorkerId heartbeat failed " + failCount
                            + " consecutive times for " + workerId + " on " + nodeIp, e);
                }
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
