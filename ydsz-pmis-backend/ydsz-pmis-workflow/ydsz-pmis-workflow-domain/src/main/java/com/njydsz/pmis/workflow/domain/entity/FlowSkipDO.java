package com.njydsz.pmis.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点跳转关联 DO
 *
 * <p>对标 Warm-Flow flow_skip，描述节点间的有向边。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_skip")
public class FlowSkipDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属流程定义 ID */
    private String definitionId;

    /** 流程编码（冗余） */
    private String flowCode;

    /** 跳转名称（线上标签） */
    private String skipName;

    /** 跳转类型（FlowSkipType.name） */
    private String skipType;

    /** 设计器坐标 JSON */
    private String coordinate;

    /** 跳转条件表达式（SpEL 或 ${var} 语法） */
    private String skipCondition;

    /** 下一节点编码 */
    private String nextNodeCode;

    /** 下一节点类型（FlowNodeType.code） */
    private Integer nextNodeType;

    /** 下一节点坐标 */
    private String coordinateNext;

    /** 跳转路由集合 JSON */
    private String skipList;

    /** 扩展字段 JSON（存储 sourceRef/sequenceFlowId 等 BPMN 派生信息） */
    private String ext;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
