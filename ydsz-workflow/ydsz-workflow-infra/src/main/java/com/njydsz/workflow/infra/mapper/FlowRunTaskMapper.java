package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowRunTask;

/**
 * 待办任务运行态 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_run_task</code>（原 {@code ydsz_flow_task}，2026-07-06 重命名），存储进行中的待办任务。
 *
 * <p>待办任务是「某个节点 + 某个处理人 + 某种状态」的实例，运行态任务结束后迁移到 {@code ydsz_flow_his_task} 归档表。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_task_id — 任务 ID 唯一索引
 *   <li>idx_assignee — 处理人维度待办查询索引
 *   <li>idx_instance_id — 流程实例维度查询索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowRunTask 待办任务实体
 * @see com.njydsz.workflow.server.service.FlowTaskService 待办 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowRunTaskMapper extends BaseMapper<FlowRunTask> {

  /**
   * 根据实例 ID 查所有任务
   *
   * @param instanceId 流程实例 ID
   * @return 任务列表
   */
  List<FlowRunTask> selectByInstanceId(@Param("instanceId") String instanceId);

  /**
   * 查某实例的当前 PENDING 任务
   *
   * @param instanceId 流程实例 ID
   * @return PENDING 状态任务列表
   */
  List<FlowRunTask> selectPendingByInstance(@Param("instanceId") String instanceId);

  /**
   * 查某节点 PENDING 任务
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 节点编码
   * @return 该节点下 PENDING 状态任务列表
   */
  List<FlowRunTask> selectPendingByNode(
      @Param("instanceId") String instanceId, @Param("nodeCode") String nodeCode);

  /**
   * 查用户的待办
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @return 待办任务列表
   */
  List<FlowRunTask> selectTodoByAssignee(
      @Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * 查用户的待办（真分页：LIMIT/OFFSET）
   *
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页大小
   * @return 待办任务分页列表
   */
  List<FlowRunTask> selectTodoByAssigneePage(
      @Param("assigneeId") String assigneeId,
      @Param("tenantId") String tenantId,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计用户待办总数（用于分页计算总页数）
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @return 待办任务总数
   */
  long countTodoByAssignee(
      @Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * P1-1: 游标分页（Keyset Pagination）— 避免深度分页性能退化
   *
   * <p>基于上一页最后一条记录的 (priority, createdAt, id) 作为游标锚点， 查询下一页数据。相比 LIMIT/OFFSET 在大 offset
   * 场景下有显著性能优势（O(1) vs O(N)）。
   *
   * <p><b>使用方式：</b>首次查询传 lastPriority=null, lastCreatedAt=null, lastId=null； 后续查询传上一页最后一条记录的对应字段值。
   *
   * @param assigneeId 办理人 ID
   * @param tenantId 租户 ID
   * @param lastPriority 上一页最后一条的 priority（首次查询传 null）
   * @param lastCreatedAt 上一页最后一条的 createdAt（首次查询传 null）
   * @param lastId 上一页最后一条的 id（首次查询传 null，用于打破平局）
   * @param limit 每页大小
   * @return 下一页任务列表
   */
  List<FlowRunTask> selectTodoByAssigneeCursor(
      @Param("assigneeId") String assigneeId,
      @Param("tenantId") String tenantId,
      @Param("lastPriority") Integer lastPriority,
      @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
      @Param("lastId") String lastId,
      @Param("limit") int limit);

  /**
   * 查用户已办
   *
   * @param assigneeId 办理人用户 ID
   * @param tenantId 租户 ID
   * @return 已办任务列表
   */
  List<FlowRunTask> selectDoneByAssignee(
      @Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * 标记任务完成
   *
   * @param id 任务 ID
   * @param taskStatus 任务最终状态（COMPLETED/REJECTED）
   * @param comment 审批意见
   * @param finishAt 完成时间
   * @param durationMs 处理耗时毫秒
   * @return 受影响行数
   */
  int completeTask(
      @Param("id") String id,
      @Param("taskStatus") String taskStatus,
      @Param("comment") String comment,
      @Param("finishAt") LocalDateTime finishAt,
      @Param("durationMs") Long durationMs);

  /**
   * 会签计数器 +1
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int incrementFinished(@Param("id") String id);

  /**
   * 取消某实例下所有 PENDING 任务
   *
   * @param instanceId 流程实例 ID
   * @param taskStatus 目标取消状态
   * @return 受影响行数
   */
  int cancelByInstance(
      @Param("instanceId") String instanceId, @Param("taskStatus") String taskStatus);

  /**
   * P0-1: 取消单个任务（边界事件触发时使用）
   *
   *
   * @param id 任务 ID
   * @param taskStatus 目标取消状态
   * @param comment 取消原因
   * @return 受影响行数
   */
  int cancelTask(
      @Param("id") String id,
      @Param("taskStatus") String taskStatus,
      @Param("comment") String comment);

  /**
   * 跳过某节点剩余 PENDING（同会签场景）
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 节点编码
   * @param taskStatus 目标跳过状态
   * @return 受影响行数
   */
  int skipByNode(
      @Param("instanceId") String instanceId,
      @Param("nodeCode") String nodeCode,
      @Param("taskStatus") String taskStatus);

  /**
   * P2-18: 冻结某实例下所有 PENDING/CLAIMED 任务（流程挂起时调用）
   *
   *
   * @param instanceId 流程实例 ID
   * @return 受影响行数
   */
  int freezeByInstance(@Param("instanceId") String instanceId);

  /**
   * P2-18: 解冻某实例下所有 FROZEN 任务（流程激活时调用，回到 PENDING）
   *
   *
   * @param instanceId 流程实例 ID
   * @return 受影响行数
   */
  int unfreezeByInstance(@Param("instanceId") String instanceId);

  /**
   * 统计某实例某节点的未完成任务数（用于并行网关 join 判断）
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 节点编码
   * @return 未完成任务数
   */
  int countPendingByNode(
      @Param("instanceId") String instanceId, @Param("nodeCode") String nodeCode);

  /**
   * 更新会签计数（设置 approveFinished）
   *
   * @param id 任务 ID
   * @param approveFinished 会签已完成数
   * @return 受影响行数
   */
  int updateApproveFinished(
      @Param("id") String id, @Param("approveFinished") Integer approveFinished);

  /**
   * 更新任务办理人信息（用于会签场景下多人共用一个任务时切换办理人）
   *
   * @param id 任务 ID
   * @param assigneeId 新办理人 ID
   * @param assigneeName 新办理人姓名
   * @param assigneeType 办理人类型
   * @return 受影响行数
   */
  int updateAssignee(
      @Param("id") String id,
      @Param("assigneeId") String assigneeId,
      @Param("assigneeName") String assigneeName,
      @Param("assigneeType") String assigneeType);

  /**
   * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
   * 查询逾期任务。
   *
   * @param assigneeId 处理人 ID
   * @param tenantId 租户 ID
   * @param limit 每页大小
   * @return 逾期任务列表
   */
  List<FlowRunTask> selectOverdue(
      @Param("assigneeId") String assigneeId,
      @Param("tenantId") String tenantId,
      @Param("limit") int limit);

  /**
   * P2-32: 统计超期任务数量
   *
   * @param assigneeId 办理人 ID（可空，为空时统计全部）
   * @param tenantId 租户 ID（可空）
   * @return 超期任务数量
   */
  long countOverdue(@Param("assigneeId") String assigneeId, @Param("tenantId") String tenantId);

  /**
   * P1-6: SLA 扫描 — 拉取所有设置了 dueAt 且未完成的任务（用于 SLA 调度器扫描）
   *
   * <p>扫描条件：task_status IN (PENDING, CLAIMED) AND due_at IS NOT NULL AND deleted = 0
   *
   * @param limit 单次扫描上限
   * @return 候选 SLA 任务列表
   */
  List<FlowRunTask> selectSlaCandidates(@Param("limit") int limit);

  /**
   * P2-7: 超期任务 Top N 排行 — 按超期时长降序返回最严重的超期任务。
   *
   * <p>审批中心"超期任务"看板。超期时长 = now - due_at。 返回 Map 字段对齐前端 OverdueTaskDTO： taskId / instanceId /
   * flowCode / flowName / title / nodeName / assigneeId / assigneeName / dueAt / overdueHours /
   * urgeCount。
   *
   * @param tenantId 租户 ID（可空）
   * @param limit 返回条数上限
   * @return 超期任务列表，按超期时长降序
   */
  List<Map<String, Object>> selectOverdueTopN(
      @Param("tenantId") String tenantId, @Param("limit") int limit);

  /**
   * P0-2: 查询关联实例已处于终态但任务仍为 PENDING/CLAIMED 的异常任务
   *
   * <p>用于一致性对账：找出「实例已结束但任务未清理」的数据不一致场景（可能由任务取消逻辑遗漏等原因导致）。
   *
   * @param limit 返回条数上限
   * @return 异常任务列表（task_id / task_status / instance_id / flow_code / node_code）
   */
  List<Map<String, Object>> selectOrphanPendingTasks(@Param("limit") int limit);

  /**
   * P0-2: 批量更新任务状态为 CANCELLED（一致性修复用）
   *
   * @param taskIds 任务 ID 列表
   * @param updatedBy 操作人（系统修复记为 0）
   * @return 更新行数
   */
  int batchCancelTasks(@Param("taskIds") List<String> taskIds, @Param("updatedBy") long updatedBy);

  /**
   * P2-7: 审批人负载分布 — 统计各审批人当前待办数量（PENDING + CLAIMED）。
   *
   * <p>审批人负载看板，用于识别负载不均。返回 Map 字段对齐前端 ApproverWorkloadDTO：assigneeId / assigneeName /
   * pendingCount / claimedCount / totalCount / overdueCount。
   *
   * @param tenantId 租户 ID（可空）
   * @param limit 返回条数上限
   * @return 审批人负载列表，按 totalCount 降序
   */
  List<Map<String, Object>> selectWorkloadByAssignee(
      @Param("tenantId") String tenantId, @Param("limit") int limit);

  /**
   * P1-6: 增加 SLA 催办计数
   *
   * @param id 任务 ID
   * @param urgeCount 新的催办计数
   * @param lastUrgedAt 最近催办时间
   * @return 受影响行数
   */
  int incrementUrgeCount(
      @Param("id") String id,
      @Param("urgeCount") int urgeCount,
      @Param("lastUrgedAt") LocalDateTime lastUrgedAt);

  /**
   * P1-6: 标记 SLA 动作（用于审计：AUTO_PASS / AUTO_REJECT / ESCALATE 等）
   *
   * @param id 任务 ID
   * @param slaAction SLA 动作（AUTO_PASS/AUTO_REJECT/ESCALATE）
   * @param slaEscalated 是否已升级
   * @return 受影响行数
   */
  int markSlaAction(
      @Param("id") String id,
      @Param("slaAction") String slaAction,
      @Param("slaEscalated") Integer slaEscalated);

  /**
   * P2-1: 标记任务已处理（补录审批场景）。
   *
   * @param id 任务 ID
   * @param userId 办理人 ID
   * @param comment 审批意见
   * @param processedAt 生效时间（补录时间）
   * @return 受影响行数
   */
  int markProcessed(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("comment") String comment,
      @Param("processedAt") LocalDateTime processedAt);

  /**
   * GAP-A1: 会签通过计数原子自增（带饱和守卫，防并发丢失更新与越界计数）。
   *
   * <p>守卫条件：{@code approve_count IS NULL OR approve_finished < approve_count} + {@code deleted = 0}。
   * 不含任务状态守卫——并行会签首个办理人完成后该行即转 COMPLETED，其余办理人仍需计入票数。
   *
   * @param id 任务 ID
   * @return 受影响行数；0 表示任务不存在或计数已饱和
   */
  int incrementApproveFinished(@Param("id") String id);

  /**
   * GAP-A1: 票签权重原子累加（防并发丢失更新）。
   *
   * @param id 任务 ID
   * @param weight 本次累加的权重值
   * @return 受影响行数
   */
  int incrementApproveWeight(@Param("id") String id, @Param("weight") int weight);
}
