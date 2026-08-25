package com.njydsz.workflow.server.service.instance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowDelegateAuth;
import com.njydsz.workflow.infra.entity.FlowNode;
import com.njydsz.workflow.infra.entity.FlowRunTask;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;

/**
 * 长期授权委派改写服务
 *
 * <p>从 {@link com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService} 中抽出的委派改写逻辑，
 * 承担运行时任务（{@link FlowRunTask}）的长期授权委派改写职责。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>委派改写</b>：根据长期授权规则改写任务办理人，支持链式解析 A→B→C 委派链路
 *   <li><b>审计记录</b>：委派改写后自动记录审计日志
 * </ul>
 *
 * <p><b>设计意图：</b>FlowTaskCreateService 原承担任务创建 + 办理人解析 + 委派改写 + 服务节点执行等多重职责，
 * 本次拆分将委派改写逻辑抽出为独立服务，使各职责边界更清晰。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService 任务创建服务（调用方）
 * @see FlowDelegateAuthService 委派授权服务
 */
@Slf4j
@Service
public class DelegateRedirectService {

  /** 委派授权服务，查询长期授权委派改写审批人 */
  private final FlowDelegateAuthService delegateAuthService;

  /** 运行时任务仓储，更新委派改写后的任务 */
  private final FlowRunTaskRepository taskRepository;

  /** MapStruct 转换器（DO/VO/DTO 转换） */
  private final WorkflowConverter converter;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  public DelegateRedirectService(
      FlowDelegateAuthService delegateAuthService,
      FlowRunTaskRepository taskRepository,
      WorkflowConverter converter,
      FlowTaskSupport support) {
    this.delegateAuthService = delegateAuthService;
    this.taskRepository = taskRepository;
    this.converter = converter;
    this.support = support;
  }

  /**
   * 应用长期授权委派改写（支持链式解析）
   *
   * <p>递归解析 A→B→C 链式委派，最终将任务分配给链路末端的代理人。
   * 无委派规则或最终代理人就是原办理人时不做任何操作。
   *
   * @param task 待改写的运行时任务（直接修改其 assignee 字段）
   * @param instance 流程实例（用于获取租户 ID、流程编码等）
   * @param node 当前流程节点（用于获取节点编码匹配委派规则）
   */
  public void applyDelegateRedirect(
      FlowRunTask task, FlowInstanceVO instance, FlowNode node) {
    try {
      if (delegateAuthService == null) {
        return;
      }
      String currentAssigneeId = task.getAssigneeId();
      if (!StringUtils.hasText(currentAssigneeId)) {
        return;
      }
      String currentUserId = currentAssigneeId.trim();
      // 链式解析最终代理人
      String finalDelegateId =
          delegateAuthService.resolveDelegateChain(
              instance.getTenantId(), currentUserId, instance.getFlowCode(), node.getNodeCode());
      if (finalDelegateId == null || finalDelegateId.equals(currentUserId)) {
        // 无委派规则，或最终代理人就是原办理人
        return;
      }
      // 仍需匹配首条授权规则用于审计记录
      FlowDelegateAuth matched =
          delegateAuthService.matchAuth(
              instance.getTenantId(), currentUserId, instance.getFlowCode(), node.getNodeCode());
      task.setAssignorId(currentUserId);
      task.setAssignorName(matched != null ? matched.getOwnerUserName() : null);
      task.setAssigneeId(finalDelegateId);
      // 最终代理人姓名：优先从链路末端匹配记录获取
      task.setAssigneeName(matched != null ? matched.getDelegateUserName() : finalDelegateId);
      taskRepository.update(converter.entityToVO(task));
      String authId = matched != null ? matched.getId() : "CHAIN_RESOLVED";
      String scopeType = matched != null ? matched.getScopeType() : "CHAIN";
      support.audit(
          task,
          "DELEGATE_AUTH_APPLIED",
          finalDelegateId,
          currentUserId,
          "长期授权委派生效(链式): " + authId + " (" + scopeType + ") → " + finalDelegateId);
      log.info(
          "[Flow] 长期授权委派改写(链式): taskId={} owner={} → finalDelegate={} authId={} scope={}",
          task.getId(),
          currentUserId,
          finalDelegateId,
          authId,
          scopeType);
    } catch (Exception e) {
      log.error(
          "[Flow] 长期授权委派改写异常: taskId={} err={}",
          task == null ? "null" : task.getId(),
          e.getMessage(),
          e);
    }
  }
}
