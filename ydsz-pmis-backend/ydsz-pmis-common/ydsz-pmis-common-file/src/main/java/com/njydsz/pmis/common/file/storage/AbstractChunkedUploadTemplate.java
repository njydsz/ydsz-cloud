package com.njydsz.pmis.common.file.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分片上传模板抽象类
 *
 * <p>提取分片上传初始化、断点续传、进度管理的通用逻辑，
 * 具体存储实现只需实现断点检查点保存/加载的抽象方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public abstract class AbstractChunkedUploadTemplate {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final long checkpointTtlMillis;

    protected AbstractChunkedUploadTemplate(long checkpointTtlMillis) {
        this.checkpointTtlMillis = checkpointTtlMillis;
    }

    /**
     * 初始化分片上传并生成断点检查点
     *
     * @param bucketName   存储桶名称
     * @param objectName   对象名称
     * @param uploadId     分片上传 ID
     * @param partSize     分片大小（字节）
     * @param totalSize    总文件大小（字节）
     * @return 分片上传初始化结果
     */
    public ChunkedUploadResult initChunkedUpload(String bucketName, String objectName,
                                                  String uploadId, long partSize, long totalSize) {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("文件大小必须大于 0");
        }
        if (partSize <= 0) {
            throw new IllegalArgumentException("分片大小必须大于 0");
        }

        int totalParts = (int) Math.ceil((double) totalSize / partSize);
        long expiresAt = System.currentTimeMillis() + checkpointTtlMillis;

        saveCheckpoint(bucketName, objectName, uploadId, partSize, totalSize, totalParts, 0, expiresAt);

        log.info("[ChunkedUpload] init, bucket={}, object={}, uploadId={}, totalSize={}, partSize={}, totalParts={}",
                bucketName, objectName, uploadId, totalSize, partSize, totalParts);

        return ChunkedUploadResult.init(uploadId, totalParts, partSize, totalSize);
    }

    /**
     * 加载断点续传检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param uploadId   分片上传 ID
     * @return 检查点信息，不存在返回 null
     */
    public ChunkedUploadCheckpoint loadCheckpoint(String bucketName, String objectName, String uploadId) {
        return doLoadCheckpoint(bucketName, objectName, uploadId);
    }

    /**
     * 保存断点续传检查点（子类实现）
     *
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param uploadId      分片上传 ID
     * @param partSize      分片大小
     * @param totalSize     总文件大小
     * @param totalParts    总分片数
     * @param completedParts 已完成的分片数
     * @param expiresAt     过期时间戳
     */
    protected abstract void saveCheckpoint(String bucketName, String objectName, String uploadId,
                                            long partSize, long totalSize, int totalParts,
                                            int completedParts, long expiresAt);

    /**
     * 加载断点续传检查点（子类实现）
     */
    protected abstract ChunkedUploadCheckpoint doLoadCheckpoint(String bucketName, String objectName, String uploadId);

    /**
     * 删除断点续传检查点（子类实现）
     */
    protected abstract void deleteCheckpoint(String bucketName, String objectName, String uploadId);

    /**
     * 分片上传初始化结果
     */
    public static class ChunkedUploadResult {
        private final String uploadId;
        private final int totalParts;
        private final long partSize;
        private final long totalSize;

        private ChunkedUploadResult(String uploadId, int totalParts, long partSize, long totalSize) {
            this.uploadId = uploadId;
            this.totalParts = totalParts;
            this.partSize = partSize;
            this.totalSize = totalSize;
        }

        public static ChunkedUploadResult init(String uploadId, int totalParts, long partSize, long totalSize) {
            return new ChunkedUploadResult(uploadId, totalParts, partSize, totalSize);
        }

        public String getUploadId() { return uploadId; }
        public int getTotalParts() { return totalParts; }
        public long getPartSize() { return partSize; }
        public long getTotalSize() { return totalSize; }
    }

    /**
     * 断点续传检查点信息
     */
    public static class ChunkedUploadCheckpoint {
        private final String uploadId;
        private final long partSize;
        private final long totalSize;
        private final int totalParts;
        private final int completedParts;
        private final long expiresAt;

        public ChunkedUploadCheckpoint(String uploadId, long partSize, long totalSize,
                                        int totalParts, int completedParts, long expiresAt) {
            this.uploadId = uploadId;
            this.partSize = partSize;
            this.totalSize = totalSize;
            this.totalParts = totalParts;
            this.completedParts = completedParts;
            this.expiresAt = expiresAt;
        }

        public String getUploadId() { return uploadId; }
        public long getPartSize() { return partSize; }
        public long getTotalSize() { return totalSize; }
        public int getTotalParts() { return totalParts; }
        public int getCompletedParts() { return completedParts; }
        public long getExpiresAt() { return expiresAt; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
