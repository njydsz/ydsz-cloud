package com.njydsz.workflow.server.service.impl.instance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowAssigneeDTO;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowSignType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.repository.FlowUserRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowUserVO;
import com.njydsz.workflow.domain.gateway.FlowAssigneeResolver;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.engine.FlowServiceNodeExecutor;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.engine.impl.DefaultFlowVariableStrategy;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowGroupResolver;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;
import com.njydsz.workflow.server.service.instance.AssigneeResolutionService;
import com.njydsz.workflow.server.service.instance.DelegateRedirectService;
import com.njydsz.workflow.server.service.instance.EmptyAssigneeStrategyService;
import com.njydsz.workflow.server.service.instance.ServiceNodeExecuteService;

/**
 * 任务创建服务（拆分自 FlowTaskCompleteServiceImpl）
 *
 * <p>工作流引擎中<b>任务创建场景最复杂</b>的服务，承担 BPMN 2.0 中几乎所有节点类型的「创建运行时任务」 职责。是从原 {@code
 * FlowTaskCompleteServiceImpl}（单体实现）拆分的产物， 是大厂 B 端工作流「灵活节点类型 + 智能审批人解析」的关键实现层。
 *
 * <p><b>P1-2 God Class 拆分规划：</b>本类承担职责过多（任务创建 + 办理人解析 + 委派改写 + 服务节点执行 + SLA + 推送），
 * 正在逐步拆分。已完成子服务：
 *
 * <ul>
 *   <li>[已完成] {@link AssigneeResolutionService} — 办理人解析（resolveAssignee / resolveInitiatorId）
 *   <li>[已完成] {@link DelegateRedirectService} — 长期授权委派改写（applyDelegateRedirect）
 *   <li>[已完成] {@link EmptyAssigneeStrategyService} — 审批人为空兜底策略（handleEmptyAssignee 四策略分发）
 *   <li>[已完成] {@link ServiceNodeExecuteService} — 服务节点 HTTP/SCRIPT/AUTO_PASS 执行（executeServiceNode）
 *   <li>[已完成] {@link FlowAutoApproveService} — 自动审批规则引擎（tryAutoApprove 多规则求值 + 动作执行）
 *   <li>[已完成] {@link FlowCrossNodeDedupService} — 跨节点办理人去重（applyCrossNodeDedup / isAutoDedupEnabled）
 * </ul>
 *
 * <p><b>支持的任务创建场景：</b>
 *
 * <ul>
 *   <li><b>普通审批节点（{@code APPROVAL}）</b>：{@link #createTask} 走标准审批人解析路径
 *   <li><b>SERVICE 服务节点（{@code SERVICE}）</b>：{@link #executeServiceNode} — HTTP / SCRIPT /
 *       AUTO_PASS 自动执行，无需人工介入
 *   <li><b>FOREACH 循环节点（{@code FOREACH}）</b>：{@link #createForeachTasks} — 对集合中每个元素创建独立
 *       task（每个元素独立审批）
 *   <li><b>LEVEL_APPROVAL 逐级审批节点（{@code LEVEL_APPROVAL}）</b>：{@link #createLevelApprovalTask} —
 *       动态展开多级上级（直属 → 二级 → 三级），依次审批
 *   <li><b>审批人为空兜底</b>：{@link #handleEmptyAssignee} — AUTO_PASS / TRANSFER_ADMIN / ASSIGN_SPECIFIED
 *       / FALLBACK 四种策略
 *   <li><b>跨节点办理人去重（P1-5）</b>：{@link #applyCrossNodeDedup} — 过滤已在当前实例审批过的用户，对标钉钉「同人不重复审批」
 *   <li><b>自动审批节点（P2-4 / GAP-14）</b>：{@link #tryAutoApprove} — 配置化规则引擎（{@code INITIATOR_IS_APPROVER
 *       / AMOUNT_BELOW / EXPR / ALWAYS}）
 *   <li><b>长期授权委派改写（P1-4）</b>：{@link #applyDelegateRedirect} — 链式解析 A→B→C 委派链路，最终将任务分配给链路末端的代理人
 * </ul>
 *
 * <p><b>被调用方（依赖注入关系）：</b>
 *
 * <ul>
 * <li>{@link FlowTaskCoreService} — 自动审批执行后调用 pass / reject 子服务
 *   <li>{@link FlowTaskOperateService} — 创建任务后应用转办 / 委派 / 加签等操作
 *   <li>{@link FlowInstanceService} — 创建子任务时调用本服务
 *   <li>{@link DefaultFlowAdvancer} — 流程推进引擎，AUTO_PASS 递归推进到下一节点
 *   <li>{@link FlowSlaService} — 任务创建时应用 SLA 配置
 *   <li>{@link FlowDelegateAuthService} — 长期授权委派查询
 *   <li>{@link FlowTodoCountPushService} — WebSocket 待办数推送
 * </ul>
 *
 * <p><b>事务边界：</b>所有公共方法开启 {@code @Transactional(rollbackFor = Exception.class)}， 「参数解析 + 任务构建 +
 * 业务字段设置 + 事件发布」原子性。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>递归保护</b>：AUTO_PASS 通过 {@link ThreadLocal} 维护递归深度，超过 {@link
 *       #MAX_AUTO_PASS_DEPTH}（20）立即抛异常，防止流程定义环路导致栈溢出
   *   <li><b>循环依赖处理</b>：与 {@link FlowTaskCoreService} / {@link
   *       FlowSlaService} / {@link FlowTodoCountPushService} 等服务存在循环依赖， 通过 {@code @Lazy} 注解打破
 *   <li><b>空安全</b>：所有集合 / 字符串参数均做空检查，避免 NPE
 *   <li><b>指标埋点</b>：通过 {@link FlowMetrics} 暴露任务创建数等 Prometheus 指标
 *   <li><b>事件发布</b>：任务创建后通过 {@link FlowTaskSupport#fireEvent} 触发监听器， 通过 {@link
 *       FlowTaskSupport#publishWorkflowEvent} 发布 Spring 事件
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：流程推进到「财务审批」节点
 * String taskId = flowTaskCreateService.createTask(
 *     instanceId,                  // 流程实例 ID
 *     financeApprovalNode,         // 节点配置
 *     flowVariables                // 流程变量
 * );
 * // 返回新创建的任务 ID
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskServiceImpl 任务门面（拆分入口）
 * @see FlowRunTaskVO 运行时任务视图对象
 * @see FlowNodeVO 流程节点视图对象
 * @see DefaultFlowAdvancer 流程推进引擎
 * @see FlowSlaService SLA 服务
 * @see FlowDelegateAuthService 委派代理服务
 * @see FlowAssigneeResolver 审批人解析器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCreateService {

  /** P0-1: 审批人为空统一默认 FALLBACK（最保守：转交管理员人工处理） */
  private static final String DEFAULT_EMPTY_STRATEGY = "FALLBACK";

  // CHECKSTYLE.OFF: RegexpSinglelineJava - ThreadLocal 使用后必须 remove，见 autoPass 方法的 finally 块
  /** AUTO_PASS 递归深度保护（防止流程定义环路导致栈溢出） */
  private static final ThreadLocal<Integer> AUTO_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /** AUTO_PASS 最大递归深度，超过则抛异常 */
  private static final int MAX_AUTO_PASS_DEPTH = 20;
  /** 任务默认优先级 */
  private static final int DEFAULT_TASK_PRIORITY = 50;

  /** 子流程最大嵌套层级默认值 */
  private static final int DEFAULT_MAX_LEVEL = 3;

  /** user: 审批人 token 前缀长度 */
  private static final int USER_TOKEN_PREFIX_LENGTH = 5;

  /** 运行时任务仓储，创建/更新待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 用户仓储，查询审批人/候选人用户信息 */
  private final FlowUserRepository userRepository;

  /** 流程实例仓储，查询/更新实例状态和变量 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储，查询节点配置（审批人/权限/SLA 等） */
  private final FlowNodeRepository nodeRepository;


  /** 流程推进引擎，AUTO_PASS 递归推进到下一节点 */
  private final DefaultFlowAdvancer advancer;

  /** 变量策略，解析节点 ext JSON 中的条件表达式 */
  private final DefaultFlowVariableStrategy variableStrategy;

  /** 审批人解析器，从节点配置解析实际审批人/候选人列表 */
  private final FlowAssigneeResolver assigneeResolver;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 任务归档服务，完成任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** 使用 @Lazy 避免循环依赖：FlowTaskCoreService → FlowTaskCreateService */
  @Lazy private final FlowTaskCoreService flowTaskCoreService;

  private final FlowInstanceService instanceService;

  /** P1-6: SLA 服务（任务创建时应用 SLA 配置） */
  @Lazy private final FlowSlaService slaService;

  /** P1-7: 待办数 WebSocket 推送服务 */
  @Lazy private final FlowTodoCountPushService todoCountPushService;

  /** P1-4: 服务节点执行器（HTTP/SCRIPT/AUTO_PASS） */
  private final FlowServiceNodeExecutor serviceNodeExecutor;

  /** P0-1: 事件订阅服务（服务节点失败时触发 error boundary） */
  @Lazy private final FlowEventSubscriptionService eventSubscriptionService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /** P2-2: 分组办理人解析器（业务系统实现注入时使用自定义逻辑；未注入时使用默认降级为用户 ID） */
  private final FlowGroupResolver groupResolver;

  /** P1-2: 办理人解析服务（从本类抽出，组合模式接入） */
  private final AssigneeResolutionService assigneeResolutionService;

  /** P1-4: 委派改写服务（从本类抽出，组合模式接入） */
  private final DelegateRedirectService delegateRedirectService;

  /** P0-1: 审批人为空兜底策略服务（从本类抽出，组合模式接入） */
  private EmptyAssigneeStrategyService emptyAssigneeStrategyService;

  /** P1-4: 服务节点执行服务（从本类抽出，组合模式接入） */
  private ServiceNodeExecuteService serviceNodeExecuteService;

  /** P2-4: 自动审批服务（从本类抽出，组合模式接入） */
  private final FlowAutoApproveService autoApproveService;

  /** P1-5: 跨节点办理人去重服务（从本类抽出，组合模式接入） */
  private final FlowCrossNodeDedupService crossNodeDedupService;

  /**
   * 初始化 EmptyAssigneeStrategyService
   *
   * <p>由于该服务需要引用本类的 advanceAfterAutoPass 方法作为回调，
   * 无法通过构造函数注入，需要在 @PostConstruct 中手动创建。
   */
  @PostConstruct
  void initEmptyAssigneeStrategyService() {
    this.emptyAssigneeStrategyService = new EmptyAssigneeStrategyService(
        taskRepository,
        archiveService,
        support,
        assigneeResolutionService,
        this::handleAdvanceAfterAutoPass);
  }

  /**
   * 处理自动通过后的推进逻辑（回调方法，供 EmptyAssigneeStrategyService 使用）
   * 
   * <p>将 advanceAfterAutoPass 的调用封装为回调，解耦递归深度保护逻辑。
   *
   * @param ctx 参数说明
   * @return 返回值说明
   */
  private Void handleAdvanceAfterAutoPass(EmptyAssigneeStrategyService.AdvanceContext ctx) {
    advanceAfterAutoPass(ctx.getInstance(), ctx.getNode(), ctx.getVariables());
    return null;
  }

  /**
   * 初始化 ServiceNodeExecuteService
   *
   * <p>由于该服务需要引用本类的 advanceAfterAutoPass 方法作为回调，
   * 无法通过构造函数注入，需要在 @PostConstruct 中手动创建。
   */
  @PostConstruct
  void initServiceNodeExecuteService() {
    this.serviceNodeExecuteService = new ServiceNodeExecuteService(
        serviceNodeExecutor,
        taskRepository,
        archiveService,
        support,
        eventSubscriptionService,
        nodeRepository,
        instanceRepository,
        this::handleAdvanceAfterServiceNode);
  }

  /**
   * 处理服务节点执行成功后的推进逻辑（回调方法，供 ServiceNodeExecuteService 使用）
   * 
   * <p>将 advanceAfterAutoPass 的调用封装为回调，解耦递归深度保护逻辑。
   *
   * @param ctx 参数说明
   * @return 返回值说明
   */
  private Void handleAdvanceAfterServiceNode(ServiceNodeExecuteService.AdvanceContext ctx) {
    advanceAfterAutoPass(ctx.getInstance(), ctx.getNode(), ctx.getVariables());
    return null;
  }

  // ============================== 公共创建入口 ==============================

  /**
   * 创建任务（向后兼容重载）
   *
   * @param instanceId 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(String instanceId, FlowNodeVO node, Map<String, Object> variables) {
    return createTask(instanceId, node, variables, null);
  }

  /**
   * 创建任务（支持显式指定办理人）
   * 
   * <p>GAP-P2-9 自由流扩展：{@code explicitAssignees} 非空时直接作为目标节点办理人， 跳过 {@code node.permissionFlag} /
   * {@code ext.collection} 解析逻辑。 为空时回退到原有解析逻辑（向后兼容）。
   *
   * @param instanceId 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param explicitAssignees 参数说明
   * @return 返回值说明
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(
      String instanceId,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    FlowInstanceVO instance = lookupInstance(instanceId);

    // 特殊节点创建路径（提前返回）
    if (isNodeType(node, FlowNodeType.SERVICE)) {
      return serviceNodeExecuteService.executeServiceNode(instance, node, variables);
    }
    if (isNodeType(node, FlowNodeType.FOREACH)) {
      return createForeachTasks(instance, node, variables, explicitAssignees);
    }
    if (isNodeType(node, FlowNodeType.LEVEL_APPROVAL)) {
      List<String> levelApprovers =
          expandLevelApprovers(instance, node, variables, explicitAssignees);
      if (levelApprovers.isEmpty()) {
        return createTaskWithEmptyAssignee(instance, node, variables);
      }
      return createLevelApprovalTask(instance, node, variables, levelApprovers);
    }

    // 标准审批创建路径：构建 → 后置处理
    TaskBuildResult result = buildTaskEntities(instance, node, variables, explicitAssignees);
    if (result.earlyReturnTaskId() != null) {
      return result.earlyReturnTaskId();
    }
    return postCreateTask(result.task(), instance, node, variables);
  }

  /**
   * 标准节点任务构建结果。
   *
   * <p>当 {@code earlyReturnTaskId} 非空时，表示任务创建流程已提前完成 （去重跳过 / 审批人为空兜底 / 自动去重），无需执行 {@link
   * #postCreateTask}， 此时 {@code task} 为 null。
   *
   * <p>当 {@code task} 非空时，表示任务已构建完毕（含 insert + flow_user）， 需继续执行 {@link #postCreateTask} 完成后置动作。
   *
   * @param earlyReturnTaskId 提前返回的任务 ID（null 表示需继续 postCreateTask）
   * @param task 已持久化的任务实体（null 表示已有提前返回）
   */
  private record TaskBuildResult(String earlyReturnTaskId, FlowRunTaskVO task) {}

  /**
   * 构建标准审批节点的任务实体（含持久化）。
   * 
   * <p>完成办理人解析 → 跨节点去重 → 审批人为空兜底 → 自动去重判断 → 写入任务表 + ydsz_flow_user。 出现提前返回场景（空审批人去重跳过 / 空审批人兜底 /
   * 自动去重命中）时， 通过 {@link TaskBuildResult#earlyReturnTaskId} 传递结果。
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param explicitAssignees 参数说明
   * @return 返回值说明
   */
  private TaskBuildResult buildTaskEntities(
      FlowInstanceVO instance,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    String instanceId = instance.getId();

    // 解析办理人：GAP-P2-9 显式指定优先；否则尝试展开 ROLE/DEPT 为多人
    List<String> userIds =
        (explicitAssignees != null && !explicitAssignees.isEmpty())
            ? new ArrayList<>(explicitAssignees)
            : expandAssignees(node, variables);
    FlowPerformType performType = resolvePerformType(node);

    // P2-2: 分组策略 — GROUP_CLAIM 强制 OR（抢办），GROUP_ALL 强制 PARALLEL（全办）
    FlowPerformType groupPerformType = resolveGroupPerformType(node);
    if (groupPerformType != null && userIds.size() > 1) {
      performType = groupPerformType;
    }

    // P1-5: 跨节点办理人去重
    boolean autoDedup = isAutoDedupEnabled(node);
    if (autoDedup && !userIds.isEmpty()) {
      userIds = applyCrossNodeDedup(userIds, instanceId, node);
    }

    FlowRunTaskVO task = buildBaseTask(instance, node, performType, userIds.size());

    if (userIds.isEmpty()) {
      // 跨节点去重后候选人为空 — 自动跳过该节点
      if (autoDedup) {
        return new TaskBuildResult(handleAutoDedupSkip(task, instance, node, variables), null);
      }
      // P0-1: 审批人为空兜底处理（委托给 EmptyAssigneeStrategyService）
      return new TaskBuildResult(emptyAssigneeStrategyService.handleEmptyAssignee(task, instance, node, variables), null);
    }

    // 正常路径：设置首个办理人
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId(userIds.get(0));
    task.setAssigneeName("USER:" + userIds.get(0));
    applyVoteConfig(task, node);
    // GAP-V2-05: 审批人自动去重 — 仅 OR 触发
    if (performType == FlowPerformType.OR) {
      String dedupTaskId = tryAutoDedup(task, instance, node, variables, userIds.get(0));
      if (dedupTaskId != null) {
        return new TaskBuildResult(dedupTaskId, null);
      }
    }

    // 持久化任务 + 写入 ydsz_flow_user（需 task ID，必须在 insert 之后）
    taskRepository.save(task);
    Map<String, Integer> userWeights = parseUserWeights(node.getExt());
    for (String uid : userIds) {
      insertFlowUser(task, instance, node, uid, userWeights);
    }
    log.info(
        "[Flow] 创建任务: instanceId={} node={} performType={} assigneeCount={}",
        instanceId,
        node.getNodeCode(),
        performType,
        userIds.size());

    return new TaskBuildResult(null, task);
  }

  /**
   * 任务创建后置处理：委派改写 + 事件发布 + WebSocket 推送 + 自动审批。
   * 
   * <p>前置条件：{@code task} 已持久化且 ydsz_flow_user 已写入。
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String postCreateTask(
      FlowRunTaskVO task, FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    // P1-4: 应用长期授权委派（委托给 DelegateRedirectService）
    delegateRedirectService.applyDelegateRedirect(task, instance, node);
    // P0-1: 事件发布
    support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
    support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());
    // P1-7: WebSocket 推送
    if (todoCountPushService != null) {
      todoCountPushService.pushTaskAssigned(task);
    }
    // P2-4: 自动审批节点
    tryAutoApprove(instance, node, task, variables);
    return task.getId();
  }

  // ============================== 内部方法 ==============================

  private FlowInstanceVO lookupInstance(String instanceId) {
    FlowInstanceVO instance = instanceService.getById(instanceId);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    return instance;
  }

  private boolean isNodeType(FlowNodeVO node, FlowNodeType type) {
    return node != null && node.getNodeType() != null && node.getNodeType() == type.getCode();
  }

  /**
   * 构建基础任务对象（设置通用字段）。
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param performType 参数说明
   * @param approveCount 参数说明
   * @return 返回值说明
   */
  private FlowRunTaskVO buildBaseTask(
      FlowInstanceVO instance, FlowNodeVO node, FlowPerformType performType, int approveCount) {
    FlowRunTaskVO task = new FlowRunTaskVO();
    task.setInstanceId(instance.getId());
    task.setFlowCode(instance.getFlowCode());
    task.setDefinitionId(instance.getDefinitionId());
    task.setNodeCode(node.getNodeCode());
    task.setNodeName(node.getNodeName());
    task.setNodeType(node.getNodeType());
    task.setBusinessType(instance.getBusinessType());
    task.setBusinessId(instance.getBusinessId());
    task.setBusinessNo(instance.getBusinessNo());
    task.setFlowName(instance.getFlowName());
    task.setTitle(instance.getTitle());
    task.setPermissionFlag(node.getPermissionFlag());
    task.setPerformType(performType.name());
    task.setApproveCount(approveCount == 0 ? 1 : approveCount);
    task.setApproveFinished(0);
    task.setTaskStatus(FlowTaskStatus.PENDING.name());
    task.setTenantId(instance.getTenantId());
    task.setProviderTraceId(instance.getProviderTraceId());

    // P2-3: 指标
    if (flowMetrics != null) {
      flowMetrics.incTask(instance.getFlowCode(), node.getNodeCode(), "created");
    }
    // P1-1: 优先级
    applyPriority(task, node);
    // P1-6: SLA
    if (slaService != null) {
      slaService.applySlaConfig(task, node);
    }
    return task;
  }

  /**
   * 跨节点去重后候选人为空 — 自动跳过该节点。
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String handleAutoDedupSkip(
      FlowRunTaskVO task, FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId("0");
    task.setAssigneeName("SYSTEM_DEDUP_SKIP");
    task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
    LocalDateTime now = LocalDateTime.now();
    task.setFinishAt(now);
    task.setDurationMs(0L);
    taskRepository.save(task);
    archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
    support.audit(task, "DEDUP_SKIP", null, null, "办理人去重后为空，自动跳过");
    log.info("[Flow] 办理人去重后为空，自动跳过: instanceId={} node={}", instance.getId(), node.getNodeCode());
    advanceAfterAutoPass(instance, node, variables);
    return task.getId();
  }

  /**
   * P2-4 (GAP-14) / P0-4: 自动审批节点（配置化规则引擎）
   *
   * <p>委托给 {@link FlowAutoApproveService} 执行。
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param task 参数说明
   * @param variables 参数说明
   */
  private void tryAutoApprove(
      FlowInstanceVO instance, FlowNodeVO node, FlowRunTaskVO task, Map<String, Object> variables) {
    autoApproveService.tryAutoApprove(instance, node, task, variables);
  }

  /**
   * 写入 ydsz_flow_user 记录
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param uid 参数说明
   * @param userWeights 参数说明
   */
  private void insertFlowUser(
      FlowRunTaskVO task,
      FlowInstanceVO instance,
      FlowNodeVO node,
      String uid,
      Map<String, Integer> userWeights) {
    FlowUserVO fu = new FlowUserVO();
    fu.setTaskId(task.getId());
    fu.setInstanceId(instance.getId());
    fu.setNodeCode(node.getNodeCode());
    fu.setUserType(FlowAssigneeType.USER.name());
    fu.setUserId(uid);
    fu.setUserName("USER:" + uid);
    fu.setProcessed(0);
    fu.setWeight(userWeights == null ? 1 : userWeights.getOrDefault(uid, 1));
    fu.setSignType(FlowSignType.ORIGINAL.name());
    fu.setTenantId(instance.getTenantId());
    fu.setProviderTraceId(instance.getProviderTraceId());
    userRepository.save(fu);
  }

  /**
   * P0-4: 创建逐级审批任务（精简为 PARALLEL 模式）
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param approvers 参数说明
   * @return 返回值说明
   */
  private String createLevelApprovalTask(
      FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables, List<String> approvers) {
    FlowRunTaskVO task = buildBaseTask(instance, node, FlowPerformType.PARALLEL, approvers.size());
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId(approvers.get(0));
    task.setAssigneeName("USER:" + approvers.get(0));
    task.setPriority(DEFAULT_TASK_PRIORITY);
    taskRepository.save(task);
    for (String uid : approvers) {
      insertFlowUser(task, instance, node, uid, null);
    }
    if (flowMetrics != null) {
      flowMetrics.incTask(instance.getFlowCode(), node.getNodeCode(), "created");
    }
    if (todoCountPushService != null) {
      todoCountPushService.pushTaskAssigned(task);
    }
    support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
    support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());
    delegateRedirectService.applyDelegateRedirect(task, instance, node);
    log.info(
        "[Flow] 逐级审批任务创建: instanceId={} node={} approvers={}",
        instance.getId(),
        node.getNodeCode(),
        approvers);
    return task.getId();
  }

  /**
   * P0-4: 逐级审批人为空时走 emptyStrategy 兜底
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String createTaskWithEmptyAssignee(
      FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    Map<String, Object> extConfig = parseExtConfig(node.getExt());
    String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
    FlowRunTaskVO task = buildBaseTask(instance, node, FlowPerformType.OR, 1);

    switch (emptyStrategy) {
      case "AUTO_PASS":
      case "TRANSFER_ADMIN":
      case "ASSIGN_SPECIFIED":
        {
          String fallbackUserId =
              "AUTO_PASS".equals(emptyStrategy)
                  ? "0"
                  : parseLongConfig(
                      extConfig,
                      "TRANSFER_ADMIN".equals(emptyStrategy) ? "adminUserId" : "specifiedUserId",
                      "1");
          task.setAssigneeType(FlowAssigneeType.USER.name());
          task.setAssigneeId(fallbackUserId);
          task.setAssigneeName("SYSTEM_" + emptyStrategy);
          if ("AUTO_PASS".equals(emptyStrategy)) {
            task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
            task.setFinishAt(LocalDateTime.now());
            task.setDurationMs(0L);
          }
          taskRepository.save(task);
          if (FlowTaskStatus.COMPLETED.name().equals(task.getTaskStatus())) {
            archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
            support.audit(
                task, "LEVEL_APPROVAL_" + emptyStrategy, null, null, "逐级审批展开为空，" + emptyStrategy);
            advanceAfterAutoPass(instance, node, variables);
          }
          log.info(
              "[Flow] 逐级审批空兜底: instanceId={} node={} strategy={}",
              instance.getId(),
              node.getNodeCode(),
              emptyStrategy);
          return task.getId();
        }
      default:
        {
          task.setAssigneeType(FlowAssigneeType.USER.name());
          task.setAssigneeId("1");
          task.setAssigneeName("FALLBACK");
          taskRepository.save(task);
          log.warn(
              "[Flow] 逐级审批空兜底 FALLBACK: instanceId={} node={}",
              instance.getId(),
              node.getNodeCode());
          return task.getId();
        }
    }
  }

  /**
   * GAP-P2-10: FOREACH 循环节点 — 对集合中每个元素创建独立 task
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param explicitAssignees 参数说明
   * @return 返回值说明
   */
  private String createForeachTasks(
      FlowInstanceVO instance,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    List<String> elements =
        (explicitAssignees != null && !explicitAssignees.isEmpty())
            ? new ArrayList<>(explicitAssignees)
            : expandAssignees(node, variables);

    if (elements.isEmpty()) {
      Map<String, Object> extConfig = parseExtConfig(node.getExt());
      String emptyStrategy =
          (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
      if ("AUTO_PASS".equals(emptyStrategy)) {
        FlowRunTaskVO autoTask = buildForeachTask(instance, node, "0", "SYSTEM_AUTO_PASS", "0");
        autoTask.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        autoTask.setFinishAt(LocalDateTime.now());
        autoTask.setDurationMs(0L);
        taskRepository.save(autoTask);
        archiveService.archiveToHistory(autoTask, FlowTaskStatus.COMPLETED);
        support.audit(autoTask, "FOREACH_AUTO_PASS", null, null, "FOREACH 集合为空，自动通过");
        log.info(
            "[Flow] FOREACH 集合为空自动通过: instanceId={} node={}", instance.getId(), node.getNodeCode());
        advanceAfterAutoPass(instance, node, variables);
        return autoTask.getId();
      }
      log.warn("[Flow] FOREACH 集合为空，使用 {} 策略: node={}", emptyStrategy, node.getNodeCode());
      elements = List.of("1");
    }

    String firstTaskId = null;
    for (String element : elements) {
      FlowRunTaskVO task = buildForeachTask(instance, node, element, "USER:" + element, element);
      taskRepository.save(task);
      insertFlowUser(task, instance, node, element, null);
      if (flowMetrics != null) {
        flowMetrics.incTask(instance.getFlowCode(), node.getNodeCode(), "created");
      }
      if (todoCountPushService != null) {
        todoCountPushService.pushTaskAssigned(task);
      }
      support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
      support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());
      if (firstTaskId == null) {
        firstTaskId = task.getId();
      }
    }
    log.info(
        "[Flow] FOREACH 创建 {} 条独立 task: instanceId={} node={}",
        elements.size(),
        instance.getId(),
        node.getNodeCode());
    return firstTaskId;
  }

  /**
   * GAP-P2-10: 构建 FOREACH 子任务
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param assigneeId 参数说明
   * @param assigneeName 参数说明
   * @param iterVar 参数说明
   * @return 返回值说明
   */
  private FlowRunTaskVO buildForeachTask(
      FlowInstanceVO instance,
      FlowNodeVO node,
      String assigneeId,
      String assigneeName,
      String iterVar) {
    FlowRunTaskVO task = new FlowRunTaskVO();
    task.setInstanceId(instance.getId());
    task.setFlowCode(instance.getFlowCode());
    task.setDefinitionId(instance.getDefinitionId());
    task.setNodeCode(node.getNodeCode());
    task.setNodeName(node.getNodeName());
    task.setNodeType(node.getNodeType());
    task.setBusinessType(instance.getBusinessType());
    task.setBusinessId(instance.getBusinessId());
    task.setBusinessNo(instance.getBusinessNo());
    task.setFlowName(instance.getFlowName());
    task.setTitle(instance.getTitle());
    task.setPermissionFlag(node.getPermissionFlag());
    task.setPerformType(FlowPerformType.PARALLEL.name());
    task.setApproveCount(1);
    task.setApproveFinished(0);
    task.setTaskStatus(FlowTaskStatus.PENDING.name());
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId(assigneeId);
    task.setAssigneeName(assigneeName);
    task.setTenantId(instance.getTenantId());
    task.setProviderTraceId(instance.getProviderTraceId());
    task.setIterVar(iterVar);
    applyPriority(task, node);
    if (slaService != null) {
      slaService.applySlaConfig(task, node);
    }
    return task;
  }

  /**
   * AUTO_PASS 后推进到下一节点（含递归深度保护）
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   */
  private void advanceAfterAutoPass(
      FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    int depth = AUTO_PASS_DEPTH.get();
    if (depth >= MAX_AUTO_PASS_DEPTH) {
      log.warn("[Flow] AUTO_PASS 递归深度超限: depth={} instanceId={}", depth, instance.getId());
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .message("error.workflow.task.autopass.depth.exceeded")
          .build();
    }
    AUTO_PASS_DEPTH.set(depth + 1);
    try {
      List<FlowNodeVO> nextNodes =
          advancer.advance(instance, node.getNodeCode(), "PASS", null, variables);
      if (nextNodes.isEmpty()) {
        instanceService.complete(instance.getId(), node.getNodeCode());
      } else {
        instanceService.generateTasksForNodes(instance.getId(), nextNodes, variables);
        updateInstanceNode(instance, nextNodes);
      }
    } finally {
      AUTO_PASS_DEPTH.remove();
    }
  }

  /**
   * 更新实例当前节点
   *
   * @param instance 参数说明
   * @param nextNodes 参数说明
   */
  private void updateInstanceNode(FlowInstanceVO instance, List<FlowNodeVO> nextNodes) {
    if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType() != FlowNodeType.END.getCode()) {
      instanceRepository.updateStatus(
          instance.getId(),
          instance.getFlowStatus(),
          nextNodes.get(0).getNodeCode(),
          nextNodes.get(0).getNodeName(),
          null,
          null);
    }
  }

  // ============================== 通用辅助方法 ==============================

  /**
   * P1-1: 从 node.ext.priority 读取优先级（默认 50）
   *
   * @param task 参数说明
   * @param node 参数说明
   */
  private void applyPriority(FlowRunTaskVO task, FlowNodeVO node) {
    Map<String, Object> nodeExt = parseExtConfig(node.getExt());
    Object priorityVal = nodeExt.get("priority");
    if (priorityVal instanceof Number n) {
      task.setPriority(n.intValue());
    } else if (priorityVal instanceof String s && !s.isBlank()) {
      try {
        task.setPriority(Integer.parseInt(s.trim()));
      } catch (NumberFormatException ignore) {
        task.setPriority(DEFAULT_TASK_PRIORITY);
      }
    } else {
      task.setPriority(DEFAULT_TASK_PRIORITY);
    }
  }

  /**
   * 解析 node.ext.votePassRate，配置通过率阈值
   *
   * @param task 参数说明
   * @param node 参数说明
   */
  private void applyVoteConfig(FlowRunTaskVO task, FlowNodeVO node) {
    Map<String, Object> ext = parseExtConfig(node.getExt());
    Object rate = ext.get("votePassRate");
    if (rate instanceof Number n) {
      task.setVotePassRate(BigDecimal.valueOf(n.doubleValue()));
    } else if (rate instanceof String s && !s.isBlank()) {
      try {
        task.setVotePassRate(new BigDecimal(s.trim()));
      } catch (NumberFormatException ignore) {
        // keep default
      }
    }
  }

  /** 解析 node.ext.userWeights，获取加权投票权重映射 */
  private Map<String, Integer> parseUserWeights(String ext) {
    Map<String, Object> config = parseExtConfig(ext);
    Object weights = config.get("userWeights");
    if (weights instanceof Map<?, ?> m) {
      Map<String, Integer> result = new HashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getValue() instanceof Number n) {
          result.put(String.valueOf(e.getKey()), n.intValue());
        }
      }
      return result;
    }
    return null;
  }

  /**
   * P0-4: 展开逐级审批的上级列表
   *
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param explicitAssignees 参数说明
   * @return 返回值说明
   */
  private List<String> expandLevelApprovers(
      FlowInstanceVO instance,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    if (explicitAssignees != null && !explicitAssignees.isEmpty()) {
      return new ArrayList<>(explicitAssignees);
    }
    Map<String, Object> extConfig = parseExtConfig(node.getExt());
    int maxLevel = parseIntConfig(extConfig, "maxLevel", DEFAULT_MAX_LEVEL);
    if (maxLevel < 1) {
      maxLevel = 1;
    }
    String startUserId = resolveInitiatorId(variables);
    if (startUserId == null && instance.getInitiatorId() != null) {
      startUserId = String.valueOf(instance.getInitiatorId());
    }
    if (startUserId == null) {
      log.warn("[Flow] 逐级审批无法解析发起人: instanceId={} node={}", instance.getId(), node.getNodeCode());
      return Collections.emptyList();
    }
    try {
      List<Long> leaders = assigneeResolver.expandMultiLeader(startUserId, maxLevel, variables);
      if (leaders == null || leaders.isEmpty()) {
        return Collections.emptyList();
      }
      List<String> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (Long uid : leaders) {
        String s = String.valueOf(uid);
        String stopAtUserId = (String) extConfig.get("stopAtUserId");
        if (stopAtUserId != null && stopAtUserId.equals(s)) {
          result.add(s);
          break;
        }
        if (seen.add(s)) {
          result.add(s);
        }
      }
      return result;
    } catch (Exception e) {
      log.error("[Flow] 逐级审批展开异常: instanceId={} err={}", instance.getId(), e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /**
   * GAP-V2-05: 审批人自动去重检查
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param currentAssigneeId 参数说明
   * @return 返回值说明
   */
  private String tryAutoDedup(
      FlowRunTaskVO task,
      FlowInstanceVO instance,
      FlowNodeVO node,
      Map<String, Object> variables,
      String currentAssigneeId) {
    try {
      List<FlowRunTaskVO> prevTasks = taskRepository.findByInstanceId(instance.getId()).stream()
          .filter(t -> FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()))
          .sorted((a, b) -> b.getId().compareTo(a.getId()))
          .limit(1)
          .collect(Collectors.toList());
      if (prevTasks.isEmpty()) {
        return null;
      }
      FlowRunTaskVO prevTask = prevTasks.get(0);
      String prevAssigneeId = prevTask.getAssigneeId();
      if (prevAssigneeId == null
          || !prevAssigneeId.equals(currentAssigneeId)
          || "SYSTEM_AUTO_PASS".equals(prevTask.getAssigneeName())) {
        return null;
      }
      log.info(
          "[Flow] 审批人自动去重: instanceId={} node={} assigneeId={}",
          instance.getId(),
          node.getNodeCode(),
          currentAssigneeId);
      task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
      LocalDateTime now = LocalDateTime.now();
      task.setFinishAt(now);
      task.setDurationMs(0L);
      taskRepository.save(task);
      archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
      support.audit(task, "AUTO_DEDUP", null, null, "审批人与上一节点相同，自动去重跳过");
      advanceAfterAutoPass(instance, node, variables);
      return task.getId();
    } catch (Exception e) {
      log.warn(
          "[Flow] 审批人自动去重检查异常: instanceId={} node={} err={}",
          instance.getId(),
          node.getNodeCode(),
          e.getMessage());
      return null;
    }
  }

  /**
   * P1-5: 跨节点办理人去重
   *
   * <p>委托给 {@link FlowCrossNodeDedupService} 执行。
   *
   * @param userIds 参数说明
   * @param instanceId 参数说明
   * @param node 参数说明
   * @return 返回值说明
   */
  private List<String> applyCrossNodeDedup(List<String> userIds, String instanceId, FlowNodeVO node) {
    return crossNodeDedupService.applyCrossNodeDedup(userIds, instanceId, node);
  }

  /**
   * P1-5: 判断节点是否启用跨节点去重
   *
   * <p>委托给 {@link FlowCrossNodeDedupService} 执行。
   *
   * @param node 参数说明
   * @return 返回值说明
   */
  private boolean isAutoDedupEnabled(FlowNodeVO node) {
    return crossNodeDedupService.isAutoDedupEnabled(node);
  }

  /**
   * 解析会签类型
   *
   * @param node 参数说明
   * @return 返回值说明
   */
  private FlowPerformType resolvePerformType(FlowNodeVO node) {
    if (node.getExt() != null) {
      try {
        Map<?, ?> ext = FlowNodeExt.parseSafe(node.getExt());
        Object ptObj = ext.get("performType");
        if (ptObj instanceof String pt) {
          return FlowPerformType.valueOf(pt);
        }
      } catch (Exception ignored) {
        log.debug("[FlowTaskCreateService] performType 解析失败，使用默认 OR: {}", ignored.getMessage());
      }
    }
    return FlowPerformType.OR;
  }

  /**
   * 展开办理人为用户列表
   *
   * <p>P0-2 增强：当节点 ext 配置 {@code selfSelect: true} 时，优先从流程变量中 读取发起人自选审批人（{@code
   * _selfSelect_<nodeCode>}），无需在 permissionFlag 中显式配置 {@code self_select:} 前缀。自选变量为空时回退到
   * permissionFlag 解析。
   *
   * <p>P2-2 分组策略：当节点 ext.assigneeType 为 {@code GROUP_CLAIM} / {@code GROUP_ALL} 时，
   * 通过 {@link FlowGroupResolver} 查询分组成员列表。
   *
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private List<String> expandAssignees(FlowNodeVO node, Map<String, Object> variables) {
    Map<String, Object> nodeExt = parseExtConfig(node.getExt());

    // P0-2: 节点 ext 配置 selfSelect=true 时，优先读取自选审批人
    List<String> selfSelectResult = tryExpandSelfSelectAssignees(node, variables, nodeExt);
    if (selfSelectResult != null) {
      return selfSelectResult;
    }

    // 尝试从 collection 变量展开
    List<String> collectionResult = tryExpandCollectionAssignees(node, variables, nodeExt);
    if (collectionResult != null) {
      return collectionResult;
    }

    // P2-2: 分组策略 — 节点 ext.assigneeType = GROUP_CLAIM / GROUP_ALL
    List<String> groupResult = tryExpandGroupAssignees(node, variables, nodeExt);
    if (groupResult != null) {
      return groupResult;
    }

    // 回退到 permissionFlag 解析
    return expandPermissionFlagAssignees(node, variables);
  }

  /**
   * 尝试从 selfSelect 配置展开审批人，不需要时返回 null。
   *
   * @param node 参数说明
   * @param variables 参数说明
   * @param nodeExt 参数说明
   * @return 返回值说明
   */
  private List<String> tryExpandSelfSelectAssignees(FlowNodeVO node, Map<String, Object> variables,
      Map<String, Object> nodeExt) {
    Object selfSelectFlag = nodeExt.get("selfSelect");
    if (selfSelectFlag == null || !isBooleanTrue(selfSelectFlag) || variables == null) {
      return null;
    }
    Object selfSelectVal = variables.get("_selfSelect_" + node.getNodeCode());
    List<String> expanded = expandCollectionValue(selfSelectVal);
    if (!expanded.isEmpty()) {
      log.info("[Flow] P0-2 自选审批人展开: nodeCode={} count={}", node.getNodeCode(), expanded.size());
      return expanded;
    }
    // 自选变量为空 → 检查是否允许回退
    Object allowFallback = nodeExt.get("selfSelectAllowFallback");
    if (!isBooleanTrue(allowFallback)) {
      log.warn("[Flow] P0-2 自选审批人为空且未配置 fallback: nodeCode={}", node.getNodeCode());
      return Collections.emptyList();
    }
    log.info("[Flow] P0-2 自选审批人为空，回退到 permissionFlag: nodeCode={}", node.getNodeCode());
    return null;
  }

  /**
   * 尝试从 collection 变量展开审批人，不需要时返回 null。
   *
   * @param node 参数说明
   * @param variables 参数说明
   * @param nodeExt 参数说明
   * @return 返回值说明
   */
  private List<String> tryExpandCollectionAssignees(FlowNodeVO node, Map<String, Object> variables,
      Map<String, Object> nodeExt) {
    Object collectionVar = nodeExt.get("collection");
    if (collectionVar == null || variables == null || variables.isEmpty()) {
      return null;
    }
    String varName = String.valueOf(collectionVar).trim();
    if (varName.startsWith("${") && varName.endsWith("}")) {
      varName = varName.substring(2, varName.length() - 1).trim();
    }
    Object collectionValue = variables.get(varName);
    if (collectionValue == null) {
      collectionValue = variables.get("_selfSelect_" + node.getNodeCode());
    }
    List<String> expanded = expandCollectionValue(collectionValue);
    if (!expanded.isEmpty()) {
      log.info("[Flow] collection 变量展开: nodeCode={} var={} count={}",
          node.getNodeCode(), varName, expanded.size());
      return expanded;
    }
    log.warn("[Flow] collection 变量为空: nodeCode={} var={}", node.getNodeCode(), varName);
    return Collections.emptyList();
  }

  /**
   * P2-2: 解析分组节点的会签类型。
   *
   * <p>GROUP_CLAIM → OR（抢办，第一人签收即获得处理权）；
   * GROUP_ALL → PARALLEL（全办，全员审批）；其他返回 null。
   *
   * @param node 流程节点
   * @return 分组会签类型，非分组节点返回 null
   */
  private FlowPerformType resolveGroupPerformType(FlowNodeVO node) {
    if (node.getExt() == null) {
      return null;
    }
    try {
      Map<?, ?> ext = FlowNodeExt.parseSafe(node.getExt());
      Object atObj = ext.get("assigneeType");
      if (atObj instanceof String assigneeType) {
        if (FlowAssigneeType.GROUP_CLAIM.name().equals(assigneeType)) {
          return FlowPerformType.OR;
        }
        if (FlowAssigneeType.GROUP_ALL.name().equals(assigneeType)) {
          return FlowPerformType.PARALLEL;
        }
      }
    } catch (Exception ignored) {
      log.debug("[FlowTaskCreateService] 解析分组类型失败，跳过: {}", ignored.getMessage());
    }
    return null;
  }

  /**
   * P2-2: 分组策略 — 尝试从 ext.assigneeType 展开分组成员。
   *
   * <p>当 ext.assigneeType 为 {@code GROUP_CLAIM} 或 {@code GROUP_ALL} 时，
   * 通过 {@link FlowGroupResolver} 查询分组成员列表。未配置分组类型时返回 null，
   * 回退到后续逻辑。
   *
   * @param node      参数说明
   * @param variables 参数说明
   * @param nodeExt   参数说明
   * @return 返回值说明
   */
  private List<String> tryExpandGroupAssignees(
      FlowNodeVO node, Map<String, Object> variables, Map<String, Object> nodeExt) {
    if (nodeExt == null) {
      return null;
    }
    Object assigneeTypeObj = nodeExt.get("assigneeType");
    if (!(assigneeTypeObj instanceof String assigneeType)) {
      return null;
    }
    if (!FlowAssigneeType.GROUP_CLAIM.name().equals(assigneeType)
        && !FlowAssigneeType.GROUP_ALL.name().equals(assigneeType)) {
      return null;
    }
    Object groupCodeObj = nodeExt.get("groupCode");
    String groupCode =
        groupCodeObj != null ? String.valueOf(groupCodeObj) : node.getPermissionFlag();
    if (groupCode == null || groupCode.isBlank()) {
      log.warn("[Flow] P2-2 分组节点未配置 groupCode: nodeCode={}", node.getNodeCode());
      return Collections.emptyList();
    }
    String tenantId =
        variables != null ? String.valueOf(variables.getOrDefault("tenantId", "")) : "";
    List<String> members = groupResolver.resolveGroupMembers(groupCode, tenantId);
    if (members == null || members.isEmpty()) {
      log.warn("[Flow] P2-2 分组成员为空: nodeCode={} groupCode={}", node.getNodeCode(), groupCode);
      return Collections.emptyList();
    }
    log.info(
        "[Flow] P2-2 分组展开: nodeCode={} groupCode={} count={}",
        node.getNodeCode(), groupCode, members.size());
    return members;
  }

  /**
   * 从 permissionFlag 解析并展开审批人列表。
   *
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private List<String> expandPermissionFlagAssignees(FlowNodeVO node, Map<String, Object> variables) {
    String perm = node.getPermissionFlag();
    if (!StringUtils.hasText(perm)) {
      return Collections.emptyList();
    }
    String resolved = variableStrategy.resolveAssignee(perm, variables);
    if (resolved == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String token : resolved.split(",")) {
      expandTokenToAssignees(token.trim(), node, variables, result, seen);
    }
    return result;
  }

  /**
   * 展开单个 token 到审批人列表（self_select / user / multi_leader / dept_leader / role）。
   *
   * @param token 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param result 参数说明
   * @param seen 参数说明
   */
  private void expandTokenToAssignees(String token, FlowNodeVO node, Map<String, Object> variables,
      List<String> result, Set<String> seen) {
    if (token.isEmpty()) {
      return;
    }
    if (token.startsWith("self_select:")) {
      expandSelfSelectToken(token, node, variables, result, seen);
      return;
    }
    if (token.startsWith("user:")) {
      String uid = token.substring(USER_TOKEN_PREFIX_LENGTH).trim();
      if (!uid.isEmpty() && seen.add(uid)) {
        result.add(uid);
      }
      return;
    }
    if (token.startsWith("multi_leader:")) {
      expandMultiLeaderToken(token, node, variables, result, seen);
      return;
    }
    if (token.startsWith("dept_leader:")) {
      expandDeptLeaderToken(token, variables, result, seen);
      return;
    }
    // role / dept 等普通占位符
    List<Long> expanded = assigneeResolver.expandUsers(token, variables);
    if (expanded != null) {
      for (Long uid : expanded) {
        String s = String.valueOf(uid);
        if (seen.add(s)) {
          result.add(s);
        }
      }
    }
  }

  /**
   * 展开 self_select token：从流程变量读取自选审批人。
   *
   * @param token 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param result 参数说明
   * @param seen 参数说明
   */
  private void expandSelfSelectToken(String token, FlowNodeVO node, Map<String, Object> variables,
      List<String> result, Set<String> seen) {
    String varName = token.substring("self_select:".length()).trim();
    Object selfSelectVal = variables != null ? variables.get("_selfSelect_" + node.getNodeCode()) : null;
    if (selfSelectVal == null && variables != null && !varName.isEmpty()) {
      selfSelectVal = variables.get(varName);
    }
    List<String> expanded = expandCollectionValue(selfSelectVal);
    for (String uid : expanded) {
      if (seen.add(uid)) {
        result.add(uid);
      }
    }
  }

  /**
   * 展开 multi_leader token：向上追溯多级领导。
   *
   * @param token 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param result 参数说明
   * @param seen 参数说明
   */
  private void expandMultiLeaderToken(String token, FlowNodeVO node, Map<String, Object> variables,
      List<String> result, Set<String> seen) {
    String levelStr = token.substring("multi_leader:".length()).trim();
    int levels = 1;
    try {
      levels = Integer.parseInt(levelStr);
    } catch (NumberFormatException ignored) {
      // use default
    }
    String startUserId = resolveInitiatorId(variables);
    if (startUserId != null) {
      List<Long> expanded = assigneeResolver.expandMultiLeader(startUserId, levels, variables);
      if (expanded != null) {
        for (Long uid : expanded) {
          String s = String.valueOf(uid);
          if (seen.add(s)) {
            result.add(s);
          }
        }
      }
    }
  }

  /**
   * 展开 dept_leader token：查询部门领导。
   *
   * @param token 参数说明
   * @param variables 参数说明
   * @param result 参数说明
   * @param seen 参数说明
   */
  private void expandDeptLeaderToken(String token, Map<String, Object> variables,
      List<String> result, Set<String> seen) {
    String deptId = token.substring("dept_leader:".length()).trim();
    if (!deptId.isEmpty()) {
      Long leaderId = assigneeResolver.expandDeptLeader(deptId, variables);
      if (leaderId != null) {
        String s = String.valueOf(leaderId);
        if (seen.add(s)) {
          result.add(s);
        }
      }
    }
  }

  /**
   * P1-4: 将 collection / self_select 变量值展开为用户 ID 字符串列表
   *
   * @param value 参数说明
   * @return 返回值说明
   */
  private List<String> expandCollectionValue(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    if (value instanceof List<?> list) {
      for (Object item : list) {
        if (item == null) {
          continue;
        }
        String s = String.valueOf(item).trim();
        if (!s.isEmpty()) {
          result.add(s);
        }
      }
    } else if (value instanceof Object[] arr) {
      for (Object item : arr) {
        if (item == null) {
          continue;
        }
        String s = String.valueOf(item).trim();
        if (!s.isEmpty()) {
          result.add(s);
        }
      }
    } else {
      String s = String.valueOf(value).trim();
      if (!s.isEmpty()) {
        for (String part : s.split(",")) {
          String p = part.trim();
          if (!p.isEmpty()) {
            result.add(p);
          }
        }
      }
    }
    return result;
  }

  /**
   * 从流程变量中解析发起人 ID
   *
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String resolveInitiatorId(Map<String, Object> variables) {
    return assigneeResolutionService.resolveInitiatorId(variables);
  }

  private void resolveAssignee(
      FlowRunTaskVO task,
      FlowNodeVO node,
      Map<String, Object> variables,
      FlowAssigneeDTO explicit,
      FlowInstanceVO instance) {
    assigneeResolutionService.resolveAssignee(task, node, variables, explicit, instance);
  }

  /** 解析 node.ext JSON 为 Map（委托给 FlowNodeExt 统一实现） */
  private Map<String, Object> parseExtConfig(String ext) {
    return FlowNodeExt.parseSafe(ext);
  }

  /**
   * P0-2: 判断 ext 配置中的布尔值是否为 true。
   *
   * @param val 配置值（Boolean / String / Number）
   * @return true 当值为 true / "true" / 1
   */
  private boolean isBooleanTrue(Object val) {
    if (val == null) {
      return false;
    }
    if (val instanceof Boolean b) {
      return b;
    }
    if (val instanceof Number n) {
      return n.intValue() != 0;
    }
    return "true".equalsIgnoreCase(String.valueOf(val).trim());
  }

  /**
   * 从 extConfig 中读取字符串配置值
   *
   * @param config 参数说明
   * @param key 参数说明
   * @param defaultValue 参数说明
   * @return 返回值说明
   */
  private String parseLongConfig(Map<String, Object> config, String key, String defaultValue) {
    Object val = config.get(key);
    if (val == null) {
      return defaultValue;
    }
    if (val instanceof Number n) {
      return String.valueOf(n.longValue());
    }
    return String.valueOf(val);
  }

  /**
   * 解析 int 配置
   *
   * @param config 参数说明
   * @param key 参数说明
   * @param defaultValue 参数说明
   * @return 返回值说明
   */
  private int parseIntConfig(Map<String, Object> config, String key, int defaultValue) {
    Object val = config.get(key);
    if (val == null) {
      return defaultValue;
    }
    if (val instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(val));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
