package com.remisoft.literule.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则决策树视图对象（VO）。
 * <p>
 * 用于 Controller 层返回决策树规则的完整信息。决策树以树形结构组织条件判断，
 * 从根节点出发逐层匹配，到达叶节点输出结果，具有高可读性和执行效率。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class RuleDecisionTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 决策树唯一标识（主键） */
    private String id;
    /** 关联的规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 分类编码 */
    private String category;
    /** 描述 */
    private String description;
    /** 根节点 JSON 定义 */
    private String rootNode;
    /** 优先级，数值越小优先级越高 */
    private Integer priority;
    /** 是否启用 */
    private Boolean enabled;
    /** 适用范围 */
    private String scope;
    /** 版本号 */
    private Integer version;
    /** 外部模型追踪 ID */
    private String providerTraceId;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
