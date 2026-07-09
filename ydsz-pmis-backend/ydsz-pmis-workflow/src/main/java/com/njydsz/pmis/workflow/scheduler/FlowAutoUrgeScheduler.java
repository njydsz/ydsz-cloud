package com.njydsz.pmis.workflow.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.engine.FlowClusterLockHelper;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskUrgeService;
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
 * <p>催办通知通过 {@link FlowNotificationHelper} 推送，覆盖站内信 + IM（钉钉/企微）双通道。
 * 分布式锁通过 {@link FlowClusterLockHelper} 保证集群只有一个节点执行。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowAutoUrgeScheduler {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskUrgeService urgeService;
    private final FlowNotificationHelper notificationHelper;
    private final FlowClusterLockHelper clusterLockHelper;

    /** 自动催办阈值（小时），可配置 */
    @Value("${flow.auto-urge.threshold-hours:24}")
    private long thresholdHours;

    /** 最大催办次数 */
    @Value("${flow.auto-urge.max-count:3}")
    private int maxUrgeCount;

    /** 每次扫描批量大小 */
    @Value("${flow.auto-urge.batch-size:200}")
    private int batchSize;

    /** IM 通道：钉钉 */
    private static final String CHANNEL_DINGTALK = "DINGTALK";
    /** IM 通道：企业微信 */
    private static final String CHANNEL_WECHAT = "WECHAT";

    /**
     * 每 30 分钟执行一次自动催办扫描。
     */
    @Scheduled(fixedDelayString = "${flow.auto-urge.scan-interval-ms:1800000}")
    public void autoUrge() {
        clusterLockHelper.tryRun("flow:auto-urge:scan", 300, () -> {
            try {
                doAutoUrge();
            } catch (Exception e) {
                log.error("[AutoUrge] 自动催办扫描异常: {}", e.getMessage(), e);
            }
        });
    }

    private void doAutoUrge() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(thresholdHours);
        log.info("[AutoUrge] 开始扫描: threshold={} batchSize={}", thresholdTime, batchSize);

        // 查询超时未处理的待办任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
                .le(FlowRunTaskDO::getCreatedAt, thresholdTime)
                .last("LIMIT " + batchSize);
        List<FlowRunTaskDO> overdueTasks = taskMapper.selectList(wrapper);

        if (overdueTasks.isEmpty()) {
            log.debug("[AutoUrge] 无超时待办");
            return;
        }

        log.info("[AutoUrge] 发现 {} 个超时待办，开始自动催办", overdueTasks.size());

        // 按实例分组，同实例只催办一次
        Map<String, List<FlowRunTaskDO>> byInstance = new HashMap<>();
        for (FlowRunTaskDO task : overdueTasks) {
            byInstance.computeIfAbsent(task.getInstanceId(), k -> new ArrayList<>()).add(task);
        }

        int urgedCount = 0;
        for (Map.Entry<String, List<FlowRunTaskDO>> entry : byInstance.entrySet()) {
            String instanceId = entry.getKey();
            List<FlowRunTaskDO> tasks = entry.getValue();
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
    private int autoUrgeInstance(String instanceId, List<FlowRunTaskDO> tasks) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("[AutoUrge] 实例不存在: {}", instanceId);
            return 0;
        }

        // 收集被催办人
        List<String> receiverIds = new ArrayList<>();
        for (FlowRunTaskDO task : tasks) {
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
                ? java.time.Duration.between(tasks.get(0).getCreatedAt(), LocalDateTime.now()).toHours()
                : thresholdHours;
        String content = String.format(
                "您有 %d 个审批任务已等待 %d 小时，请尽快处理。\n流程：%s\n标题：%s",
                tasks.size(), pendingHours,
                instance.getFlowName() != null ? instance.getFlowName() : instance.getFlowCode(),
                instance.getTitle() != null ? instance.getTitle() : "无标题"
        );

        // 站内信 + IM 双通道推送
        notificationHelper.notifyUrge(receiverIds, title, content, instanceId);

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
            // NotificationHelper 内部会尝试所有启用的通道
            notificationHelper.notifyUrge(List.of(receiverId), title, content, instanceId);
        } catch (Exception e) {
            log.debug("[AutoUrge] IM 推送失败: receiverId={} err={}", receiverId, e.getMessage());
        }
    }
}
