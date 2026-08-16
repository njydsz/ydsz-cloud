package com.njydsz.workflow.server.service.impl.instance;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.workflow.server.engine.FlowEventContext;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

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

  /** 任务完成事件（无 vars） */
  public void fireTaskCompleted(String taskId, String action) {
    fireTaskCompleted(taskId, action, null);
  }

  /**
   * 任务完成事件（含流程变量）
   *
   * <p>同时调用两版监听器：老版（taskId/action/vars）和 P2-37 引入的 携带 {@link FlowEventContext} 的新版本，保证向后兼容。
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
      if (mdcTraceId == null) mdcTraceId = MDC.get("tid");
    }
    ctx.setTraceId(mdcTraceId);
    support.fireEvent(l -> l.onTaskCompleted(taskId, ctx), taskId);
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_COMPLETED", null, taskId);
  }

  /** 流程被驳回事件 */
  public void fireInstanceRejected(String instanceId, String reason) {
    support.fireEvent(l -> l.onInstanceRejected(instanceId, reason), null);
  }
}
