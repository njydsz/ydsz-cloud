package com.njydsz.pmis.workflow.entity.delegate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自建工作流引擎 - 委派沟通记录实体
 *
 * <p>P2-1 (GAP-08): 委托人与被委托人之间的留言沟通，持久化留存。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_flow_delegate_message")
public class FlowDelegateMessageDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 关联被委托任务 ID */
    private String taskId;

    /** 关联流程实例 ID */
    private String instanceId;

    /** 关联节点编码 */
    private String nodeCode;

    /** 发送人 ID */
    private String senderId;

    /** 发送人姓名 */
    private String senderName;

    /** 发送人角色: OWNER=委托人 / DELEGATE=被委托人 */
    private String senderRole;

    /** 沟通内容 */
    private String content;

    /** 可选附件存储 key */
    private String attachmentKey;

    /** 是否已读: 0=未读 1=已读 */
    private Integer readFlag;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 修改时间 */
    private LocalDateTime updatedAt;

    /** 链路追踪 ID */
    private String providerTraceId;
}
