package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程节点 DO
 *
 * <p>对标 Warm-Flow flow_node，描述流程图中的每个节点（开始/审批/网关/结束）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_node")
public class FlowNodeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程定义 ID */
    private Long definitionId;

    /** 流程编码（冗余便于查询） */
    private String flowCode;

    /** 节点类型（FlowNodeType.code） */
    private Integer nodeType;

    /** 节点编码（流程内唯一） */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 办理人权限标识：role:hr / dept:10 / user:1001 / ${spel} */
    private String permissionFlag;

    /** 任意跳转目标节点编码 */
    private String skipAnyNode;

    /** 设计器坐标 JSON */
    private String coordinate;

    /** 节点跳转路由集合 JSON */
    private String skipList;

    /** 扩展字段 JSON */
    private String ext;

    /** GAP-P0: 表单字段权限配置 JSON — 按节点控制字段可编辑/只读/隐藏，格式: {"fieldKey":"EDIT|READONLY|HIDDEN",...} */
    private String formFieldsConfig;

    /** GAP-P1: SLA 超时配置 JSON — {"timeoutMinutes":120,"action":"REMIND|ESCALATE|AUTO_PASS|AUTO_REJECT","reminderCount":3,"adminUserId":1} */
    private String slaConfig;

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
