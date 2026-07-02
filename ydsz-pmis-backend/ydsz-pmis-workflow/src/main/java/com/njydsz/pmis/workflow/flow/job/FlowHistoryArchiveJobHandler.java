package com.njydsz.pmis.workflow.flow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 历史数据归档任务处理器
 *
 * <p>定时扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 校验其全部任务已归档至 pmis_flow_his_task，并记录归档统计。
 *
 * <p>当前版本仅做校验与日志记录（生产环境会将冷数据迁移至归档/冷存储表）。
 *
 * <p>容错策略：每个实例独立 try-catch，单个实例处理失败不影响其余实例。
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"（每日 03:00 执行）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days（归档阈值天数，默认 30），可空
     * @return 执行结果摘要：archived/verified/missing 等计数
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        int days = parseDays(paramsJson);

        log.info("[FlowHistoryArchive] 开始归档历史数据 days={}", days);

        // 查询已结束且结束时间超过阈值的实例（deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt);

        List<FlowInstanceDO> oldInstances;
        try {
            oldInstances = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oldInstances == null || oldInstances.isEmpty()) {
            log.info("FlowHistoryArchiveJobHandler: archived 0 instances older than {} days", days);
            Map<String, Object> empty = new HashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        for (FlowInstanceDO instance : oldInstances) {
            try {
                if (verifyAndArchive(instance)) {
                    archived++;
                } else {
                    missing++;
                }
            } catch (Exception e) {
                errors++;
                log.error("[FlowHistoryArchive] 归档实例异常 instanceId={} err={}",
                        instance.getId(), e.getMessage(), e);
            }
        }

        log.info("FlowHistoryArchiveJobHandler: archived {} instances older than {} days",
                archived, days);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("total", oldInstances.size());
        result.put("archived", archived);
        result.put("missing", missing);
        result.put("errors", errors);
        result.put("days", days);
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    // ============================== 单实例归档 ==============================

    /**
     * 校验实例全部任务已归档，并记录归档日志
     *
     * <p>当前版本仅校验 + 日志，不实际迁移数据。
     * 生产环境此处应将实例及关联任务/变量迁移至冷存储表并清理主表。
     *
     * @param instance 历史实例
     * @return true=归档校验通过；false=存在未归档任务
     */
    private boolean verifyAndArchive(FlowInstanceDO instance) {
        Long instanceId = instance.getId();
        // 1. 查询该实例的全部待办任务（含已完成，主表保留记录）
        List<FlowTaskDO> tasks = taskMapper.selectByInstanceId(instanceId);
        // 2. 查询已归档的历史任务
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(instanceId);

        Set<Long> archivedTaskIds = new HashSet<>();
        if (hisTasks != null) {
            for (FlowHisTaskDO his : hisTasks) {
                if (his.getTaskId() != null) {
                    archivedTaskIds.add(his.getTaskId());
                }
            }
        }

        // 3. 校验每个任务是否都已归档
        int taskCount = tasks == null ? 0 : tasks.size();
        int unarchived = 0;
        if (tasks != null) {
            for (FlowTaskDO task : tasks) {
                if (task.getId() != null && !archivedTaskIds.contains(task.getId())) {
                    unarchived++;
                }
            }
        }

        if (unarchived > 0) {
            log.warn("[FlowHistoryArchive] 实例存在未归档任务 instanceId={} status={} taskCount={} archived={} unarchived={}",
                    instanceId, instance.getFlowStatus(), taskCount, archivedTaskIds.size(), unarchived);
            return false;
        }

        // 4. 归档记录（当前仅日志，生产环境迁移冷数据）
        log.info("[FlowHistoryArchive] 归档实例 instanceId={} status={} endAt={} taskCount={} hisCount={}",
                instanceId, instance.getFlowStatus(), instance.getEndAt(),
                taskCount, archivedTaskIds.size());
        return true;
    }

    // ============================== 私有辅助 ==============================

    /** 从 paramsJson 解析归档阈值天数，默认 30 */
    private int parseDays(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return DEFAULT_ARCHIVE_DAYS;
        }
        try {
            JSONObject obj = JSON.parseObject(paramsJson);
            if (obj == null) {
                return DEFAULT_ARCHIVE_DAYS;
            }
            Integer days = obj.getInteger("days");
            if (days == null || days <= 0) {
                return DEFAULT_ARCHIVE_DAYS;
            }
            return days;
        } catch (Exception e) {
            log.warn("[FlowHistoryArchive] 参数 JSON 解析失败，使用默认天数 {}: {}",
                    DEFAULT_ARCHIVE_DAYS, e.getMessage());
            return DEFAULT_ARCHIVE_DAYS;
        }
    }
}
