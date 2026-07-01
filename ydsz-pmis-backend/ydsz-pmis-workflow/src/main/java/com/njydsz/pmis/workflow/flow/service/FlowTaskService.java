package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;

import java.util.List;
import java.util.Map;

/**
 * 待办任务 Service
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowTaskService {

    /**
     * 创建任务
     */
    Long createTask(Long instanceId, FlowNodeDO node, Map<String, Object> variables);

    /**
     * 签收
     */
    void claim(Long taskId, Long userId);

    /**
     * 通过
     */
    void pass(FlowTaskOperateDTO dto);

    /**
     * 驳回
     */
    void reject(FlowTaskOperateDTO dto);

    /**
     * 转办
     */
    void transfer(FlowTaskOperateDTO dto);

    /**
     * 委派
     */
    void delegate(FlowTaskOperateDTO dto);

    /**
     * 取消某实例的全部 PENDING 任务（终止/驳回终态时使用）
     */
    void cancelByInstance(Long instanceId, String reason);

    /**
     * 查实例的当前 PENDING 任务
     */
    List<FlowTaskDO> listPendingByInstance(Long instanceId);

    /**
     * 查用户的待办
     */
    List<FlowTaskDO> listTodoByAssignee(String assigneeId, Long tenantId);

    /**
     * 查用户的已办
     */
    List<FlowTaskDO> listDoneByAssignee(String assigneeId, Long tenantId);

    /**
     * 转视图
     */
    FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowTaskDO task);
}
