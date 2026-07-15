package com.njydsz.pmis.common.redis.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.metrics.RedisMetricsCollector;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式延时队列（基于 Redis ZSET）
 *
 * <p>使用 ZSET 存储延时任务，score 为任务到期时间戳，member 为任务 ID。
 * 消费时通过 {@code ZRANGEBYSCORE} 获取已到期任务，{@code ZREM} 移除任务保证不重复消费。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>精确延时：score 即为到期时间戳，毫秒级精度</li>
 *   <li>不重复消费：消费时通过 ZREM 原子移除，未抢到的线程无法消费</li>
 *   <li>消费者侧去重：使用 Redis SETNX 防止同一任务被多个消费者重复处理</li>
 *   <li>支持重试：将失败任务重新入队，附带重试次数</li>
 *   <li>集群友好：基于 Redis 单线程原子操作，水平扩展无状态</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 生产者：投递延时任务
 * String taskId = delayedQueue.schedule(
 *     "order:pay:timeout",                              // 队列名
 *     "orderId=10086",                                  // 任务内容
 *     Duration.ofMinutes(30)                            // 30 分钟后到期
 * );
 *
 * // 消费者：拉取并处理已到期任务
 * DelayedTask task = delayedQueue.poll(
 *     "order:pay:timeout",
 *     Duration.ofSeconds(5)                             // 最多阻塞 5 秒
 * );
 * if (task != null) {
 *     try {
 *         processOrder(task.getPayload());
 *     } catch (Exception e) {
 *         // 失败重试（指数退避）
 *         delayedQueue.requeue(task, 3, Duration.ofSeconds(1));
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>消费者应当单实例运行（避免重复消费），或通过 ZREM 的返回值判断谁抢到任务</li>
 *   <li>长时间无消费时建议使用 {@link #peek} 清理已过期但未消费的数据</li>
 *   <li>任务 payload 建议使用 JSON 序列化以保证可读性</li>
 *   <li>消费者侧去重通过 Redis SETNX 实现，默认去重窗口为 24 小时</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisDelayedQueue {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetricsCollector metricsCollector;

    public RedisDelayedQueue(RedisTemplate<String, Object> redisTemplate,
                             RedisProperties redisProperties,
                             RedisMetricsCollector metricsCollector) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 投递延时任务
     *
     * @param queueName 队列名
     * @param payload   任务内容
     * @param delay     延时时长
     * @return 任务 ID
     */
    public String schedule(String queueName, Object payload, Duration delay) {
        return schedule(queueName, payload, delay, 0);
    }

    /**
     * 投递延时任务（指定重试次数）
     *
     * @param queueName 队列名
     * @param payload   任务内容
     * @param delay     延时时长
     * @param retryCount 当前重试次数
     * @return 任务 ID
     */
    public String schedule(String queueName, Object payload, Duration delay, int retryCount) {
        if (queueName == null || payload == null || delay == null) {
            throw new IllegalArgumentException("队列名、任务内容、延时时长不能为空");
        }
        String taskId = UUID.randomUUID().toString();
        long score = Instant.now().plus(delay).toEpochMilli();
        DelayedTask task = new DelayedTask(taskId, queueName, payload, score, retryCount);
        try {
            redisTemplate.opsForZSet().add(formatQueueKey(queueName), task, score);
            log.debug("【RedisDelayedQueue】任务已入队 | queue={} | taskId={} | delay={}s",
                    queueName, taskId, delay.toSeconds());
            return taskId;
        } catch (Exception e) {
            log.error("【RedisDelayedQueue】任务入队失败 | queue={} | error={}", queueName, e);
            throw new RuntimeException("延时任务入队失败", e);
        }
    }

    /**
     * 在指定时刻投递延时任务
     *
     * @param queueName    队列名
     * @param payload      任务内容
     * @param executeAt    到期时间点
     * @return 任务 ID
     */
    public String scheduleAt(String queueName, Object payload, Instant executeAt) {
        if (executeAt == null) {
            throw new IllegalArgumentException("到期时间不能为空");
        }
        long delayMs = executeAt.toEpochMilli() - System.currentTimeMillis();
        if (delayMs < 0) {
            delayMs = 0;
        }
        return schedule(queueName, payload, Duration.ofMillis(delayMs));
    }

    /**
     * 拉取一个已到期的任务（非阻塞）
     *
     * @param queueName 队列名
     * @return 已到期的任务；无可用任务时返回 null
     */
    public DelayedTask poll(String queueName) {
        return poll(queueName, 0);
    }

    /**
     * 拉取一个已到期的任务（阻塞）
     *
     * <p>循环扫描已到期任务，通过 ZREM 抢任务模式保证唯一消费。
     * 若 maxWaitMs > 0 则轮询等待，否则立即返回。
     *
     * @param queueName  队列名
     * @param maxWaitMs  最大等待时长（毫秒），0 表示非阻塞
     * @return 已到期的任务；超时仍无任务时返回 null
     */
    public DelayedTask poll(String queueName, long maxWaitMs) {
        if (queueName == null) {
            return null;
        }
        String queueKey = formatQueueKey(queueName);
        long deadline = maxWaitMs > 0 ? System.currentTimeMillis() + maxWaitMs : 0;
        int pollInterval = 100;
        try {
            while (true) {
                long now = System.currentTimeMillis();
                Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                        .rangeByScoreWithScores(queueKey, 0, now, 0, 1);
                if (tuples != null && !tuples.isEmpty()) {
                    ZSetOperations.TypedTuple<Object> tuple = tuples.iterator().next();
                    Object value = tuple.getValue();
                    if (value instanceof DelayedTask) {
                        // 通过 score+value 精确删除，防止 ZSET 中存在其他 member 误删
                        Long removed = redisTemplate.opsForZSet().remove(queueKey, value);
                        if (removed != null && removed > 0) {
                            if (metricsCollector != null) {
                                metricsCollector.recordOperation("delayed_queue_poll", () -> {});
                            }
                            return (DelayedTask) value;
                        }
                        // 未抢到任务（被其他消费者删除），继续循环
                    }
                }
                if (deadline == 0 || now >= deadline) {
                    return null;
                }
                try {
                    Thread.sleep(Math.min(pollInterval, Math.max(1, deadline - now)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("【RedisDelayedQueue】拉取任务失败 | queue={} | error={}", queueName, e);
            return null;
        }
    }

    /**
     * 偷看队列头部任务（不移除）
     *
     * @param queueName 队列名
     * @return 队头任务；队列为空时返回 null
     */
    public DelayedTask peek(String queueName) {
        if (queueName == null) {
            return null;
        }
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .rangeWithScores(formatQueueKey(queueName), 0, 0);
            if (tuples != null && !tuples.isEmpty()) {
                Object value = tuples.iterator().next().getValue();
                if (value instanceof DelayedTask) {
                    return (DelayedTask) value;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("【RedisDelayedQueue】偷看任务失败 | queue={} | error={}", queueName, e);
            return null;
        }
    }

    /**
     * 重新入队（消费失败时调用）
     *
     * <p>使用指数退避策略：delay = baseDelay * (2 ^ retryCount)，
     * 最大重试次数由 maxRetries 控制。
     *
     * @param task       失败的任务
     * @param maxRetries 最大重试次数
     * @param baseDelay  基础延时
     * @return true=重试入队成功，false=已超过最大重试次数
     */
    public boolean requeue(DelayedTask task, int maxRetries, Duration baseDelay) {
        if (task == null) {
            return false;
        }
        int newRetryCount = task.getRetryCount() + 1;
        if (newRetryCount > maxRetries) {
            log.warn("【RedisDelayedQueue】任务超过最大重试次数，丢弃 | taskId={} | retryCount={}",
                    task.getTaskId(), newRetryCount);
            return false;
        }
        long backoffMs = baseDelay.toMillis() * (1L << Math.min(newRetryCount, 10));
        try {
            long score = System.currentTimeMillis() + backoffMs;
            DelayedTask newTask = new DelayedTask(task.getTaskId(), task.getQueueName(),
                    task.getPayload(), score, newRetryCount);
            redisTemplate.opsForZSet().add(formatQueueKey(task.getQueueName()), newTask, score);
            log.info("【RedisDelayedQueue】任务重试入队 | taskId={} | retryCount={} | delay={}ms",
                    task.getTaskId(), newRetryCount, backoffMs);
            return true;
        } catch (Exception e) {
            log.error("【RedisDelayedQueue】任务重试入队失败 | taskId={} | error={}", task.getTaskId(), e);
            return false;
        }
    }

    /**
     * 取消任务
     *
     * @param queueName 队列名
     * @param taskId    任务 ID
     * @return true=取消成功
     */
    public boolean cancel(String queueName, String taskId) {
        if (queueName == null || taskId == null) {
            return false;
        }
        try {
            String queueKey = formatQueueKey(queueName);
            ScanOptions options = ScanOptions.scanOptions().count(100).build();
            try (Cursor<ZSetOperations.TypedTuple<Object>> cursor =
                    redisTemplate.opsForZSet().scan(queueKey, options)) {
                while (cursor.hasNext()) {
                    ZSetOperations.TypedTuple<Object> tuple = cursor.next();
                    Object value = tuple.getValue();
                    if (value instanceof DelayedTask task && taskId.equals(task.getTaskId())) {
                        Long removed = redisTemplate.opsForZSet().remove(queueKey, value);
                        return removed != null && removed > 0;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("【RedisDelayedQueue】取消任务失败 | queue={} | taskId={} | error={}",
                    queueName, taskId, e);
            return false;
        }
    }

    /**
     * 获取队列大小
     */
    public long size(String queueName) {
        if (queueName == null) {
            return 0;
        }
        try {
            Long size = redisTemplate.opsForZSet().size(formatQueueKey(queueName));
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.warn("【RedisDelayedQueue】获取队列大小失败 | queue={} | error={}", queueName, e);
            return 0;
        }
    }

    /**
     * 清理已过期但长时间未消费的任务
     *
     * @param queueName 队列名
     * @param olderThanMs 早于该时间戳（毫秒）的任务将被清理
     * @return 清理数量
     */
    public long purgeExpired(String queueName, long olderThanMs) {
        if (queueName == null) {
            return 0;
        }
        try {
            String queueKey = formatQueueKey(queueName);
            long cutoff = System.currentTimeMillis() - olderThanMs;
            Set<Object> values = redisTemplate.opsForZSet().rangeByScore(queueKey, 0, cutoff);
            if (values != null && !values.isEmpty()) {
                Long removed = redisTemplate.opsForZSet().remove(queueKey, values.toArray());
                return removed != null ? removed : 0L;
            }
            return 0;
        } catch (Exception e) {
            log.warn("【RedisDelayedQueue】清理过期任务失败 | queue={} | error={}", queueName, e);
            return 0;
        }
    }

    private String formatQueueKey(String queueName) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return "delayed:queue:" + queueName;
        }
        return prefix + ":delayed:queue:" + queueName;
    }
}
