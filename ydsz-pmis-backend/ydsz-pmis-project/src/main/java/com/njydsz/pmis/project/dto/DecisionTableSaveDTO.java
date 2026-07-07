package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 决策表保存 DTO
 *
 * <p>隔离 {@link com.njydsz.pmis.project.entity.DecisionTableDO} 的
 * id/version/createdBy/createdAt/updatedBy/updatedAt 审计字段，避免越权写入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "决策表表单")
public class DecisionTableSaveDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    @Schema(description = "决策表 ID（更新时传入）")
    private String id;

    @NotBlank(message = "决策表编码不能为空")
    @Schema(description = "决策表编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableCode;

    @NotBlank(message = "决策表名称不能为空")
    @Schema(description = "决策表名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "条件列定义")
    private List<Map<String, Object>> conditionColumns;

    @Schema(description = "动作列定义")
    private List<Map<String, Object>> actionColumns;

    @Schema(description = "规则行")
    private List<Map<String, Object>> rows;

    @Schema(description = "默认动作")
    private Map<String, Object> defaultActions;

    @Schema(description = "命中策略: UNIQUE/FIRST/PRIORITY/COLLECT")
    private String hitPolicy;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "优先级")
    private Integer priority;
}
