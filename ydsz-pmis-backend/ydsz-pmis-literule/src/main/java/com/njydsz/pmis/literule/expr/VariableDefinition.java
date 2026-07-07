package com.njydsz.pmis.literule.expr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 变量定义元数据
 *
 * <p>描述规则表达式中可引用的变量，包括名称、类型、描述、示例值等。
 * 由 {@link VariableRegistry} 提供，供 {@link ExpressionValidationService} 做 UNDEFINED_VARIABLE 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变量名（如 cpi / budgetAmount / evmRedCount） */
    private String name;

    /** 变量类型（java.lang.String / java.lang.Number / java.lang.Boolean 等） */
    private String type;

    /** 变量描述（中文，供前端编辑器提示） */
    private String description;

    /** 示例值（用于前端编辑器预览和 dryRun 默认 facts） */
    private Object sampleValue;

    /** 变量来源类别（如 EVM / PROJECT / FINANCE / BENCH 等） */
    private String category;

    /** 是否必填（前端编辑器可标记必填变量） */
    @Builder.Default
    private boolean required = false;

    /**
     * 简化类型显示（去掉 java.lang. 前缀）
     *
     * @return 简化类型名
     */
    public String getSimpleType() {
        if (type == null) {
            return "Object";
        }
        return type.replace("java.lang.", "")
                .replace("java.util.", "")
                .replace("java.math.", "");
    }
}
