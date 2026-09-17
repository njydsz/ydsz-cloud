package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 语义 Tool 搜索请求 DTO
 *
 * <p>封装语义搜索的查询参数，包含自然语言查询文本与返回数量上限。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "语义 Tool 搜索请求")
public class ToolSearchRequestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 自然语言查询文本 */
  @Schema(description = "自然语言查询文本", example = "查询天气的工具")
  private String query;

  /** 返回数量上限（默认 5，最大 20） */
  @Schema(description = "返回数量上限（默认 5，最大 20）", example = "5")
  private Integer topK = 5;
}
