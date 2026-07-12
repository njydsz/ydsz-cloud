package com.njydsz.pmis.common.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储抽象层
 *
 * <p>统一文件存储接口，支持 MinIO/OSS/COS/S3 等多种存储后端。
 * 业务模块注入此接口，无需关心底层存储实现。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface StorageService {

    /**
     * 上传文件
     *
     * @param bucketName  存储桶
     * @param objectName  对象名（含路径）
     * @param inputStream 文件流
     * @param contentType 内容类型
     * @return 访问 URL
     */
    String upload(String bucketName, String objectName, InputStream inputStream, String contentType);

    /**
     * 下载文件
     *
     * @param bucketName 存储桶
     * @param objectName 对象名
     * @return 文件流
     */
    InputStream download(String bucketName, String objectName);

    /**
     * 删除文件
     *
     * @param bucketName 存储桶
     * @param objectName 对象名
     */
    void delete(String bucketName, String objectName);

    /**
     * 判断文件是否存在
     *
     * @param bucketName 存储桶
     * @param objectName 对象名
     * @return true 表示存在
     */
    boolean exists(String bucketName, String objectName);

    /**
     * 列举存储桶中的文件
     *
     * @param bucketName 存储桶
     * @param prefix     前缀
     * @return 对象名列表
     */
    List<String> list(String bucketName, String prefix);

    /**
     * 获取预签名 URL（临时访问）
     *
     * @param bucketName 存储桶
     * @param objectName 对象名
     * @param expiry     有效期（秒）
     * @return 预签名 URL
     */
    String getPresignedUrl(String bucketName, String objectName, int expiry);
}
