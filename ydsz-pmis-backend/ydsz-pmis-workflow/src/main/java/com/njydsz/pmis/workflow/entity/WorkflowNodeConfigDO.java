package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程节点配置实体
 *
 * <p>扩展节点级别的审批人与表单权限配置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_workflow_node_config")
public class WorkflowNodeConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程定义 KEY */
    private String processKey;

    /** 节点 ID（流程图节点 ID） */
    private String nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 审批人类型: USER/ROLE/DEPT/EXPRESSION/EMPTY */
    private String assigneeType;

    /** 审批人值(用户ID/角色编码/部门ID/SpEL) */
    private String assigneeValue;

    /** 表单字段权限 JSON */
    private String formFieldPerm;

    /** 扩展 JSON */
    private String extraJson;

    /** 租户 ID */
    private Long tenantId;
}
