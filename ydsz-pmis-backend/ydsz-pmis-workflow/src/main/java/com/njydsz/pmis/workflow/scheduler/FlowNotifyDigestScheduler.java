package com.njydsz.pmis.workflow.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.engine.FlowClusterLockHelper;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.entity.notification.FlowNotifyOutboxDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowNotifyOutboxMapper;
import com.njydsz.pmis.workflow.service.notification.FlowNotifyPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知聚合推送调度器（P2-2）
 *
 * <p>当用户开启了免打扰 + 聚合模式（digestMode=1）时，在免打扰时段内收到的通知
 * 会被标记为 DEFERRED 状态。本调度器在免打扰时段结束后，将同一用户的 DEFERRED 通知
 * 聚合为一条摘要消息推送，避免频繁打扰。
 *
 * <p>聚合策略：
 * <ul>
 *   <li>按用户分组 DEFERRED 通知</li>
 *   <li>每用户生成一条摘要：包含通知条数 + 分类统计 + 最新 5 条标题</li>
 *   <li>通过站内信 + IM 通道推送摘要</li>
 *   <li>原 DEFERRED 通知标记为 AGGREGATED_SENT</li>
 * </ul>
 *
 * <p>分布式锁通过 {@link FlowClusterLockHelper} 保证集群只有一个节点执行。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNotifyDigestScheduler {

    private final FlowNotifyOutboxMapper outboxMapper;
    private final FlowNotifyPreferenceService preferenceService;
    private final FlowNotificationHelper notificationHelper;
    private final FlowClusterLockHelper clusterLockHelper;

    /** 每次扫描批量大小 */
    @Value("${flow.notify.digest.batch-size:500}")
    private int batchSize;

    /** DEFERRED 状态标记 */
    private static final String STATUS_DEFERRED = "DEFERRED";
    /** 聚合推送完成标记 */
    private static final String STATUS_AGGREGATED = "AGGREGATED";

    /**
     * 每 15 分钟扫描一次待聚合通知。
     */
    @Scheduled(fixedDelayString = "${flow.notify.digest.scan-interval-ms:900000}")
    public void sendDigest() {
        clusterLockHelper.tryRun("flow:notify-digest", 600, () -> {
            try {
                doSendDigest();
            } catch (Exception e) {
                log.error("[NotifyDigest] 聚合推送扫描异常: {}", e.getMessage(), e);
            }
        });
    }

    private void doSendDigest() {
        // 查询 DEFERRED 状态的通知
        LambdaQueryWrapper<FlowNotifyOutboxDO> wrapper = new LambdaQueryWrapper<FlowNotifyOutboxDO>()
                .eq(FlowNotifyOutboxDO::getStatus, STATUS_DEFERRED)
                .eq(FlowNotifyOutboxDO::getDeleted, 0)
                .le(FlowNotifyOutboxDO::getCreatedAt, LocalDateTime.now())
                .last("LIMIT " + batchSize);
        List<FlowNotifyOutboxDO> deferredNotifs = outboxMapper.selectList(wrapper);

        if (deferredNotifs.isEmpty()) {
            log.debug("[NotifyDigest] 无待聚合通知");
            return;
        }

        log.info("[NotifyDigest] 发现 {} 条待聚合通知", deferredNotifs.size());

        // 按 tenantId + targetUserIds 分组
        Map<String, List<FlowNotifyOutboxDO>> byUser = new HashMap<>();
        for (FlowNotifyOutboxDO notif : deferredNotifs) {
            if (notif.getTargetUserIds() == null || notif.getTargetUserIds().isEmpty()) {
                continue;
            }
            // targetUserIds 是逗号分隔的，简化处理：取第一个用户
            String userId = notif.getTargetUserIds().split(",")[0].trim();
            String key = notif.getTenantId() + ":" + userId;
            byUser.computeIfAbsent(key, k -> new ArrayList<>()).add(notif);
        }

        int sentCount = 0;
        for (Map.Entry<String, List<FlowNotifyOutboxDO>> entry : byUser.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length < 2) continue;
            String tenantId = parts[0];
            String userId = parts[1];
            List<FlowNotifyOutboxDO> userNotifs = entry.getValue();

            try {
                // 检查用户是否仍在免打扰时段
                if (preferenceService.isQuietHours(tenantId, userId)) {
                    log.debug("[NotifyDigest] 用户仍在免打扰时段，跳过: userId={}", userId);
                    continue;
                }

                // 生成并发送摘要
                sendDigestToUser(userId, tenantId, userNotifs);

                // 标记通知为已聚合
                for (FlowNotifyOutboxDO notif : userNotifs) {
                    notif.setStatus(STATUS_AGGREGATED);
                    notif.setSentAt(LocalDateTime.now());
                    outboxMapper.updateById(notif);
                }
                sentCount += userNotifs.size();
            } catch (Exception e) {
                log.warn("[NotifyDigest] 用户摘要推送失败: userId={} err={}", userId, e.getMessage());
            }
        }

        log.info("[NotifyDigest] 聚合推送完成: total={} sent={}", deferredNotifs.size(), sentCount);
    }

    /**
     * 生成摘要消息并发送给用户。
     */
    private void sendDigestToUser(String userId, String tenantId,
                                   List<FlowNotifyOutboxDO> notifs) {
        int count = notifs.size();

        // 按 eventType 分类统计
        Map<String, Long> byEventType = notifs.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getEventType() != null ? n.getEventType() : "UNKNOWN",
                        Collectors.counting()));

        // 构建摘要标题和内容
        String title = String.format("【审批摘要】您有 %d 条待处理通知", count);
        StringBuilder content = new StringBuilder();
        content.append(String.format("在免打扰时段内，您收到了 %d 条审批通知：\n", count));

        for (Map.Entry<String, Long> entry : byEventType.entrySet()) {
            String eventLabel = mapEventLabel(entry.getKey());
            content.append(String.format("  · %s: %d 条\n", eventLabel, entry.getValue()));
        }

        // 列出最新 5 条通知
        List<FlowNotifyOutboxDO> latest = notifs.stream()
                .sorted((a, b) -> {
                    LocalDateTime ta = a.getCreatedAt();
                    LocalDateTime tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                })
                .limit(5)
                .collect(Collectors.toList());

        if (!latest.isEmpty()) {
            content.append("\n最新通知：\n");
            for (FlowNotifyOutboxDO n : latest) {
                content.append(String.format("  · [%s] %s\n",
                        n.getEventType() != null ? n.getEventType() : "通知",
                        n.getBizType() != null ? n.getBizType() : ""));
            }
        }

        // 推送摘要通知
        notificationHelper.notifyUrge(
                List.of(userId), title, content.toString(), null);
        log.info("[NotifyDigest] 摘要推送成功: userId={} count={}", userId, count);
    }

    /**
     * 将事件类型映射为用户可读的标签。
     */
    private String mapEventLabel(String eventType) {
        if (eventType == null) return "其他";
        return switch (eventType) {
            case "TASK_CREATED" -> "新待办";
            case "TASK_COMPLETED" -> "任务完成";
            case "TASK_URGED" -> "催办提醒";
            case "INSTANCE_TERMINATED" -> "流程终止";
            case "INSTANCE_REJECTED" -> "流程驳回";
            case "CC" -> "抄送通知";
            case "DELEGATE" -> "委派通知";
            case "TRANSFER" -> "转办通知";
            default -> "其他";
        };
    }
}
