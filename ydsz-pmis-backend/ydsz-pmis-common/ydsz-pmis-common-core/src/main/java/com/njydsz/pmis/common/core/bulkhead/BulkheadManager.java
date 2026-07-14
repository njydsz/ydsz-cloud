package com.njydsz.pmis.common.core.bulkhead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 舱壁隔离管理器
 *
 * <p>基于信号量实现资源隔离，防止某个下游服务的故障扩散到整个系统。
 *
 * <p>每个 "舱壁" 是一个独立的信号量，限制并发请求数。
 * 当并发数超过限制时，新请求将被阻塞或快速失败。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 初始化
 * BulkheadManager manager = new BulkheadManager();
 * manager.register("payment-service", 20);  // 最多 20 个并发
 *
 * // 使用
 * try {
 *     BulkheadManager.Ticket ticket = manager.acquire("payment-service", 5, TimeUnit.SECONDS);
 *     try {
 *         // 调用下游服务
 *         return paymentClient.charge(request);
 *     } finally {
 *         ticket.release();
 *     }
 * } catch (TimeoutException e) {
 *     throw new DegradeException("Payment service bulkhead full");
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public class BulkheadManager {

    private static final Logger log = LoggerFactory.getLogger(BulkheadManager.class);

    private final Map<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

    /**
     * 注册舱壁
     *
     * @param name        舱壁名称
     * @param maxConcurrent 最大并发数
     */
    public void register(String name, int maxConcurrent) {
        bulkheads.put(name, new Semaphore(maxConcurrent, true));
        log.info("Bulkhead registered: name={}, maxConcurrent={}", name, maxConcurrent);
    }

    /**
     * 获取舱壁许可
     *
     * @param name     舱壁名称
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 许可票据
     * @throws TimeoutException 获取超时
     * @throws InterruptedException 线程中断
     */
    public Ticket acquire(String name, long timeout, TimeUnit unit)
            throws TimeoutException, InterruptedException {
        Semaphore semaphore = bulkheads.get(name);
        if (semaphore == null) {
            throw new IllegalArgumentException("Bulkhead not registered: " + name);
        }
        if (!semaphore.tryAcquire(timeout, unit)) {
            int queueLength = semaphore.getQueueLength();
            log.warn("Bulkhead full: name={}, queueLength={}", name, queueLength);
            throw new TimeoutException("Bulkhead '" + name + "' is full, queue length: " + queueLength);
        }
        return new Ticket(semaphore);
    }

    /**
     * 获取舱壁状态
     */
    public Map<String, BulkheadStats> getStats() {
        Map<String, BulkheadStats> stats = new java.util.HashMap<>();
        bulkheads.forEach((name, sem) -> {
            stats.put(name, new BulkheadStats(
                    sem.availablePermits(),
                    sem.getQueueLength()
            ));
        });
        return stats;
    }

    /**
     * 舱壁票据
     */
    public static final class Ticket {
        private final Semaphore semaphore;
        private volatile boolean released = false;

        Ticket(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        public void release() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }

    /**
     * 舱壁状态
     */
    public record BulkheadStats(int availablePermits, int queueLength) {}
}
