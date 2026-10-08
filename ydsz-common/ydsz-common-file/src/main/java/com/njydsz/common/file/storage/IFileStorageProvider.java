package com.njydsz.common.file.storage;

/**
 * 文件存储提供者接口
 *
 * <p>用于获取具体的文件存储实现（Local/MinIO/S3/OSS/COS/OBS/Qiniu）。 支持运行时热切换：调用 {@link #evictStorageCache} 清除缓存后，
 * 下次 {@link #getStorage()} 将重新创建实例，可使用更新的配置。
 *
 * <p>同时提供<b>分片上传</b>（Multipart Upload）能力的三步式 API，供需要大文件分片上传的场景直接调用， 无需业务层自行管理临时状态。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface IFileStorageProvider {

  /** 默认分片大小（5MB），与 S3 协议对齐 */
  int DEFAULT_PART_SIZE = 5 * 1024 * 1024;

  /**
   * 获取文件存储实现类
   *
   * @return 文件存储实现类
   */
  IFileStorage getStorage();

  /**
   * 清除指定存储类型的缓存实例
   *
   * <p>清除后，下次 {@link #getStorage()} 调用将重新根据当前配置创建新的存储实例。 用于运行时热切换存储后端（如从 local 切换到 minio）场景。
   *
   * @param storageType 存储类型标识（如 "minio"、"local"），传 null 时清除当前配置的存储类型缓存
   * @return 被清除的实例，若无缓存则返回 null
   */
  default IFileStorage evictStorageCache(String storageType) {
    // 默认无缓存实现，不做任何操作
    return null;
  }

  /**
   * 清除全部缓存的存储实例
   *
   * <p>用于运维场景下强制重置所有存储后端（如配置全面刷新）。
   */
  default void clearStorageCache() {
    // 默认无缓存实现，不做任何操作
  }

  // ==================== 分片上传（Multipart Upload） ====================

  /**
   * 初始化分片上传，返回 uploadId。
   *
   * <p>调用成功后返回全局唯一的 {@code uploadId}，调用方需保存此 uploadId 并在后续 {@link #uploadPart} / {@link
   * #completeMultipartUpload} 时传入。 推荐的单分片大小为 {@link #DEFAULT_PART_SIZE}（5MB），调用方可据此决定分片策略。
   *
   * @param bucket 存储桶名称，传 null 时使用配置默认值
   * @param key 对象存储键（即文件在存储层中的路径）
   * @param contentType 文件 MIME 类型（如 "application/octet-stream"），完成上传时以此为准
   * @return 分片上传任务唯一标识 uploadId
   * @throws com.njydsz.common.exception.custom.BusinessException 存储未配置或初始化失败时抛出
   */
  String initiateMultipartUpload(String bucket, String key, String contentType);

  /**
   * 上传一个分片（partNumber 从 1 开始）。
   *
   * <p>分片编号 {@code partNumber} 必须为正整数且在同一次分片任务内保持唯一。 所有分片（除最后一个外）大小应不小于 5MB（以兼容 S3 协议）， 推荐使用 {@link
   * #DEFAULT_PART_SIZE} 作为分片大小。 不同分片可以并行上传，实现类内部保证写入幂等性（同一 partNumber 重复上传覆盖）。
   *
   * @param bucket 存储桶名称，传 null 时使用配置默认值
   * @param key 对象存储键（必须与 {@link #initiateMultipartUpload} 时一致）
   * @param uploadId {@link #initiateMultipartUpload} 返回的上传任务 ID
   * @param partNumber 分片编号（从 1 开始）
   * @param data 分片二进制数据
   * @throws com.njydsz.common.exception.custom.BusinessException 会话不存在、分片编号非法或上传失败时抛出
   */
  void uploadPart(String bucket, String key, String uploadId, int partNumber, byte[] data);

  /**
   * 完成分片上传，将所有已上传分片合并为最终对象。
   *
   * <p>自动列举该 uploadId 下所有已上传的分片，按分片编号升序合并。 合并成功后云端自动清理分片中间数据，并返回对象的访问 URL。
   *
   * @param bucket 存储桶名称，传 null 时使用配置默认值
   * @param key 对象存储键（必须与 {@link #initiateMultipartUpload} 时一致）
   * @param uploadId {@link #initiateMultipartUpload} 返回的上传任务 ID
   * @throws com.njydsz.common.exception.custom.BusinessException 分片不完整、合并失败或存储未配置时抛出
   */
  void completeMultipartUpload(String bucket, String key, String uploadId);
}
