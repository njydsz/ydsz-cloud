package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * P2-3: 附件在线预览 VO
 *
 * <p>根据文件类型返回不同的预览策略：
 * <ul>
 *   <li>{@code previewType=IMAGE/PDF/VIDEO/TEXT} → {@code previewUrl} 即 {@code downloadUrl}，
 *       前端原生标签（img/iframe/video/pre）直接渲染</li>
 *   <li>{@code previewType=OFFICE} → {@code previewUrl} 为外部预览服务 URL
 *      （kkFileView/Office Online），需配置 {@code workflow.attachment.preview-server-url}</li>
 *   <li>{@code previewType=UNSUPPORTED} → 不支持在线预览，前端引导下载</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAttachmentPreviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 附件 ID */
    private String attachmentId;

    /** 原始文件名 */
    private String fileName;

    /** 文件扩展名（小写，无点号） */
    private String fileExt;

    /** MIME 类型 */
    private String contentType;

    /** 预览类型：IMAGE / PDF / VIDEO / TEXT / OFFICE / UNSUPPORTED */
    private String previewType;

    /** 预览 URL（IMAGE/PDF/VIDEO/TEXT 即 downloadUrl；OFFICE 为外部预览服务 URL；UNSUPPORTED 为 downloadUrl） */
    private String previewUrl;

    /** 下载 URL（始终提供，前端可降级为下载） */
    private String downloadUrl;

    /** 是否支持在线预览（false 时前端应引导下载） */
    private boolean previewable;
}
