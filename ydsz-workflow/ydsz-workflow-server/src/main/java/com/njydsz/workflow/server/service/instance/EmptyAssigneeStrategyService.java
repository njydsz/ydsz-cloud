package com.njydsz.workflow.server.service.instance;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.workflow.domain.dto.FlowAssigneeDTO;
import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskSupport;

/**
 * 审批人为空兜底策略服务
 *
 * <p>从 {@link com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService} 中抽出的
 * 审批人为空兜底逻辑，承担运行时任务（{@link FlowRunTask}）的办理人为空时策略分发职责。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>策略分发</b>：按 ext 配置的 emptyStrategy 分发到对应策略
 *       {@code AUTO_PASS / TRANSFER_ADMIN / ASSIGN_SPECIFIED / FALLBACK}
 *   <li><b>自动通过</b>：AUTO_PASS 策略 - 标记任务为已完成并归档
 *   <li><b>转交管理员</b>：TRANSFER_ADMIN 策略 - 转交管理员处理
 *   <li><b>指定人员</b>：ASSIGN_SPECIFIED 策略 - 分配给指定人员
 *   <li><b>回退解析</b>：FALLBACK 策略 - 回退到原有 resolveAssignee 逻辑
 * </ul>
 *
 * <p><b>设计意图：</b>FlowTaskCreateService 原承担任务创建 + 办理人解析 + 委派改写 + 服务节点执行 +
 * 空办理人兜底等多重职责，本次拆分将空办理人兜底逻辑抽出为独立服务，使各职责边界更清晰。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService 任务创建服务（调用方）
 * @see AssigneeResolutionService 办理人解析服务
 */
@Slf4j
@Service
public class EmptyAssigneeStrategyService {

  /** P0-1: 审批人为空统一默认 FALLBACK（最保守：转交管理员人工处理） */
  private static final String DEFAULT_EMPTY_STRATEGY = "FALLBACK";

  /** 运行时任务仓储，创建/更新待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 任务归档服务，完成任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 办理人解析服务 */
  private final AssigneeResolutionService assigneeResolutionService;

  /**
   * 自动通过后推进到下一节点的回调
   *
   * <p>此回调由调用方（FlowTaskCreateService）提供，用于处理递归深度保护等逻辑。
   */
  private final Function<AdvanceContext, Void> advanceCallback;

  /**
   * 创建 EmptyAssigneeStrategyService 实例
   * 
   *
   * @param taskRepository 参数说明
   * @param archiveService 参数说明
   * @param support 参数说明
   * @param assigneeResolutionService 参数说明
   * @param advanceCallback 参数说明   */
  public EmptyAssigneeStrategyService(
      FlowRunTaskRepository taskRepository,
      FlowTaskArchiveService archiveService,
      FlowTaskSupport support,
      AssigneeResolutionService assigneeResolutionService,
      Function<AdvanceContext, Void> advanceCallback) {
    this.taskRepository = taskRepository;
    this.archiveService = archiveService;
    this.support = support;
    this.assigneeResolutionService = assigneeResolutionService;
    this.advanceCallback = advanceCallback;
  }

  /**
   * 审批人为空兜底处理
   *
   * <p>按 ext 配置的 emptyStrategy 分发到对应策略：
   *
   * <ul>
   *   <li>{@code AUTO_PASS} — 自动通过并推进到下一节点
   *   <li>{@code TRANSFER_ADMIN} — 转交管理员
   *   <li>{@code ASSIGN_SPECIFIED} — 指定人员
   *   <li>其它（{@code FALLBACK}）— 回退到 resolveAssignee 逻辑
   * </ul>
   *
   * @param task 待处理的运行时任务
   * @param instance 流程实例
   * @param node 当前流程节点
   * @param variables 流程变量
   * @return 任务 ID
   */
  public String handleEmptyAssignee(
      FlowRunTaskVO task, FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    String emptyStrategy = FlowNodeExt.getEmptyStrategy(node.getExt());

    return switch (emptyStrategy) {
      case "AUTO_PASS" -> handleAutoPass(task, instance, node, variables);
      case "TRANSFER_ADMIN" ->
          assignToFallbackUser(
              task,
              instance,
              node,
              FlowNodeExt.getAdminUserId(node.getExt()),
              "ADMIN_FALLBACK",
              "[Flow] 审批人为空转管理员: instanceId={} node={} adminId={}");
      case "ASSIGN_SPECIFIED" ->
          assignToFallbackUser(
              task,
              instance,
              node,
              FlowNodeExt.getSpecifiedUserId(node.getExt()),
              "SPECIFIED_FALLBACK",
              "[Flow] 审批人为空指定人员: instanceId={} node={} userId={}");
      default -> fallbackToResolveAssignee(task, instance, node, variables);
    };
  }

  /**
   * AUTO_PASS 策略：标记任务为已完成（自动通过），归档并推进到下一节点
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String handleAutoPass(
      FlowRunTaskVO task, FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId("0");
    task.setAssigneeName("SYSTEM_AUTO_PASS");
    task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
    LocalDateTime now = LocalDateTime.now();
    task.setFinishAt(now);
    task.setDurationMs(0L);
    FlowRunTaskDTO autoPassDto = toDto(task);
    FlowRunTaskVO saved = taskRepository.save(autoPassDto);
    archiveService.archiveToHistory(saved, FlowTaskStatus.COMPLETED);
    support.audit(saved, "AUTO_PASS", null, null, "审批人为空，自动通过");
    log.info("[Flow] 审批人为空自动通过: instanceId={} node={}", instance.getId(), node.getNodeCode());
    advanceAfterAutoPass(instance, node, variables);
    return saved.getId();
  }

  /**
   * 将任务分配给指定的回退用户（管理员或指定人员）
   * 
   *
   * @param logMsg 参数说明
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param userId 参数说明
   * @param fallbackName 参数说明
   * @return 返回值说明
   */
  private String assignToFallbackUser(
      FlowRunTaskVO task,
      FlowInstanceVO instance,
      FlowNodeVO node,
      String userId,
      String fallbackName,
      String logMsg) {
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId(userId);
    task.setAssigneeName(fallbackName);
    FlowRunTaskDTO fallbackDto = toDto(task);
    FlowRunTaskVO saved = taskRepository.save(fallbackDto);
    log.info(logMsg, instance.getId(), node.getNodeCode(), userId);
    return saved.getId();
  }

  /**
   * FALLBACK 策略：回退到原有 resolveAssignee 逻辑
   *
   * @param task 参数说明
   * @param instance 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private String fallbackToResolveAssignee(
      FlowRunTaskVO task, FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
    FlowRunTaskDTO resolveDto = toDto(task);
    FlowRunTaskVO saved = taskRepository.save(resolveDto);
    resolveAssignee(saved, node, variables, null, instance);
    taskRepository.update(saved);
    return saved.getId();
  }

  /**
   * 写入 ydsz_flow_user 记录
   *
   * @param task 参数说明
   * @param node 参数说明
   * @param variables 参数说明
   * @param explicit 参数说明
   * @param instance 参数说明
   */
  private void resolveAssignee(
      FlowRunTaskVO task,
      FlowNodeVO node,
      Map<String, Object> variables,
      FlowAssigneeDTO explicit,
      FlowInstanceVO instance) {
    assigneeResolutionService.resolveAssignee(task, node, variables, explicit, instance);
  }

  /**
   * 将运行时任务 VO 显式映射为 DTO（写入入参）。
   *
   * <p>本方法替代 {@code BeanUtils.copyProperties()}，提供编译期类型安全的字段映射。
   * 仅在这几个兜底策略方法内部使用，避免反射拷贝带来的字段不同步风险。
   *
   * @param vo 源 VO（由调用方组装完成）
   * @return 同名字段复制后的 DTO
   */
  private FlowRunTaskDTO toDto(FlowRunTaskVO vo) {
    FlowRunTaskDTO dto = new FlowRunTaskDTO();
    dto.setId(vo.getId());
    dto.setInstanceId(vo.getInstanceId());
    dto.setFlowCode(vo.getFlowCode());
    dto.setDefinitionId(vo.getDefinitionId());
    dto.setNodeCode(vo.getNodeCode());
    dto.setNodeName(vo.getNodeName());
    dto.setNodeType(vo.getNodeType());
    dto.setBusinessType(vo.getBusinessType());
    dto.setBusinessId(vo.getBusinessId());
    dto.setBusinessNo(vo.getBusinessNo());
    dto.setFlowName(vo.getFlowName());
    dto.setTitle(vo.getTitle());
    dto.setAssignorId(vo.getAssignorId());
    dto.setAssignorName(vo.getAssignorName());
    dto.setAssigneeType(vo.getAssigneeType());
    dto.setAssigneeId(vo.getAssigneeId());
    dto.setAssigneeName(vo.getAssigneeName());
    dto.setPermissionFlag(vo.getPermissionFlag());
    dto.setPerformType(vo.getPerformType());
    dto.setApproveCount(vo.getApproveCount());
    dto.setApproveFinished(vo.getApproveFinished());
    dto.setVotePassRate(vo.getVotePassRate());
    dto.setTaskStatus(vo.getTaskStatus());
    dto.setComment(vo.getComment());
    dto.setClaimAt(vo.getClaimAt());
    dto.setFinishAt(vo.getFinishAt());
    dto.setEffectiveTime(vo.getEffectiveTime());
    dto.setCompletedAt(vo.getCompletedAt());
    dto.setDurationMs(vo.getDurationMs());
    dto.setDueAt(vo.getDueAt());
    dto.setPriority(vo.getPriority());
    dto.setUrgeCount(vo.getUrgeCount());
    dto.setLastUrgedAt(vo.getLastUrgedAt());
    dto.setSlaAction(vo.getSlaAction());
    dto.setSlaEscalated(vo.getSlaEscalated());
    dto.setRevision(vo.getRevision());
    dto.setUserWeight(vo.getUserWeight());
    dto.setApproveWeight(vo.getApproveWeight());
    dto.setTotalWeight(vo.getTotalWeight());
    dto.setIterVar(vo.getIterVar());
    dto.setTenantId(vo.getTenantId());
    dto.setProviderTraceId(vo.getProviderTraceId());
    dto.setDeleted(vo.getDeleted());
    dto.setCreatedAt(vo.getCreatedAt());
    return dto;
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
    if (advanceCallback != null) {
      advanceCallback.apply(new AdvanceContext(instance, node, variables));
    }
  }

  /**
   * 自动通过推进上下文
   *
   * <p>封装自动通过后的推进参数，传递给调用方的 advanceCallback。
   */
  public static class AdvanceContext {
    private final FlowInstanceVO instance;
    private final FlowNodeVO node;
    private final Map<String, Object> variables;

    public AdvanceContext(FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables) {
      this.instance = instance;
      this.node = node;
      this.variables = variables;
    }

    public FlowInstanceVO getInstance() {
      return instance;
    }

    public FlowNodeVO getNode() {
      return node;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }
  }
}
