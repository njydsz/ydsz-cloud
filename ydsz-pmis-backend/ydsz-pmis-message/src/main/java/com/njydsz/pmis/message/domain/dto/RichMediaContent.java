package com.njydsz.pmis.message.domain.dto.core;

import lombok.Data;

import java.util.List;

/**
 * P1-2: 富媒体消息内容模型。
 *
 * <p>支持在消息中嵌入图片、音频、视频、文件等富媒体内容，
 * 适配多通道（邮件HTML、站内信富文本、推送卡片、IM卡片消息等）。
 *
 * <p>使用方式：在 {@code MessageRequest.params} 中设置 {@code _richMedia} 键，
 * 值为 {@link RichMediaContent} 的 JSON 序列化结果。
 * 通道实现按需解析并渲染为通道特定的富媒体格式。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class RichMediaContent {

    /** 消息标题（富媒体卡片标题） */
    private String title;

    /** 纯文本摘要（不支持富媒体的通道降级使用） */
    private String summary;

    /** 富文本/HTML 正文（EMAIL 通道直接使用） */
    private String htmlContent;

    /** Markdown 正文（站内信/IM 通道渲染） */
    private String markdownContent;

    /** 附件列表 */
    private List<Attachment> attachments;

    /** 图片列表（URL，用于推送大图、邮件图片等） */
    private List<String> images;

    /** 操作按钮列表（推送卡片/站内信操作按钮） */
    private List<ActionButton> buttons;

    /** 跳转链接 */
    private String actionUrl;

    /**
     * 附件模型。
     */
    @Data
    public static class Attachment {
        /** 文件名 */
        private String filename;
        /** 文件 URL */
        private String url;
        /** MIME 类型（如 image/png, application/pdf） */
        private String mimeType;
        /** 文件大小（字节，可选） */
        private Long size;
    }

    /**
     * 操作按钮模型。
     */
    @Data
    public static class ActionButton {
        /** 按钮文案 */
        private String text;
        /** 按钮动作类型: OPEN_URL(打开链接) / OPEN_APP(打开应用) / COPY(复制) / DISMISS(关闭) */
        private String actionType;
        /** 动作参数（如 URL、复制内容等） */
        private String actionValue;
    }
}
