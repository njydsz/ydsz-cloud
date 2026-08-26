package com.njydsz.cronjob.domain.dto.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务批量修改 DTO（P1-13）。
 *
 * <p>支持批量修改任务的分组、Cron 表达式。字段为可选，仅修改非空字段（null 表示不修改）。
 *
 * <p>当 {@code cronExpression} 非空时，自动校验 Cron 表达式合法性并重新注册调度器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "任务批量修改 DTO（分组 / Cron）")
public class JobBatchUpdateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotEmpty(message = "任务 ID 列表不能为空")
  @Size(max = 100, message = "批量操作上限 100 条")
  @Schema(description = "任务 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
  private List<String> jobIds;

  @Schema(description = "新任务分组（null 表示不修改）")
  private String jobGroup;

  @Schema(description = "新 Cron 表达式（null 表示不修改；非空时会校验合法性并重新注册调度器）")
  private String cronExpression;
}
