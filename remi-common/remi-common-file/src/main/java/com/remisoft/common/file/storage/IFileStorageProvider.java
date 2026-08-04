package com.remisoft.common.file.storage;

/**
 * 文件存储提供者接口
 * <p>用于获取具体的文件存储实现（Local/MinIO/S3/OSS/COS/OBS/Qiniu）。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface IFileStorageProvider {
    /**
     * 获取文件存储实现类
     *
     * @return 文件存储实现类
     */
    IFileStorage getStorage();
}
