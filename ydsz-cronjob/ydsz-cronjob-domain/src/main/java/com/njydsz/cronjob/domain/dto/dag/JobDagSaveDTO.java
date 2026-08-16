package com.njydsz.cronjob.domain.dto.dag;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DAG 工作流定义创建/更新 DTO（P2 DAG 增强）。
 *
 * <p>仅包含前端可控的业务字段，隔离 {@link com.njydsz.cronjob.domain.entity.JobDag} 的
 * 审计字段、运行时统计与调度器字段，避免表结构泄露与越权写入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "DAG 工作流表单")
public class JobDagSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.cronjob.msg_dag_key_required}")
    @Schema(description = "DAG 唯一 KEY（调度与触发使用）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dagKey;

    @NotBlank(message = "{validation.cronjob.msg_dag_name_required}")
    @Schema(description = "DAG 名称（展示用）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dagName;

    @NotBlank(message = "DAG 定义不能为空")
    @Schema(description = "DAG 定义 JSON（nodes + edges + 可视化坐标）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String dagDefinition;

    @Pattern(regexp = "^(DRAFT|ENABLED|DISABLED)$",
            message = "DAG 状态必须为 DRAFT / ENABLED / DISABLED 之一")
    @Schema(description = "DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用")
    private String status;

    @Pattern(regexp = "^(MANUAL|CRON)$",
            message = "触发类型必须为 MANUAL / CRON 之一")
    @Schema(description = "触发类型: MANUAL 手动 / CRON 定时")
    private String triggerType;

    @Schema(description = "Cron 表达式（triggerType=CRON 时必填）")
    private String cronExpression;

    @Min(value = 0, message = "最大并发实例数必须 >= 0")
    @Schema(description = "最大并发实例数(0=不限制, 默认1)")
    private Integer maxConcurrentInstances;

    @Pattern(regexp = "^(FAIL_FAST|CONTINUE_ON_FAIL)$",
            message = "失败策略必须为 FAIL_FAST / CONTINUE_ON_FAIL 之一")
    @Schema(description = "DAG 级失败策略: FAIL_FAST 中止 / CONTINUE_ON_FAIL 继续")
    private String failStrategy;

    @Schema(description = "DAG 描述")
    private String description;
}
