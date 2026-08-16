package com.njydsz.cronjob.domain.dto.alert;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 告警规则创建/更新 DTO（P5 告警 + 监控）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "告警规则表单")
public class AlertRuleSaveDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Schema(description = "规则 ID（更新时必填）")
  private String id;

  @NotBlank(message = "规则名称不能为空")
  @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
  private String ruleName;

  @Schema(description = "关联任务 ID（NULL 表示全局规则）")
  private String jobId;

  @Schema(description = "任务 KEY（冗余，全局规则为 NULL）")
  private String jobKey;

  @NotBlank(message = "告警类型不能为空")
  @Pattern(
      regexp = "^(FAIL|TIMEOUT|SLOW|FAIL_RATE|DURATION_P95)$",
      message = "告警类型必须为 FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 之一")
  @Schema(
      description = "告警类型: FAIL/TIMEOUT/SLOW/FAIL_RATE/DURATION_P95",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String alertType;

  @Pattern(
      regexp = "^(INFO|WARN|ERROR|CRITICAL)$",
      message = "告警级别必须为 INFO / WARN / ERROR / CRITICAL 之一")
  @Schema(description = "告警级别: INFO/WARN/ERROR/CRITICAL（默认 WARN）")
  private String alertLevel;

  @Min(value = 0, message = "阈值必须 >= 0")
  @Schema(description = "阈值（FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒数；FAIL/TIMEOUT 可空）")
  private Long threshold;

  @Min(value = 1, message = "时间窗口必须 > 0")
  @Schema(description = "统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 必填")
  private Integer timeWindowMinutes;

  @NotBlank(message = "通知通道不能为空")
  @Schema(
      description = "通知通道（JSON 数组: [\"EMAIL\",\"DINGTALK\"]）",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String channels;

  @Schema(description = "接收人（JSON 数组: 邮箱/手机号/userId 列表）")
  private String receivers;

  @Min(value = 0, message = "冷却时间必须 >= 0")
  @Schema(description = "冷却时间（分钟），同一规则在冷却期内不重复告警（默认 10）")
  private Integer cooldownMinutes;

  @NotNull(message = "启用状态不能为空")
  @Schema(description = "是否启用: 0 禁用 / 1 启用", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer enabled;
}
