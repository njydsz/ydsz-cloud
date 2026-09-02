package com.njydsz.cronjob.domain.dto.job;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 任务依赖关系创建/更新 DTO（P4 DAG 工作流）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "任务依赖关系表单")
public class JobRelationSaveDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotBlank(message = "前置任务 ID 不能为空")
  @Schema(description = "前置任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private String parentJobId;

  @NotBlank(message = "后继任务 ID 不能为空")
  @Schema(description = "后继任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  private String childJobId;

  @Pattern(
      regexp = "^(FAIL_FAST|CONTINUE_ON_FAIL)$",
      message = "失败策略必须为 FAIL_FAST / CONTINUE_ON_FAIL 之一")
  @Schema(description = "失败传播策略: FAIL_FAST(默认) / CONTINUE_ON_FAIL")
  private String failStrategy;
}
