package com.njydsz.common.file.storage;

/**
 * 文件存储提供者接口
 *
 * <p>用于获取具体的文件存储实现（Local/MinIO/S3/OSS/COS/OBS/Qiniu）。
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
}
