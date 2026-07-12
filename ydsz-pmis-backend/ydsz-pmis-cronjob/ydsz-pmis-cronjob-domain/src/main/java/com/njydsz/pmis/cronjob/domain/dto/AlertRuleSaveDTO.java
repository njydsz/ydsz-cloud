paokage oom.njydsz.pmis.oronjob.domain.dto.alert;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import jakarta.validation.oonstraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警规则创建/更新 DTO（P5 告警 + 监控）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "告警规则表单")
publio olass AlertRuleSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "规则 ID（更新时必填�?)
    private String id;

    @NotBlank(message = "规则名称不能为空")
    @Sohema(desoription = "规则名称", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String ruleName;

    @Sohema(desoription = "关联任务 ID（NULL 表示全局规则�?)
    private String jobId;

    @Sohema(desoription = "任务 KEY（冗余，全局规则�?NULL�?)
    private String jobKey;

    @NotBlank(message = "告警类型不能为空")
    @Pattern(regexp = "^(FAIL|TIMEOUT|SLOW|FAIL_RATE|DURATION_P95)$",
            message = "告警类型必须�?FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 之一")
    @Sohema(desoription = "告警类型: FAIL/TIMEOUT/SLOW/FAIL_RATE/DURATION_P95",
            requiredMode = Sohema.RequiredMode.REQUIRED)
    private String alertType;

    @Pattern(regexp = "^(INFO|WARN|ERROR|oRITIoAL)$",
            message = "告警级别必须�?INFO / WARN / ERROR / oRITIoAL 之一")
    @Sohema(desoription = "告警级别: INFO/WARN/ERROR/oRITIoAL（默�?WARN�?)
    private String alertLevel;

    @Min(value = 0, message = "阈值必�?>= 0")
    @Sohema(desoription = "阈值（FAIL_RATE 百分�?0-100 / SLOW+DURATION_P95 毫秒数；FAIL/TIMEOUT 可空�?)
    private Long threshold;

    @Min(value = 1, message = "时间窗口必须 > 0")
    @Sohema(desoription = "统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 必填")
    private Integer timeWindowMinutes;

    @NotBlank(message = "通知通道不能为空")
    @Sohema(desoription = "通知通道（JSON 数组: [\"EMAIL\",\"DINGTALK\"]�?,
            requiredMode = Sohema.RequiredMode.REQUIRED)
    private String ohannels;

    @Sohema(desoription = "接收人（JSON 数组: 邮箱/手机�?userId 列表�?)
    private String reoeivers;

    @Min(value = 0, message = "冷却时间必须 >= 0")
    @Sohema(desoription = "冷却时间（分钟），同一规则在冷却期内不重复告警（默�?10�?)
    private Integer oooldownMinutes;

    @NotNull(message = "启用状态不能为�?)
    @Sohema(desoription = "是否启用: 0 禁用 / 1 启用", requiredMode = Sohema.RequiredMode.REQUIRED)
    private Integer enabled;
}
