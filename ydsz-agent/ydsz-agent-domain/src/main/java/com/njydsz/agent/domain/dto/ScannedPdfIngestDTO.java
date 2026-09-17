package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 扫描版 PDF 摄入请求 DTO
 *
 * <p>封装扫描版 PDF 文档的摄入请求，文件内容以 Base64 编码传输，
 * 服务端调用 OCR 引擎逐页提取文字后走正常 ingestion 流程。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "扫描版 PDF 摄入请求")
public class ScannedPdfIngestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 文件名（含后缀，用于格式检测和索引标识） */
  @NotBlank(message = "文件名不能为空")
  @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String fileName;

  /** PDF 文件内容的 Base64 编码 */
  @NotBlank(message = "文件内容不能为空")
  @Schema(description = "PDF 文件 Base64 编码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String base64Content;

  /** 数据集 ID（可选，用于分组管理） */
  @Schema(description = "数据集 ID")
  private String datasetId;
}
