package com.remisoft.literule.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 决策表视图对象（VO）。
 * <p>
 * 用于 Controller 层返回决策表的完整信息，包含决策表基本信息、命中策略、
 * 优先级及审计字段。决策表是一种结构化的规则表达形式，以行列方式组织
 * 条件与动作的映射关系。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class DecisionTableVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 决策表唯一标识（主键） */
    private String id;
    /** 决策表编码，业务唯一 */
    private String tableCode;
    /** 决策表名称，用于展示 */
    private String tableName;
    /** 决策表描述 */
    private String description;
    /** 分类编码 */
    private String category;
    /** 命中策略（UNIQUE/FIRST/PRIORITY/COLLECT/RULE_ORDER） */
    private String hitPolicy;
    /** 是否启用 */
    private Boolean enabled;
    /** 优先级，数值越小优先级越高 */
    private Integer priority;
    /** 版本号 */
    private Integer version;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
