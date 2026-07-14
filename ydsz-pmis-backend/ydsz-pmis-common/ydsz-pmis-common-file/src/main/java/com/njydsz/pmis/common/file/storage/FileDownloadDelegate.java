package com.njydsz.pmis.common.file.storage;

import java.io.InputStream;
import java.io.OutputStream;

import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件下载委托类
 *
 * <p>从 {@link AbstractFileStorage} 中提取的下载相关操作，
 * 包括文件下载、URL 生成等。
 *
 * <p>通过持有 {@link AbstractFileStorage} 实例来访问基类的
 * protected 方法和抽象方法（如 resolveBucketName、doGetObject 等）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class FileDownloadDelegate {

    /** 文件存储抽象层实例，用于访问基类的 protected 方法和抽象方法 */
    private final AbstractFileStorage storage;

    /**
     * 构造文件下载委托类
     *
     * @param storage 文件存储抽象层实例
     */
    public FileDownloadDelegate(AbstractFileStorage storage) {
        this.storage = storage;
    }

    /**
     * 下载文件到 HTTP 响应（全量下载）
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径）
     * @param response   HTTP 响应对象
     * @throws BusinessException 下载失败时抛出
     */
    public void download(String bucketName, String objectName, HttpServletResponse response) {
        download(bucketName, objectName, response, null, null);
    }

    /**
     * 下载文件到 HTTP 响应（支持范围下载）
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径）
     * @param response   HTTP 响应对象
     * @param offset     起始偏移量（字节），为 null 时从 0 开始
     * @param length     下载长度（字节），为 null 时下载到文件末尾
     * @throws BusinessException 下载失败时抛出
     */
    public void download(String bucketName, String objectName, HttpServletResponse response, Long offset, Long length) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        try (InputStream is = storage.doGetObject(resolvedBucket, resolvedKey, offset, length);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            log.info("[Storage] file download success, bucket={}, object={}", resolvedBucket, resolvedKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file download failed, bucket={}, object={}, error={}", resolvedBucket, resolvedKey, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 获取文件的公开访问 URL
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径）
     * @return 公开访问 URL
     */
    public String getPublicUrl(String bucketName, String objectName) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);
        return storage.buildObjectUrl(resolvedBucket, resolvedKey);
    }

    /**
     * 获取文件的私有访问 URL（带签名，有时效限制）
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径）
     * @return 带签名的私有访问 URL
     */
    public String getPrivateUrl(String bucketName, String objectName) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);
        return storage.buildPrivateUrl(resolvedBucket, resolvedKey);
    }

    /**
     * 以流的方式下载文件
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径）
     * @return 文件输入流，调用方负责关闭
     * @throws BusinessException 下载失败时抛出
     */
    public InputStream downloadAsStream(String bucketName, String objectName) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);
        try {
            return storage.doGetObject(resolvedBucket, resolvedKey, null, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] downloadAsStream failed, bucket={}, object={}, error={}", resolvedBucket, resolvedKey, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }
}
