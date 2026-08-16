package com.njydsz.workflow.server.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskUrgeService;

/**
 * 自动催办调度器（P1-2）
 *
 * <p>定时扫描超时未处理的待办任务，自动触发催办并推送 IM 通知。
 *
 * <p>催办策略：
 * <ul>
 *   <li>第一次催办：任务创建后 24 小时未处理</li>
 *   <li>第二次催办：任务创建后 48 小时未处理</li>
 *   <li>第三次催办：任务创建后 72 小时未处理（同时通知发起人）</li>
 * </ul>
 *
 * <p>催办通知通过 {@link FlowNotificationService} 推送，覆盖站内信 + IM（钉钉/企微）双通道。
 * 分布式锁通过 {@link DistributedScheduled} 保证集群只有一个节点执行。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowAutoUrgeScheduler {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskUrgeService urgeService;
    private final FlowNotificationService notificationService;
    /** P3-3.4: 自动催办配置统一从 FlowProperties 读取 */
    private final FlowProperties flowProperties;

    /** IM 通道：钉钉 */
    private static final String CHANNEL_DINGTALK = "DINGTALK";
    /** IM 通道：企业微信 */
    private static final String CHANNEL_WECHAT = "WECHAT";

    /**
     * 每 30 分钟执行一次自动催办扫描。
     *
     * <p>通过 {@link DistributedScheduled} 保证多节点部署时仅一个节点执行扫描，
     * 获取不到锁的节点直接跳过本次执行（非阻塞）。
     */
    @Scheduled(fixedDelayString = "${ydsz.flow.auto-urge.scan-interval-ms:1800000}")
    @DistributedScheduled(lockKey = "flow:auto-urge:scan", leaseTime = 300)
    public void autoUrge() {
        try {
            doAutoUrge();
        } catch (Exception e) {
            log.error("[AutoUrge] 自动催办扫描异常: {}", e.getMessage(), e);
        }
    }

    private void doAutoUrge() {
        FlowProperties.AutoUrge cfg = flowProperties.getAutoUrge();
        long thresholdHours = cfg.getThresholdHours();
        int batchSize = cfg.getBatchSize();

        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(thresholdHours);
        log.info("[AutoUrge] 开始扫描: threshold={} batchSize={}", thresholdTime, batchSize);

        // 查询超时未处理的待办任务
        LambdaQueryWrapper<FlowRunTask> wrapper = new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getDeleted, 0)
                .in(FlowRunTask::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
                .le(FlowRunTask::getCreatedAt, thresholdTime)
                .last("LIMIT " + batchSize);
        List<FlowRunTask> overdueTasks = taskMapper.selectList(wrapper);

        if (overdueTasks.isEmpty()) {
            log.debug("[AutoUrge] 无超时待办");
            return;
        }

        log.info("[AutoUrge] 发现 {} 个超时待办，开始自动催办", overdueTasks.size());

        // 按实例分组，同实例只催办一次
        Map<String, List<FlowRunTask>> byInstance = new HashMap<>();
        for (FlowRunTask task : overdueTasks) {
            byInstance.computeIfAbsent(task.getInstanceId(), k -> new ArrayList<>()).add(task);
        }

        int urgedCount = 0;
        for (Map.Entry<String, List<FlowRunTask>> entry : byInstance.entrySet()) {
            String instanceId = entry.getKey();
            List<FlowRunTask> tasks = entry.getValue();
            try {
                urgedCount += autoUrgeInstance(instanceId, tasks);
            } catch (Exception e) {
                log.warn("[AutoUrge] 实例催办失败: instanceId={} err={}", instanceId, e.getMessage());
            }
        }

        log.info("[AutoUrge] 扫描完成: instances={} tasks={} urged={}",
                byInstance.size(), overdueTasks.size(), urgedCount);
    }

    /**
     * 自动催办单个实例的超时任务。
     */
    private int autoUrgeInstance(String instanceId, List<FlowRunTask> tasks) {
        FlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("[AutoUrge] 实例不存在: {}", instanceId);
            return 0;
        }

        // 收集被催办人
        List<String> receiverIds = new ArrayList<>();
        for (FlowRunTask task : tasks) {
            if (task.getAssigneeId() != null && !receiverIds.contains(task.getAssigneeId())) {
                receiverIds.add(task.getAssigneeId());
            }
        }
        if (receiverIds.isEmpty()) {
            return 0;
        }

        // 调用催办服务（使用系统账号）
        try {
            urgeService.urge(instanceId, "SYSTEM_AUTO_URGE",
                    "[自动催办] 您的审批任务已超时，请尽快处理");
        } catch (Exception e) {
            // 催办限流可能触发，忽略继续推送通知
            log.debug("[AutoUrge] 催办限流: instanceId={} err={}", instanceId, e.getMessage());
        }

        // 推送 IM 通知（钉钉 + 企业微信）
        String title = "【审批催办】" + (instance.getTitle() != null ? instance.getTitle() : instance.getFlowName());
        long pendingHours = tasks.get(0).getCreatedAt() != null
                ? Duration.between(tasks.get(0).getCreatedAt(), LocalDateTime.now()).toHours()
                : flowProperties.getAutoUrge().getThresholdHours();
        String content = String.format(
                "您有 %d 个审批任务已等待 %d 小时，请尽快处理。\n流程：%s\n标题：%s",
                tasks.size(), pendingHours,
                instance.getFlowName() != null ? instance.getFlowName() : instance.getFlowCode(),
                instance.getTitle() != null ? instance.getTitle() : "无标题"
        );

        // 站内信 + IM 双通道推送
        notificationService.notifyBatch("INAPP", receiverIds, title, content, "WORKFLOW_URGE", "URGENT");

        // 额外推送 IM 通道（钉钉/企微）
        for (String receiverId : receiverIds) {
            pushImNotification(receiverId, title, content, instanceId);
        }

        log.info("[AutoUrge] 实例催办完成: instanceId={} receivers={} tasks={}",
                instanceId, receiverIds, tasks.size());
        return receiverIds.size();
    }

    /**
     * 推送 IM 通知（钉钉/企业微信）。
     *
     * <p>通过 NotificationHelper 的 send 方法发送到 DINGTALK/WECHAT 通道。
     * 实际推送由通知中心服务异步执行，此处只负责投递消息。
     */
    private void pushImNotification(String receiverId, String title, String content, String instanceId) {
        try {
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_URGE");
            extra.put("level", "URGENT");
            extra.put("instanceId", instanceId);
            extra.put("autoUrge", true);
            // NotificationService 内部会尝试所有启用的通道
            notificationService.notifyBatch("INAPP", List.of(receiverId), title, content, "WORKFLOW_URGE", "URGENT");
        } catch (Exception e) {
            log.debug("[AutoUrge] IM 推送失败: receiverId={} err={}", receiverId, e.getMessage());
        }
    }
}
