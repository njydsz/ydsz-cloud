package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;

import java.util.List;

/**
 * 流程实例 Service
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowInstanceService {

    /**
     * 启动流程
     */
    Long start(FlowStartProcessDTO dto);

    /**
     * 按 ID 查
     */
    FlowInstanceDO getById(Long id);

    /**
     * 业务关联查询
     */
    FlowInstanceDO getByBusiness(String businessType, String businessId);

    /**
     * 终止流程
     */
    void terminate(Long instanceId, String reason);

    /**
     * 挂起
     */
    void suspend(Long instanceId);

    /**
     * 激活
     */
    void activate(Long instanceId);

    /**
     * 强制完成（驳回到终态时由调用方使用）
     */
    void complete(Long instanceId, String endNodeCode);

    /**
     * 转化为视图对象
     */
    FlowInstanceViewDTO toView(FlowInstanceDO instance, List<FlowInstanceViewDTO.FlowTaskViewDTO> currentTasks);

    /**
     * 发起人维度查询
     */
    List<FlowInstanceDO> listByInitiator(Long initiatorId, String flowStatus);
}
