package com.njydsz.pmis.workflow.flow;

import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;

import java.util.List;
import java.util.Map;

/**
 * 自建工作流引擎 - 业务侧统一入口 Facade
 *
 * <p>所有业务模块（project / execution / closure 等）只能依赖本接口，<br>
 * 不允许直接引用 FlowEngine 内部服务，便于引擎隔离与升级。
 *
 * <p>当前实现：基于 pmis_flow_* 自建表（Warm-Flow 风格）的轻量级流程引擎，<br>
 * 兼容 BPMN 2.0 标准流程文件（通过 BpmnXmlParser 解析 startEvent / userTask / gateway / endEvent / sequenceFlow）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface WorkflowFacade {

    /**
     * 启动流程
     *
     * @return 流程实例 ID（pmis_flow_instance.id，字符串形式）
     */
    String startProcess(FlowStartProcessDTO dto);

    /**
     * 通过业务类型 + 业务 ID 查实例
     */
    FlowInstanceViewDTO getByBusiness(String businessType, String businessId);

    /**
     * 完成任务（通过/拒绝）
     */
    void completeTask(FlowTaskOperateDTO dto);

    /**
     * 签收任务
     */
    void claimTask(Long taskId, Long userId);

    /**
     * 转办任务
     */
    void transferTask(FlowTaskOperateDTO dto);

    /**
     * 委派任务（任务保留原办理人，被委派人处理后回到原办理人）
     */
    void delegateTask(FlowTaskOperateDTO dto);

    /**
     * 退回任务
     */
    void rejectTask(FlowTaskOperateDTO dto);

    /**
     * 终止流程
     */
    void terminateProcess(String processInstanceId, String reason);

    /**
     * 挂起流程
     */
    void suspendProcess(String processInstanceId);

    /**
     * 激活流程
     */
    void activateProcess(String processInstanceId);

    /**
     * 查用户待办
     */
    List<Map<String, Object>> listTodoTasks(Long userId, int page, int size);

    /**
     * 查用户已办
     */
    List<Map<String, Object>> listDoneTasks(Long userId, int page, int size);

    /**
     * 前加签
     */
    void countersignBeforeTask(FlowTaskOperateDTO dto);

    /**
     * 后加签
     */
    void countersignAfterTask(FlowTaskOperateDTO dto);

    /**
     * 催办
     */
    List<String> urgeTask(Long instanceId, Long operatorId, String comment);

    /**
     * 撤回流程
     */
    boolean recallProcess(String processInstanceId, Long initiatorId);

    /**
     * 查询审批轨迹（审计日志）
     */
    List<Map<String, Object>> listAuditTrail(String processInstanceId);

    /**
     * 引擎类型：PMIS（自研）
     */
    String engineType();
}
