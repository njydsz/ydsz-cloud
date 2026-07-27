package com.njydsz.cronjob.domain.dto.post;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * AlertRule 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AlertRulePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "规则名称不能为空")
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    @Schema(description = "关联任务 ID（NULL 表示全局规则）")
    private String jobId;

    @Schema(description = "任务 KEY（冗余，全局规则为 NULL）")
    private String jobKey;

    private String alertType;

    @Schema(description = "告警级别: INFO/WARN/ERROR/CRITICAL（默认 WARN）")
    private String alertLevel;

    @Min(value = 0, message = "阈值必须 >= 0")
    @Schema(description = "阈值（FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒数；FAIL/TIMEOUT 可空）")
    private Long threshold;

    @Min(value = 1, message = "时间窗口必须 > 0")
    @Schema(description = "统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 必填")
    private Integer timeWindowMinutes;

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