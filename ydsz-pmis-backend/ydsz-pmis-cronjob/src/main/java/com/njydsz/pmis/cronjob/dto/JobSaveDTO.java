package com.njydsz.pmis.cronjob.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 定时任务创建/更新 DTO
 *
 * <p>仅包含前端可控的业务字段，隔离 {@link com.njydsz.pmis.cronjob.entity.JobDO} 的
 * 审计字段（createdAt/updatedAt/createdBy 等）、运行时统计（fireCount/successCount 等）、
 * 调度器字段（nextFireTime/lastFireTime）及租户字段（tenantId），避免表结构泄露与越权写入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "任务表单")
public class JobSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID（更新时必填）")
    private Long id;

    @NotBlank(message = "{validation.cronjob.msg_f96f7bb7}")
    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobName;

    @Schema(description = "任务分组")
    private String jobGroup;

    @NotBlank(message = "{validation.cronjob.msg_fcfe1413}")
    @Schema(description = "任务 KEY（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobKey;

    @NotBlank(message = "{validation.cronjob.msg_4b699261}")
    @Schema(description = "任务处理器 Bean 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String handler;

    @NotBlank(message = "{validation.cronjob.msg_14201280}")
    @Schema(description = "Cron 表达式", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cronExpression;

    @Schema(description = "参数 JSON")
    private String paramsJson;

    @Schema(description = "状态: NORMAL/PAUSED/ERROR")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
