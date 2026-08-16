package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 规则变量定义视图对象（VO）。
 * <p>
 * 用于 Controller 层返回规则变量的定义信息。规则变量是规则表达式中引用的
 * 上下文因子，包含变量名、类型、示例值和必填标志，支撑规则编辑时的变量提示
 * 和运行时的类型校验。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVariableDefVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 变量定义唯一标识（主键） */
    private String id;
    /** 变量名称 */
    private String varName;
    /** 变量类型（STRING/NUMBER/BOOLEAN/DATE/LIST/MAP） */
    private String varType;
    /** 变量描述 */
    private String description;
    /** 示例值 */
    private String sampleValue;
    /** 分类编码 */
    private String category;
    /** 是否必填 */
    private Boolean required;
    /** 是否启用 */
    private Boolean enabled;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
