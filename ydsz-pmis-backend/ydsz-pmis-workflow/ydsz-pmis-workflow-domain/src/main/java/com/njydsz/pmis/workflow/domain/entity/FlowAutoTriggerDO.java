package com.njydsz.pmis.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程自动触发规则 DO
 *
 * <p>当一个流程实例完成时，自动检查是否需要触发另一个流程的启动。
 * 每条记录描述一条"源流程 -> 目标流程"的触发规则，支持条件表达式过滤。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_auto_trigger")
public class FlowAutoTriggerDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 源流程编码（触发方） */
    private String sourceFlowCode;

    /** 目标流程编码（被触发方） */
    private String targetFlowCode;

    /** 条件表达式（Aviator 语法，为空则无条件触发） */
    @TableField("condition_expression")
    private String conditionExpression;

    /** 规则描述 */
    private String description;

    /** 是否启用：0 禁用 / 1 启用 */
    private Integer enabled;

    /** 排序权重 */
    @TableField("sort_order")
    private Integer sortOrder;
}