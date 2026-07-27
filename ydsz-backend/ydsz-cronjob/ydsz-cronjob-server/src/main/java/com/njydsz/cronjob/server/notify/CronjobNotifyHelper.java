package com.njydsz.cronjob.server.notify;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.notify.core.AsyncNotifyService;
import com.njydsz.common.notify.enums.NotifyChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务模块通知助手
 *
 * <p>基于 common-notify 的 {@link AsyncNotifyService} 实现任务相关通知的本地直发，
 * 作为现有 Feign 调用（NotificationClient）的补充路径。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>当需要绕过 message 模块直接发送通知时使用（如运维告警、系统级通知）</li>
 *   <li>支持企业微信/钉钉/飞书等 IM 渠道的直接推送</li>
 *   <li>使用 {@link AsyncNotifyService#sendAsync} 异步发送，不阻塞任务执行主流程</li>
 *   <li>发送失败自动重试，重试失败进入持久化重试队列</li>
 * </ul>
 *
 * <p><b>与 AlertDispatcher 的关系：</b>
 * <p>{@code AlertDispatcher} 通过 Feign 调用 message 模块发送告警（适合业务告警），
 * 本助手通过 common-notify 直接发送（适合系统级通知和 IM 渠道直推）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronjobNotifyHelper {

    private final AsyncNotifyService asyncNotifyService;

    /**
     * 发送任务失败告警到 IM 渠道
     *
     * <p>当任务执行失败且需要即时通知运维人员时，直接通过企业微信/钉钉推送。
     *
     * @param webhookUrl IM 机器人 Webhook URL（作为 receiver）
     * @param jobKey     任务 KEY
     * @param jobName    任务名称
     * @param errorMsg   错误信息
     * @param channel    通知渠道（WECOM/DINGTALK/FEISHU）
     */
    public void notifyJobFailure(String webhookUrl, String jobKey, String jobName,
                                  String errorMsg, NotifyChannel channel) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String title = "任务执行失败告警";
        String content = String.format("任务: %s (%s)\n错误: %s\n时间: %s\n请及时处理",
                jobName, jobKey, errorMsg, LocalDateTime.now());
        try {
            asyncNotifyService.sendAsync(channel, webhookUrl, title, content)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[CronjobNotify] 任务失败告警发送失败: jobKey={} err={}",
                                    jobKey, ex.getMessage());
                        } else if (!result.isSuccess()) {
                            log.warn("[CronjobNotify] 任务失败告警发送失败: jobKey={} error={}",
                                    jobKey, result.getErrorMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("[CronjobNotify] 任务失败告警发送异常: jobKey={} err={}",
                    jobKey, e.getMessage());
        }
    }

    /**
     * 发送系统级通知到指定接收人
     *
     * @param receiverIds 接收人 ID 列表
     * @param title       通知标题
     * @param content     通知内容
     */
    public void notifySystemAlert(List<String> receiverIds, String title, String content) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        for (String receiverId : receiverIds) {
            try {
                asyncNotifyService.sendAsync(NotifyChannel.INSITE, receiverId, title, content)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("[CronjobNotify] 系统通知发送失败: receiver={} err={}",
                                        receiverId, ex.getMessage());
                            } else if (!result.isSuccess()) {
                                log.warn("[CronjobNotify] 系统通知发送失败: receiver={} error={}",
                                        receiverId, result.getErrorMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("[CronjobNotify] 系统通知发送异常: receiver={} err={}",
                        receiverId, e.getMessage());
            }
        }
    }

    /**
     * 发送任务超时告警
     *
     * @param receiverIds 接收人 ID 列表
     * @param jobKey      任务 KEY
     * @param jobName     任务名称
     * @param timeoutSec  超时秒数
     */
    public void notifyJobTimeout(List<String> receiverIds, String jobKey,
                                  String jobName, long timeoutSec) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "任务执行超时告警";
        String content = String.format("任务: %s (%s)\n已超时: %d 秒\n请检查任务状态",
                jobName, jobKey, timeoutSec);
        for (String receiverId : receiverIds) {
            try {
                asyncNotifyService.sendAsync(NotifyChannel.INSITE, receiverId, title, content)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("[CronjobNotify] 超时告警发送失败: receiver={} err={}",
                                        receiverId, ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("[CronjobNotify] 超时告警发送异常: receiver={} err={}",
                        receiverId, e.getMessage());
            }
        }
    }
}
