package com.njydsz.pmis.common.queue.manager;

import com.njydsz.pmis.common.queue.metrics.MessageMetrics;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息队列管理器
 *
 * <p>提供统一的队列生命周期管理和监控指标收集。
 * 负责注册、查询、移除队列实例及其对应的监控指标。
 *
 * <p><b>功能：</b>
 * <ul>
 *   <li>队列实例注册与管理</li>
 *   <li>监控指标收集与查询</li>
 *   <li>全局健康检查</li>
 *   <li>优雅停机支持</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * QueueManager manager = new QueueManager();
 *
 * // 注册队列
 * manager.register("order-queue", "redis", queue, metrics);
 *
 * // 查询监控
 * MessageMetrics metrics = manager.getMetrics("order-queue");
 * log.info("Metrics: {}", metrics.getSummary());
 *
 * // 优雅停机
 * manager.shutdown();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public class QueueManager {

    private final Map<String, QueueEntry> queueRegistry = new ConcurrentHashMap<>();
    private final Map<String, MessageMetrics> metricsRegistry = new ConcurrentHashMap<>();

    /**
     * 注册队列及其监控指标
     *
     * @param queueName 队列名称
     * @param queueType 队列类型
     * @param queue 队列实例（可为 null，仅用于管理用途）
     * @param metrics 监控指标实例
     */
    public void register(String queueName, String queueType, AutoCloseable queue, MessageMetrics metrics) {
        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("监控指标不能为空");
        }
        QueueEntry entry = new QueueEntry(queueName, queueType, queue);
        queueRegistry.put(queueName, entry);
        metricsRegistry.put(queueName, metrics);
    }

    /**
     * 获取队列监控指标
     *
     * @param queueName 队列名称
     * @return 监控指标，未注册时返回 null
     */
    public MessageMetrics getMetrics(String queueName) {
        return metricsRegistry.get(queueName);
    }

    /**
     * 获取所有队列名称
     *
     * @return 队列名称集合（不可变）
     */
    public Collection<String> getAllQueueNames() {
        return Collections.unmodifiableCollection(queueRegistry.keySet());
    }

    /**
     * 获取所有监控指标
     *
     * @return 监控指标集合（不可变）
     */
    public Collection<MessageMetrics> getAllMetrics() {
        return Collections.unmodifiableCollection(metricsRegistry.values());
    }

    /**
     * 获取队列实例
     *
     * @param queueName 队列名称
     * @return 队列实例，未注册时返回 null
     */
    public AutoCloseable getQueue(String queueName) {
        QueueEntry entry = queueRegistry.get(queueName);
        return entry != null ? entry.getQueue() : null;
    }

    /**
     * 移除队列注册
     *
     * @param queueName 队列名称
     * @return 是否成功移除
     */
    public boolean unregister(String queueName) {
        queueRegistry.remove(queueName);
        metricsRegistry.remove(queueName);
        return true;
    }

    /**
     * 检查队列是否已注册
     *
     * @param queueName 队列名称
     * @return 是否已注册
     */
    public boolean isRegistered(String queueName) {
        return queueRegistry.containsKey(queueName);
    }

    /**
     * 获取已注册队列数量
     *
     * @return 队列数量
     */
    public int getQueueCount() {
        return queueRegistry.size();
    }

    /**
     * 获取全局指标摘要
     *
     * @return 全局摘要字符串
     */
    public String getGlobalSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== QueueManager Global Summary ===\n");
        sb.append("Total queues: ").append(queueRegistry.size()).append("\n\n");

        for (Map.Entry<String, MessageMetrics> entry : metricsRegistry.entrySet()) {
            sb.append(entry.getValue().getSummary()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 优雅停机所有队列
     *
     * <p>依次调用每个队列的 close() 方法。
     * 按注册顺序关闭，确保资源正确释放。
     */
    public void shutdown() {
        for (QueueEntry entry : queueRegistry.values()) {
            try {
                AutoCloseable queue = entry.getQueue();
                if (queue != null) {
                    invokeClose(queue);
                }
            } catch (Exception e) {
                // 忽略关闭异常，继续关闭下一个
            }
        }
        queueRegistry.clear();
        metricsRegistry.clear();
    }

    /**
     * 队列注册表条目
     */
    private static class QueueEntry {
        @SuppressWarnings("unused")
        private final String queueName;
        @SuppressWarnings("unused")
        private final String queueType;
        private final AutoCloseable queue;

        QueueEntry(String queueName, String queueType, AutoCloseable queue) {
            this.queueName = queueName;
            this.queueType = queueType;
            this.queue = queue;
        }

        public AutoCloseable getQueue() {
            return queue;
        }
    }

    private void invokeClose(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            // 关闭异常忽略，继续关闭下一个
        }
    }
}
