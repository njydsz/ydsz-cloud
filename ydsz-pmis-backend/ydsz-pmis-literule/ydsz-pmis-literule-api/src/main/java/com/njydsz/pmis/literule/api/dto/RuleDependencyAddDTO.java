package com.njydsz.pmis.literule.api.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则依赖新增请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/dependencies} 接口，为规则添加依赖关系
 * （依赖另一条规则的执行结果，支持级联禁用）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "规则依赖新增请求体")
public class RuleDependencyAddDTO {

    /**
     * 被依赖的规则编码
     */
    @Schema(description = "被依赖的规则编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "RULE_ORDER_LIMIT")
    @NotBlank(message = "{validation.project.msg_c3a4b5c4}")
    private String dependsOnRuleCode;

    /**
     * 依赖类型：EXECUTE / DATA，默认 EXECUTE
     */
    @Schema(description = "依赖类型：EXECUTE / DATA", defaultValue = "EXECUTE", example = "EXECUTE")
    private String dependencyType = "EXECUTE";

    /**
     * 被依赖规则禁用时是否级联禁用本规则，默认 false
     */
    @Schema(description = "被依赖规则禁用时是否级联禁用本规则", defaultValue = "false")
    private Boolean cascadeOnDisable = false;

    /**
     * 依赖关系描述（可选）
     */
    @Schema(description = "依赖关系描述")
    private String description;
}
