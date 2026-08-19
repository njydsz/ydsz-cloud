package com.njydsz.agent.api.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DAG 编排请求 DTO
 *
 * <p>封装基于 YAML DSL 的多 Agent DAG 编排执行请求， 支持定义节点间的依赖关系和数据流转。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "DAG 编排请求")
public class DagExecutionDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** YAML DSL 内容，定义 DAG 节点和边（必填） */
  @NotBlank(message = "DSL 内容不能为空")
  @Schema(
      description = "YAML DSL 内容",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example =
          """
          name: project-analysis
          nodes:
            analyze:
              agent-type: CHAT
              prompt: "分析项目"
            report:
              agent-type: CHAT
              prompt: "生成报告"
              input-from: analyze
          edges:
            report:
              - analyze
          """)
  private String dsl;

  /** 用户输入内容（必填） */
  @NotBlank(message = "用户输入不能为空")
  @Schema(description = "用户输入", requiredMode = Schema.RequiredMode.REQUIRED)
  private String userInput;

  /**
   * 续跑的执行 ID（可选）。
   *
   * <p>传入时尝试从已有检查点恢复，跳过已成功的节点，仅重试失败及未执行的节点。 为空时按全新编排执行。
   */
  @Schema(description = "续跑的执行 ID（空则全新执行）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String resumeExecutionId;

  public String getDsl() {
    return dsl;
  }

  public void setDsl(String dsl) {
    this.dsl = dsl;
  }

  public String getUserInput() {
    return userInput;
  }

  public void setUserInput(String userInput) {
    this.userInput = userInput;
  }

  public String getResumeExecutionId() {
    return resumeExecutionId;
  }

  public void setResumeExecutionId(String resumeExecutionId) {
    this.resumeExecutionId = resumeExecutionId;
  }
}
