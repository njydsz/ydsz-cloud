package com.njydsz.common.file.storage;

/**
 * 文件存储提供者接口
 *
 * <p>用于获取具体的文件存储实现（Local/MinIO/S3/OSS/COS/OBS/Qiniu）。 支持运行时热切换：调用 {@link #evictStorageCache} 清除缓存后，
 * 下次 {@link #getStorage()} 将重新创建实例，可使用更新的配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface IFileStorageProvider {
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
}
