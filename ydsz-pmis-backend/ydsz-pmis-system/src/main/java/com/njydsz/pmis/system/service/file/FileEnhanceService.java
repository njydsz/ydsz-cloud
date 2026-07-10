package com.njydsz.pmis.system.service.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件增强服务接口。
 *
 * <p>提供病毒扫描、分片上传、文件类型校验、在线预览能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FileEnhanceService {

    /**
     * 文件类型白名单校验。
     *
     * @param filename    文件名
     * @param contentType MIME 类型
     * @return true 表示通过校验
     */
    boolean validateFileType(String filename, String contentType);

    /**
     * 文件大小校验。
     *
     * @param fileSize 文件大小（字节）
     * @param maxSize  最大允许大小（字节）
     * @return true 表示通过校验
     */
    boolean validateFileSize(long fileSize, long maxSize);

    /**
     * 模拟病毒扫描（实际环境对接 ClamAV）。
     *
     * @param file 上传文件
     * @return true 表示文件安全
     */
    boolean scanVirus(MultipartFile file);

    /**
     * 初始化分片上传，返回 uploadId。
     *
     * @param filename    文件名
     * @param totalSize   文件总大小（字节）
     * @param totalChunks 分片总数
     * @return uploadId
     */
    String initMultipartUpload(String filename, long totalSize, int totalChunks);

    /**
     * 上传分片。
     *
     * @param uploadId   分片上传 ID
     * @param chunkIndex 分片序号（从 0 开始）
     * @param chunkData  分片数据
     * @return true 表示上传成功
     */
    boolean uploadChunk(String uploadId, int chunkIndex, byte[] chunkData);

    /**
     * 合并所有分片完成上传。
     *
     * @param uploadId 分片上传 ID
     * @return 文件 key，合并失败返回 null
     */
    String completeMultipartUpload(String uploadId);

    /**
     * 取消分片上传。
     *
     * @param uploadId 分片上传 ID
     */
    void abortMultipartUpload(String uploadId);

    /**
     * 生成在线预览 URL。
     *
     * @param fileKey 文件 key
     * @return 预览 URL
     */
    String generatePreviewUrl(String fileKey);
}
