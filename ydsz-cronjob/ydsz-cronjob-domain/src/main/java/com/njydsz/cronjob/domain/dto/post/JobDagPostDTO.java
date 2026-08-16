package com.njydsz.cronjob.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * JobDag 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagPostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotBlank(message = "{validation.cronjob.msg_dag_key_required}")
  @Schema(description = "DAG 唯一 KEY（调度与触发使用）", requiredMode = Schema.RequiredMode.REQUIRED)
  private String dagKey;

  @NotBlank(message = "{validation.cronjob.msg_dag_name_required}")
  @Schema(description = "DAG 名称（展示用）", requiredMode = Schema.RequiredMode.REQUIRED)
  private String dagName;

  @Schema(description = "DAG 定义 JSON（nodes + edges + 可视化坐标）")
  private String dagDefinition;

  @Schema(description = "DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用")
  private String status;

  @Schema(description = "触发类型: MANUAL 手动 / CRON 定时")
  private String triggerType;

  @Schema(description = "Cron 表达式（triggerType=CRON 时必填）")
  private String cronExpression;

  @Min(value = 0, message = "最大并发实例数必须 >= 0")
  @Schema(description = "最大并发实例数(0=不限制, 默认1)")
  private Integer maxConcurrentInstances;

  @Schema(description = "DAG 级失败策略: FAIL_FAST 中止 / CONTINUE_ON_FAIL 继续")
  private String failStrategy;

  @Schema(description = "DAG 描述")
  private String description;
}
