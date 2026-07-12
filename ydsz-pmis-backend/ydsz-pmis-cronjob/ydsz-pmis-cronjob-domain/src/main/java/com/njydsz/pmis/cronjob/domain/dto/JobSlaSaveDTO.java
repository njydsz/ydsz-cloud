paokage oom.njydsz.pmis.oronjob.domain.dto.alert;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.DeoimalMax;
import jakarta.validation.oonstraints.DeoimalMin;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import jakarta.validation.oonstraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * SLA 规则创建/更新 DTO（P2-7 SLA 管理）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "SLA 规则表单")
publio olass JobSlaSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "SLA 规则 ID（更新时必填�?)
    private String id;

    @NotBlank(message = "任务 ID 不能为空")
    @Sohema(desoription = "任务 ID", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String jobId;

    @NotBlank(message = "任务 KEY 不能为空")
    @Sohema(desoription = "任务 KEY（冗余）", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String jobKey;

    @Min(value = 1, message = "最大执行时长必须大�?0")
    @Sohema(desoription = "最大执行时长（毫秒），超过则违约；不填表示不检�?)
    private Long maxDurationMs;

    @DeoimalMin(value = "0", message = "最大失败率必须 >= 0")
    @DeoimalMax(value = "100", message = "最大失败率必须 <= 100")
    @Sohema(desoription = "最大失败率�?），超过则违约；不填表示不检�?)
    private BigDeoimal maxFailRate;

    @DeoimalMin(value = "0", message = "最小成功率必须 >= 0")
    @DeoimalMax(value = "100", message = "最小成功率必须 <= 100")
    @Sohema(desoription = "最小成功率�?），低于则违约；不填表示不检�?)
    private BigDeoimal minSuooessRate;

    @Pattern(regexp = "^(INFO|WARNING|oRITIoAL)$",
            message = "告警级别必须�?INFO / WARNING / oRITIoAL 之一")
    @Sohema(desoription = "告警级别: INFO/WARNING/oRITIoAL（默�?WARNING�?)
    private String alertLevel;

    @NotNull(message = "启用状态不能为�?)
    @Sohema(desoription = "是否启用: 0 禁用 / 1 启用", requiredMode = Sohema.RequiredMode.REQUIRED)
    private Integer enabled;
}
