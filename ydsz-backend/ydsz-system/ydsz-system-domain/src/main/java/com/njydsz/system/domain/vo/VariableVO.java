package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统变量 VO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "系统变量视图对象")
public class VariableVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "变量键")
    private String variableKey;

    @Schema(description = "变量值")
    private String variableValue;

    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "变量说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
