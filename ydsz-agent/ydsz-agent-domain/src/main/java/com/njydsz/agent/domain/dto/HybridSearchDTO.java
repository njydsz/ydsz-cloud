package com.njydsz.agent.domain.dto;


import com.njydsz.common.safe.annotation.Xss;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 混合搜索请求 DTO
 *
 * <p>封装融合知识库 RAG 检索和 Web 搜索的请求参数。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "混合搜索请求")
public class HybridSearchDTO {


  /** 查询文本（必填） */
  @NotBlank(message = "查询内容不能为空")
  @Schema(description = "查询文本", requiredMode = Schema.RequiredMode.REQUIRED)
  @Xss(message = "查询内容包含非法字符")
  private String query;

  /** 数据集 ID（可选，为 null 时仅执行 web 搜索） */
  @Schema(description = "数据集 ID")
  private String datasetId;

  /** 返回前 K 条结果（默认 5） */
  @Schema(description = "返回前 K 条（默认 5）")
  private Integer topK;
}
