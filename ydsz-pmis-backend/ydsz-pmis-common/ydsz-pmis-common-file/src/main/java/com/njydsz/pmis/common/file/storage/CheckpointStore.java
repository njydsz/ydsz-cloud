package com.njydsz.pmis.common.file.storage;

/**
 * 检查点存储接口
 * <p>支持多实例部署时断点续传检查点共享。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public interface CheckpointStore {

    /**
     * 保存检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param checkpoint 检查点数据（JSON 字符串）
     * @param ttlSeconds 过期时间（秒）
     */
    void save(String bucketName, String objectName, String checkpoint, long ttlSeconds);

    /**
     * 获取检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 检查点数据，不存在时返回 null
     */
    String get(String bucketName, String objectName);

    /**
     * 删除检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     */
    void remove(String bucketName, String objectName);

    /**
     * 构建检查点存储键
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 存储键
     */
    String buildKey(String bucketName, String objectName);
}
