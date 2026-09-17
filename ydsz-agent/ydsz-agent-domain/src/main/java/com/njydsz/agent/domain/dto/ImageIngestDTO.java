package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 图片摄入请求 DTO
 *
 * <p>封装图片文件的摄入请求，图片经 OCR 提取文字后存入向量库。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "图片摄入请求")
public class ImageIngestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 图片文件内容的 Base64 编码 */
  @NotBlank(message = "图片内容不能为空")
  @Schema(description = "图片 Base64 编码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String base64Content;

  /** 图片格式（PNG/JPEG/BMP/TIFF） */
  @NotNull(message = "图片格式不能为空")
  @Schema(description = "图片格式", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PNG", "JPEG", "BMP", "TIFF"})
  private String format;

  /** 数据集 ID */
  @Schema(description = "数据集 ID")
  private String datasetId;
}
