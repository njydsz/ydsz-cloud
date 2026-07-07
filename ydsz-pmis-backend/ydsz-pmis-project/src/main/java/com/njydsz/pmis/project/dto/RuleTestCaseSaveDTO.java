package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 规则测试用例保存 DTO
 *
 * <p>隔离 {@link com.njydsz.pmis.project.entity.RuleTestCaseDO} 的
 * id/createdAt/updatedAt 审计字段，避免越权写入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "规则测试用例表单")
public class RuleTestCaseSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "测试用例 ID（更新时传入）")
    private String id;

    @NotBlank(message = "测试用例名称不能为空")
    @Schema(description = "测试用例名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "规则编码不能为空")
    @Schema(description = "规则编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleCode;

    @Schema(description = "事实数据")
    private Map<String, Object> factsData;

    @Schema(description = "期望触发的规则列表")
    private List<String> expectedTriggered;

    @Schema(description = "描述")
    private String description;
}
