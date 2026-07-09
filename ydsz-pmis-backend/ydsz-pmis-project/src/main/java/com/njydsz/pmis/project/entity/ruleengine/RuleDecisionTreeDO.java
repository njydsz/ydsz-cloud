package com.njydsz.pmis.project.entity.ruleengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 规则决策树实体
 *
 * <p>决策树规则：root_node 字段为嵌套 JSON 结构，描述树形决策过程。
 * 节点类型：CONDITION（条件）/ ACTION（动作）/ DEFAULT（默认分支）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pmis_rule_decision_tree", autoResultMap = true)
public class RuleDecisionTreeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则分类 */
    private String category;

    /** 规则描述 */
    private String description;

    /** 根节点 JSON（嵌套结构） */
    private String rootNode;

    /** 优先级（数字越小越优先） */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 适用范围 */
    private String scope;

    /** 版本号 */
    private Integer version;

    /** 供应商侧追踪 ID */
    private String providerTraceId;
}
