package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * P1-8: 撤回流程（仅发起人可撤回，仅运行中可撤回，下一节点未被处理才可撤回）
     *
     * @param instanceId  实例 ID
     * @param initiatorId 发起人 ID
     * @return 是否撤回成功
     */
    boolean recall(Long instanceId, Long initiatorId);

    /**
     * P2-23: 实例多维分页查询
     *
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @param pageNo       页码（从 1 开始）
     * @param pageSize     每页大小
     * @return 分页结果
     */
    PageResult<FlowInstanceDO> page(String businessType, Long initiatorId, String flowStatus,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    Long tenantId, int pageNo, int pageSize);

    /**
     * P2-24: 读取实例流程变量
     *
     * @param instanceId 实例 ID
     * @return 变量 Map，无变量返回空 Map
     */
    Map<String, Object> getVariables(Long instanceId);

    /**
     * P2-24: 合并写入单个变量并持久化
     *
     * @param instanceId 实例 ID
     * @param key        变量名
     * @param value      变量值
     */
    void setVariable(Long instanceId, String key, Object value);

    /**
     * P2-24: 批量合并写入变量并持久化
     *
     * @param instanceId 实例 ID
     * @param variables  变量 Map
     */
    void setVariables(Long instanceId, Map<String, Object> variables);

    /**
     * 引擎内部方法：推进后批量生成任务（供 FlowAdvancer / FlowTaskService 调用）
     *
     * @param instanceId 流程实例 ID
     * @param nextNodes  推进后的下一节点列表
     * @param variables  流程变量
     */
    void generateTasksForNodes(Long instanceId, List<FlowNodeDO> nextNodes,
                                Map<String, Object> variables);
}
