package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.engine.FlowEventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务事件通知服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分出的"事件触发"职责。
 * 集中处理：
 * <ul>
 *   <li>{@link #fireTaskCompleted} — 任务完成事件（含上下文重载）</li>
 *   <li>{@link #fireInstanceRejected} — 流程被驳回事件</li>
 * </ul>
 *
 * <p>事件分发委托给 {@link FlowTaskSupport}（其内部吞异常、遍历监听器）。
 * 本类只做事件语义封装，避免在主流程中嵌入事件发布样板代码。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskNotificationService {

    private final FlowTaskSupport support;

    /**
     * 任务完成事件（无 vars）
     */
    public void fireTaskCompleted(String taskId, String action) {
        fireTaskCompleted(taskId, action, null);
    }

    /**
     * 任务完成事件（含流程变量）
     *
     * <p>同时调用两版监听器：老版（taskId/action/vars）和 P2-37 引入的
     * 携带 {@link FlowEventContext} 的新版本，保证向后兼容。
     */
    public void fireTaskCompleted(String taskId, String action, Map<String, Object> vars) {
        support.fireEvent(l -> l.onTaskCompleted(taskId, action, vars), taskId);
        FlowEventContext ctx = new FlowEventContext();
        ctx.setTaskId(taskId);
        ctx.setAction(action);
        ctx.setOperatedAt(LocalDateTime.now());
        support.fireEvent(l -> l.onTaskCompleted(taskId, ctx), taskId);
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_COMPLETED", null, taskId);
    }

    /**
     * 流程被驳回事件
     */
    public void fireInstanceRejected(String instanceId, String reason) {
        support.fireEvent(l -> l.onInstanceRejected(instanceId, reason), null);
    }
}
