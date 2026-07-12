package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.file.domain.UploadCheckpoint;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传检查点服务接口
 * <p>封装检查点的序列化、反序列化、校验、MD5 累积计算等上层业务逻辑，
 * 与底层 {@link CheckpointStore} 分离，使 {@link AbstractFileStorage} 职责更清晰。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface CheckpointService {

    /**
     * 保存检查点（自动序列化 JSON）
     *
     * @param checkpoint 检查点对象
     */
    void saveCheckpoint(UploadCheckpoint checkpoint);

    /**
     * 加载检查点（自动反序列化 JSON）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 检查点对象，不存在时返回 null
     */
    UploadCheckpoint loadCheckpoint(String bucketName, String objectName);

    /**
     * 删除检查点
     *
     * @param checkpoint 检查点对象
     */
    void deleteCheckpoint(UploadCheckpoint checkpoint);

    /**
     * 验证并恢复检查点
     * <p>校验文件大小是否一致、分片是否仍然存在，恢复已上传分片列表。
     *
     * @param checkpoint 检查点对象
     * @param file       上传的文件
     * @return 有效的检查点，若检查点无效则返回 null
     */
    UploadCheckpoint validateAndRecoverCheckpoint(UploadCheckpoint checkpoint, MultipartFile file);

    /**
     * 更新检查点中指定分片的 chunkMd5
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @param partNumber  分片编号
     * @param chunkMd5    分片 MD5
     * @param chunkSize   分片大小
     */
    void updateChunkMd5InCheckpoint(String bucketName, String objectName, int partNumber, String chunkMd5, long chunkSize);

    /**
     * 更新检查点中的累积 MD5
     *
     * @param bucketName     存储桶名称
     * @param objectName     对象名称
     * @param accumulatedMd5 累积 MD5 值
     */
    void updateAccumulatedMd5(String bucketName, String objectName, String accumulatedMd5);

    /**
     * 合并完成后，基于 checkpoint 中的 fileMd5 校验文件完整性
     *
     * @param bucketName      存储桶名称
     * @param objectName      对象名称
     * @param md5CheckEnabled 是否启用 MD5 校验
     * @param md5Computer     MD5 计算函数 (输入流 → MD5 字符串)
     * @param objectDownloader 对象下载函数 (bucket, object → InputStream)
     */
    void validateFileMd5(String bucketName, String objectName, boolean md5CheckEnabled,
                         Md5Computer md5Computer, ObjectDownloader objectDownloader);

    /**
     * 获取检查点 TTL（秒）
     *
     * @return TTL 秒数
     */
    long getCheckpointTtlSeconds();

    /**
     * MD5 计算函数接口
     */
    @FunctionalInterface
    interface Md5Computer {
        String compute(java.io.InputStream inputStream);
    }

    /**
     * 对象下载函数接口
     */
    @FunctionalInterface
    interface ObjectDownloader {
        java.io.InputStream download(String bucketName, String objectName) throws Exception;
    }
}
