package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.constant.DataSourceConstants;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.repository.FlowUserRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.converter.WorkflowConverter;

/**
 * 待办任务 — 查询类 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 单体（1847 行）按职责拆分的<b>只读查询子服务</b>。 通过
 * {@code @DS(DataSourceConstants.SLAVE)} 强制走从库（只读副本），减轻主库压力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>任务详情</b>：{@link #getById} — 单任务查询（主键索引）
 *   <li><b>待办列表</b>：{@code listTodoByAssignee} / {@code listTodoByAssigneePage} / {@code
 *       listTodoByUser} — 分别覆盖「我的待办」「真分页」「多维匹配（直接分配 + ROLE/DEPT 展开）」
 *   <li><b>已办列表</b>：{@code listDoneByAssignee} / {@code listDoneByAssigneePage} / {@code
 *       listDoneByAssigneePageMulti} — 真分页 + 多维筛选
 *   <li><b>实例待办</b>：{@code listPendingByInstance} — 推进器内部使用
 *   <li><b>超期统计</b>：{@code listOverdue} / {@code countOverdue} — P2-32 SLA 监控
 *   <li><b>耗时统计</b>：{@code nodeDurationStats} — P2-31 节点级效率分析
 *   <li><b>视图转换</b>：{@link #toView} — 实体转 VO
 * </ul>
 *
 * <p><b>事务边界：</b>类级别 {@code @Transactional(readOnly = true)}，所有方法走只读事务， 配合 {@code @DS(SLAVE)}
 * 实现读写分离。
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>「我的待办」走 {@code ydsz_flow_run_task} 复合索引 {@code idx_assignee}
 *   <li>「已办分页」走 {@code idx_assignee_completed_at} 复合索引，避免大 OFFSET
 *   <li>{@code listTodoByUser} 走 {@code ydsz_flow_user} 关联表，避免 IN 子查询超过 PG 1000 上限
 *   <li>从库路由由 {@code DynamicDataSource} 切面自动完成，调用方无感知
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskServiceImpl 任务服务门面
 * @see FlowRunTaskDO 运行时任务实体
 * @see FlowHisTaskDO 历史任务实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceConstants.SLAVE)
@Transactional(readOnly = true)
public class FlowTaskQueryServiceImpl {

  /** 运行时任务仓储，查询待办/已办任务列表 */
  private final FlowRunTaskRepository taskRepository;

  /** 历史任务仓储，查询已归档的已办任务 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** 审计日志仓储，查询加签历史 */
  private final FlowAuditLogRepository auditLogRepository;

  /** 用户仓储，查询流程用户关联 */
  private final FlowUserRepository userRepository;

  /** DO/VO 转换器 */
  private final WorkflowConverter converter;

  // ============================== 详情查询 ==============================

  /**
   * P2-20: 按 ID 查任务（任务详情查询）
   *
   * @param taskId 任务 ID
   * @return 任务 VO，不存在返回 null
   */
  public FlowRunTaskVO getById(String taskId) {
    if (taskId == null) {
      return null;
    }
    return taskRepository.findById(taskId).orElse(null);
  }

  // ============================== 列表查询 ==============================

  /** 查实例的当前 PENDING 任务 */
  public List<FlowRunTaskVO> listPendingByInstance(String instanceId) {
    return taskRepository.findPendingByInstance(instanceId);
  }

  /** 查用户的待办 */
  @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
  public List<FlowRunTaskVO> listTodoByAssignee(String assigneeId, String tenantId) {
    // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return taskRepository.selectTodoByAssignee(assigneeId, tid);
  }

  /**
   * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + ydsz_flow_user 关联）
   *
   * @param userId 用户 ID
   * @param roleCodes 用户拥有的角色编码（可空）
   * @param deptIds 用户所属部门 ID（字符串形式，可空）
   * @param tenantId 租户 ID（可空，默认 "1"）
   */
  @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
  public List<FlowRunTaskVO> listTodoByUser(
      String userId, List<String> roleCodes, List<String> deptIds, String tenantId) {
    // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    Set<FlowRunTaskVO> result = new LinkedHashSet<>();
    // 1. 直接分配给该用户的任务
    result.addAll(taskRepository.selectTodoByAssignee(String.valueOf(userId), tid));
    // 2. 通过 ydsz_flow_user 关联的任务
    List<String> taskIds = userRepository.selectTaskIdsByUser(String.valueOf(userId), tid);
    if (taskIds != null && !taskIds.isEmpty()) {
      for (String taskId2 : taskIds) {
        FlowRunTaskVO t = taskRepository.findById(taskId2).orElse(null);
        if (t != null && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
          result.add(t);
        }
      }
    }
    // 3. ROLE/DEPT 匹配
    if (roleCodes != null) {
      for (String rc : roleCodes) {
        result.addAll(taskRepository.selectTodoByAssignee(rc, tid));
      }
    }
    if (deptIds != null) {
      for (String did : deptIds) {
        result.addAll(taskRepository.selectTodoByAssignee(did, tid));
      }
    }
    return new ArrayList<>(result);
  }

  /** 查用户的已办 */
  @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
  public List<FlowRunTaskVO> listDoneByAssignee(String assigneeId, String tenantId) {
    // P0-3: 改查历史表
    // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    List<FlowHisTaskVO> hisTasks = hisTaskRepository.selectDoneByAssignee(assigneeId, tid);
    List<FlowRunTaskVO> result = new ArrayList<>();
    for (FlowHisTaskVO his : hisTasks) {
      result.add(hisTaskVoToRunTaskVo(his));
    }
    return result;
  }

  // ============================== 分页查询 ==============================

  /** P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET） */
  public PageResponse<List<FlowRunTaskVO>> listTodoByAssigneePage(
      String assigneeId, String tenantId, int page, int size) {
    // P2-17: 真分页（SQL LIMIT/OFFSET）
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    int safePage = Math.max(1, page);
    int safeSize = size > 0 ? size : 20;
    int offset = (safePage - 1) * safeSize;
    List<FlowRunTaskVO> list = taskRepository.selectTodoByAssigneePage(assigneeId, tid, offset, safeSize);
    long total = taskRepository.countTodoByAssignee(assigneeId, tid);
    return PageResponse.success(total, (long) safePage, (long) safeSize, list);
  }

  /** P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET） */
  public PageResponse<List<FlowRunTaskVO>> listDoneByAssigneePage(
      String assigneeId, String tenantId, int page, int size) {
    // P2-17: 真分页（SQL LIMIT/OFFSET） — 走历史表
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    int safePage = Math.max(1, page);
    int safeSize = size > 0 ? size : 20;
    int offset = (safePage - 1) * safeSize;
    List<FlowHisTaskVO> hisTasks =
        hisTaskRepository.selectDoneByAssigneePage(assigneeId, tid, offset, safeSize);
    List<FlowRunTaskVO> list = new ArrayList<>();
    for (FlowHisTaskVO his : hisTasks) {
      list.add(hisTaskVoToRunTaskVo(his));
    }
    long total = hisTaskRepository.countDoneByAssignee(assigneeId, tid);
    return PageResponse.success(total, (long) safePage, (long) safeSize, list);
  }

  /** P2-33: 已办多维筛选分页查询（真分页：SQL LIMIT/OFFSET） */
  public PageResponse<List<FlowRunTaskVO>> listDoneByAssigneePageMulti(
      String assigneeId,
      String businessType,
      String flowCode,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int page,
      int size) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    int safePage = Math.max(1, page);
    int safeSize = size > 0 ? size : 20;
    int offset = (safePage - 1) * safeSize;
    List<FlowHisTaskVO> hisTasks =
        hisTaskRepository.selectDonePage(
            assigneeId, businessType, flowCode, startTime, endTime, tid, offset, safeSize);
    List<FlowRunTaskVO> list = new ArrayList<>();
    for (FlowHisTaskVO his : hisTasks) {
      list.add(hisTaskVoToRunTaskVo(his));
    }
    long total =
        hisTaskRepository.countDone(assigneeId, businessType, flowCode, startTime, endTime, tid);
    return PageResponse.success(total, (long) safePage, (long) safeSize, list);
  }

  // ============================== 统计查询 ==============================

  /** P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name） */
  public List<Map<String, Object>> nodeDurationStats(String flowCode, String tenantId) {
    return hisTaskRepository.selectNodeDurationStats(flowCode, tenantId);
  }

  /** P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED） */
  public List<FlowRunTaskVO> listOverdue(String assigneeId, String tenantId, int limit) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return taskRepository.selectOverdue(assigneeId, tid, limit);
  }

  /** P2-32: 统计超期任务数量 */
  public long countOverdue(String assigneeId, String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return taskRepository.countOverdueByAssignee(assigneeId, tid);
  }

  /** P2-4: 统计待办任务总数（PENDING + CLAIMED） */
  public long countPending(String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return taskRepository.countPendingByTenantId(tid);
  }

  // ============================== 视图转换 ==============================

  /** 转视图 */
  public FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTaskVO task) {
    if (task == null) {
      return null;
    }
    return FlowInstanceViewDTO.FlowTaskViewDTO.builder()
        .id(task.getId())
        .nodeCode(task.getNodeCode())
        .nodeName(task.getNodeName())
        .nodeType(task.getNodeType())
        .assigneeType(task.getAssigneeType())
        .assigneeId(task.getAssigneeId())
        .assigneeName(task.getAssigneeName())
        .performType(task.getPerformType())
        .taskStatus(task.getTaskStatus())
        .comment(task.getComment())
        .createAt(task.getCreatedAt())
        .claimAt(task.getClaimAt())
        .finishAt(task.getFinishAt())
        .durationMs(task.getDurationMs())
        .dueAt(task.getDueAt())
        .priority(task.getPriority())
        .build();
  }

  /**
   * P1-1: 查询实例经过的历史节点（去重，按首次完成时间排序），用于驳回时让用户选择驳回到任意历史节点。
   *
   * @param instanceId 流程实例 ID
   * @return 节点列表：nodeCode / nodeName / firstFinishAt / assigneeName
   */
  public List<Map<String, Object>> listPassedNodes(String instanceId) {
    return hisTaskRepository.listPassedNodes(instanceId);
  }

  // ============================== 加签历史查询（Map 形式，避免 DO 泄漏） ==============================

  /** 加签类型常量 */
  private static final List<String> COUNTERSIGN_ACTIONS =
      List.of(
          "COUNTERSIGN_BEFORE", "COUNTERSIGN_AFTER", "COUNTERSIGN_PARALLEL", "COUNTERSIGN_REMOVE");

  /**
   * 查询流程实例的加签历史记录（Map 形式返回，避免 Controller 层接触 DO）
   *
   * @param instanceId 流程实例 ID
   * @return 加签历史列表
   */
  public List<Map<String, Object>> listCountersignByInstance(String instanceId) {
    List<FlowAuditLogVO> logs = auditLogRepository.findByInstanceId(instanceId);
    return toCountersignMapList(logs);
  }

  /**
   * 查询任务的加签历史记录（Map 形式返回，避免 Controller 层接触 DO）
   *
   * @param taskId 任务 ID
   * @return 加签历史列表
   */
  public List<Map<String, Object>> listCountersignByTask(String taskId) {
    List<FlowAuditLogVO> logs = auditLogRepository.findByTaskId(taskId);
    return toCountersignMapList(logs);
  }

  /** 将审计日志 VO 列表转换为加签视图 Map 列表 */
  private List<Map<String, Object>> toCountersignMapList(List<FlowAuditLogVO> logs) {
    if (logs == null || logs.isEmpty()) {
      return List.of();
    }
    return logs.stream()
        .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
        .map(this::toCountersignMap)
        .toList();
  }

  /** 将审计日志 VO 转换为加签视图 Map */
  private Map<String, Object> toCountersignMap(FlowAuditLogVO log) {
    Map<String, Object> vo = new LinkedHashMap<>();
    vo.put("id", log.getId());
    vo.put("instanceId", log.getInstanceId());
    vo.put("taskId", log.getTaskId());
    vo.put("flowCode", log.getFlowCode());
    vo.put("nodeCode", log.getNodeCode());
    vo.put("nodeName", log.getNodeName());
    vo.put("action", log.getAction());
    vo.put("actionName", getActionName(log.getAction()));
    vo.put("operatorId", log.getOperatorId());
    vo.put("operatorName", log.getOperatorName());
    vo.put("targetId", log.getTargetId());
    vo.put("targetName", log.getTargetName());
    vo.put("comment", log.getComment());
    vo.put("operatedAt", log.getOperatedAt());
    return vo;
  }

  /** 获取加签操作名称 */
  private String getActionName(String action) {
    if (action == null) {
      return "未知";
    }
    return switch (action) {
      case "COUNTERSIGN_BEFORE" -> "前加签";
      case "COUNTERSIGN_AFTER" -> "后加签";
      case "COUNTERSIGN_PARALLEL" -> "并加签";
      case "COUNTERSIGN_REMOVE" -> "减签";
      default -> "未知加签操作";
    };
  }

  // ============================== 监控聚合查询（供 Controller 层使用，避免 DO 泄漏） ==============================

  /**
   * 超期任务 Top N 排行（按超期时长降序）
   *
   * @param tenantId 租户 ID
   * @param limit 返回条数上限
   * @return 超期任务列表
   */
  public List<Map<String, Object>> selectOverdueTopN(String tenantId, int limit) {
    return taskRepository.selectOverdueTopN(tenantId, limit);
  }

  /**
   * 审批人负载分布（当前待办数量）
   *
   * @param tenantId 租户 ID
   * @param limit 返回条数上限
   * @return 审批人负载列表
   */
  public List<Map<String, Object>> selectWorkloadByAssignee(String tenantId, int limit) {
    return taskRepository.selectWorkloadByAssignee(tenantId, limit);
  }

  // ============================== 私有辅助 ==============================

  /** 将历史任务 VO 转换为运行时任务 VO（用于已办查询结果统一） */
  private FlowRunTaskVO hisTaskVoToRunTaskVo(FlowHisTaskVO his) {
    FlowRunTaskVO vo = new FlowRunTaskVO();
    vo.setId(his.getTaskId());
    vo.setInstanceId(his.getInstanceId());
    vo.setFlowCode(his.getFlowCode());
    vo.setDefinitionId(his.getDefinitionId());
    vo.setNodeCode(his.getNodeCode());
    vo.setNodeName(his.getNodeName());
    vo.setNodeType(his.getNodeType());
    vo.setBusinessType(his.getBusinessType());
    vo.setBusinessId(his.getBusinessId());
    vo.setBusinessNo(his.getBusinessNo());
    vo.setFlowName(his.getFlowName());
    vo.setTitle(his.getTitle());
    vo.setAssigneeType(his.getAssigneeType());
    vo.setAssigneeId(his.getAssigneeId());
    vo.setAssigneeName(his.getAssigneeName());
    vo.setPerformType(his.getPerformType());
    vo.setTaskStatus(his.getTaskStatus());
    vo.setComment(his.getComment());
    vo.setCreatedAt(his.getCreatedAt());
    vo.setClaimAt(his.getClaimAt());
    vo.setFinishAt(his.getFinishAt());
    vo.setDurationMs(his.getDurationMs());
    return vo;
  }
}
