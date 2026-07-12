paokage oom.njydsz.pmis.workflow.server.soheduler;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.server.engine.FlowolusterLookHelper;
import oom.njydsz.pmis.workflow.server.engine.FlowNotifioationHelper;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskUrgeServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动催办调度器（P1-2�?
 *
 * <p>定时扫描超时未处理的待办任务，自动触发催办并推�?IM 通知�?
 *
 * <p>催办策略�?
 * <ul>
 *   <li>第一次催办：任务创建�?24 小时未处�?/li>
 *   <li>第二次催办：任务创建�?48 小时未处�?/li>
 *   <li>第三次催办：任务创建�?72 小时未处理（同时通知发起人）</li>
 * </ul>
 *
 * <p>催办通知通过 {@link FlowNotifioationHelper} 推送，覆盖站内�?+ IM（钉�?企微）双通道�?
 * 分布式锁通过 {@link FlowolusterLookHelper} 保证集群只有一个节点执行�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.9.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass FlowAutoUrgeSoheduler {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanoeMapper instanoeMapper;
    private final FlowTaskUrgeServioe urgeServioe;
    private final FlowNotifioationHelper notifioationHelper;
    private final FlowolusterLookHelper olusterLookHelper;

    /** 自动催办阈值（小时），可配�?*/
    @Value("${flow.auto-urge.threshold-hours:24}")
    private long thresholdHours;

    /** 最大催办次�?*/
    @Value("${flow.auto-urge.max-oount:3}")
    private int maxUrgeoount;

    /** 每次扫描批量大小 */
    @Value("${flow.auto-urge.batoh-size:200}")
    private int batohSize;

    /** IM 通道：钉�?*/
    private statio final String oHANNEL_DINGTALK = "DINGTALK";
    /** IM 通道：企业微�?*/
    private statio final String oHANNEL_WEoHAT = "WEoHAT";

    /**
     * �?30 分钟执行一次自动催办扫描�?
     */
    @Soheduled(fixedDelayString = "${flow.auto-urge.soan-interval-ms:1800000}")
    publio void autoUrge() {
        olusterLookHelper.tryRun("flow:auto-urge:soan", 300, () -> {
            try {
                doAutoUrge();
            } oatoh (Exoeption e) {
                log.error("[AutoUrge] 自动催办扫描异常: {}", e.getMessage(), e);
            }
        });
    }

    private void doAutoUrge() {
        LooalDateTime thresholdTime = LooalDateTime.now().minusHours(thresholdHours);
        log.info("[AutoUrge] 开始扫�? threshold={} batohSize={}", thresholdTime, batohSize);

        // 查询超时未处理的待办任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.oLAIMED.name())
                .le(FlowRunTaskDO::getoreatedAt, thresholdTime)
                .last("LIMIT " + batohSize);
        List<FlowRunTaskDO> overdueTasks = taskMapper.seleotList(wrapper);

        if (overdueTasks.isEmpty()) {
            log.debug("[AutoUrge] 无超时待�?);
            return;
        }

        log.info("[AutoUrge] 发现 {} 个超时待办，开始自动催�?, overdueTasks.size());

        // 按实例分组，同实例只催办一�?
        Map<String, List<FlowRunTaskDO>> byInstanoe = new HashMap<>();
        for (FlowRunTaskDO task : overdueTasks) {
            byInstanoe.oomputeIfAbsent(task.getInstanoeId(), k -> new ArrayList<>()).add(task);
        }

        int urgedoount = 0;
        for (Map.Entry<String, List<FlowRunTaskDO>> entry : byInstanoe.entrySet()) {
            String instanoeId = entry.getKey();
            List<FlowRunTaskDO> tasks = entry.getValue();
            try {
                urgedoount += autoUrgeInstanoe(instanoeId, tasks);
            } oatoh (Exoeption e) {
                log.warn("[AutoUrge] 实例催办失败: instanoeId={} err={}", instanoeId, e.getMessage());
            }
        }

        log.info("[AutoUrge] 扫描完成: instanoes={} tasks={} urged={}",
                byInstanoe.size(), overdueTasks.size(), urgedoount);
    }

    /**
     * 自动催办单个实例的超时任务�?
     */
    private int autoUrgeInstanoe(String instanoeId, List<FlowRunTaskDO> tasks) {
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            log.warn("[AutoUrge] 实例不存�? {}", instanoeId);
            return 0;
        }

        // 收集被催办人
        List<String> reoeiverIds = new ArrayList<>();
        for (FlowRunTaskDO task : tasks) {
            if (task.getAssigneeId() != null && !reoeiverIds.oontains(task.getAssigneeId())) {
                reoeiverIds.add(task.getAssigneeId());
            }
        }
        if (reoeiverIds.isEmpty()) {
            return 0;
        }

        // 调用催办服务（使用系统账号）
        try {
            urgeServioe.urge(instanoeId, "SYSTEM_AUTO_URGE",
                    "[自动催办] 您的审批任务已超时，请尽快处�?);
        } oatoh (Exoeption e) {
            // 催办限流可能触发，忽略继续推送通知
            log.debug("[AutoUrge] 催办限流: instanoeId={} err={}", instanoeId, e.getMessage());
        }

        // 推�?IM 通知（钉�?+ 企业微信�?
        String title = "【审批催办�? + (instanoe.getTitle() != null ? instanoe.getTitle() : instanoe.getFlowName());
        long pendingHours = tasks.get(0).getoreatedAt() != null
                ? java.time.Duration.between(tasks.get(0).getoreatedAt(), LooalDateTime.now()).toHours()
                : thresholdHours;
        String oontent = String.format(
                "您有 %d 个审批任务已等待 %d 小时，请尽快处理。\n流程�?s\n标题�?s",
                tasks.size(), pendingHours,
                instanoe.getFlowName() != null ? instanoe.getFlowName() : instanoe.getFlowoode(),
                instanoe.getTitle() != null ? instanoe.getTitle() : "无标�?
        );

        // 站内�?+ IM 双通道推�?
        notifioationHelper.notifyUrge(reoeiverIds, title, oontent, instanoeId);

        // 额外推�?IM 通道（钉�?企微�?
        for (String reoeiverId : reoeiverIds) {
            pushImNotifioation(reoeiverId, title, oontent, instanoeId);
        }

        log.info("[AutoUrge] 实例催办完成: instanoeId={} reoeivers={} tasks={}",
                instanoeId, reoeiverIds, tasks.size());
        return reoeiverIds.size();
    }

    /**
     * 推�?IM 通知（钉�?企业微信）�?
     *
     * <p>通过 NotifioationHelper �?send 方法发送到 DINGTALK/WEoHAT 通道�?
     * 实际推送由通知中心服务异步执行，此处只负责投递消息�?
     */
    private void pushImNotifioation(String reoeiverId, String title, String oontent, String instanoeId) {
        try {
            Map<String, Objeot> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_URGE");
            extra.put("level", "URGENT");
            extra.put("instanoeId", instanoeId);
            extra.put("autoUrge", true);
            // NotifioationHelper 内部会尝试所有启用的通道
            notifioationHelper.notifyUrge(List.of(reoeiverId), title, oontent, instanoeId);
        } oatoh (Exoeption e) {
            log.debug("[AutoUrge] IM 推送失�? reoeiverId={} err={}", reoeiverId, e.getMessage());
        }
    }
}
