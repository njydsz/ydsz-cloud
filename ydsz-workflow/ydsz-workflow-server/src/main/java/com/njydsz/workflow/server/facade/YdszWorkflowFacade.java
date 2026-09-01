package com.njydsz.workflow.server.facade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowAuditTrailVO;
import com.njydsz.workflow.domain.vo.FlowBatchUrgeResultVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDetailVO;
import com.njydsz.workflow.domain.vo.FlowDiagramVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowReplayStepVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowTimelineVO;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 自建工作流 Facade（唯一实现）
 *
 * <p>所有操作落 ydsz_flow_* 表，对外暴露的 WorkflowFacade 统一接口实现。
 *
 * <p>1.0.0 新增能力：加签 / 撤回 / 催办 / 审计轨迹查询。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper 或直接引用 infra.entity（符合 §34.2.3 / §34.2.1）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YdszWorkflowFacade implements WorkflowFacade {

  /** 列表初始容量：时间线 / 步骤明细等场景（32） */
  private static final int LIST_INIT_CAPACITY_32 = 32;

  /** Map 初始容量：小集合（8） */
  private static final int MAP_INIT_CAPACITY_8 = 8;

  /** Map 初始容量：中集合（16） */
  private static final int MAP_INIT_CAPACITY_16 = 16;

  private final FlowInstanceService instanceService;
  private final FlowTaskService taskService;
  private final FlowAuditLogRepository auditLogRepository;

  /** P2-30: 审批轨迹时间线需要查询历史任务 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** P2-22: 流程图查询需要查询流程定义详情 */
  private final FlowDefinitionService definitionService;

  @Override
  public String startProcess(FlowStartProcessDTO dto) {
    String id = instanceService.start(dto);
    return id == null ? null : String.valueOf(id);
  }

  @Override
  public FlowInstanceViewDTO getByBusiness(String businessType, String businessId) {
    FlowInstanceVO instance = instanceService.getByBusiness(businessType, businessId);
    if (instance == null) {
      return null;
    }
    List<FlowRunTaskVO> currentTasks = taskService.listPendingByInstance(instance.getId());
    return instanceService.toView(
        instance, currentTasks.stream().map(taskService::toView).toList());
  }

  @Override
  public void completeTask(FlowTaskOperateDTO dto) {
    taskService.pass(dto);
  }

  @Override
  public void claimTask(String taskId, String userId) {
    taskService.claim(taskId, userId);
  }

  @Override
  public void transferTask(FlowTaskOperateDTO dto) {
    taskService.transfer(dto);
  }

  @Override
  public void delegateTask(FlowTaskOperateDTO dto) {
    taskService.delegate(dto);
  }

  @Override
  public void rejectTask(FlowTaskOperateDTO dto) {
    taskService.reject(dto);
  }

  @Override
  public void terminateProcess(String processInstanceId, String reason) {
    instanceService.terminate(processInstanceId, reason);
  }

  @Override
  public void suspendProcess(String processInstanceId) {
    instanceService.suspend(processInstanceId);
  }

  @Override
  public void activateProcess(String processInstanceId) {
    instanceService.activate(processInstanceId);
  }

  @Override
  public List<Map<String, Object>> listTodoTasks(String userId, int page, int size) {
    // P2-17: 真分页（SQL LIMIT/OFFSET）
    PageResponse<List<FlowRunTaskVO>> pageResult =
        taskService.listTodoByAssigneePage(
            String.valueOf(userId), AuthContextUtils.getTenantIdOrDefault(), page, size);
    List<FlowRunTaskVO> list = MapUtils.safeCastList(pageResult.getData(), FlowRunTaskVO.class);
    return list.stream().map(this::toMap).toList();
  }

  @Override
  public List<Map<String, Object>> listDoneTasks(String userId, int page, int size) {
    // P0-3: 已办走历史表（FlowTaskServiceImpl 内部已切换到 FlowHisTaskRepository）
    // P2-17: 真分页（SQL LIMIT/OFFSET）
    PageResponse<List<FlowRunTaskVO>> pageResult =
        taskService.listDoneByAssigneePage(
            String.valueOf(userId), AuthContextUtils.getTenantIdOrDefault(), page, size);
    List<FlowRunTaskVO> list = MapUtils.safeCastList(pageResult.getData(), FlowRunTaskVO.class);
    return list.stream().map(this::toMap).toList();
  }

  // ============================== GAP-P0-1: 全部流程实例（管理员视图） ==============================

  /**
   * GAP-P0-1: 查全部流程实例（管理员视图）
   *
   * <p>复用 {@link FlowInstanceService#page}，不按 initiatorId 过滤，返回当前租户下所有实例。 上层 Controller 应通过
   * {@code @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR)} 拦截非管理员访问。
   *
   * <p>P0-2 修复：返回 {@link PageResponse}，保留 total / page / size，避免前端假分页。
   */
  @Override
  public PageResponse<List<Map<String, Object>>> listAllInstances(
      String businessType,
      String flowStatus,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int page,
      int size) {
    FlowInstancePageQuery query = new FlowInstancePageQuery();
    query.setPageNum(page);
    query.setPageSize(size);
    query.setBusinessType(businessType);
    query.setFlowStatus(flowStatus);
    query.setStartTime(startTime);
    query.setEndTime(endTime);
    query.setTenantId(AuthContextUtils.getTenantIdOrDefault());
    PageResponse<List<FlowInstanceVO>> pageResult = instanceService.page(query);
    List<FlowInstanceVO> dataList = MapUtils.safeCastList(pageResult.getData(), FlowInstanceVO.class);
    List<Map<String, Object>> list = dataList.stream().map(this::instanceToMap).toList();
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), list);
  }

  @Override
  public void countersignBeforeTask(FlowTaskOperateDTO dto) {
    taskService.countersignBefore(dto);
  }

  @Override
  public void countersignAfterTask(FlowTaskOperateDTO dto) {
    taskService.countersignAfter(dto);
  }

  /**
   * GAP-P0-3: 并加签
   *
   * @param dto 任务操作参数（taskId / userId / targetUserId / targetUserName）
   */
  @Override
  public void countersignParallelTask(FlowTaskOperateDTO dto) {
    taskService.countersignParallel(dto);
  }

  @Override
  public List<String> urgeTask(String instanceId, String operatorId, String comment) {
    return taskService.urge(instanceId, operatorId, comment);
  }

  @Override
  public List<String> urgeNodeTask(
      String instanceId, String nodeCode, String operatorId, String comment) {
    return taskService.urgeByNode(instanceId, nodeCode, operatorId, comment);
  }

  @Override
  public boolean recallProcess(String processInstanceId, String initiatorId) {
    return instanceService.recall(processInstanceId, initiatorId);
  }

  @Override
  public List<FlowAuditTrailVO> listAuditTrail(String processInstanceId) {
    String instanceId = processInstanceId;
    // 通过 Repository 获取审计日志（符合 §34.2.3，禁止直接注入 Mapper）
    List<FlowAuditLogVO> logs = auditLogRepository.findByInstanceId(instanceId);
    return logs.stream().map(this::toAuditTrailVO).toList();
  }

  @Override
  public String engineType() {
    return "YDSZ";
  }

  // ============================== P2-20: 任务详情查询 ==============================

  @Override
  public Map<String, Object> getTaskDetail(String taskId) {
    // P2-20: 调用 taskService.getById 获取任务，再用 toView 转换为视图
    FlowRunTaskVO task = taskService.getById(taskId);
    if (task == null) {
      return Collections.emptyMap();
    }
    FlowInstanceViewDTO.FlowTaskViewDTO view = taskService.toView(task);
    return taskViewToMap(view);
  }

  // ============================== P2-25: 自由跳转 / P2-26: 批量审批 ==============================

  @Override
  public void jumpTask(FlowTaskOperateDTO dto) {
    taskService.jump(dto);
  }

  @Override
  public void batchPassTasks(List<String> taskIds, String userId, String comment) {
    taskService.batchPass(taskIds, userId, comment);
  }

  /**
   * GAP-P0-4: 一键通过所有待办
   *
   * @param userId 操作用户 ID（一键通过其名下所有待办）
   * @param comment 审批意见（写入每条通过任务的 comment）
   * @return 实际一键通过的任务数
   */
  @Override
  public int passAllTodoTasks(String userId, String comment) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    PageResponse<List<FlowRunTaskVO>> pageResult =
        taskService.listTodoByAssigneePage(String.valueOf(userId), tenantId, 1, 100);
    List<FlowRunTaskVO> todos = MapUtils.safeCastList(pageResult.getData(), FlowRunTaskVO.class);
    if (todos.isEmpty()) {
      return 0;
    }
    List<String> taskIds = todos.stream().map(FlowRunTaskVO::getId).toList();
    taskService.batchPass(taskIds, userId, comment);
    log.info("[Flow] 一键通过所有待办: userId={} count={}", userId, taskIds.size());
    return taskIds.size();
  }

  // ============================== P2-22: 流程图查询（高亮当前节点） ==============================

  /**
   * P2-22: 流程图查询，高亮当前节点
   *
   * @param instanceId 实例 ID（字符串形式）
   * @return FlowDiagramVO 包含 definition / nodes / skips，nodes 中每个节点带 active 标记
   */
  public FlowDiagramVO getDiagram(String instanceId) {
    String id = instanceId;
    FlowInstanceVO instance = instanceService.getById(id);
    if (instance == null) {
      return null;
    }
    // 通过 definitionService.getDetail 组装 definition + nodes + skips
    FlowDefinitionDetailVO detail = definitionService.getDetail(instance.getDefinitionId());
    if (detail == null) {
      return null;
    }
    String currentNodeCode = instance.getCurrentNodeCode();
    // 在每个 node 上标注 active: true/false（currentNodeCode 匹配则为 active）
    List<FlowDiagramVO.DiagramNodeVO> nodes = new ArrayList<>();
    if (detail.getNodes() != null) {
      for (FlowNodeVO n : detail.getNodes()) {
        FlowDiagramVO.DiagramNodeVO diagramNode = new FlowDiagramVO.DiagramNodeVO();
        diagramNode.setNodeCode(n.getNodeCode());
        diagramNode.setNodeName(n.getNodeName());
        diagramNode.setNodeType(n.getNodeType());
        diagramNode.setExt(n.getExt());
        boolean active = currentNodeCode != null && currentNodeCode.equals(n.getNodeCode());
        diagramNode.setActive(active);
        nodes.add(diagramNode);
      }
    }
    // 组装强类型 VO
    FlowDiagramVO result = new FlowDiagramVO();
    result.setDefinition(detail.getDefinition());
    result.setNodes(nodes);
    result.setSkips(detail.getSkips());
    return result;
  }

  // ============================== P2-30: 审批轨迹时间线查询 ==============================

  /**
   * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
   *
   * <p>每条记录包含：type（HIS_TASK/AUDIT_LOG/CURRENT_TASK）、timestamp、nodeCode、nodeName、
   * assigneeId、assigneeName、action、comment、taskStatus。 按 timestamp 排序（历史任务用 finishAt，审计日志用
   * operatedAt，当前待办用 createdAt）。
   *
   * @param instanceId 实例 ID（字符串形式）
   * @return 统一时间线列表，实例不存在时返回空列表
   */
  @Override
  public List<FlowTimelineVO> getTimeline(String instanceId) {
    String id = instanceId;
    // 1. 获取实例信息
    FlowInstanceVO instance = instanceService.getById(id);
    if (instance == null) {
      return Collections.emptyList();
    }

    List<FlowTimelineVO> timeline = new ArrayList<>(LIST_INIT_CAPACITY_32);

    // 2. 获取历史任务列表（通过 Repository，符合 §34.2.3）
    List<FlowHisTaskVO> hisTasks = hisTaskRepository.findByInstanceId(id);
    for (FlowHisTaskVO his : hisTasks) {
      FlowTimelineVO entry = new FlowTimelineVO();
      entry.setType("HIS_TASK");
      entry.setTimestamp(his.getFinishAt());
      entry.setNodeCode(his.getNodeCode());
      entry.setNodeName(his.getNodeName());
      entry.setAssigneeId(his.getAssigneeId());
      entry.setAssigneeName(his.getAssigneeName());
      entry.setAction(his.getTaskStatus());
      entry.setComment(his.getComment());
      entry.setTaskStatus(his.getTaskStatus());
      timeline.add(entry);
    }

    // 3. 获取审计日志列表（通过 Repository，符合 §34.2.3）
    List<FlowAuditLogVO> logs = auditLogRepository.findByInstanceId(id);
    for (FlowAuditLogVO log : logs) {
      FlowTimelineVO entry = new FlowTimelineVO();
      entry.setType("AUDIT_LOG");
      entry.setTimestamp(log.getOperatedAt());
      entry.setNodeCode(log.getNodeCode());
      entry.setNodeName(log.getNodeName());
      entry.setAssigneeId(log.getOperatorId() == null ? null : String.valueOf(log.getOperatorId()));
      entry.setAssigneeName(log.getOperatorName());
      entry.setAction(log.getAction());
      entry.setComment(log.getComment());
      entry.setTaskStatus(null);
      timeline.add(entry);
    }

    // 4. 获取当前待办任务
    List<FlowRunTaskVO> currentTasks = taskService.listPendingByInstance(id);
    for (FlowRunTaskVO task : currentTasks) {
      FlowTimelineVO entry = new FlowTimelineVO();
      entry.setType("CURRENT_TASK");
      entry.setTimestamp(task.getCreatedAt());
      entry.setNodeCode(task.getNodeCode());
      entry.setNodeName(task.getNodeName());
      entry.setAssigneeId(task.getAssigneeId());
      entry.setAssigneeName(task.getAssigneeName());
      entry.setAction(task.getTaskStatus());
      entry.setComment(task.getComment());
      entry.setTaskStatus(task.getTaskStatus());
      timeline.add(entry);
    }

    // 5. 按 timestamp 排序（null 排最后），保持同时间戳的插入顺序（稳定排序）
    timeline.sort(
        (a, b) -> {
          LocalDateTime ta = a.getTimestamp();
          LocalDateTime tb = b.getTimestamp();
          if (ta == null && tb == null) {
            return 0;
          }
          if (ta == null) {
            return 1;
          }
          if (tb == null) {
            return -1;
          }
          return ta.compareTo(tb);
        });

    return timeline;
  }

  // ============================== 私有辅助 ==============================

  /** 将 FlowTaskViewDTO 转换为 Map */
  private Map<String, Object> taskViewToMap(FlowInstanceViewDTO.FlowTaskViewDTO v) {
    Map<String, Object> m = new HashMap<>(MAP_INIT_CAPACITY_16);
    m.put("id", v.getId());
    m.put("nodeCode", v.getNodeCode());
    m.put("nodeName", v.getNodeName());
    m.put("nodeType", v.getNodeType());
    m.put("assigneeType", v.getAssigneeType());
    m.put("assigneeId", v.getAssigneeId());
    m.put("assigneeName", v.getAssigneeName());
    m.put("performType", v.getPerformType());
    m.put("taskStatus", v.getTaskStatus());
    m.put("comment", v.getComment());
    m.put("createAt", v.getCreateAt());
    m.put("claimAt", v.getClaimAt());
    m.put("finishAt", v.getFinishAt());
    m.put("durationMs", v.getDurationMs());
    m.put("dueAt", v.getDueAt());
    return m;
  }

  private Map<String, Object> toMap(FlowRunTaskVO t) {
    Map<String, Object> m = new HashMap<>(MAP_INIT_CAPACITY_16);
    m.put("id", t.getId());
    m.put("instanceId", t.getInstanceId());
    m.put("flowCode", t.getFlowCode());
    m.put("nodeCode", t.getNodeCode());
    m.put("nodeName", t.getNodeName());
    m.put("title", t.getTitle());
    m.put("assigneeId", t.getAssigneeId());
    m.put("assigneeName", t.getAssigneeName());
    m.put("taskStatus", t.getTaskStatus());
    m.put("businessType", t.getBusinessType());
    m.put("businessId", t.getBusinessId());
    m.put("businessNo", t.getBusinessNo());
    m.put("createdAt", t.getCreatedAt());
    m.put("finishAt", t.getFinishAt());
    m.put("priority", t.getPriority());
    return m;
  }

  /** GAP-P0-1: 将 FlowInstanceVO 转换为 Map（管理员"全部"视图） */
  private Map<String, Object> instanceToMap(FlowInstanceVO i) {
    Map<String, Object> m = new HashMap<>(MAP_INIT_CAPACITY_16);
    m.put("id", i.getId());
    m.put("flowCode", i.getFlowCode());
    m.put("flowName", i.getFlowName());
    m.put("definitionId", i.getDefinitionId());
    m.put("flowVersion", i.getFlowVersion());
    m.put("businessType", i.getBusinessType());
    m.put("businessId", i.getBusinessId());
    m.put("businessNo", i.getBusinessNo());
    m.put("title", i.getTitle());
    m.put("initiatorId", i.getInitiatorId());
    m.put("initiatorName", i.getInitiatorName());
    m.put("currentNodeCode", i.getCurrentNodeCode());
    m.put("currentNodeName", i.getCurrentNodeName());
    m.put("flowStatus", i.getFlowStatus());
    m.put("activityStatus", i.getActivityStatus());
    m.put("startAt", i.getStartAt());
    m.put("endAt", i.getEndAt());
    m.put("durationMs", i.getDurationMs());
    m.put("dueAt", i.getDueAt());
    return m;
  }

  /** 将 FlowAuditLogVO 转换为 FlowAuditTrailVO */
  private FlowAuditTrailVO toAuditTrailVO(FlowAuditLogVO log) {
    FlowAuditTrailVO vo = new FlowAuditTrailVO();
    vo.setId(log.getId());
    vo.setInstanceId(log.getInstanceId());
    vo.setTaskId(log.getTaskId());
    vo.setFlowCode(log.getFlowCode());
    vo.setBusinessType(log.getBusinessType());
    vo.setBusinessId(log.getBusinessId());
    vo.setNodeCode(log.getNodeCode());
    vo.setNodeName(log.getNodeName());
    vo.setAction(log.getAction());
    vo.setOperatorId(log.getOperatorId());
    vo.setTargetId(log.getTargetId());
    vo.setComment(log.getComment());
    vo.setOperatedAt(log.getOperatedAt());
    return vo;
  }

  // ============================== P2-4: 流程回放步骤序列 ==============================

  /**
   * P2-4: 生成流程回放步骤序列 — 按时间顺序合并历史任务 + 审计日志 + 当前待办为回放步骤。
   *
   * <p>每一步包含：
   *
   * <ul>
   *   <li>stepIndex — 步骤序号（从 0 开始）
   *   <li>type — HIS_TASK / AUDIT_LOG / CURRENT_TASK / START / END
   *   <li>timestamp — 发生时间
   *   <li>nodeCode / nodeName — 节点
   *   <li>actor / actorName — 操作人
   *   <li>action — 操作动作（PASS/REJECT/AUTO_PASS ...）
   *   <li>comment — 意见
   *   <li>nodeState — 节点回放后状态：ENTERED / PASSED / REJECTED / ACTIVE / SKIPPED
   *   <li>durationMs — 本步耗时（可选）
   * </ul>
   *
   * <p>回放步骤用于驱动前端 FlowDiagramReplay 组件，依次高亮节点 + 展示轨迹事件。
   *
   * @param instanceId 实例 ID（字符串形式）
   * @return 步骤列表（按 timestamp 升序），实例不存在时返回空列表
   */
  public List<FlowReplayStepVO> getReplaySteps(String instanceId) {
    String id = instanceId;
    FlowInstanceVO instance = instanceService.getById(id);
    if (instance == null) {
      return Collections.emptyList();
    }

    // 预加载节点坐标映射（key = nodeCode），用于步骤中携带 coordinate 字段
    Map<String, Map<String, Object>> nodeCoordMap = loadNodeCoordinates(instance.getDefinitionId());
    List<FlowReplayStepVO> steps = new ArrayList<>(LIST_INIT_CAPACITY_32);

    // 1. 起始步骤
    steps.add(buildStartStepVO(instance));

    // 2. 历史任务步骤
    List<FlowHisTaskVO> hisTasks = hisTaskRepository.findByInstanceId(id);
    for (FlowHisTaskVO his : hisTasks) {
      steps.add(buildHisTaskStepVO(his, nodeCoordMap));
    }

    // 3. 审计日志步骤（URGE/TRANSFER/DELEGATE/JUMP/RECALL 等任务外操作）
    List<FlowAuditLogVO> logs = auditLogRepository.findByInstanceId(id);
    for (FlowAuditLogVO log : logs) {
      FlowReplayStepVO step = buildAuditLogStepVO(log, nodeCoordMap);
      if (step != null) {
        steps.add(step);
      }
    }

    // 4. 当前待办（RUNNING 实例的最后状态）
    if ("RUNNING".equals(instance.getFlowStatus()) || "SUSPENDED".equals(instance.getFlowStatus())) {
      List<FlowRunTaskVO> currentTasks = taskService.listPendingByInstance(id);
      for (FlowRunTaskVO task : currentTasks) {
        steps.add(buildCurrentTaskStepVO(task, nodeCoordMap));
      }
    }

    // 5. 终止步骤
    if (instance.getEndAt() != null) {
      steps.add(buildEndStepVO(instance, nodeCoordMap));
    }

    // 6. 按 timestamp 升序排序，重新分配 stepIndex
    sortStepsByIndex(steps);

    return steps;
  }

  /** 构建起始步骤 VO。 */
  private FlowReplayStepVO buildStartStepVO(FlowInstanceVO instance) {
    FlowReplayStepVO step = new FlowReplayStepVO();
    step.setStepIndex(0);
    step.setType("START");
    step.setTimestamp(instance.getStartAt());
    step.setActor(instance.getInitiatorId());
    step.setActorName(instance.getInitiatorName());
    step.setAction("START");
    step.setNodeState("ENTERED");
    return step;
  }

  /** 构建历史任务步骤 VO。 */
  private FlowReplayStepVO buildHisTaskStepVO(FlowHisTaskVO his,
      Map<String, Map<String, Object>> nodeCoordMap) {
    FlowReplayStepVO step = new FlowReplayStepVO();
    step.setType("HIS_TASK");
    step.setTimestamp(his.getFinishAt());
    step.setNodeCode(his.getNodeCode());
    step.setNodeName(his.getNodeName());
    step.setActor(his.getAssigneeId());
    step.setActorName(his.getAssigneeName());
    step.setAction(his.getTaskStatus());
    step.setComment(his.getComment());
    step.setNodeState(mapNodeState(his.getTaskStatus()));
    step.setDurationMs(his.getDurationMs());
    step.setCoordinate(nodeCoordMap.get(his.getNodeCode()));
    return step;
  }

  /** 构建审计日志步骤 VO。任务自身操作（PASS/REJECT 等）返回 null 以跳过。 */
  private FlowReplayStepVO buildAuditLogStepVO(FlowAuditLogVO log,
      Map<String, Map<String, Object>> nodeCoordMap) {
    String action = log.getAction();
    if (action == null || isTaskAction(action)) {
      return null;
    }
    FlowReplayStepVO step = new FlowReplayStepVO();
    step.setType("AUDIT_LOG");
    step.setTimestamp(log.getOperatedAt());
    step.setNodeCode(log.getNodeCode());
    step.setNodeName(log.getNodeName());
    step.setActor(log.getOperatorId());
    step.setActorName(log.getOperatorName());
    step.setAction(action);
    step.setComment(log.getComment());
    step.setNodeState("OBSERVED");
    step.setCoordinate(log.getNodeCode() != null ? nodeCoordMap.get(log.getNodeCode()) : null);
    return step;
  }

  /**
   * 判断 action 是否为任务自身操作（已在 HIS_TASK 中体现，回放时跳过）。
   *
   * @param action 审计动作类型码（如 TASK_CREATED / PASS / REJECT 等）
   * @return true 表示该 action 是任务自身操作（已在 HIS_TASK 中体现，回放时跳过）
   */
  private boolean isTaskAction(String action) {
    return action.startsWith("TASK_")
        || "PASS".equals(action)
        || "REJECT".equals(action)
        || "CLAIM".equals(action)
        || "COMPLETED".equals(action);
  }

  /** 构建当前待办步骤 VO。 */
  private FlowReplayStepVO buildCurrentTaskStepVO(FlowRunTaskVO task,
      Map<String, Map<String, Object>> nodeCoordMap) {
    FlowReplayStepVO step = new FlowReplayStepVO();
    step.setType("CURRENT_TASK");
    step.setTimestamp(task.getCreatedAt());
    step.setNodeCode(task.getNodeCode());
    step.setNodeName(task.getNodeName());
    step.setActor(task.getAssigneeId());
    step.setActorName(task.getAssigneeName());
    step.setAction(task.getTaskStatus());
    step.setComment(task.getComment());
    step.setNodeState("ACTIVE");
    step.setDurationMs(task.getDurationMs());
    step.setCoordinate(nodeCoordMap.get(task.getNodeCode()));
    return step;
  }

  /** 构建终止步骤 VO。 */
  private FlowReplayStepVO buildEndStepVO(FlowInstanceVO instance,
      Map<String, Map<String, Object>> nodeCoordMap) {
    FlowReplayStepVO step = new FlowReplayStepVO();
    step.setType("END");
    step.setTimestamp(instance.getEndAt());
    step.setNodeCode(instance.getCurrentNodeCode());
    step.setNodeName(instance.getCurrentNodeName());
    step.setAction(instance.getFlowStatus());
    step.setNodeState("FINISHED");
    step.setDurationMs(instance.getDurationMs());
    step.setCoordinate(
        instance.getCurrentNodeCode() != null
            ? nodeCoordMap.get(instance.getCurrentNodeCode())
            : null);
    return step;
  }

  /**
   * 按 timestamp 升序排序（null 排最后），并重新分配 stepIndex。
   *
   * @param steps 步骤列表
   */
  private void sortStepsByIndex(List<FlowReplayStepVO> steps) {
    steps.sort((a, b) -> {
      LocalDateTime ta = a.getTimestamp();
      LocalDateTime tb = b.getTimestamp();
      if (ta == null && tb == null) {
        return 0;
      }
      if (ta == null) {
        return 1;
      }
      if (tb == null) {
        return -1;
      }
      return ta.compareTo(tb);
    });
    for (int i = 0; i < steps.size(); i++) {
      steps.get(i).setStepIndex(i);
    }
  }

  /**
   * P3-1: 加载流程定义下所有节点的坐标映射。
   *
   * <p>key = nodeCode，value = {x, y, width, height}。 来源：ydsz_flow_node.coordinate JSON 字段（BPMN 部署时由
   * BPMNDI 段自动注入， 或前端设计器保存）。
   *
   * <p>解析失败或字段为空时降级为 null，前端回放将不自动滚屏。
   *
   * @param definitionId 流程定义 ID
   * @return 节点坐标映射，无定义时返回空 Map
   */
  private Map<String, Map<String, Object>> loadNodeCoordinates(String definitionId) {
    if (definitionId == null) {
      return Collections.emptyMap();
    }
    FlowDefinitionDetailVO detail = definitionService.getDetail(definitionId);
    if (detail == null) {
      return Collections.emptyMap();
    }
    List<FlowNodeVO> nodes = detail.getNodes();
    if (nodes == null || nodes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Map<String, Object>> result = new HashMap<>(nodes.size());
    for (FlowNodeVO n : nodes) {
      String coord = n.getCoordinate();
      if (coord == null || coord.isBlank()) {
        continue;
      }
      try {
        Map<String, Object> parsed = YdszJson.parseMap(coord);
        if (parsed != null && !parsed.isEmpty()) {
          result.put(n.getNodeCode(), parsed);
        }
      } catch (Exception e) {
        // coordinate 解析失败：跳过此节点
        log.warn("[WorkflowFacade] coordinate 解析失败，跳过此节点: nodeCode={}, err={}",
            n.getNodeCode(), e.getMessage());
      }
    }
    return result;
  }

  /**
   * 根据任务状态映射到回放节点状态
   *
   * @param taskStatus 任务状态枚举名（如 PASSED / REJECTED / SKIPPED）
   * @return 回放节点状态字符串（ENTERED / PASSED / REJECTED / SKIPPED）
   */
  private String mapNodeState(String taskStatus) {
    if (taskStatus == null) {
      return "ENTERED";
    }
    return switch (taskStatus) {
      case "PASSED", "COMPLETED" -> "PASSED";
      case "REJECTED" -> "REJECTED";
      case "SKIPPED" -> "SKIPPED";
      case "CANCELLED" -> "SKIPPED";
      case "TIMEOUT" -> "SKIPPED";
      case "PENDING", "CLAIMED" -> "ACTIVE";
      default -> "ENTERED";
    };
  }

  // ======================== P0-03: 暂存待审 / 追加处理人 / 减签 / 已阅 / 沟通 ========================

  @Override
  public void saveDraft(FlowTaskOperateDTO dto) {
    taskService.saveDraft(dto);
  }

  @Override
  public void addApprover(FlowTaskOperateDTO dto) {
    taskService.addApprover(dto);
  }

  @Override
  public void countersignRemoveTask(FlowTaskOperateDTO dto) {
    taskService.countersignRemove(dto);
  }

  @Override
  public void markReadTask(String taskId, String userId) {
    taskService.markRead(taskId, userId);
  }

  @Override
  public void communicateTask(FlowTaskOperateDTO dto) {
    taskService.communicate(dto);
  }

  @Override
  public String resubmitProcess(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    return instanceService.resubmit(instanceId, initiatorId, variables, comment, redoMode);
  }

  @Override
  public void batchReject(List<FlowTaskOperateDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    String userId = dtos.get(0).getUserId();
    String comment = dtos.get(0).getComment();
    String targetNodeCode = dtos.get(0).getTargetNodeCode();
    List<String> taskIds = new ArrayList<>(dtos.size());
    for (FlowTaskOperateDTO dto : dtos) {
      taskIds.add(dto.getTaskId());
    }
    taskService.batchReject(taskIds, userId, comment, targetNodeCode);
  }

  @Override
  public void batchTransfer(List<FlowTaskOperateDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    String userId = dtos.get(0).getUserId();
    String comment = dtos.get(0).getComment();
    String targetUserId = dtos.get(0).getTargetUserId();
    String targetUserName = dtos.get(0).getTargetUserName();
    List<String> taskIds = new ArrayList<>(dtos.size());
    for (FlowTaskOperateDTO dto : dtos) {
      taskIds.add(dto.getTaskId());
    }
    taskService.batchTransfer(taskIds, userId, comment, targetUserId, targetUserName);
  }

  @Override
  public FlowBatchUrgeResultVO batchUrge(
      List<String> instanceIds, String operatorId, String comment) {
    int successCount = taskService.batchUrge(instanceIds, operatorId, comment);
    FlowBatchUrgeResultVO result =
        new FlowBatchUrgeResultVO();
    result.setTotalCount(instanceIds == null ? 0 : instanceIds.size());
    result.setSuccessCount(successCount);
    result.setFailedCount(result.getTotalCount() - successCount);
    return result;
  }

  @Override
  public void suspendTask(String taskId, String operatorId, String reason) {
    taskService.suspendTask(taskId, operatorId, reason);
  }

  @Override
  public void activateTask(String taskId, String operatorId) {
    taskService.activateTask(taskId, operatorId);
  }
}
