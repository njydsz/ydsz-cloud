package com.njydsz.pmis.cronjob.dto.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SLA 规则创建/更新 DTO（P2-7 SLA 管理）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "SLA 规则表单")
public class JobSlaSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "SLA 规则 ID（更新时必填）")
    private String id;

    @NotBlank(message = "任务 ID 不能为空")
    @Schema(description = "任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobId;

    @NotBlank(message = "任务 KEY 不能为空")
    @Schema(description = "任务 KEY（冗余）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobKey;

    @Min(value = 1, message = "最大执行时长必须大于 0")
    @Schema(description = "最大执行时长（毫秒），超过则违约；不填表示不检查")
    private Long maxDurationMs;

    @DecimalMin(value = "0", message = "最大失败率必须 >= 0")
    @DecimalMax(value = "100", message = "最大失败率必须 <= 100")
    @Schema(description = "最大失败率（%），超过则违约；不填表示不检查")
    private BigDecimal maxFailRate;

    @DecimalMin(value = "0", message = "最小成功率必须 >= 0")
    @DecimalMax(value = "100", message = "最小成功率必须 <= 100")
    @Schema(description = "最小成功率（%），低于则违约；不填表示不检查")
    private BigDecimal minSuccessRate;

    @Pattern(regexp = "^(INFO|WARNING|CRITICAL)$",
            message = "告警级别必须为 INFO / WARNING / CRITICAL 之一")
    @Schema(description = "告警级别: INFO/WARNING/CRITICAL（默认 WARNING）")
    private String alertLevel;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用: 0 禁用 / 1 启用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer enabled;
}
