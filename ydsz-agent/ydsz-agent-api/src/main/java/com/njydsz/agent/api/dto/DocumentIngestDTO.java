package com.njydsz.agent.api.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 文档摄入请求 DTO
 *
 * <p>封装将文档内容写入 RAG 知识库的请求参数， 支持来自 nextwiki、project、contract 等不同来源的文档。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "文档摄入请求")
public class DocumentIngestDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文档 ID（必填，唯一标识待摄入的文档） */
  @NotBlank(message = "文档 ID 不能为空")
  @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private String documentId;

  /** 文档文本内容（必填，将被分块、向量化和索引） */
  @NotBlank(message = "文档内容不能为空")
  @Schema(description = "文档文本内容", requiredMode = Schema.RequiredMode.REQUIRED)
  private String content;

  /** 文档标题（可选，用于展示和检索） */
  @Schema(description = "文档标题")
  private String documentTitle;

  /** 文档来源（nextwiki/project/contract，用于来源过滤） */
  @Schema(description = "来源（nextwiki/project/contract）")
  private String source;

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getDocumentTitle() {
    return documentTitle;
  }

  public void setDocumentTitle(String documentTitle) {
    this.documentTitle = documentTitle;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
