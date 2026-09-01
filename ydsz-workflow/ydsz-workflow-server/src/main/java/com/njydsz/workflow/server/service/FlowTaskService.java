package com.njydsz.workflow.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * 待办任务 Service
 *
 * <p>流程任务（{@link FlowRunTask}）是工作流引擎的<b>调度核心</b>，本 Service 负责任务的整个生命周期：
 * 创建、查询、签收、通过、驳回、转办、委派、加签/减签等。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>任务创建</b>：流程推进到审批节点时创建任务（{@link #createTask}）
 *   <li><b>办理动作</b>：签收（{@link #claim}）/ 通过（{@link #pass}）/ 驳回（{@link #reject}）
 *   <li><b>任务流转</b>：转办（{@link #transfer}）/ 委派（{@link #delegate}）/ 加签减签
 *   <li><b>任务查询</b>：按实例 / 按办理人 / 分页 / 待办智能排序
 *   <li><b>催办与超时</b>：催办、加签、SLA 超时处理
 *   <li><b>会签策略</b>：或签/顺序会签/并行会签/FOREACH并行（{@code CountersignStrategy}）
 * </ul>
 *
 * <p><b>事务边界：</b>所有办理动作（{@code pass/reject/transfer/delegate/claim}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保「会签计数 + 任务状态 + 流程实例推进 + 审计日志」原子性。
 *
 * <p><b>并发控制：</b>
 *
 * <ul>
 *   <li>悲观锁：{@code pass/reject} 时 {@code SELECT ... FOR UPDATE} 锁任务行，避免并发办理
 *   <li>分布式锁：跨实例的全局锁（如「同发起人同时只能有一个流程」场景）由 {@code Redisson} 实现
 *   <li>乐观锁：{@link FlowRunTask} 继承 {@code MpBaseEntity.revision}，并发更新自动重试
 * </ul>
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>「我的待办」使用 {@code ydsz_flow_run_task} 复合索引（{@code idx_assignee}）
 *   <li>任务完成 → 归档调度器异步迁移至 {@code ydsz_flow_his_task}，避免主表膨胀
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.FlowTaskServiceImpl 实现类（编排器，委派给具体的子服务）
 * @see FlowInstanceService 流程实例 Service
 * @see CountersignStrategy 会签策略接口
 */
public interface FlowTaskService {

  /**
   * 创建任务（流程推进到审批节点时由路由引擎调用）
   *
   * <p>根据流程节点 {@code node} 解析审批人（角色/部门/岗位/直属上级/Spec 表达式）， 写入 {@link FlowRunTask} 表。会签节点（{@code
   * performType=ALL/PARALLEL}）按 {@code approvers} 数量 生成多条子任务，单人节点生成单条任务。
   *
   * @param instanceId 流程实例 ID
   * @param node 流程节点（{@code nodeCode} / {@code nodeName} / {@code approverSpec} / {@code
   *     performType}）
   * @param variables 流程变量（用于解析审批人 Spec 表达式，如 ${starter}）
   * @return 新创建的任务 ID（单人节点）或首个任务 ID（会签节点）
   */
  String createTask(String instanceId, FlowNodeVO node, Map<String, Object> variables);

  /**
   * P2-20: 按 ID 查任务（任务详情查询）
   *
   * @param taskId 任务 ID
   * @return 任务 DO，不存在返回 null
   */
  FlowRunTaskVO getById(String taskId);

  /**
   * 签收任务（多人任务转单人办理）
   *
   * <p>仅对 PENDING 状态任务有效；签收后任务状态变为 CLAIMED， 签收人记入 {@code claimer} 字段。会签任务签收后不影响其它子任务状态。
   *
   * @param taskId 任务 ID
   * @param userId 签收人 ID（必须为任务的 assignee 之一）
   */
  void claim(String taskId, String userId);

  /**
   * 通过任务（审批人执行审批动作）
   *
   * <p>会签节点会累加 approveCount，达到阈值后推进流程实例。 单一任务直接推进到下一节点。事务内会写审计日志、触发任务通过事件。
   *
   * @param dto 任务操作参数（taskId / userId / comment / variables）
   */
  void pass(FlowTaskOperateDTO dto);

  /**
   * 驳回任务（审批人执行驳回动作）
   *
   * <p>支持两种驳回策略（由 {@code rejectStrategy} 决定）：
   *
   * <ul>
   *   <li>驳回到上一节点（{@code TO_PREVIOUS}）
   *   <li>驳回到发起人（{@code TO_START}）
   * </ul>
   *
   * 驳回后实例状态变为 REJECTED 或退回到指定节点重新审批。
   *
   * @param dto 任务操作参数（taskId / userId / comment / targetNodeCode）
   */
  void reject(FlowTaskOperateDTO dto);

  /**
   * 转办任务（审批人把任务转交给其他人办理，自己退出审批人列表）
   *
   * <p>转办能力。原 assignee 解除，转给新 assignee 继续办理。 转办后流程审计日志会记录 {@code TRANSFER} 动作。
   *
   * @param dto 任务操作参数（taskId / userId / targetUserId / comment）
   */
  void transfer(FlowTaskOperateDTO dto);

  /**
   * 委派任务（审批人把任务委派给其他人办理，但自己仍是责任人）
   *
   * <p>委派能力。被委派人完成后，任务仍由委派人确认。 区别于转办：委派不改变任务 assignee，委派人仍为最终责任人。
   *
   * @param dto 任务操作参数（taskId / userId / targetUserId / comment）
   */
  void delegate(FlowTaskOperateDTO dto);

  /**
   * 取消某实例的全部 PENDING 任务（终止/驳回终态时使用）
   *
   * <p>调用场景：流程实例被驳回到发起人、流程被管理员强制终止、流程被发起人撤回。 取消后任务状态变为 CANCELED，保留审计轨迹。
   *
   * @param instanceId 流程实例 ID
   * @param reason 取消原因（写入每条任务的 comment 字段）
   */
  void cancelByInstance(String instanceId, String reason);

  /**
   * 查实例的当前 PENDING 任务
   *
   * @param instanceId 流程实例 ID
   * @return 当前所有 PENDING 状态的任务列表（含 CLAIMED）
   */
  List<FlowRunTaskVO> listPendingByInstance(String instanceId);

  /**
   * 查用户的待办（不分页）
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @return 用户的待办任务列表
   */
  List<FlowRunTaskVO> listTodoByAssignee(String assigneeId, String tenantId);

  /**
   * P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET）
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页结果
   */
  PageResponse<List<FlowRunTaskVO>> listTodoByAssigneePage(
      String assigneeId, String tenantId, int page, int size);

  /**
   * P2-1: 游标分页查询用户待办任务（Keyset Pagination）。
   *
   * <p>基于上一页最后一条记录的 (priority, createdAt, id) 作为游标锚点，查询下一页数据。
   * 相比 LIMIT/OFFSET 在大 offset 场景下有显著性能优势（O(1) vs O(N)）。
   *
   * <p><b>使用方式：</b>首次查询传 {@code lastPriority=null, lastCreatedAt=null, lastId=null}；
   * 后续查询传上一页最后一条记录的对应字段值。
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @param lastPriority 上一页最后一条的 priority（首次查询传 null）
   * @param lastCreatedAt 上一页最后一条的 createdAt（首次查询传 null）
   * @param lastId 上一页最后一条的 id（首次查询传 null，用于打破平局）
   * @param limit 每页大小
   * @return 下一页任务列表
   */
  List<FlowRunTaskVO> listTodoByAssigneeCursor(
      String assigneeId,
      String tenantId,
      Integer lastPriority,
      LocalDateTime lastCreatedAt,
      String lastId,
      int limit);

  /**
   * 查用户的已办（不分页）
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @return 用户的已办任务列表（按完成时间倒序）
   */
  List<FlowRunTaskVO> listDoneByAssignee(String assigneeId, String tenantId);

  /**
   * P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET）
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页结果
   */
  PageResponse<List<FlowRunTaskVO>> listDoneByAssigneePage(
      String assigneeId, String tenantId, int page, int size);

  /**
   * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + ydsz_flow_user 关联）
   * 
   *
   * @param userId 用户 ID
   * @param roleCodes 角色编码列表（可空）
   * @param deptIds 部门 ID 列表（可空）
   * @param tenantId 租户 ID
   * @return 多维匹配的待办任务列表
   */
  List<FlowRunTaskVO> listTodoByUser(
      String userId, List<String> roleCodes, List<String> deptIds, String tenantId);

  /**
   * P1-7: 前加签 — 在当前节点前插入临时审批人
   *
   * <p>前加签能力。在当前审批节点前插入临时审批人，
   * 加签人先审批，全部通过后由原审批人继续。会签模式切换为 PARALLEL。
   *
   * @param dto 任务操作参数（taskId / userId / targetUserId / targetUserName）
   */
  void countersignBefore(FlowTaskOperateDTO dto);

  /**
   * P1-7: 后加签 — 在当前节点通过后、下一节点前插入临时审批人
   *
   * <p>后加签能力。原审批人通过后，加签人先于下一节点审批。
   * 加签人通过后流程才推进到下一节点。会签模式切换为 PARALLEL。
   *
   * @param dto 任务操作参数（taskId / userId / targetUserId / targetUserName）
   */
  void countersignAfter(FlowTaskOperateDTO dto);

  /**
   * GAP-P0-3: 并加签 — 动态追加审批人与原审批人并行审批，所有人审完后才推进。
   *
   * <p>并加签能力。当前审批人尚未审批时动态追加，
   * 加签人与原审批人<b>并行</b>审批（performType 强制切换为 PARALLEL），
   * 所有人全部通过后才推进到下一节点。
   *
   * <p>与 {@link #countersignAfter}（后加签，PARALLEL 并行）的区别：
   * 后加签是"当前人审完→加签人审"的串行流程；并加签是"当前人+加签人同时审"的并行流程。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   * @since 1.0.0
   */
  void countersignParallel(FlowTaskOperateDTO dto);

  /**
   * GAP-P1: 减签 — 从会签任务中移除指定审批人
   *
   * <p>减签功能。从 ydsz_flow_user 中删除指定用户， 并更新任务的 approveCount（应到人数）。
   *
   * @param dto 任务操作参数（需含 taskId + userId 为被减签人）
   */
  void countersignRemove(FlowTaskOperateDTO dto);

  /**
   * GAP-P2: 已阅 — 标记任务已阅（不改变任务状态，仅记录审计日志）
   *
   * @param taskId 任务 ID
   * @param userId 操作人 ID
   */
  void markRead(String taskId, String userId);

  /**
   * GAP-P2: 沟通 — 在任务下添加沟通评论（不改变任务状态）
   *
   * @param dto 任务操作参数（需含 taskId + userId + comment）
   */
  void communicate(FlowTaskOperateDTO dto);

  /**
   * P1-9: 催办 — 通知当前节点所有待办处理人
   * 
   * 
   *
   * @param instanceId 流程实例 ID
   * @param operatorId 催办人用户 ID
   * @param comment 催办说明
   * @return 被催办人 ID 列表
   */
  List<String> urge(String instanceId, String operatorId, String comment);

  /**
   * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
   *
   * <p>当 nodeCode 为 null 或空时，退化为 {@link #urge} 的实例级催办行为。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码（指定则只催办该节点的待办）
   * @param operatorId 催办人 ID
   * @param comment 催办说明
   * @return 被催办人 ID 列表
   */
  List<String> urgeByNode(String instanceId, String nodeCode, String operatorId, String comment);

  /**
   * P2-25: 自由跳转 — 管理员强制跳转到任意节点
   *
   * <p>完成当前任务、取消同实例其他 PENDING 任务、在目标节点创建新任务。
   *
   * @param dto 任务操作参数（需含 taskId + targetNodeCode）
   */
  void jump(FlowTaskOperateDTO dto);

  /**
   * P2-26: 批量审批 — 对多个任务逐一执行 pass，@Transactional 保证原子性
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 审批意见
   */
  void batchPass(List<String> taskIds, String userId, String comment);

  /**
   * P1-4: 批量驳回 — 对多个任务逐一执行 reject，@Transactional 保证原子性。
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 审批意见
   * @param targetNodeCode 退回目标节点编码（可选，为空时走默认退回逻辑）
   */
  void batchReject(List<String> taskIds, String userId, String comment, String targetNodeCode);

  /**
   * P1-4: 批量转办 — 对多个任务逐一执行 transfer，@Transactional 保证原子性。
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 转办说明
   * @param targetUserId 目标人 ID
   * @param targetUserName 目标人姓名
   */
  void batchTransfer(
      List<String> taskIds,
      String userId,
      String comment,
      String targetUserId,
      String targetUserName);

  /**
   * P1-4: 批量催办 — 对多个实例逐一执行 urge，单个失败不影响其他。
   *
   * @param instanceIds 实例 ID 列表
   * @param operatorId 操作人 ID
   * @param comment 催办说明
   * @return 成功催办的实例数量
   */
  int batchUrge(List<String> instanceIds, String operatorId, String comment);

  /**
   * 将任务实体转换为视图对象
   *
   * <p>用于「我的待办」列表渲染，组装 assigneeName / claimerName / flowName 等富化字段， 由 {@code NameAssembler}
   * 跨服务查询用户/流程名称后填入。
   *
   * @param task 任务 DO
   * @return 任务视图 VO（含基础字段 + 富化字段）
   */
  FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTaskVO task);

  /**
   * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID（可空）
   * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
   */
  List<Map<String, Object>> nodeDurationStats(String flowCode, String tenantId);

  /**
   * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
   *
   * @param assigneeId 办理人 ID（可空，为空时查全部）
   * @param tenantId 租户 ID（可空）
   * @param limit 返回条数上限
   * @return 超期任务列表
   */
  List<FlowRunTaskVO> listOverdue(String assigneeId, String tenantId, int limit);

  /**
   * P2-32: 统计超期任务数量
   *
   * @param assigneeId 办理人 ID（可空，为空时统计全部）
   * @param tenantId 租户 ID（可空）
   * @return 超期任务数量
   */
  long countOverdue(String assigneeId, String tenantId);

  /**
   * P2-4: 统计待办任务总数（PENDING + CLAIMED）
   *
   * @param tenantId 租户 ID（可空）
   * @return 待办任务数量
   */
  long countPending(String tenantId);

  /**
   * P2-33: 已办多维筛选分页查询（真分页：SQL LIMIT/OFFSET）
   *
   * @param assigneeId 办理人 ID（可空）
   * @param businessType 业务类型（可空）
   * @param flowCode 流程编码（可空）
   * @param startTime 完成时间下界（可空）
   * @param endTime 完成时间上界（可空）
   * @param tenantId 租户 ID（可空）
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页结果
   */
  PageResponse<List<FlowRunTaskVO>> listDoneByAssigneePageMulti(
      String assigneeId,
      String businessType,
      String flowCode,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int page,
      int size);

  /**
   * P2-36: 标记任务超时
   *
   * <p>校验任务状态为 PENDING/CLAIMED，更新为 TIMEOUT，写审计日志并触发 onTaskTimeout 事件。 当前仅实现标记超时 +
   * 触发事件，节点超时策略（自动通过/自动驳回/仅提醒）后续扩展。
   *
   * @param taskId 任务 ID
   * @param reason 超时原因（可选）
   */
  void timeoutTask(String taskId, String reason);

  // ======================== P0-03: 暂存待审 / 追加处理人 ========================

  /**
   * GAP-P0: 暂存待审 — 审批人保存审批意见草稿（不改变任务主状态）
   *
   * <p>将审批意见保存到任务 comment 字段，任务状态保持 PENDING/CLAIMED 不变， 写审计日志记录 SAVE_DRAFT 操作。审批"暂存"功能。
   *
   * @param dto 任务操作参数（需含 taskId + userId + comment）
   */
  void saveDraft(FlowTaskOperateDTO dto);

  /**
   * GAP-P0: 追加处理人 — 在已有会签任务中追加一个审批人
   *
   * <p>对标 FlowString 的"追加处理人"功能。向 ydsz_flow_user 插入新审批人， approveCount +1，保持当前会签模式不变。比加签更轻量，不改变
   * performType。
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   */
  void addApprover(FlowTaskOperateDTO dto);

  /**
   * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起（不推进、不计超时），激活后回到 PENDING。
   *
   * <p>任务挂起能力。与实例级挂起（{@code suspendProcess}）的区别：
   *
   * <ul>
   *   <li>实例级挂起：整个实例全部 PENDING/CLAIMED 任务连带冻结为 FROZEN；
   *   <li>任务级挂起：仅挂起指定任务为 SUSPENDED，其它任务不受影响。
   * </ul>
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>任务存在；
   *   <li>任务状态为 PENDING 或 CLAIMED（已签收但未完成）；
   *   <li>挂起后任务状态 → SUSPENDED，写审计日志 action=SUSPEND。
   * </ul>
   *
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @param reason 挂起原因（可选，写入 comment）
   * @since 1.0.0
   */
  void suspendTask(String taskId, String operatorId, String reason);

  /**
   * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>任务存在；
   *   <li>任务状态为 SUSPENDED；
   *   <li>激活后任务状态 → PENDING（清空签收人，需重新签收），写审计日志 action=ACTIVATE。
   * </ul>
   *
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @since 1.0.0
   */
  void activateTask(String taskId, String operatorId);

  /**
   * P1-3: 取回 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
   *
   * <p>取回能力。与发起人撤回（{@link com.njydsz.workflow.server.service.FlowInstanceService#recall})
   * 不同， 取回是<b>审批人</b>维度：审批人已 PASS 后，下一节点尚未处理时，可取回自己的审批， 流程退回到审批人所在节点重新审批。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>历史任务存在且 taskStatus=COMPLETED；
   *   <li>操作人必须是历史任务的办理人（assigneeId）；
   *   <li>实例状态为 RUNNING；
   *   <li>下一节点的待办任务必须全部为 PENDING（未签收/未完成）。
   * </ul>
   *
   * @param hisTaskId 历史任务 ID（ydsz_flow_his_task.id）
   * @param operatorId 操作人 ID（校验与 hisTask.assigneeId 一致）
   * @param comment 取回说明（可选）
   * @return 新创建的待办任务 ID
   * @since 1.0.0
   */
  String retract(String hisTaskId, String operatorId, String comment);

  // ======================== VO 查询方法（供 Controller 层使用，避免 DO 泄漏） ========================

  /**
   * 根据任务 ID 获取实例 ID（驳回候选节点查询场景，避免 Controller 层直接接触 DO）。
   *
   * @param taskId 任务 ID
   * @return 实例 ID，任务不存在返回 null
   */
  String getTaskInstanceId(String taskId);

  /**
   * 查询实例经过的历史节点（去重，按首次完成时间排序），用于驳回时让用户选择驳回到任意历史节点。
   *
   * @param instanceId 流程实例 ID
   * @return 节点列表：nodeCode / nodeName / firstFinishAt / assigneeName
   */
  List<Map<String, Object>> listPassedNodes(String instanceId);

  /**
   * 待办任务分页查询（VO 版本）
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param page 页码
   * @param size 每页大小
   * @return 分页结果（VO）
   */
  PageResponse<List<FlowRunTaskVO>> pageTodoVO(
      String userId, String tenantId, String flowCode, String businessType,
      LocalDateTime startTime, LocalDateTime endTime, int page, int size);

  /**
   * 已办任务分页查询（VO 版本）
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param page 页码
   * @param size 每页大小
   * @return 分页结果（VO）
   */
  PageResponse<List<FlowRunTaskVO>> pageDoneVO(
      String userId, String tenantId, String flowCode, String businessType,
      LocalDateTime startTime, LocalDateTime endTime, int page, int size);

  /**
   * 已办任务多维筛选分页查询（VO 版本）
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param keyword 关键字（可选，匹配 title / businessNo）
   * @param page 页码
   * @param size 每页大小
   * @return 分页结果（VO）
   */
  PageResponse<List<FlowRunTaskVO>> pageDoneSearchVO(
      String userId, String tenantId, String flowCode, String businessType,
      LocalDateTime startTime, LocalDateTime endTime, String keyword, int page, int size);

  /**
   * 超期任务查询（VO 版本）
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param limit 返回条数上限
   * @return 超期任务列表（VO）
   */
  List<FlowRunTaskVO> listOverdueVO(String userId, String tenantId, int limit);

  /**
   * 查询流程实例的加签历史（Map 形式，避免 Controller 接触 DO）。
   *
   * <p><b>注意：</b>此方法返回全量数据，适用于数据量较小的场景。
   * 大数据量场景请使用 {@link #pageCountersignByInstance(String, int, int)}。
   *
   * @param instanceId 流程实例 ID
   * @return 加签历史列表
   */
  List<Map<String, Object>> listCountersignByInstance(String instanceId);

  /**
   * 分页查询流程实例的加签历史（P1-8: 数据库级分页，避免内存分页 OOM 风险）。
   *
   * @param instanceId 流程实例 ID
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 分页结果（含 total 和 records）
   */
  PageResponse<List<Map<String, Object>>> pageCountersignByInstance(
      String instanceId, int pageNo, int pageSize);

  /**
   * 查询任务的加签历史（Map 形式，避免 Controller 接触 DO）
   *
   * @param taskId 任务 ID
   * @return 加签历史列表
   */
  List<Map<String, Object>> listCountersignByTask(String taskId);

  /**
   * 一键通过全部待办
   *
   * @param userId 用户 ID
   * @param userName 用户姓名
   * @return 通过数量
   */
  int passAll(String userId, String userName);

  /**
   * 超期任务统计（按流程编码分组）
   *
   * @param flowCode 流程编码（可选）
   * @param startTime 统计开始时间（可选）
   * @param endTime 统计结束时间（可选）
   * @return 超期统计列表
   */
  List<Map<String, Object>> overdueStats(String flowCode, LocalDateTime startTime, LocalDateTime endTime);

  /**
   * 超期任务 Top N 排行（按超期时长降序）
   *
   * @param tenantId 租户 ID
   * @param limit 返回条数上限
   * @return 超期任务列表
   */
  List<Map<String, Object>> selectOverdueTopN(String tenantId, int limit);

  /**
   * 审批人负载分布（当前待办数量）
   *
   * @param tenantId 租户 ID
   * @param limit 返回条数上限
   * @return 审批人负载列表
   */
  List<Map<String, Object>> selectWorkloadByAssignee(String tenantId, int limit);

  /**
   * 手动触发单条任务的 SLA 处理（按 taskId，避免 Controller 层接触 DO）。
   *
   * <p>内部通过 {@code FlowSlaService#processOverdue} 执行 SLA 策略，返回：
   * <ul>
   *   <li>{@code null} — 任务不存在</li>
   *   <li>{@code true} — 已处理（REMIND / 最终动作）</li>
   *   <li>{@code false} — 跳过（未到期 / 已完成 / 状态不符）</li>
   * </ul>
   *
   * @param taskId 任务 ID
   * @return Boolean（nullable）
   */
  Boolean slaProcessByTaskId(String taskId);
}
