package com.njydsz.pmis.workflow.web;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.domain.dto.instance.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.domain.dto.instance.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.domain.dto.instance.FlowTaskOperateDTO;

import java.time.LocalDateTime;
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
    void claimTask(String taskId, String userId);

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
    List<Map<String, Object>> listTodoTasks(String userId, int page, int size);

    /**
     * 查用户已办
     */
    List<Map<String, Object>> listDoneTasks(String userId, int page, int size);

    /**
     * GAP-P0-1: 查全部流程实例（管理员视图）
     *
     * <p>对标钉钉/飞书/企微审批中心的"全部"Tab，管理员可查看当前租户下所有流程实例。
     * 非管理员调用应由上层权限拦截（需要 workflow:monitor:view 权限）。
     *
     * <p>P0-2 修复：返回类型由 {@code List<Map>} 改为 {@code PageResult<Map>}，
     * 保留 total / page / size，避免前端假分页。
     *
     * @param businessType 业务类型（可空）
     * @param flowStatus   流程状态（可空）
     * @param startTime    开始时间下界（可空）
     * @param endTime      开始时间上界（可空）
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 分页实例 Map 列表
     */
    PageResult<Map<String, Object>> listAllInstances(String businessType, String flowStatus,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime,
                                                     int page, int size);

    /**
     * 前加签
     */
    void countersignBeforeTask(FlowTaskOperateDTO dto);

    /**
     * 后加签
     */
    void countersignAfterTask(FlowTaskOperateDTO dto);

    /**
     * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
     */
    void countersignParallelTask(FlowTaskOperateDTO dto);

    /**
     * 催办
     */
    List<String> urgeTask(String instanceId, String operatorId, String comment);

    /**
     * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点的待办任务
     *
     * @param instanceId 实例 ID
     * @param nodeCode   节点编码（null/空则退化为实例级催办）
     * @param operatorId 催办人 ID
     * @param comment    催办说明
     * @return 被催办人 ID 列表
     */
    List<String> urgeNodeTask(String instanceId, String nodeCode, String operatorId, String comment);

    /**
     * 撤回流程
     */
    boolean recallProcess(String processInstanceId, String initiatorId);

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
    Map<String, Object> getTaskDetail(String taskId);

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
    void batchPassTasks(List<String> taskIds, String userId, String comment);

    /**
     * GAP-P0-4: 一键通过所有待办 — 查询当前用户全部待办（上限 100 条）并逐一通过。
     *
     * <p>对标钉钉/飞书审批中心"一键通过"按钮。内部委托 {@link #batchPassTasks} 保证原子性。
     *
     * @param userId  操作人 ID
     * @param comment 审批意见（可选）
     * @return 实际通过的任务数量
     */
    int passAllTodoTasks(String userId, String comment);

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 时间线列表，每条记录包含 type/timestamp/nodeCode/nodeName/assigneeId/assigneeName/action/comment/taskStatus
     */
    List<Map<String, Object>> getTimeline(String instanceId);

    /**
     * P2-4: 流程回放步骤序列 — 按时间顺序合并历史任务 + 审计日志 + 当前待办为统一步骤序列，
     * 驱动前端 {@code FlowDiagramReplay} 组件依次高亮节点并展示轨迹事件。
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 步骤列表（按 timestamp 升序），实例不存在时返回空列表
     */
    List<Map<String, Object>> getReplaySteps(String instanceId);

    // ======================== P0-03: 暂存待审 / 追加处理人 / 减签 / 已阅 / 沟通 ========================

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
     */
    void saveDraft(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
     */
    void addApprover(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 减签 — 从会签任务中移除指定审批人
     */
    void countersignRemoveTask(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 已阅 — 标记任务已阅
     */
    void markReadTask(String taskId, String userId);

    /**
     * GAP-P0: 沟通 — 在任务下添加沟通评论
     */
    void communicateTask(FlowTaskOperateDTO dto);

    /**
     * P2-2 (GAP-10): 驳回后快速重审 — 基于被驳回的原实例重新提交
     *
     * @param instanceId 被驳回的实例 ID
     * @param initiatorId 发起人 ID
     * @param variables 重审时新增/覆盖的变量（可空）
     * @param comment 重审说明（可选）
     * @return 实例 ID
     */
    String resubmitProcess(String instanceId, String initiatorId,
                           Map<String, Object> variables, String comment);

    /**
     * P1-8: 流程重做 — 支持 redoMode 指定重做策略（RESTART / NEW_INSTANCE）。
     *
     * @param instanceId  原实例 ID
     * @param initiatorId 发起人 ID
     * @param variables   重做时新增/覆盖的变量（可空）
     * @param comment     重做说明（可选）
     * @param redoMode    重做模式：RESTART / NEW_INSTANCE（null/空时默认 RESTART）
     * @return 实例 ID（RESTART 返回原 instanceId，NEW_INSTANCE 返回新 instanceId）
     */
    String resubmitProcess(String instanceId, String initiatorId,
                           Map<String, Object> variables, String comment, String redoMode);

    /**
     * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
     *
     * @param taskId     任务 ID
     * @param operatorId 操作人 ID
     * @param reason     挂起原因（可选）
     */
    void suspendTask(String taskId, String operatorId, String reason);

    /**
     * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
     *
     * @param taskId     任务 ID
     * @param operatorId 操作人 ID
     */
    void activateTask(String taskId, String operatorId);
}
