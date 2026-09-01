package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowEventContext;
import com.njydsz.workflow.server.engine.listener.FlowListenerEventType;

/**
 * 流程任务通知服务实现。
 *
 * <p>向当前审批人发送任务到达通知（站内信、IM、邮件、短信），
 *
 * <p>支持免打扰时段、用户偏好、租户级模板覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskNotificationService {

  private final FlowTaskSupport support;

  /**
   * 任务完成事件（无 vars）
   *
   * @param taskId 任务 ID
   * @param action 完成动作（PASS/REJECT 等）
   */
  public void fireTaskCompleted(String taskId, String action) {
    fireTaskCompleted(taskId, action, null);
  }

  /**
   * 任务完成事件（含流程变量）
   * 
   * <p>同时调用两版监听器：老版（taskId/action/vars）和 P2-37 引入的 携带 {@link FlowEventContext} 的新版本，保证向后兼容。
   *
   * @param taskId 任务 ID
   * @param action 完成动作（PASS/REJECT 等）
   * @param vars 流程变量上下文
   */
  public void fireTaskCompleted(String taskId, String action, Map<String, Object> vars) {
    support.fireEvent(l -> l.onTaskCompleted(taskId, action, vars), taskId);
    FlowEventContext ctx = new FlowEventContext();
    ctx.setTaskId(taskId);
    ctx.setAction(action);
    ctx.setOperatedAt(LocalDateTime.now());
    // P1-5: 优先从 RequestContext 获取分布式追踪 ID，回退 MDC（兼容 SkyWalking/Zipkin/Sleuth）
    String mdcTraceId = RequestContext.getTraceId();
    if (mdcTraceId == null || mdcTraceId.isBlank()) {
      mdcTraceId = MDC.get("traceId");
      if (mdcTraceId == null) {
        mdcTraceId = MDC.get("tid");
      }
    }
    ctx.setTraceId(mdcTraceId);
    support.fireEvent(l -> l.onTaskCompleted(taskId, ctx), taskId);
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_COMPLETED", null, taskId);
  }

  /**
   * 会签个人完成事件（全局 SPI + 节点配置监听器）
   *
   * <p>会签场景下，某个办理人通过审批但会签尚未全部完成时触发。 业务方通过此事件可实时跟踪会签进度（如"3/5 人已通过"）。
   *
   * <p>同时触发两套监听器：全局 {@link com.njydsz.workflow.server.engine.FlowEventListener} SPI 和 节点
   * ext JSON 中配置的 {@link com.njydsz.workflow.server.engine.listener.FlowListenerPlugin} 插件。
   *
   * @param task             运行时任务实体（用于获取 instanceId / nodeCode / tenantId 等）
   * @param personalUserId   当前办理人用户 ID
   * @param action           操作类型（PASS / REJECT）
   * @param approveFinished  当前已通过人数（含本次）
   * @param approveCount      会签总人数
   * @param nodeExt          节点 ext JSON 字符串（用于触发节点配置的监听器插件）
   * @param variables        流程变量
   */
  public void fireTaskPersonalCompleted(
      FlowRunTaskVO task,
      String personalUserId,
      String action,
      int approveFinished,
      int approveCount,
      String nodeExt,
      Map<String, Object> variables) {
    // 构建事件上下文
    FlowEventContext ctx = new FlowEventContext();
    ctx.setInstanceId(task.getInstanceId());
    ctx.setTaskId(task.getId());
    ctx.setNodeCode(task.getNodeCode());
    ctx.setOperatorId(personalUserId);
    ctx.setAction(action);
    ctx.setTenantId(task.getTenantId());
    ctx.setOperatedAt(LocalDateTime.now());
    ctx.setApproveFinished(approveFinished);
    ctx.setApproveCount(approveCount);

    // P1-5: 从 RequestContext / MDC 获取 traceId
    String traceId = RequestContext.getTraceId();
    if (traceId == null || traceId.isBlank()) {
      traceId = MDC.get("traceId");
      if (traceId == null) {
        traceId = MDC.get("tid");
      }
    }
    ctx.setTraceId(traceId);

    // 1. 全局 SPI 监听器
    support.fireEvent(l -> l.onTaskPersonalCompleted(task.getId(), ctx), task.getId());

    // 2. 节点配置的监听器插件
    support.firePluginEvent(
        nodeExt,
        FlowListenerEventType.TASK_PERSONAL_FINISHED,
        task.getInstanceId(),
        task.getId(),
        task.getNodeCode(),
        variables,
        ctx);

    // 3. Spring 异步事件
    support.publishWorkflowEvent("TASK_PERSONAL_COMPLETED", task.getInstanceId(), task.getId());
  }

  /**
   * 流程被驳回事件
   *
   * @param instanceId 参数说明
   * @param reason 参数说明
   */
  public void fireInstanceRejected(String instanceId, String reason) {
    support.fireEvent(l -> l.onInstanceRejected(instanceId, reason), null);
  }
}
