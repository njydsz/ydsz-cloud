package com.njydsz.pmis.common.audit.storage;

import com.njydsz.pmis.common.audit.core.AuditStorage;
import com.njydsz.pmis.common.audit.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 默认审计日志存储实现
 * 通过内存队列缓存审计日志，适用于开发和测试环境
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class DefaultAuditStorage implements AuditStorage {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditStorage.class);

    /**
     * 默认最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY = 10000;

    /**
     * 有界阻塞队列，限制内存中审计日志的最大数量
     */
    private final BlockingQueue<AuditLog> queue;

    /**
     * 队列最大容量
     */
    private final int maxCapacity;

    /**
     * 使用默认最大容量（10000）构造
     */
    public DefaultAuditStorage() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * 使用指定最大容量构造
     * @param maxCapacity 最大容量
     */
    public DefaultAuditStorage(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.queue = new LinkedBlockingQueue<>(maxCapacity);
    }

    @Override
    public void save(AuditLog auditLog) {
        if (auditLog == null) {
            log.warn("【审计存储】审计日志为空,跳过保存");
            return;
        }
        // 尝试将日志加入队列，队列满时丢弃最旧日志
        if (!queue.offer(auditLog)) {
            // 队列已满，移除最旧的并重新尝试
            AuditLog dropped = queue.poll();
            if (dropped != null) {
                log.warn("【审计存储】队列已满(容量={})，丢弃最旧日志: {}", maxCapacity, dropped);
            }
            // 再次尝试插入
            if (!queue.offer(auditLog)) {
                log.error("【审计存储】队列已满且无法插入新日志，该日志被丢弃: {}", auditLog);
            }
        }
        log.info("【审计日志】{}", auditLog);
    }

    @Override
    public void saveBatch(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            log.warn("【审计存储】审计日志列表为空,跳过保存");
            return;
        }
        for (AuditLog auditLog : auditLogs) {
            save(auditLog);
        }
    }

    /**
     * 获取当前队列中的日志数量
     * @return 队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 获取最大容量
     * @return 最大容量
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * 获取队列中排队的日志（用于消费）
     * @return 审计日志
     */
    public AuditLog poll() {
        return queue.poll();
    }

    @Override
    public String getType() {
        return "DEFAULT";
    }
}
