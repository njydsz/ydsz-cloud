package com.njydsz.workflow.engine.listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.engine.FlowEventContext;

/**
 * 全局监听器执行器。
 *
 * <p>负责发现 Spring 容器中所有 {@link GlobalFlowListener} 实现，按 {@link GlobalFlowListener#getOrder()} 排序后注册，
 * 在流程引擎关键生命周期事件触发时依次回调。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>全局监听器异常不会中断主流程，仅记录错误日志</li>
 *   <li>监听器按 order 升序执行，建议业务方使用 10 的倍数间隔取值</li>
 *   <li>所有方法均为空安全：监听器列表为空时直接返回</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see GlobalFlowListener 全局监听器接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalFlowListenerExecutor {

  /** Spring 注入的所有全局监听器 Bean */
  private final List<GlobalFlowListener> globalListeners;

  /** 排序后的监听器列表（不可变） */
  private List<GlobalFlowListener> sortedListeners = List.of();

  /**
   * 初始化：按 order 排序监听器列表。
   *
   * <p>排序后列表不可变，保证运行时线程安全。
   */
  @PostConstruct
  public void init() {
    List<GlobalFlowListener> list = new ArrayList<>(globalListeners);
    list.sort(Comparator.comparingInt(GlobalFlowListener::getOrder));
    this.sortedListeners = List.copyOf(list);
    log.info("[Flow] 全局监听器已注册: count={}", sortedListeners.size());
    for (GlobalFlowListener listener : sortedListeners) {
      log.info("[Flow]   - {} order={}", listener.getClass().getSimpleName(), listener.getOrder());
    }
  }

  /**
   * 触发任务创建事件。
   *
   * @param instanceId 流程实例 ID
   * @param taskId     任务 ID
   * @param nodeCode   节点编码
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  public void fireTaskCreated(String instanceId, String taskId, String nodeCode,
      Map<String, Object> variables, FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onTaskCreated(instanceId, taskId, nodeCode, variables, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onTaskCreated 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  /**
   * 触发任务完成事件。
   *
   * @param instanceId 流程实例 ID
   * @param taskId     任务 ID
   * @param nodeCode   节点编码
   * @param action     操作类型
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  public void fireTaskFinished(String instanceId, String taskId, String nodeCode,
      String action, Map<String, Object> variables, FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onTaskFinished(instanceId, taskId, nodeCode, action, variables, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onTaskFinished 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  /**
   * 触发实例启动事件。
   *
   * @param instanceId 流程实例 ID
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  public void fireInstanceStarted(String instanceId, Map<String, Object> variables,
      FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onInstanceStarted(instanceId, variables, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onInstanceStarted 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  /**
   * 触发实例完成事件。
   *
   * @param instanceId 流程实例 ID
   * @param ctx        事件上下文
   */
  public void fireInstanceFinished(String instanceId, FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onInstanceFinished(instanceId, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onInstanceFinished 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  /**
   * 触发实例拒绝事件。
   *
   * @param instanceId 流程实例 ID
   * @param reason     拒绝原因
   * @param ctx        事件上下文
   */
  public void fireInstanceRejected(String instanceId, String reason, FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onInstanceRejected(instanceId, reason, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onInstanceRejected 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  /**
   * 触发实例终止事件。
   *
   * @param instanceId 流程实例 ID
   * @param reason     终止原因
   * @param ctx        事件上下文
   */
  public void fireInstanceTerminated(String instanceId, String reason, FlowEventContext ctx) {
    for (GlobalFlowListener listener : sortedListeners) {
      try {
        listener.onInstanceTerminated(instanceId, reason, ctx);
      } catch (Exception e) {
        log.error("[Flow] 全局监听器 onInstanceTerminated 异常: listener={} err={}",
            listener.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }
}
