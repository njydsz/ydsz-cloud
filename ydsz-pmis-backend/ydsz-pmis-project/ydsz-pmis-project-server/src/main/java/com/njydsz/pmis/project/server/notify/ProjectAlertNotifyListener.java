package com.njydsz.pmis.project.server.notify;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.notify.core.AsyncNotifyService;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.event.UnifiedAlertEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目模块统一告警通知监听器
 *
 * <p>监听 {@link UnifiedAlertEvent}，通过 common-notify 的 {@link AsyncNotifyService}
 * 直接发送多渠道通知，作为 message 模块 Feign 调用的补充路径。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>RED 级告警：站内信 + 邮件双渠道</li>
 *   <li>YELLOW 级告警：仅站内信</li>
 *   <li>使用 {@link AsyncNotifyService#sendAsync} 异步发送，不阻塞业务主流程</li>
 *   <li>与 AlertDispatchServiceImpl 的事件总线互补：Feign 调用负责业务消息发送，
 *       本监听器负责本地渠道直发（如邮件、IM）</li>
 * </ul>
 *
 * <p><b>与 AlertDispatchServiceImpl 的关系：</b>
 * <p>AlertDispatchServiceImpl 通过 {@code ApplicationEventPublisher} 发布 {@link UnifiedAlertEvent}，
 * 由 UnifiedAlertDispatcher 消费并通过 Feign 调用 message 模块分发。
 * 本监听器同时消费同一事件，通过 common-notify 的本地渠道策略直接发送，
 * 确保关键告警在 message 模块不可用时仍能通过备用渠道触达。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectAlertNotifyListener {

    private final AsyncNotifyService asyncNotifyService;

    /**
     * 异步监听统一告警事件并发送多渠道通知
     *
     * @param event 统一告警事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onUnifiedAlert(UnifiedAlertEvent event) {
        if (event == null) {
            return;
        }
        try {
            String level = event.getAlertLevel() == null ? "YELLOW" : event.getAlertLevel();
            String title = event.getTitle() == null ? "项目告警" : event.getTitle();
            String content = event.getContent() == null ? "" : event.getContent();

            // RED 级告警：站内信 + 邮件
            if ("RED".equalsIgnoreCase(level)) {
                sendToTargetUsers(event, title, content, NotifyChannel.INSITE);
                sendToTargetUsers(event, title, content, NotifyChannel.EMAIL);
            } else {
                // YELLOW/NORMAL 级告警：仅站内信
                sendToTargetUsers(event, title, content, NotifyChannel.INSITE);
            }

            log.debug("[ProjectNotify] 告警通知已发送: code={} level={} channels={}",
                    event.getAlertCode(), level,
                    "RED".equalsIgnoreCase(level) ? "INSITE,EMAIL" : "INSITE");
        } catch (Exception e) {
            log.warn("[ProjectNotify] 告警通知发送失败: code={} err={}",
                    event.getAlertCode(), e.getMessage());
        }
    }

    /**
     * 向目标用户发送通知
     *
     * <p>优先使用 targetUserIds，若为空则跳过（由 UnifiedAlertDispatcher 通过角色解析处理）。
     *
     * @param event    告警事件
     * @param title    通知标题
     * @param content  通知内容
     * @param channel  通知渠道
     */
    private void sendToTargetUsers(UnifiedAlertEvent event, String title,
                                    String content, NotifyChannel channel) {
        String targetUserIds = event.getTargetUserIds();
        if (targetUserIds == null || targetUserIds.isBlank()) {
            return;
        }
        String[] userIds = targetUserIds.split(",");
        for (String userId : userIds) {
            String trimmed = userId.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            asyncNotifyService.sendAsync(channel, trimmed, title, content)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[ProjectNotify] 通知发送失败: user={} channel={} err={}",
                                    trimmed, channel.getName(), ex.getMessage());
                        } else if (!result.isSuccess()) {
                            log.warn("[ProjectNotify] 通知发送失败: user={} channel={} error={}",
                                    trimmed, channel.getName(), result.getErrorMessage());
                        }
                    });
        }
    }
}
