package com.njydsz.workflow.server.engine.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.engine.FlowTerminateEventHandler;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowJoinTokenService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.impl.instance.DefaultFlowRoutingService;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceLifecycleManager;

/**
 * 流程推进器默认实现
 *
 * <p>P0 修复：排他网关互斥（CONDITION 只取第一条匹配）、并行网关 join 聚合。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class DefaultFlowAdvancer {


  /** P1: 流程定义元数据缓存（节点 + skip），替代直查 nodeMapper/skipMapper */
  private final FlowDefinitionCacheService flowDefinitionCacheService;

  private final FlowInstanceRepository instanceRepository;
  private final FlowRunTaskRepository taskRepository;
  private final FlowTaskService taskService;
  private final FlowInstanceService instanceService;
  private final DefaultFlowVariableStrategy variableStrategy;

  /** GAP-P2: 并行网关 join 令牌服务（精确跟踪分支到达状态） */
  private final FlowJoinTokenService joinTokenService;

  /**
   * 智能路由服务（可选注入）
   *
   * <p>P2-1: 本字段注入 {@link DefaultFlowRoutingService}（引擎自包含实现）， 引擎内置异常检测与路由能力，无需依赖外部模块。
   */
  private final DefaultFlowRoutingService routingService;

  /** 统一配置属性 */
  private final FlowProperties flowProperties;

  /** BPMN 终止事件处理器 */
  private final FlowTerminateEventHandler terminateEventHandler;

  /** 流程实例生命周期管理器，终止事件时调用 */
  private final FlowInstanceLifecycleManager lifecycleManager;

  public DefaultFlowAdvancer(
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowInstanceRepository instanceRepository,
      FlowRunTaskRepository taskRepository,
      FlowTaskService taskService,
      FlowInstanceService instanceService,
      DefaultFlowVariableStrategy variableStrategy,
      FlowJoinTokenService joinTokenService,
      ObjectProvider<DefaultFlowRoutingService> routingServiceProvider,
      FlowProperties flowProperties,
      FlowTerminateEventHandler terminateEventHandler,
      @Lazy FlowInstanceLifecycleManager lifecycleManager) {
    this.flowDefinitionCacheService = flowDefinitionCacheService;
    this.instanceRepository = instanceRepository;
    this.taskRepository = taskRepository;
    this.taskService = taskService;
    this.instanceService = instanceService;
    this.variableStrategy = variableStrategy;
    this.joinTokenService = joinTokenService;
    this.routingService = routingServiceProvider.getIfAvailable();
    this.flowProperties = flowProperties;
    this.terminateEventHandler = terminateEventHandler;
    this.lifecycleManager = lifecycleManager;
  }

  public FlowInstanceService getInstanceService() {
    return instanceService;
  }

  /**
   * 启动流程实例：从开始节点推进到第一批业务节点并生成待办。
   *
   * <p><b>推进流程：</b>定位开始节点 → 以 {@code PASS} 推进一步 → 为下一批节点生成任务 →
   * 回写实例的当前节点。若开始节点直连结束节点（无下游），流程<b>立即自动完成</b>， 不产生任何待办。下一批节点为 {@code END} 类型时不更新当前节点，交由完成逻辑收尾。
   *
   * <p><b>并发控制：</b>以 {@code flow:instance:op:{instanceId}} 为键加分布式锁， 最长等锁 5 秒、持锁 60
   * 秒，防止「重复提交」或「启动与审批并发」造成同一实例被推进两次。 抢锁失败向调用方返回「流程正在处理中，请稍后重试」。
   *
   * <p><b>注意（Spring AOP 自调用）：</b>方法体内直接调用 {@link #advance} 属于同类自调用， 不经过代理，因此 {@code advance}
   * 上的同键锁注解<b>不会</b>再次生效——这既避免了 非可重入锁自死锁，也意味着 {@code advance} 的原子性在此路径下完全由本方法的锁保证。
   *
   * <p><b>事务边界：</b>本方法整体包在 {@code @Transactional} 事务中，任务生成与状态回写 在同一事务内提交，避免"任务已生成但状态未更新"的中间态。
   * P2-1: 异常态兜底逻辑由业务系统通过 cronjob 引擎自行编排对账任务实现。
   *
   * @param instanceId 流程实例 ID，不可为 {@code null}
   * @return 推进后的实例视图，含当前待办任务列表
   * @throws SysException 实例不存在时抛出，错误码 {@link YdszResultCode#NOT_FOUND}； 流程定义缺少开始节点时抛出，错误码 {@link
   *     YdszResultCode#INTERNAL_ERROR}
   */
  @Transactional
  @YdszDistributedLock(
      key = "'flow:instance:op:' + #{#instanceId}",
      waitTime = 5,
      leaseTime = 60,
      message = "流程正在处理中，请稍后重试")
  public FlowInstanceViewDTO start(String instanceId) {
    FlowInstanceVO instance = instanceService.getById(instanceId);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    FlowNodeVO startNode = flowDefinitionCacheService.getStartNode(instance.getDefinitionId());
    if (startNode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.instance.start.node.missing")
          .params(instance.getDefinitionId())
          .build();
    }
    List<FlowNodeVO> nextNodes =
        advance(
            instance, startNode.getNodeCode(), "PASS", null, parseVariable(instance.getVariable()));
    if (nextNodes.isEmpty()) {
      log.info("[Flow] 流程无下游节点，自动完成: instanceId={}", instanceId);
      instanceService.complete(instanceId, startNode.getNodeCode());
      return instanceService.toView(
          instanceService.getById(instanceId), loadCurrentTasks(instanceId));
    }
    // P0: BPMN 终止事件检查 — 如果下一节点是终止事件节点，立即终止实例
    if (terminateEventHandler.isTerminateEventNode(nextNodes.get(0))) {
      String nodeCode = nextNodes.get(0).getNodeCode();
      log.info("[Flow] BPMN终止事件触发: instanceId={} nodeCode={}", instanceId, nodeCode);
      lifecycleManager.doTerminateInstance(instanceId,
          terminateEventHandler.getDescription(nodeCode));
      return instanceService.toView(
          instanceService.getById(instanceId), loadCurrentTasks(instanceId));
    }
    // P2-4: 直接通过接口调用，无需 instanceof 强转
    instanceService.generateTasksForNodes(
        instanceId, nextNodes, parseVariable(instance.getVariable()));
    if (nextNodes.get(0).getNodeType() != FlowNodeType.END.getCode()) {
      instanceRepository.updateStatus(
          instanceId,
          instance.getFlowStatus(),
          nextNodes.get(0).getNodeCode(),
          nextNodes.get(0).getNodeName(),
          null,
          null);
    }
    return instanceService.toView(
        instanceService.getById(instanceId), loadCurrentTasks(instanceId));
  }

  /**
   * 计算流程从当前节点推进后应到达的下一批节点。
   *
   * <p><b>本方法是纯粹的「路由计算」</b>：只返回目标节点列表，<b>不</b>创建任务、 <b>不</b>修改实例状态，副作用仅限于并行网关的 join 令牌。任务生成与状态流转
   * 由调用方（{@code instanceService}）负责，便于在同一事务中统一提交。
   *
   * <h3>REJECT 回退规则</h3>
   *
   * <ol>
   *   <li>显式传入 {@code targetNodeCode} 时优先按其回退，支持跳退到任意历史节点
   *   <li>未指定时由 {@link #resolveRejectTarget} 推导：取当前节点第一条入边的来源节点， 无入边则回退到开始节点
   *   <li>推导不出目标或目标节点不存在时抛异常，<b>不会</b>静默把流程留在原地
   * </ol>
   *
   * <h3>PASS 推进规则</h3>
   *
   * <ul>
   *   <li>出边由 {@link #resolvePassSkips} 按网关语义筛选：排他网关只取首条匹配， 包容网关取全部匹配，均无匹配时走 BPMN {@code default}
   *       出边
   *   <li>跳转目标节点不存在时<b>跳过该边并告警</b>，不中断其余分支，避免一条脏数据卡死整个流程
   *   <li>返回空列表表示无下游，调用方据此判定流程结束
   * </ul>
   *
   * <h3>并行/包容网关 join 聚合</h3>
   *
   * <p>目标为多入边的 join 节点时，通过 {@code joinTokenService} 令牌精确跟踪分支到达情况， 支持由节点 {@code ext.joinRequired}
   * 配置的 N/M 聚合（全部到达 / 指定条数 / 过半数）。 未达聚合条件的分支<b>不进入返回列表</b>，即在此处静默等待。
   *
   * <p><b>降级策略：</b>令牌服务（Redis）异常时回退为扫描入边源节点的活跃任务数， 仅统计 PENDING/CLAIMED 任务。这样即使某分支已
   * CANCELLED/FAILED，join 也不会永久挂起。 降级路径依赖任务表实时状态，精度低于令牌，可能出现少数重复聚合。
   *
   * <p><b>并发控制：</b>锁键与 {@link #start} 相同；但经 {@code start}/{@code advanceMulti}
   * 内部自调用进入时不走代理，锁由外层方法持有（详见 {@link #start}）。
   *
   * @param currentInstance 当前流程实例，须已持久化且 {@code definitionId} 有效，不可为 {@code null}
   * @param currentNodeCode 当前节点编码，不可为 {@code null}
   * @param skipType 推进类型，{@code "REJECT"} 走回退分支，其余（通常为 {@code "PASS"}）走正向推进，大小写不敏感
   * @param targetNodeCode 回退目标节点编码；仅 REJECT 生效，为 {@code null} 时自动推导
   * @param variables 流程变量，用于条件表达式求值；可为 {@code null}，等价于无变量
   * @return 下一批节点列表；流程已无下游或 join 仍在等待时返回<b>空列表</b>而非 {@code null}
   * @throws SysException 当前节点不存在或回退目标不存在时抛出，错误码 {@link YdszResultCode#NOT_FOUND}； REJECT
   *     无法推导出回退目标时抛出，错误码 {@link YdszResultCode#BAD_REQUEST}
   */
  @YdszDistributedLock(
      key = "'flow:instance:op:' + #{#currentInstance.id}",
      waitTime = 5,
      leaseTime = 60,
      message = "流程正在处理中，请稍后重试")
  public List<FlowNodeVO> advance(
      FlowInstanceVO currentInstance,
      String currentNodeCode,
      String skipType,
      String targetNodeCode,
      Map<String, Object> variables) {
    FlowNodeVO currentNode =
        flowDefinitionCacheService.getNodeByCode(
            currentInstance.getDefinitionId(), currentNodeCode);
    if (currentNode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.node.not.found")
          .params(currentNodeCode)
          .build();
    }

    // REJECT 退回
    if ("REJECT".equalsIgnoreCase(skipType)) {
      return executeRejectRollback(currentInstance, currentNodeCode, targetNodeCode);
    }

    // PASS 推进
    return executePassUpdate(currentInstance, currentNode, variables);
  }

  /**
   * REJECT 回退：解析目标节点、校验存在性，返回单元素列表。
   * 
   * <p>显式传入 {@code targetNodeCode} 时优先按其回退；未指定时由 {@link #resolveRejectTarget} 推导。
   * 推导不出目标或目标节点不存在时抛异常，不会静默把流程留在原地。
   *
   * @param currentInstance 当前流程实例
   * @param currentNodeCode 当前节点编码
   * @param targetNodeCode 回退目标节点编码（可空）
   * @return 包含单个目标节点的列表
   */
  private List<FlowNodeVO> executeRejectRollback(
      FlowInstanceVO currentInstance, String currentNodeCode, String targetNodeCode) {
    String rejectTarget =
        targetNodeCode != null
            ? targetNodeCode
            : resolveRejectTarget(currentInstance.getDefinitionId(), currentNodeCode);
    if (rejectTarget == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.reject.target.not.found")
          .params(currentNodeCode)
          .build();
    }
    FlowNodeVO target =
        flowDefinitionCacheService.getNodeByCode(currentInstance.getDefinitionId(), rejectTarget);
    if (target == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.node.not.found")
          .params(rejectTarget)
          .build();
    }
    return List.of(target);
  }

  /**
   * PASS 推进：解析出边 + 遍历目标节点并执行 join 聚合。
   * 
   * <p>无下游节点时返回空列表（流程结束）；有下游时交由 {@link #aggregateJoinResults} 执行网关 join 聚合逻辑。
   *
   * @param currentInstance 当前流程实例
   * @param currentNode 当前节点实体
   * @param variables 流程变量
   * @return 下一批目标节点列表
   */
  private List<FlowNodeVO> executePassUpdate(
      FlowInstanceVO currentInstance, FlowNodeVO currentNode, Map<String, Object> variables) {
    List<FlowSkipVO> skips = resolvePassSkips(currentInstance, currentNode, variables);
    if (skips.isEmpty()) {
      log.info(
          "[Flow] 流程无下一节点，结束: instanceId={} nodeCode={}",
          currentInstance.getId(),
          currentNode.getNodeCode());
      return Collections.emptyList();
    }
    return aggregateJoinResults(currentInstance, skips);
  }

  /**
   * PASS 推进：遍历出边 skip，解析节点并执行并行网关 join 聚合。
   * 
   * <p>跳过不存在的目标节点并告警；对于 join 节点委托 {@link #tryAggregateJoin} 执行令牌聚合逻辑，未满足聚合条件的分支不进入返回列表（静默等待）。
   *
   * @param currentInstance 当前流程实例
   * @param skips 当前节点的正向跳转列表
   * @return 经 join 聚合后的下一批节点列表
   */
  private List<FlowNodeVO> aggregateJoinResults(FlowInstanceVO currentInstance, List<FlowSkipVO> skips) {
    List<FlowNodeVO> nextNodes = new ArrayList<>(skips.size());
    for (FlowSkipVO skip : skips) {
      FlowNodeVO next =
          flowDefinitionCacheService.getNodeByCode(
              currentInstance.getDefinitionId(), skip.getNextNodeCode());
      if (next == null) {
        log.warn("[Flow] 跳转目标节点不存在: skipId={} nextNode={}", skip.getId(), skip.getNextNodeCode());
        continue;
      }
      // P0-5 / GAP-P2 / P0-3: 网关 join 聚合 — 支持 N/M join 策略
      if (isJoinNode(next)
          && hasMultipleIncoming(currentInstance.getDefinitionId(), next.getNodeCode())) {
        if (!tryAggregateJoin(currentInstance, next)) {
          continue; // 未满足聚合条件，等待其他分支
        }
      }
      nextNodes.add(next);
    }
    return nextNodes;
  }

  /**
   * P0-5 / GAP-P2 / P0-3: 尝试通过 join 聚合检查。
   * 
   * <p>通过令牌服务标记本次分支到达，判断是否满足 N/M 聚合条件。 令牌服务（Redis）异常时降级为扫描活跃任务数，避免 join 永久挂起。
   * 
   * 
   *
   * @param currentInstance 当前流程实例
   * @param joinNode join 类型目标节点
   * @return true=允许通过聚合检查；false=继续等待
   */
  private boolean tryAggregateJoin(FlowInstanceVO currentInstance, FlowNodeVO joinNode) {
    String definitionId = currentInstance.getDefinitionId();
    String joinCode = joinNode.getNodeCode();
    String instId = currentInstance.getId();
    int incomingCount =
        flowDefinitionCacheService.getSkipsByNextNode(definitionId, joinCode).size();
    try {
      // P0-3: 从节点 VO ext 中解析 joinRequired 配置（N/M 聚合）
      int requiredCount = joinNode.getJoinRequired(incomingCount);
      // 懒初始化：首次到达时初始化令牌
      if (!joinTokenService.isInitialized(instId, joinCode)) {
        if (requiredCount < incomingCount) {
          // N/M join: 部分分支到达即可聚合
          joinTokenService.initTokensWithRequired(instId, joinCode, incomingCount, requiredCount);
          log.info(
              "[Flow] P0-3 N/M join 初始化: instanceId={} node={} total={} required={}",
              instId,
              joinCode,
              incomingCount,
              requiredCount);
        } else {
          joinTokenService.initTokens(instId, joinCode, incomingCount);
        }
      }
      // 标记本次到达
      boolean canJoin;
      if (requiredCount < incomingCount) {
        canJoin = joinTokenService.arriveTokenWithRequired(instId, joinCode);
      } else {
        canJoin = joinTokenService.arriveToken(instId, joinCode);
      }
      if (canJoin) {
        joinTokenService.clearTokens(instId, joinCode);
        log.info(
            "[Flow] 并行网关 join 聚合通过: instanceId={} node={} required={}/{}",
            instId,
            joinCode,
            requiredCount,
            incomingCount);
        return true;
      } else {
        log.info(
            "[Flow] 并行网关 join 等待: instanceId={} node={} required={}/{}",
            instId,
            joinCode,
            requiredCount,
            incomingCount);
        return false;
      }
    } catch (Exception e) {
      // Redis 异常降级：回退到 countPendingByNode 逻辑
      log.warn(
          "[Flow] join 令牌异常，降级到 countPending: instanceId={} node={} err={}",
          instId,
          joinCode,
          e.getMessage());
      // P0-2: 降级路径增强 — 统计所有入边源节点的活跃任务数，
      // 避免某分支已终止（CANCELLED/FAILED）但 join 永久等待。
      // 只统计 PENDING/CLAIMED 状态任务，已终止分支不计入。
      int activeIncoming = countActiveIncomingTasks(definitionId, instId, joinCode);
      if (activeIncoming > 0) {
        log.info(
            "[Flow] 并行网关 join 等待（降级）: instanceId={} node={} activeIncoming={}",
            instId,
            joinCode,
            activeIncoming);
        return false;
      }
      // 无活跃入边任务，视为最后一个分支，允许通过
      return true;
    }
  }

  /**
   * GAP-P0-2: 退回多节点同退
   *
   * <p>退回多节点同退能力。当 skipType=REJECT 且 targetNodeCodes 非空时， 在所有指定节点同时创建待办任务，让多个前序节点重新审批。
   * 单节点退回（targetNodeCodes 为空或单元素）降级到原 advance 逻辑。
   *
   * @param currentInstance 当前流程实例
   * @param currentNodeCode 当前节点编码
   * @param skipType 跳转类型（REJECT 等）
   * @param targetNodeCodes 目标节点编码列表
   * @param variables 流程变量
   * @return 推进后的节点列表
   */
  @YdszDistributedLock(
      key = "'flow:instance:op:' + #{#currentInstance.id}",
      waitTime = 5,
      leaseTime = 60,
      message = "流程正在处理中，请稍后重试")
  public List<FlowNodeVO> advanceMulti(
      FlowInstanceVO currentInstance,
      String currentNodeCode,
      String skipType,
      List<String> targetNodeCodes,
      Map<String, Object> variables) {
    // 非 REJECT 或多节点列表为空：降级到单节点 advance
    if (!"REJECT".equalsIgnoreCase(skipType)
        || targetNodeCodes == null
        || targetNodeCodes.isEmpty()) {
      String single =
          (targetNodeCodes == null || targetNodeCodes.isEmpty()) ? null : targetNodeCodes.get(0);
      return advance(currentInstance, currentNodeCode, skipType, single, variables);
    }

    // 单元素：降级到单节点 advance（保持原有语义）
    if (targetNodeCodes.size() == 1) {
      return advance(currentInstance, currentNodeCode, skipType, targetNodeCodes.get(0), variables);
    }

    // GAP-P0-2: 多节点同退 — 校验所有目标节点存在，返回全部目标节点列表
    log.info(
        "[Flow] 退回多节点同退: instanceId={} currentNode={} targets={}",
        currentInstance.getId(),
        currentNodeCode,
        targetNodeCodes);
    List<FlowNodeVO> targets = new ArrayList<>(targetNodeCodes.size());
    for (String nodeCode : targetNodeCodes) {
      if (nodeCode == null || nodeCode.isBlank()) {
        continue;
      }
      FlowNodeVO target =
          flowDefinitionCacheService.getNodeByCode(currentInstance.getDefinitionId(), nodeCode);
      if (target == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.node.not.found")
            .params(nodeCode)
            .build();
      }
      // 避免重复
      if (targets.stream().noneMatch(t -> t.getNodeCode().equals(nodeCode))) {
        targets.add(target);
      }
    }
    if (targets.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.reject.target.not.found")
          .params(currentNodeCode)
          .build();
    }
    return targets;
  }

  public List<FlowSkipVO> resolvePassSkips(
      FlowInstanceVO instance, FlowNodeVO currentNode, Map<String, Object> variables) {
    // P1: 通过缓存获取当前节点的出发跳转，并在内存中按 skipType=PASS 过滤
    List<FlowSkipVO> all =
        flowDefinitionCacheService
            .getSkipsByNodeCode(instance.getDefinitionId(), currentNode.getNodeCode())
            .stream()
            .filter(s -> "PASS".equalsIgnoreCase(s.getSkipType()))
            .toList();
    if (all.isEmpty()) {
      return Collections.emptyList();
    }

    // P0-6: 排他网关互斥 — CONDITION 节点只取第一条匹配
    boolean isExclusive =
        currentNode.getNodeType() != null
            && currentNode.getNodeType() == FlowNodeType.CONDITION.getCode();
    // GAP-P0: 包容网关 — INCLUSIVE 节点取所有匹配，无匹配时取默认出边
    boolean isInclusive =
        currentNode.getNodeType() != null
            && currentNode.getNodeType() == FlowNodeType.INCLUSIVE.getCode();

    List<FlowSkipVO> matched = new ArrayList<>(all.size());
    for (FlowSkipVO skip : all) {
      String cond = skip.getSkipCondition();
      if (evaluateSkipCondition(cond, variables)) {
        matched.add(skip);
        if (isExclusive) {
          break; // 排他网关：只取第一条匹配
        }
        // 包容网关：不 break，继续收集所有匹配分支
      }
    }

    // P0-2: 排他/包容网关默认出边 — BPMN 2.0 规范：取 gateway.default 属性指向的
    // sequenceFlow，而非盲目取 all.get(0)，避免设计器边排序不确定导致走错分支。
    // 兜底链：default 属性 → 无条件出边 → 空列表（流程结束）
    if ((isExclusive || isInclusive) && matched.isEmpty()) {
      FlowSkipVO defaultSkip = resolveDefaultSkip(currentNode, all);
      if (defaultSkip != null) {
        log.info(
            "[Flow] {}无匹配条件，取 BPMN default 出边: node={} defaultFlowId={}",
            isExclusive ? "排他网关" : "包容网关",
            currentNode.getNodeCode(),
            defaultSkip.getSequenceFlowId());
        matched.add(defaultSkip);
      } else {
        // 未配置 default 属性时，取无条件的出边（BPMN 规范：default 边本身不能有 conditionExpression）
        FlowSkipVO fallback =
            all.stream()
                .filter(s -> s.getSkipCondition() == null || s.getSkipCondition().isBlank())
                .findFirst()
                .orElse(null);
        if (fallback != null) {
          log.info(
              "[Flow] {}无匹配条件且未配置 default，取无条件出边: node={}",
              isExclusive ? "排他网关" : "包容网关",
              currentNode.getNodeCode());
          matched.add(fallback);
        }
        // 若连无条件边也没有，matched 保持空，advance 返回空列表，流程结束
      }
    }

    return matched;
  }

  /**
   * 评估跳转条件表达式
   *
   * <p>评估优先级：
   *
   * <ol>
   *   <li>FlowRoutingService（引擎内置 Aviator 引擎实现）
   *   <li>DefaultFlowVariableStrategy（Aviator 引擎 + 正则降级兜底）
   * </ol>
   *
   * @param condition 跳转条件表达式
   * @param variables 流程变量
   * @return true=条件成立，false=不成立
   */
  public boolean evaluateSkipCondition(String condition, Map<String, Object> variables) {
    if (condition == null || condition.isBlank()) {
      return true;
    }

    // 优先使用内置 FlowRoutingService 评估
    if (routingService != null) {
      try {
        boolean result = routingService.evaluateCondition(condition, variables);
        log.debug("[Flow] 使用 FlowRoutingService 评估条件: expr={} -> {}", condition, result);
        return result;
      } catch (Exception e) {
        log.warn(
            "[Flow] FlowRoutingService 评估失败，回退到 variableStrategy: expr={} err={}",
            condition,
            e.getMessage());
      }
    }

    // 回退到变量策略（Aviator 引擎 + 正则降级）
    return variableStrategy.evaluate(condition, variables);
  }

  public String resolveRejectTarget(String definitionId, String currentNodeCode) {
    List<FlowSkipVO> incoming =
        flowDefinitionCacheService.getSkipsByNextNode(definitionId, currentNodeCode);
    if (!incoming.isEmpty()) {
      // P0-1 修复：使用 extractSourceNodeCode 获取入边 sourceRef（前驱节点编码），
      // 跳过脆弱的 lookupNodeCodeByName 名称匹配路径。
      // 设计器常将 sequenceFlow 的 name 属性留空，按名称查找极易失败或错配。
      String sourceNodeCode = incoming.get(0).getSourceRef();
      if (sourceNodeCode != null && !sourceNodeCode.isBlank()) {
        return sourceNodeCode;
      }
    }
    // 兜底：无前驱时退回到开始节点
    FlowNodeVO start = flowDefinitionCacheService.getStartNode(definitionId);
    return start == null ? null : start.getNodeCode();
  }

  // ============================== 私有 ==============================

  /**
   * 判断是否为 join 节点（并行/包容网关）
   *
   * @param node 节点实体
   * @return true=该节点是 join 类型网关
   */
  private boolean isJoinNode(FlowNodeVO node) {
    return node.getNodeType() != null
        && (node.getNodeType() == FlowNodeType.PARALLEL.getCode()
            || node.getNodeType() == FlowNodeType.INCLUSIVE.getCode());
  }

  /**
   * P0-2: 解析网关默认出边
   *
   * <p>BPMN 2.0 规范：exclusiveGateway / inclusiveGateway 的 {@code default} 属性 指向一条无条件的
   * sequenceFlow。当所有带条件的出边都不匹配时，走这条默认边。
   *
   * @param gatewayNode 网关节点（ext 中可能含 defaultFlowId）
   * @param allSkips 网关的所有 PASS 出边
   * @return 默认出边，未配置或未找到时返回 null
   */
  private FlowSkipVO resolveDefaultSkip(FlowNodeVO gatewayNode, List<FlowSkipVO> allSkips) {
    String defaultId = gatewayNode.getDefaultFlowId();
    if (defaultId == null) {
      return null;
    }
    for (FlowSkipVO skip : allSkips) {
      if (defaultId.equals(skip.getSequenceFlowId())) {
        return skip;
      }
    }
    return null;
  }


  /**
   * P0-2: 统计 join 节点的入边源节点中仍有活跃任务的数量（降级路径使用）
   *
   * <p>当 Redis 令牌服务异常时，通过查询任务表判断其他分支是否仍在处理中。 只统计 PENDING 状态的任务（已 CANCELLED/COMPLETED/FAILED 的不计入），
   * 避免某分支已终止但 join 永久等待。
   *
   * @param definitionId 流程定义 ID
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 仍有活跃任务的入边源节点数量
   */
  private int countActiveIncomingTasks(
      String definitionId, String instanceId, String joinNodeCode) {
    List<FlowSkipVO> incoming =
        flowDefinitionCacheService.getSkipsByNextNode(definitionId, joinNodeCode);
    if (incoming == null || incoming.isEmpty()) {
      return 0;
    }
    int active = 0;
    for (FlowSkipVO skip : incoming) {
      String sourceNodeCode = skip.getSourceRef();
      if (sourceNodeCode == null || sourceNodeCode.isBlank()) {
        continue;
      }
      int nodePending = taskRepository.findPendingByNode(instanceId, sourceNodeCode).size();
      active += nodePending;
    }
    return active;
  }

  /**
   * 判断节点是否有多个入边
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return true=该节点有多条入边
   */
  private boolean hasMultipleIncoming(String definitionId, String nodeCode) {
    List<FlowSkipVO> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, nodeCode);
    return incoming != null && incoming.size() > 1;
  }

  private Map<String, Object> parseVariable(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return YdszJson.parseMap(json);
    } catch (Exception e) {
      log.warn("[Flow] 变量解析失败: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }

  private List<FlowInstanceViewDTO.FlowTaskViewDTO> loadCurrentTasks(String instanceId) {
    return taskService.listPendingByInstance(instanceId).stream().map(task -> taskService.toView(task)).toList();
  }
}
