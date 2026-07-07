package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程决策表创建/更新 DTO
 *
 * <p>隔离 {@link com.njydsz.pmis.workflow.entity.FlowDmnTableDO} 的
 * id/tenantId/version/status 及审计字段，避免越权写入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "决策表表单")
public class FlowDmnTableSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "决策表 ID（更新时传入）")
    private String id;

    @NotBlank(message = "决策表 KEY 不能为空")
    @Schema(description = "决策表 KEY（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableKey;

    @NotBlank(message = "决策表名称不能为空")
    @Schema(description = "决策表名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "命中策略: UNIQUE/FIRST/PRIORITY/COLLECT")
    private String hitPolicy;

    @Schema(description = "聚合运算符（COLLECT 策略时使用）")
    private String collectOperator;

    @Schema(description = "输入定义 JSON")
    private String inputsJson;

    @Schema(description = "输出定义 JSON")
    private String outputsJson;

    @Schema(description = "规则 JSON")
    private String rulesJson;
}
