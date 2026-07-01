package com.njydsz.pmis.workflow.flow.listener;

import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目立项流程事件监听器（业务侧示例）
 *
 * <p>对接 PMIS 立项审批：在关键生命周期埋点。
 * 生产环境应通过 @Async + ApplicationEventPublisher 解耦。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("projectInitiationFlowListener")
public class ProjectInitiationFlowListener implements FlowEventListener {

    @Override
    public void onInstanceStart(Long instanceId, Map<String, Object> variables) {
        log.info("[FlowListener] 立项流程启动: instanceId={} vars={}", instanceId,
                variables == null ? java.util.Collections.emptySet() : variables.keySet());
        // TODO: 调用 initiationService.markProcessing(instanceId)
    }

    @Override
    public void onTaskCreated(Long taskId) {
        log.info("[FlowListener] 立项任务创建: taskId={}", taskId);
        // TODO: 推送消息给当前办理人
    }

    @Override
    public void onTaskCompleted(Long taskId, String action, Map<String, Object> variables) {
        log.info("[FlowListener] 立项任务完成: taskId={} action={}", taskId, action);
        // TODO:
        //   action=PASS  → 记录审批轨迹到 pmis_audit_log
        //   action=REJECT → 通知发起人（站内信 / 邮件）
    }

    @Override
    public void onInstanceCompleted(Long instanceId) {
        log.info("[FlowListener] 立项流程完成: instanceId={}", instanceId);
        // TODO: initiationService.markApproved(instanceId)
        //        + wbsService.bootstrapFromInitiation(instanceId)
    }

    @Override
    public void onInstanceRejected(Long instanceId, String reason) {
        log.info("[FlowListener] 立项流程驳回: instanceId={} reason={}", instanceId, reason);
        // TODO: initiationService.markRejected(instanceId, reason)
    }

    @Override
    public void onError(Long instanceId, Throwable t) {
        log.error("[FlowListener] 立项流程异常: instanceId={}", instanceId, t);
        // TODO: 告警 + 重试
    }
}
