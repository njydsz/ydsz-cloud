package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务评论 DO
 *
 * <p>用于工作流任务下的独立沟通评论，区别于任务操作（通过/驳回）时附带的审批意见。
 * 支持评论（COMMENT）、提问（QUESTION）、回复（REPLY）三种类型，并通过 parentId 支持楼中楼回复。
 * created_at 复用 BaseDO 字段，由 MyBatis-Plus MetaObjectHandler 自动填充。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_task_comment")
public class FlowTaskCommentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;
    /** 流程实例 ID */
    private Long instanceId;
    /** 任务 ID */
    private Long taskId;
    /** 节点编码 */
    private String nodeCode;
    /** 评论人 ID */
    private Long userId;
    /** 评论人姓名 */
    private String userName;
    /** 评论内容 */
    private String content;
    /** 评论类型：COMMENT / QUESTION / REPLY */
    private String type;
    /** 父评论 ID（可空，用于楼中楼回复） */
    private Long parentId;
}
