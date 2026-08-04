package com.remisoft.literule.api.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import com.remisoft.literule.api.RuleDefinition;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则 A/B 测试请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/ab-test} 接口，对同一事实数据分别评估
 * 当前规则版本和候选规则版本，返回对比报告。
 *
 * <p>注意：{@code facts} 是动态事实数据（键名由业务自定义），保留 {@code Map<String, Object>}。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Schema(description = "规则 A/B 测试请求体")
public class RuleABTestDTO {

    /**
     * 候选规则定义（与当前规则对比）
     */
    @Schema(description = "候选规则定义", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{validation.project.msg_e5c6d7e6}")
    private RuleDefinition candidate;

    /**
     * 事实数据（动态键值对，键名由业务自定义）
     */
    @Schema(description = "事实数据（动态键值对）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"amount\": 15000, \"level\": \"紧急\"}")
    @NotNull(message = "{validation.project.msg_f6d7e8f7}")
    private Map<String, Object> facts;
}
