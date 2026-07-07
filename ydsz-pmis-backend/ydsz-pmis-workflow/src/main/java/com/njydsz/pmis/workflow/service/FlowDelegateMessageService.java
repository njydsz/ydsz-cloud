package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.dto.FlowDelegateMessageDTO;
import com.njydsz.pmis.workflow.entity.FlowDelegateMessageDO;

import java.util.List;

/**
 * 自建工作流引擎 - 委派沟通记录服务
 *
 * <p>P2-1 (GAP-08)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowDelegateMessageService {

    /**
     * 发送一条委派沟通留言
     *
     * @param dto        留言内容（taskId 必填）
     * @param senderId   发送人 ID
     * @param senderName 发送人姓名
     * @param senderRole 发送人角色 OWNER/DELEGATE
     * @param tenantId   租户 ID
     * @param traceId    链路追踪 ID
     * @return 消息实体
     */
    FlowDelegateMessageDO send(FlowDelegateMessageDTO dto, String senderId, String senderName,
                               String senderRole, String tenantId, String traceId);

    /**
     * 查询某任务的全部沟通记录（按时间升序）
     */
    List<FlowDelegateMessageDO> listByTask(String taskId);

    /**
     * 标记对方消息为已读（当前发送人角色的对侧消息）
     *
     * @param taskId     任务 ID
     * @param viewerRole 查看者角色 OWNER/DELEGATE（标记非该角色的消息为已读）
     */
    void markRead(String taskId, String viewerRole);
}
