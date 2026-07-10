package com.njydsz.pmis.cronjob.dto.dag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DAG 工作流手动触发 DTO（P2 DAG 增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "DAG 触发表单")
public class JobDagTriggerDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.cronjob.msg_dag_key_required}")
    @Schema(description = "DAG 唯一 KEY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dagKey;

    @Schema(description = "触发人（MANUAL 时为用户 ID，可空）")
    private String triggerBy;
}
