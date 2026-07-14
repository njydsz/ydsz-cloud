package com.njydsz.pmis.common.util.id;

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
 * registry.heartbeat(workerId, "192.168.1.1");
 * registry.release(workerId, "192.168.1.1");
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
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
     * 注册中心类型
     */
    default String type() {
        return getClass().getSimpleName();
    }
}
