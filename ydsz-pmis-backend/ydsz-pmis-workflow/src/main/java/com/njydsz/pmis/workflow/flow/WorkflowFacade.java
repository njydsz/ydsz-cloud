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

    /**
     * P2-20: 任务详情查询
     *
     * @param taskId 任务 ID
     * @return 任务详情 Map（含办理人、状态、节点等），不存在返回 null
     */
    Map<String, Object> getTaskDetail(Long taskId);

    /**
     * P2-22: 流程图查询（高亮当前节点）
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 包含 definition / nodes / skips 的 Map，nodes 中每个节点带 active 标记
     */
    Map<String, Object> getDiagram(String instanceId);

    /**
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     */
    void jumpTask(FlowTaskOperateDTO dto);

    /**
     * P2-26: 批量审批 — 对多个任务逐一执行 pass，保证原子性
     *
     * @param taskIds 任务 ID 列表
     * @param userId  操作人 ID
     * @param comment 审批意见
     */
    void batchPassTasks(List<Long> taskIds, Long userId, String comment);

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 时间线列表，每条记录包含 type/timestamp/nodeCode/nodeName/assigneeId/assigneeName/action/comment/taskStatus
     */
    List<Map<String, Object>> getTimeline(String instanceId);
}
