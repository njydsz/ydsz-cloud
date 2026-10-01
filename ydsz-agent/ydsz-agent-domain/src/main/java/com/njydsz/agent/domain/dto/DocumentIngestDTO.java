package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.safe.annotation.Xss;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档摄入请求 DTO
 *
 * <p>封装将文档内容写入 RAG 知识库的请求参数， 支持来自 nextwiki、project、contract 等不同来源的文档。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@Schema(description = "文档摄入请求")
public class DocumentIngestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 文档 ID（必填，唯一标识待摄入的文档） */
  @NotBlank(message = "文档 ID 不能为空")
  @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private String documentId;

  /** 文档文本内容（必填，将被分块、向量化和索引） */
  @NotBlank(message = "文档内容不能为空")
  @Schema(description = "文档文本内容", requiredMode = Schema.RequiredMode.REQUIRED)
  @Xss(message = "文档内容包含非法字符")
  private String content;

  /** 文档标题（可选，用于展示和检索） */
  @Schema(description = "文档标题")
  @Xss(message = "文档标题包含非法字符")
  private String documentTitle;

  /** 文档来源（nextwiki/project/contract，用于来源过滤） */
  @Schema(description = "来源（nextwiki/project/contract）")
  private String source;
}
