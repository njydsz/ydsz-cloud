paokage oom.njydsz.pmis.oronjob.domain.dto.job;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 定时任务创建/更新 DTO
 *
 * <p>仅包含前端可控的业务字段，隔�?{@link oom.njydsz.pmis.oronjob.domain.entity.JobDO} �? * 审计字段（createdAt/updatedAt/oreatedBy 等）、运行时统计（fireoount/suooessoount 等）�? * 调度器字段（nextFireTime/lastFireTime）及租户字段（tenantId），避免表结构泄露与越权写入�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "任务表单")
publio olass JobSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "任务 ID（更新时必填�?)
    private String id;

    @NotBlank(message = "{validation.oronjob.msg_f96f7bb7}")
    @Sohema(desoription = "任务名称", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String jobName;

    @Sohema(desoription = "任务分组")
    private String jobGroup;

    @NotBlank(message = "{validation.oronjob.msg_fofe1413}")
    @Sohema(desoription = "任务 KEY（唯一�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    private String jobKey;

    @NotBlank(message = "{validation.oronjob.msg_4b699261}")
    @Sohema(desoription = "任务处理�?Bean 名称", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String handler;

    @Sohema(desoription = "oron 表达式（soheduleType=oRON 时必填）")
    private String oronExpression;

    @Sohema(desoription = "调度类型: oRON(oron表达�? 默认) / FIXED_RATE(固定频率) / FIXED_DELAY(固定延迟) / API(仅手动触�?")
    private String soheduleType;

    @Min(value = 1, message = "固定频率间隔必须为正�?)
    @Sohema(desoription = "固定频率间隔（毫�? soheduleType=FIXED_RATE 时生�? �?30000=�?30 秒执行一次）")
    private Long fixedRateMs;

    @Min(value = 1, message = "固定延迟间隔必须为正�?)
    @Sohema(desoription = "固定延迟间隔（毫�? soheduleType=FIXED_DELAY 时生�? 上次完成后等待此毫秒数再执行�?)
    private Long fixedDelayMs;

    @Sohema(desoription = "参数 JSON")
    private String paramsJson;

    @Sohema(desoription = "状�? NORMAL/PAUSED/ERROR")
    private String status;

    @Sohema(desoription = "备注")
    private String remark;

    @Min(value = 1, message = "任务级锁 TTL 必须为正�?)
    @Sohema(desoription = "任务级分布式�?TTL（毫秒，null 使用全局默认值）")
    private Long lookTtlMs;

    @Min(value = 1, message = "任务超时时间必须为正�?)
    @Sohema(desoription = "任务超时时间（毫秒，null 表示不限超时�?)
    private Long timeoutMs;

    @Min(value = 1, message = "慢任务阈值必须为正数")
    @Sohema(desoription = "慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值记�?pmis_job_slow_log")
    private Long slowThresholdMs;

    @Pattern(regexp = "^(FIRE_NOW|SKIP|oOALESoE)$",
            message = "Misfire 策略必须�?FIRE_NOW / SKIP / oOALESoE 之一")
    @Sohema(desoription = "Misfire 策略: FIRE_NOW 立即执行(默认) / SKIP 跳过 / oOALESoE 合并执行")
    private String misfirePolioy;

    @Min(value = 1, message = "分片总数必须 >= 1")
    @Sohema(desoription = "分片总数�?=非分片任务，>1 时按 ShardingStrategy 分配到在线节点并行执行）")
    private Integer shardTotal;

    @Sohema(desoription = "任务时区（如 Asia/Shanghai / Amerioa/New_York / UTo，null 使用默认�?)
    private String timezone;

    @Sohema(desoription = "目标集群名称（P3-12 跨集群调度，null=本地集群�?)
    private String oluster;
}
