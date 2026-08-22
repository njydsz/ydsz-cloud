package com.njydsz.workflow.server.service.instance;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.server.engine.FlowServiceNodeExecutor;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

/**
 * 服务节点执行服务
 *
 * <p>从 {@link com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService} 中抽出的
 * 服务节点执行逻辑，承担运行时任务（{@link FlowRunTaskDO}）的 HTTP/SCRIPT/AUTO_PASS 自动执行职责。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>服务节点执行</b>：HTTP / SCRIPT / AUTO_PASS 自动执行，无需人工介入
 *   <li><b>错误边界触发</b>：服务节点失败时优先触发 error boundary 事件
 *   <li><b>审计追溯</b>：创建 COMPLETED/TIMEOUT 任务记录用于审计追溯
 * </ul>
 *
 * <p><b>设计意图：</b>FlowTaskCreateService 原承担任务创建 + 办理人解析 + 委派改写 + 服务节点执行 +
 * 空办理人兜底等多重职责，本次拆分将服务节点执行逻辑抽出为独立服务，使各职责边界更清晰。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService 任务创建服务（调用方）
 * @see FlowServiceNodeExecutor 服务节点执行器
 * @see FlowEventSubscriptionService 事件订阅服务
 */
@Slf4j
@Service
public class ServiceNodeExecuteService {

  /** 服务节点执行器（HTTP/SCRIPT/AUTO_PASS） */
  private final FlowServiceNodeExecutor serviceNodeExecutor;

  /** 运行时任务仓储，创建/更新待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** MapStruct 转换器（DO/VO/DTO 转换） */
  private final WorkflowConverter converter;

  /** 任务归档服务，完成任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 事件订阅服务（服务节点失败时触发 error boundary） */
  private final FlowEventSubscriptionService eventSubscriptionService;

  /** 流程节点仓储，查询节点配置 */
  private final FlowNodeRepository nodeRepository;

  /** 流程实例仓储，更新实例状态 */
  private final FlowInstanceRepository instanceRepository;

  /**
   * 执行成功后推进到下一节点的回调
   *
   * <p>此回调由调用方（FlowTaskCreateService）提供，用于处理递归深度保护等逻辑。
   */
  private final Function<ServiceNodeExecuteService.AdvanceContext, Void> advanceCallback;

  /**
   * 创建 ServiceNodeExecuteService 实例
   *
   * @param serviceNodeExecutor 服务节点执行器
   * @param taskRepository 运行时任务仓储
   * @param converter MapStruct 转换器
   * @param archiveService 任务归档服务
   * @param support 跨子 Service 共享的任务校验/审计/事件辅助
   * @param eventSubscriptionService 事件订阅服务
   * @param nodeRepository 流程节点仓储
   * @param instanceRepository 流程实例仓储
   * @param advanceCallback 执行成功后推进到下一节点的回调
   */
  public ServiceNodeExecuteService(
      FlowServiceNodeExecutor serviceNodeExecutor,
      FlowRunTaskRepository taskRepository,
      WorkflowConverter converter,
      FlowTaskArchiveService archiveService,
      FlowTaskSupport support,
      FlowEventSubscriptionService eventSubscriptionService,
      FlowNodeRepository nodeRepository,
      FlowInstanceRepository instanceRepository,
      Function<ServiceNodeExecuteService.AdvanceContext, Void> advanceCallback) {
    this.serviceNodeExecutor = serviceNodeExecutor;
    this.taskRepository = taskRepository;
    this.converter = converter;
    this.archiveService = archiveService;
    this.support = support;
    this.eventSubscriptionService = eventSubscriptionService;
    this.nodeRepository = nodeRepository;
    this.instanceRepository = instanceRepository;
    this.advanceCallback = advanceCallback;
  }

  /**
   * 执行 SERVICE 服务节点（HTTP/SCRIPT/AUTO_PASS 自动执行）
   *
   * <p>创建 COMPLETED/TIMEOUT 任务记录（仅用于审计追溯），归档，审计。
   * 成功时推进到下一节点；失败时优先触发 error boundary 事件，否则标记实例异常。
   *
   * @param instance 流程实例
   * @param node 当前流程节点
   * @param variables 流程变量
   * @return 任务 ID
   */
  public String executeServiceNode(
      FlowInstanceVO instance, FlowNodeDO node, Map<String, Object> variables) {
    // 1. 执行服务节点逻辑
    FlowServiceNodeExecutor.ServiceExecutionResult result;
    try {
      result = serviceNodeExecutor.execute(node, variables);
    } catch (Exception e) {
      log.error(
          "[Flow] 服务节点执行异常: instanceId={} node={} err={}",
          instance.getId(),
          node.getNodeCode(),
          e.getMessage(),
          e);
      result =
          new FlowServiceNodeExecutor.ServiceExecutionResult(
              false, "服务节点执行异常: " + e.getMessage());
    }

    // 2. 创建任务记录（用于审计追溯）
    FlowRunTaskDO task = new FlowRunTaskDO();
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
    task.setPerformType(FlowPerformType.OR.name());
    task.setApproveCount(1);
    task.setApproveFinished(1);
    task.setAssigneeType(com.njydsz.workflow.domain.enums.FlowAssigneeType.USER.name());
    task.setAssigneeId("0");
    task.setAssigneeName("SYSTEM_SERVICE");
    task.setTenantId(instance.getTenantId());
    task.setProviderTraceId(instance.getProviderTraceId());
    LocalDateTime now = LocalDateTime.now();
    task.setFinishAt(now);
    task.setDurationMs(0L);

    if (result.success()) {
      // 3a. 成功：标记 COMPLETED，归档，审计，推进
      task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
      task.setComment(result.message());
      taskRepository.save(converter.entityToVO(task));
      archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
      support.audit(task, "SERVICE_EXECUTE", null, null, "服务节点执行成功: " + result.message());
      log.info(
          "[Flow] 服务节点执行成功: instanceId={} node={} msg={}",
          instance.getId(),
          node.getNodeCode(),
          result.message());
      advanceAfterSuccess(instance, node, variables);
    } else {
      // 3b. 失败：优先尝试触发 error boundary 接管流程
      boolean errorBoundaryTriggered =
          triggerErrorBoundaryIfExists(instance, node, result.message());
      task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
      if (errorBoundaryTriggered) {
        task.setComment("服务节点失败，error boundary 已触发: " + result.message());
      } else {
        task.setComment("服务节点执行失败: " + result.message());
      }
      taskRepository.save(converter.entityToVO(task));
      archiveService.archiveToHistory(task, FlowTaskStatus.TIMEOUT);
      if (errorBoundaryTriggered) {
        support.audit(
            task,
            "SERVICE_ERROR_BOUNDARY",
            null,
            null,
            "服务节点失败，error boundary 触发: " + result.message());
        log.info(
            "[Flow] 服务节点失败，error boundary 已触发: instanceId={} node={}",
            instance.getId(),
            node.getNodeCode());
      } else {
        support.audit(task, "SERVICE_ERROR", null, null, "服务节点执行失败: " + result.message());
        instanceRepository.updateStatus(
            instance.getId(),
            FlowInstanceStatus.ERROR.name(),
            node.getNodeCode(),
            node.getNodeName(),
            null,
            null);
        log.error(
            "[Flow] 服务节点执行失败，实例标记为异常: instanceId={} node={} msg={}",
            instance.getId(),
            node.getNodeCode(),
            result.message());
      }
    }
    return task.getId();
  }

  /**
   * 触发附着在 serviceNode 上的 error boundary 事件
   *
   * <p>当服务节点执行失败时，查找附着在该节点上的 error boundary 事件并触发。
   *
   * @param instance 流程实例
   * @param serviceNode 服务节点
   * @param errorMsg 错误消息
   * @return 是否成功触发 error boundary
   */
  private boolean triggerErrorBoundaryIfExists(
      FlowInstanceVO instance, FlowNodeDO serviceNode, String errorMsg) {
    if (eventSubscriptionService == null) {
      return false;
    }
    try {
      List<FlowNodeDO> allNodes =
          nodeRepository.findByDefinitionId(instance.getDefinitionId()).stream()
              .map(converter::entityToDO)
              .toList();
      if (allNodes == null || allNodes.isEmpty()) {
        return false;
      }
      List<FlowNodeDO> errorBoundaries =
          allNodes.stream()
              .filter(
                  n -> {
                    if (!eventSubscriptionService.isEventCatchNode(n)) {
                      return false;
                    }
                    String attachedTo = FlowNodeExt.getAttachedToRef(n.getExt());
                    String eventType = FlowNodeExt.getEventType(n.getExt());
                    return serviceNode.getNodeCode().equals(attachedTo)
                        && "ERROR".equalsIgnoreCase(eventType);
                  })
              .toList();
      if (errorBoundaries.isEmpty()) {
        return false;
      }
      for (FlowNodeDO boundary : errorBoundaries) {
        String errorRef = FlowNodeExt.getErrorRef(boundary.getExt());
        eventSubscriptionService.throwError(
            instance.getTenantId(), instance.getId(), errorRef, errorMsg);
        log.info(
            "[Flow] error boundary 触发: instanceId={} serviceNode={} boundary={} errorRef={}",
            instance.getId(),
            serviceNode.getNodeCode(),
            boundary.getNodeCode(),
            errorRef);
      }
      return true;
    } catch (Exception e) {
      log.warn(
          "[Flow] 触发 error boundary 失败，降级到标记实例异常: instanceId={} node={} err={}",
          instance.getId(),
          serviceNode.getNodeCode(),
          e.getMessage());
      return false;
    }
  }

  /** 执行成功后推进到下一节点（含递归深度保护） */
  private void advanceAfterSuccess(
      FlowInstanceVO instance, FlowNodeDO node, Map<String, Object> variables) {
    if (advanceCallback != null) {
      advanceCallback.apply(new AdvanceContext(instance, node, variables));
    }
  }

  /**
   * *执行成功推进上下文
   *
   * <p>封装执行成功后的推进参数，传递给调用方的 advanceCallback。
   */
  public static class AdvanceContext {
    private final FlowInstanceVO instance;
    private final FlowNodeDO node;
    private final Map<String, Object> variables;

    public AdvanceContext(FlowInstanceVO instance, FlowNodeDO node, Map<String, Object> variables) {
      this.instance = instance;
      this.node = node;
      this.variables = variables;
    }

    public FlowInstanceVO getInstance() {
      return instance;
    }

    public FlowNodeDO getNode() {
      return node;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }
  }
}
