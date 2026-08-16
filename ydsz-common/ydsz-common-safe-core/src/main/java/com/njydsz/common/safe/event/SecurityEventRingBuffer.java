package com.njydsz.common.safe.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.njydsz.common.safe.alert.SecurityEvent;

/**
 * 安全事件环形缓冲区
 *
 * <p>基于固定容量的数组实现 FIFO 环形缓冲，保留最近 N 个安全事件的内存快照。
 * 用于运行时查询近期事件（如通过 Actuator 端点暴露、问题排查时回溯）。
 *
 * <p><b>线程安全：</b>使用 {@code synchronized} 保证写入/读取的原子性。
 * 缓冲区大小固定（默认 256），性能开销恒定，不会出现 GC 压力。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class SecurityEventRingBuffer {

    /** 默认缓冲区容量 */
    private static final int DEFAULT_CAPACITY = 256;

    private final SecurityEvent[] buffer;
    private final int capacity;
    private int writeIndex;
    private int size;

    /**
     * 使用默认容量（256）创建环形缓冲区
     */
    public SecurityEventRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定容量创建环形缓冲区
     *
     * @param capacity 容量（须 &gt; 0）
     */
    public SecurityEventRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = new SecurityEvent[capacity];
        this.writeIndex = 0;
        this.size = 0;
    }

    /**
     * 写入一个事件（覆盖最旧的数据）
     *
     * @param event 安全事件（忽略 null）
     */
    public synchronized void offer(@Nullable SecurityEvent event) {
        if (event == null) {
            return;
        }
        buffer[writeIndex] = event;
        writeIndex = (writeIndex + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    /**
     * 获取当前缓冲区中的所有事件（按时间从旧到新排序）
     *
     * @return 不可变的事件列表
     */
    public synchronized List<SecurityEvent> snapshot() {
        if (size == 0) {
            return Collections.emptyList();
        }
        List<SecurityEvent> result = new ArrayList<>(size);
        int startIndex = size < capacity ? 0 : writeIndex;
        for (int i = 0; i < size; i++) {
            int idx = (startIndex + i) % capacity;
            SecurityEvent event = buffer[idx];
            if (event != null) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 获取最近 N 个事件
     *
     * @param count 获取数量（不超过当前大小）
     * @return 不可变的事件列表
     */
    public synchronized List<SecurityEvent> recent(int count) {
        List<SecurityEvent> all = snapshot();
        if (count >= all.size()) {
            return all;
        }
        return Collections.unmodifiableList(all.subList(all.size() - count, all.size()));
    }

    /**
     * 当前缓冲区中事件的数量
     *
     * @return 事件数量（0 ~ capacity）
     */
    public synchronized int size() {
        return size;
    }

    /**
     * 清空缓冲区
     */
    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        writeIndex = 0;
        size = 0;
    }
}
