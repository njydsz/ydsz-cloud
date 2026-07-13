package com.njydsz.pmis.cronjob.server.core.dispatch;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 带优先级的 Runnable 包装器（P0-3 优先级调度）。
 *
 * <p>用于将普通 {@link Runnable} 提交到 {@link java.util.concurrent.PriorityBlockingQueue} 时，
 * 按任务优先级排序。优先级数值越小，优先级越高（与 {@code pmis_job.priority} 语义一致）。
 *
 * <h3>排序规则</h3>
 * <ol>
 *   <li>优先按 {@code priority} 升序（1 最高，10 最低，默认 5）</li>
 *   <li>同优先级按 {@code sequenceNumber} 升序（FIFO，先提交先执行）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class PriorityRunnable implements Runnable, Comparable<PriorityRunnable> {

    /** 全局序列号生成器（保证同优先级 FIFO） */
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    /** 任务优先级（1-10，越小越高） */
    private final int priority;

    /** 提交序列号（同优先级时按 FIFO 排序） */
    private final long sequenceNumber;

    /** 被包装的 Runnable */
    private final Runnable delegate;

    /**
     * 构造带优先级的 Runnable。
     *
     * @param priority 任务优先级（1-10，越小越高；null 默认 5）
     * @param delegate 被包装的 Runnable
     */
    public PriorityRunnable(Integer priority, Runnable delegate) {
        this.priority = (priority == null || priority < 1) ? 5 : Math.min(priority, 10);
        this.sequenceNumber = SEQUENCE.getAndIncrement();
        this.delegate = delegate;
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public int compareTo(PriorityRunnable other) {
        // 优先按 priority 升序
        int cmp = Integer.compare(this.priority, other.priority);
        if (cmp != 0) {
            return cmp;
        }
        // 同优先级按 sequenceNumber 升序（FIFO）
        return Long.compare(this.sequenceNumber, other.sequenceNumber);
    }

    /** 获取优先级（仅供日志/监控使用） */
    public int getPriority() {
        return priority;
    }
}
