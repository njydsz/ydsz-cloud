package com.njydsz.agent.api.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档摄入请求 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "文档摄入请求")
public class DocumentIngestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "文档 ID 不能为空")
    @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentId;

    @NotBlank(message = "文档内容不能为空")
    @Schema(description = "文档文本内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "文档标题")
    private String documentTitle;

    @Schema(description = "来源（nextwiki/project/contract）")
    private String source;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
