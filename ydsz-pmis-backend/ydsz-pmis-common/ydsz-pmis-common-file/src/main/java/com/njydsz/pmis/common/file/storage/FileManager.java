package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.file.domain.ListObjectsResult;
import com.njydsz.pmis.common.file.domain.ObjectMetadata;

import java.util.List;

/**
 * 文件管理器接口
 * <p>
 * 定义文件管理相关的核心操作，包括存储桶生命周期（创建、判定存在）、目录管理、
 * 对象元信息获取、对象删除、对象列举等能力。
 * </p>
 *
 * <p><b>目录语义：</b>云对象存储没有真正的"目录"概念，目录通过 Key 前缀 + {@code /} 模拟。
 * {@link #makeFolder} 本质是写入一个 0 字节、以 {@code /} 结尾的对象作为占位。</p>
 *
 * <p><b>批量删除不保证原子性：</b>见 {@link #batchDelete}，部分失败时已成功的对象
 * 不可恢复，业务方需根据 {@code BatchDeleteResult} 进行业务补偿（如触发重试或回滚）。</p>
 *
 * <p><b>分页列举：</b>{@link #listObjects} 通过 cursor（而非 offset）实现分页，
 * 在大数据量下无 OFFSET 性能回退问题。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface FileManager {

    /**
     * 判断指定存储桶是否存在
     *
     * @param bucketName 存储桶名称，传 null 时使用配置默认值
     * @return true 表示桶已存在
     */
    boolean bucketExists(String bucketName);

    /**
     * 创建指定存储桶
     *
     * @param bucketName 存储桶名称，传 null 时使用配置默认值
     */
    void makeBucket(String bucketName);

    /**
     * 判断指定路径是否已存在（通常用于判断"目录"是否已存在）
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径（末尾带 / 表示目录）
     * @return true 表示已存在
     */
    boolean folderExists(String bucketName, String objectName);

    /**
     * 创建目录（本质是在存储层写入一个以 / 结尾的 0 字节对象）
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  目录路径，必须以 / 结尾
     */
    void makeFolder(String bucketName, String objectName);

    /**
     * 判断对象是否存在
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径
     * @return true 表示对象存在
     */
    boolean objectExists(String bucketName, String objectName);

    /**
     * 获取对象元信息
     * <p>返回对象的大小、类型、ETag、最后修改时间等元数据。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径
     * @return 对象元信息，若不存在返回 null
     */
    ObjectMetadata getMetadata(String bucketName, String objectName);

    /**
     * 删除单个文件对象
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  待删除的对象路径
     */
    void delete(String bucketName, String objectName);

    /**
     * 批量删除多个文件对象
     * <p>返回 BatchDeleteResult，包含成功列表和失败列表（含失败原因），不静默吞异常。
     * <p>删除操作不保证原子性，部分失败时已成功删除的文件不可恢复。
     *
     * @param bucketName   存储桶名称，传 null 时使用配置默认值
     * @param objectNames  待删除的对象路径列表
     * @return 批量删除结果，包含成功删除的路径列表和失败路径及原因映射
     */
    com.njydsz.pmis.common.file.domain.BatchDeleteResult batchDelete(String bucketName, List<String> objectNames);

    /**
     * 分页列举对象
     * <p>用于目录遍历、文件列表展示等场景。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param prefix     对象前缀过滤（常用于模拟目录，如 "images/"）
     * @param cursor     分页游标（首次调用传 null，后续调用传上次返回的 nextCursor）
     * @param maxKeys    每页最大返回数量（建议 100-1000）
     * @return 分页结果，包含对象列表和下次继续列举的游标
     */
    ListObjectsResult listObjects(String bucketName, String prefix, String cursor, int maxKeys);

    /**
     * 生成文件预签名 URL（用于临时授权访问）
     * <p>默认实现抛出 UnsupportedOperationException，各云存储实现类按需覆盖。
     *
     * @param bucketName     存储桶名称（为 null 时使用默认配置）
     * @param objectName     对象存储键
     * @param expiryDuration 过期时长
     * @return 预签名 URL
     * @throws UnsupportedOperationException 如果当前存储后端不支持
     */
    default String generatePresignedUrl(String bucketName, String objectName, java.time.Duration expiryDuration) {
        throw new UnsupportedOperationException("Presigned URL not supported by this storage backend");
    }
}
