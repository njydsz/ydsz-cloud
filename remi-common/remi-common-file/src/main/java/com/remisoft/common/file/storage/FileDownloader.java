package com.remisoft.common.file.storage;

import java.io.InputStream;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件下载器接口
 * <p>
 * 定义文件下载相关的核心操作，支持完整下载、范围下载（断点续传）、流式下载、
 * 公开/私有访问地址生成、预签名 URL 生成等能力。
 * </p>
 *
 * <p><b>安全约束：</b></p>
 * <ul>
 *   <li>公开 URL（{@link #getPublicUrl}）会暴露文件地址，应仅用于公开资源（CDN 静态资源）</li>
 *   <li>私有 URL（{@link #getPrivateUrl}）和预签名 URL（{@link #generatePresignedUrl}）有过期时间，
 *       用于受控访问，避免文件被永久外链</li>
 *   <li>下载时建议通过 {@code Content-Disposition} 头指定下载文件名，避免 XSS（不要直接用 objectName）</li>
 * </ul>
 *
 * <p><b>性能要点：</b></p>
 * <ul>
 *   <li>范围下载（{@link #download(..., Long, Long)}）实现 {@code Range} 协议，
 *       支持客户端断点续传与多线程下载</li>
 *   <li>流式下载（{@link #downloadAsStream}）适合作为图片代理、文件预览等业务内嵌场景</li>
 *   <li>大文件下载应在响应头中显式设置 {@code Content-Length}，避免客户端按 chunk 解析</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface FileDownloader {

    /**
     * 下载文件（完整内容）
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  待下载的对象路径
     * @param response    HttpServletResponse，模块内部直接向输出流写入字节
     */
    void download(String bucketName, String objectName, HttpServletResponse response);

    /**
     * 范围下载（支持断点续传、视频点播等场景）
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  待下载的对象路径
     * @param response    HttpServletResponse
     * @param offset      起始字节偏移量（从 0 开始），传 null 表示从 0 开始
     * @param length      请求的字节长度，传 null 表示读取到文件末尾
     */
    void download(String bucketName, String objectName, HttpServletResponse response, Long offset, Long length);

    /**
     * 获取文件公开访问地址
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径
     * @return 公开访问 URL（若未配置 domain 则返回云厂商默认地址）
     */
    String getPublicUrl(String bucketName, String objectName);

    /**
     * 获取文件私有签名地址（临时访问令牌）
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径
     * @return 私有签名 URL，仅在有效期内可访问；若存储类型不支持私有签名则抛出 F05001 异常
     */
    String getPrivateUrl(String bucketName, String objectName);

    InputStream downloadAsStream(String bucketName, String objectName);

    /**
     * 生成文件预签名 URL（临时访问令牌）
     *
     * <p>与 {@link #getPrivateUrl} 的区别在于此方法允许自定义过期时间，
     * 适用于需要灵活控制临时访问有效期的场景。
     *
     * @param objectKey     对象存储键（objectKey）
     * @param expireSeconds 过期时间（秒），最小 1 秒
     * @return 预签名 URL，在有效期内可直接用于访问文件
     */
    String generatePresignedUrl(String objectKey, int expireSeconds);

    /**
     * 生成文件预签名 URL（临时访问令牌，指定存储桶）
     *
     * @param bucketName    存储桶名称，传 null 时使用配置默认值
     * @param objectKey     对象存储键（objectKey）
     * @param expireSeconds 过期时间（秒），最小 1 秒
     * @return 预签名 URL，在有效期内可直接用于访问文件
     */
    String generatePresignedUrl(String bucketName, String objectKey, int expireSeconds);
}
