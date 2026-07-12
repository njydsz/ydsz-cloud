package com.njydsz.pmis.common.sensitive;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P2-14: 数据访问审计服务
 *
 * <p>记录敏感字段的加密/解密/脱敏操作，满足《个人信息保护法》(PIPL) 和
 * 《数据安全法》的合规审计要求。
 *
 * <h3>审计范围</h3>
 * <ul>
 *   <li>字段级解密操作（谁、何时、哪个密钥、哪个算法）</li>
 *   <li>字段级加密操作</li>
 *   <li>敏感数据导出操作</li>
 *   <li>密钥轮换操作</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 记录解密操作
 * DataAccessAuditService.getInstance().recordDecryption("pmis.crypto.aes-key", "AES_GCM");
 *
 * // 记录导出操作
 * DataAccessAuditService.getInstance().recordExport("user_id_card", "数据导出API");
 * }</pre>
 *
 * <p>审计日志通过异步队列写入，不影响主流程性能。
 * 队列满时自动丢弃最旧记录（防 OOM），并记录丢弃计数。
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class DataAccessAuditService {

    /** 单例实例 */
    private static volatile DataAccessAuditService instance;

    /** 审计日志队列（异步写入） */
    private final Queue<AuditRecord> auditQueue = new ConcurrentLinkedQueue<>();

    /** 队列最大容量（超过则丢弃最旧记录） */
    private static final int MAX_QUEUE_SIZE = 10_000;

    /** 统计：总审计记录数 */
    private final AtomicLong totalRecords = new AtomicLong(0);

    /** 统计：丢弃记录数 */
    private final AtomicLong droppedRecords = new AtomicLong(0);

    /** 审计记录数据模型 */
    public static class AuditRecord {
        /** 操作类型 */
        public final String operation;
        /** 密钥引用 */
        public final String keyRef;
        /** 算法 */
        public final String algorithm;
        /** 操作时间 */
        public final LocalDateTime timestamp;
        /** 操作来源（调用方标识） */
        public final String source;
        /** 当前线程名 */
        public final String threadName;

        AuditRecord(String operation, String keyRef, String algorithm, String source) {
            this.operation = operation;
            this.keyRef = keyRef;
            this.algorithm = algorithm;
            this.timestamp = LocalDateTime.now();
            this.source = source;
            this.threadName = Thread.currentThread().getName();
        }
    }

    private DataAccessAuditService() {
    }

    /**
     * 获取单例实例
     *
     * @return 审计服务实例；未初始化返回 null
     */
    public static DataAccessAuditService getInstance() {
        if (instance == null) {
            synchronized (DataAccessAuditService.class) {
                if (instance == null) {
                    instance = new DataAccessAuditService();
                }
            }
        }
        return instance;
    }

    /**
     * 记录解密操作
     *
     * @param keyRef    密钥引用
     * @param algorithm 加密算法
     */
    public void recordDecryption(String keyRef, String algorithm) {
        record("DECRYPT", keyRef, algorithm, "EncryptedFieldDeserializer");
    }

    /**
     * 记录加密操作
     *
     * @param keyRef    密钥引用
     * @param algorithm 加密算法
     */
    public void recordEncryption(String keyRef, String algorithm) {
        record("ENCRYPT", keyRef, algorithm, "EncryptedFieldSerializer");
    }

    /**
     * 记录数据导出操作
     *
     * @param fieldName 字段名
     * @param source    导出来源（API名/方法名）
     */
    public void recordExport(String fieldName, String source) {
        record("EXPORT", fieldName, null, source);
    }

    /**
     * 记录密钥轮换操作
     *
     * @param keyRef    密钥引用
     * @param algorithm 加密算法
     */
    public void recordKeyRotation(String keyRef, String algorithm) {
        record("KEY_ROTATION", keyRef, algorithm, "KeyRotationService");
    }

    /**
     * 记录脱敏操作
     *
     * @param strategy 脱敏策略
     * @param source   调用来源
     */
    public void recordMasking(String strategy, String source) {
        record("MASK", strategy, null, source);
    }

    /**
     * 内部记录方法
     */
    private void record(String operation, String keyRef, String algorithm, String source) {
        if (auditQueue.size() >= MAX_QUEUE_SIZE) {
            auditQueue.poll();
            droppedRecords.incrementAndGet();
            log.warn("[DataAudit] 审计队列已满，丢弃最旧记录 (dropped={})", droppedRecords.get());
        }

        auditQueue.offer(new AuditRecord(operation, keyRef, algorithm, source));
        totalRecords.incrementAndGet();
    }

    /**
     * 获取并清空审计队列
     *
     * @return 审计记录列表
     */
    public java.util.List<AuditRecord> drain() {
        java.util.List<AuditRecord> records = new java.util.ArrayList<>();
        AuditRecord record;
        while ((record = auditQueue.poll()) != null) {
            records.add(record);
        }
        return records;
    }

    /**
     * 获取当前队列大小
     *
     * @return 队列大小
     */
    public int getQueueSize() {
        return auditQueue.size();
    }

    /**
     * 获取总审计记录数
     *
     * @return 总记录数
     */
    public long getTotalRecords() {
        return totalRecords.get();
    }

    /**
     * 获取丢弃记录数
     *
     * @return 丢弃记录数
     */
    public long getDroppedRecords() {
        return droppedRecords.get();
    }

    /**
     * 获取审计统计摘要
     *
     * @return 统计摘要文本
     */
    public String getStatsSummary() {
        return String.format(
                "[DataAudit] queueSize=%d, totalRecords=%d, droppedRecords=%d",
                getQueueSize(), getTotalRecords(), getDroppedRecords());
    }
}
