package com.njydsz.common.file.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分片上传上下文存储接口
 *
 * <p>支持多实例部署时分片上下文共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MultipartContextStore {

  /**
   * 保存分片上传上下文
   *
   * @param uploadId 分片上传 ID
   * @param context 分片上下文
   * @param ttlSeconds 过期时间（秒）
   */
  void save(String uploadId, MultipartContextData context, long ttlSeconds);

  /**
   * 获取分片上传上下文
   *
   * @param uploadId 分片上传 ID
   * @return 分片上下文，不存在时返回 null
   */
  MultipartContextData get(String uploadId);

  /**
   * 删除分片上传上下文
   *
   * @param uploadId 分片上传 ID
   */
  void remove(String uploadId);

  /**
   * 获取所有分片上传上下文（用于定时清理）
   *
   * @return 所有分片上下文映射
   */
  Map<String, MultipartContextData> getAll();

  /**
   * 清理过期的分片上传上下文
   *
   * @param timeoutMinutes 超时时间（分钟），超过此时间未更新的上下文将被清理
   */
  void cleanExpired(int timeoutMinutes);

  /**
   * 分片上下文数据（可序列化）。
   *
   * @param uploadId 分片上传 ID
   * @param bucketName 存储桶名
   * @param objectName 对象名
   * @param partChunkNames 分片序号 → 分片块名映射
   * @param createTime 创建时间戳（毫秒）
   * @param lastAccessTime 最后访问时间戳（毫秒）
   */
  record MultipartContextData(
      String uploadId,
      String bucketName,
      String objectName,
      Map<Integer, String> partChunkNames,
      long createTime,
      long lastAccessTime) {
    public MultipartContextData(String uploadId, String bucketName, String objectName) {
      this(
          uploadId,
          bucketName,
          objectName,
          new ConcurrentHashMap<>(),
          System.currentTimeMillis(),
          System.currentTimeMillis());
    }
  }
}
