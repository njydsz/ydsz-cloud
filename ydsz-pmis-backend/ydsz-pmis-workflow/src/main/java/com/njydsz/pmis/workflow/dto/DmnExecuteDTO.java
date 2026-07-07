package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * DMN 决策表执行请求体 DTO
 *
 * <p>用于 {@code /workflow/dmn/execute} 接口，按 tableKey 加载决策表并以
 * context 作为事实数据执行 DMN 评估。
 *
 * <p>注意：{@code context} 是动态键值对（变量名 -> 值），保留 {@code Map<String, Object>}。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@Schema(description = "DMN 决策表执行请求体")
public class DmnExecuteDTO {

    /**
     * 决策表唯一标识
     */
    @Schema(description = "决策表唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "risk_level")
    @NotBlank(message = "{validation.workflow.msg_a1e2f3a4}")
    private String tableKey;

    /**
     * 决策上下文（变量名 -> 值，动态键值对）
     */
    @Schema(description = "决策上下文（变量名 -> 值）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"amount\": 15000, \"level\": \"紧急\"}")
    private Map<String, Object> context;
}
