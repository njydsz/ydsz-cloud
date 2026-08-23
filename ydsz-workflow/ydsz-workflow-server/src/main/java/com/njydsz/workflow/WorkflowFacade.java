package com.njydsz.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;

/**
 * 自建工作流引擎 — 业务侧统一入口 Facade
 *
 * <p>所有业务模块（project / execution / closure 等）只能依赖本接口， 不允许直接引用 FlowEngine 内部服务，便于引擎隔离与升级。
 *
 * <p><b>设计模式：</b>门面模式（Facade Pattern）— 将引擎内部的 {@code FlowInstanceService}、 {@code
 * FlowTaskService}、{@code FlowDefinitionService} 等多个 Service 的复杂调用编排 封装为统一的粗粒度接口，降低业务模块与引擎的耦合度。
 *
 * <p><b>引擎实现：</b>基于 {@code ydsz_flow_*} 自建表（Warm-Flow 风格）的轻量级流程引擎， 兼容 BPMN 2.0 标准流程文件（通过 {@code
 * BpmnXmlParser} 解析 startEvent / userTask / gateway / endEvent / sequenceFlow）。
 *
 * <p><b>能力矩阵：</b>
 *
 * <ul>
 *   <li><b>流程实例</b>：启动 / 终止 / 挂起 / 激活 / 撤回 / 重审 / 批量启动
 *   <li><b>任务办理</b>：通过 / 驳回 / 转办 / 委派 / 签收 / 跳转 / 自由流
 *   <li><b>加签操作</b>：前加签 / 后加签 / 并加签 / 减签 / 追加处理人
 *   <li><b>查询能力</b>：待办 / 已办 / 审批轨迹 / 时间线 / 流程图 / 流程回放
 *   <li><b>批量操作</b>：批量通过 / 一键通过 / 批量驳回 / 批量转办
 *   <li><b>辅助操作</b>：暂存待审 / 已阅 / 沟通 / 任务级挂起激活
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 * @see com.njydsz.workflow.server.service.FlowInstanceService 流程实例服务
 * @see com.njydsz.workflow.server.service.FlowTaskService 任务服务
 * @see com.njydsz.workflow.domain.dto.FlowStartProcessDTO 启动参数 DTO
 * @see com.njydsz.workflow.domain.dto.FlowTaskOperateDTO 任务操作 DTO
 */
public interface WorkflowFacade {

  /**
   * 启动流程
   * 
   * 
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  String startProcess(FlowStartProcessDTO dto);

  /**
   * 通过业务类型 + 业务 ID 查实例
   *
   * @param businessType 参数说明
   * @param businessId 参数说明
   * @return 返回值说明
   */
  FlowInstanceViewDTO getByBusiness(String businessType, String businessId);

  /**
   * 完成任务（通过/拒绝）
   *
   * @param dto 参数说明
   */
  void completeTask(FlowTaskOperateDTO dto);

  /**
   * 签收任务
   *
   * @param taskId 参数说明
   * @param userId 参数说明
   */
  void claimTask(String taskId, String userId);

  /**
   * 转办任务
   *
   * @param dto 参数说明
   */
  void transferTask(FlowTaskOperateDTO dto);

  /**
   * 委派任务（任务保留原办理人，被委派人处理后回到原办理人）
   *
   * @param dto 参数说明
   */
  void delegateTask(FlowTaskOperateDTO dto);

  /**
   * 退回任务
   *
   * @param dto 参数说明
   */
  void rejectTask(FlowTaskOperateDTO dto);

  /**
   * 终止流程
   *
   * @param processInstanceId 参数说明
   * @param reason 参数说明
   */
  void terminateProcess(String processInstanceId, String reason);

  /**
   * 挂起流程
   *
   * @param processInstanceId 参数说明
   */
  void suspendProcess(String processInstanceId);

  /**
   * 激活流程
   *
   * @param processInstanceId 参数说明
   */
  void activateProcess(String processInstanceId);

  /**
   * 查询用户待办任务列表（分页）
   *
   * <p>对标钉钉/飞书审批中心「我的待办」Tab，查询当前用户名下所有 PENDING / CLAIMED 状态的任务。 结果按创建时间降序排列，支持分页。
   *
   * @param userId 用户 ID
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 待办任务 Map 列表（含 taskId / flowCode / instanceId / nodeCode / nodeName / assigneeName /
   *     createdAt）
   */
  List<Map<String, Object>> listTodoTasks(String userId, int page, int size);

  /**
   * 查询用户已办任务列表（分页）
   *
   * <p>对标钉钉/飞书审批中心「我已审批」Tab，查询当前用户处理过的历史任务（APPROVED / REJECTED / DELEGATED）。 数据来源为 {@code
   * ydsz_flow_his_task} 归档表，结果按完成时间降序排列。
   *
   * @param userId 用户 ID
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 已办任务 Map 列表（含 taskId / flowCode / instanceId / nodeCode / action / comment / finishAt）
   */
  List<Map<String, Object>> listDoneTasks(String userId, int page, int size);

  /**
   * GAP-P0-1: 查全部流程实例（管理员视图）
   *
   * <p>对标钉钉/飞书/企微审批中心的"全部"Tab，管理员可查看当前租户下所有流程实例。 非管理员调用应由上层权限拦截（需要 workflow:monitor:view 权限）。
   *
   * <p>P0-2 修复：返回类型由 {@code List<Map>} 改为 {@code YdszResponse<Map>}， 保留 total / page /
   * size，避免前端假分页。
   *
   * @param businessType 业务类型（可空）
   * @param flowStatus 流程状态（可空）
   * @param startTime 开始时间下界（可空）
   * @param endTime 开始时间上界（可空）
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页实例 Map 列表
   */
  PageResponse<List<Map<String, Object>>> listAllInstances(
      String businessType,
      String flowStatus,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int page,
      int size);

  /**
   * 前加签 — 在当前审批人之前插入额外审批人
   *
   * <p>对标钉钉/飞书「前加签」能力。加签后流程变为：被加签人 → 原审批人 → 下一节点。 被加签人审批通过后，流程回到原审批人继续办理。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   */
  void countersignBeforeTask(FlowTaskOperateDTO dto);

  /**
   * 后加签 — 在当前审批人之后插入额外审批人
   *
   * <p>对标钉钉/飞书「后加签」能力。加签后流程变为：原审批人 → 被加签人 → 下一节点。 原审批人审批通过后，流程流转到被加签人办理，而非直接进入下一节点。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   */
  void countersignAfterTask(FlowTaskOperateDTO dto);

  /**
   * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
   *
   * @param dto 参数说明
   */
  void countersignParallelTask(FlowTaskOperateDTO dto);

  /**
   * 催办 — 向实例下所有待办任务的办理人发送催办通知
   *
   * <p>对标钉钉/飞书「催办」按钮。催办通过 IM 通道（钉钉/飞书/企微）发送提醒消息， 同时记录催办日志。催办频率受 SLA 配置的 {@code maxUrges} 上限控制。
   *
   * @param instanceId 流程实例 ID
   * @param operatorId 催办人 ID
   * @param comment 催办说明（可选）
   * @return 被催办人 ID 列表
   */
  List<String> urgeTask(String instanceId, String operatorId, String comment);

  /**
   * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点的待办任务
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码（null/空则退化为实例级催办）
   * @param operatorId 催办人 ID
   * @param comment 催办说明
   * @return 被催办人 ID 列表
   */
  List<String> urgeNodeTask(String instanceId, String nodeCode, String operatorId, String comment);

  /**
   * 撤回流程 — 发起人撤回正在运行中的流程实例
   *
   * <p>对标钉钉/飞书「撤回」能力。仅当流程处于 RUNNING 状态且当前待办节点尚未被审批时允许撤回。 撤回后流程回到发起人，发起人可修改表单后重新提交。
   *
   * @param processInstanceId 流程实例 ID
   * @param initiatorId 发起人 ID（仅发起人本人可撤回）
   * @return {@code true} 撤回成功；{@code false} 不满足撤回条件
   */
  boolean recallProcess(String processInstanceId, String initiatorId);

  /**
   * 查询审批轨迹（审计日志）
   *
   * <p>返回流程实例从启动到当前的所有操作记录（含通过/驳回/转办/委派/加签/撤回等）， 按时间正序排列，用于前端审批详情页的「审批记录」列表展示。
   *
   * @param processInstanceId 流程实例 ID
   * @return 审计记录列表（含 operatorName / action / nodeCode / nodeName / comment / timestamp）
   */
  List<Map<String, Object>> listAuditTrail(String processInstanceId);

  /**
   * 获取当前引擎类型标识
   *
   * <p>用于多引擎场景下区分底层实现（当前固定返回 {@code "YDSZ"}，标识自研引擎）。 业务侧可据此判断引擎能力特性，如是否支持子流程等。
   *
   * @return 引擎类型字符串
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
   * @param userId 操作人 ID
   * @param comment 审批意见
   */
  void batchPassTasks(List<String> taskIds, String userId, String comment);

  /**
   * GAP-P0-4: 一键通过所有待办 — 查询当前用户全部待办（上限 100 条）并逐一通过。
   *
   * <p>对标钉钉/飞书审批中心"一键通过"按钮。内部委托 {@link #batchPassTasks} 保证原子性。
   *
   * @param userId 操作人 ID
   * @param comment 审批意见（可选）
   * @return 实际通过的任务数量
   */
  int passAllTodoTasks(String userId, String comment);

  /**
   * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
   *
   * @param instanceId 实例 ID（字符串形式）
   * @return 时间线列表，每条记录包含
   *     type/timestamp/nodeCode/nodeName/assigneeId/assigneeName/action/comment/taskStatus
   */
  List<Map<String, Object>> getTimeline(String instanceId);

  /**
   * P2-4: 流程回放步骤序列 — 按时间顺序合并历史任务 + 审计日志 + 当前待办为统一步骤序列， 驱动前端 {@code FlowDiagramReplay}
   * 组件依次高亮节点并展示轨迹事件。
   *
   * @param instanceId 实例 ID（字符串形式）
   * @return 步骤列表（按 timestamp 升序），实例不存在时返回空列表
   */
  List<Map<String, Object>> getReplaySteps(String instanceId);

  // ======================== P0-03: 暂存待审 / 追加处理人 / 减签 / 已阅 / 沟通 ========================

  /**
   * 暂存待审 — 审批人保存审批意见草稿
   *
   * <p>对标钉钉/飞书「暂存」能力。审批人可在正式提交前先保存审批意见和附件， 草稿存储在 Redis 中（TTL 7 天），提交后自动清除。
   *
   * @param dto 任务操作参数（需含 taskId + comment + attachments）
   */
  void saveDraft(FlowTaskOperateDTO dto);

  /**
   * 追加处理人 — 在已有会签任务中追加审批人
   *
   * <p>对标钉钉/飞书「加人」能力。在任务进入会签节点后，动态追加新的审批人参与会签， 不影响已有审批人的审批状态。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   */
  void addApprover(FlowTaskOperateDTO dto);

  /**
   * 减签 — 从会签任务中移除指定审批人
   *
   * <p>对标钉钉/飞书「减签」能力。将会签任务中尚未办理的指定审批人移除， 已办理的审批人不受影响。移除后会签完成条件相应调整。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId）
   */
  void countersignRemoveTask(FlowTaskOperateDTO dto);

  /**
   * 已阅 — 标记任务已阅
   *
   * <p>对标钉钉/飞书「已阅」能力。抄送任务（CC）或会签任务中非办理人可以标记已阅， 标记后不再在「待办」列表中展示该任务。
   *
   * @param taskId 任务 ID
   * @param userId 操作人 ID
   */
  void markReadTask(String taskId, String userId);

  /**
   * 沟通 — 在任务下添加沟通评论
   *
   * <p>对标钉钉/飞书「沟通」能力。审批人可在任务下发起沟通，@提及其他同事提供意见， 被沟通人收到通知后可回复评论。沟通不阻塞流程推进。
   *
   * @param dto 任务操作参数（需含 taskId + comment + targetUserId）
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
  String resubmitProcess(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment);

  /**
   * P1-8: 流程重做 — 支持 redoMode 指定重做策略（RESTART / NEW_INSTANCE）。
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量（可空）
   * @param comment 重做说明（可选）
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE（null/空时默认 RESTART）
   * @return 实例 ID（RESTART 返回原 instanceId，NEW_INSTANCE 返回新 instanceId）
   */
  String resubmitProcess(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode);

  /**
   * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
   *
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @param reason 挂起原因（可选）
   */
  void suspendTask(String taskId, String operatorId, String reason);

  /**
   * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
   *
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   */
  void activateTask(String taskId, String operatorId);
}
