package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DAG 工作流保存 DTO。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "DAG 工作流保存请求")
public class DagWorkflowDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 工作流编码（唯一，更新时必填） */
  @Schema(description = "工作流编码（唯一标识，为空则自动生成）")
  private String workflowCode;

  /** 工作流名称（必填） */
  @NotBlank(message = "工作流名称不能为空")
  @Schema(description = "工作流名称", requiredMode = Schema.RequiredMode.REQUIRED)
  private String workflowName;

  /** 工作流描述 */
  @Schema(description = "工作流描述")
  private String description;

  /** YAML DSL 内容（必填） */
  @NotBlank(message = "DSL 内容不能为空")
  @Schema(description = "YAML DSL 内容", requiredMode = Schema.RequiredMode.REQUIRED)
  private String dslContent;

  /** 可视化布局 JSON（节点坐标等前端状态，可选） */
  @Schema(description = "可视化布局 JSON（节点坐标等前端状态）")
  private String layoutJson;

  /** 分类标签 */
  @Schema(description = "分类标签")
  private String category;
}
