package com.njydsz.pmis.project.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 表达式校验请求体 DTO
 *
 * <p>用于 {@code /rules/validate-expression} 接口，校验条件/严重度/模板表达式。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Schema(description = "表达式校验请求体")
public class ExpressionValidateDTO {

    /**
     * 表达式内容
     */
    @Schema(description = "表达式内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "amount > 10000 && level == '紧急'")
    @NotBlank(message = "{validation.project.msg_a1e2f3a1}")
    private String expression;

    /**
     * 表达式类型：condition / severity / template，默认 condition
     */
    @Schema(description = "表达式类型：condition / severity / template", defaultValue = "condition",
            example = "condition")
    @Pattern(regexp = "condition|severity|template", message = "{validation.project.msg_b2f3a4b2}")
    private String type = "condition";
}
