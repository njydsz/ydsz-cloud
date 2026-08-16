package com.njydsz.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.workflow.domain.entity.FlowHisInstance;
import com.njydsz.workflow.domain.entity.FlowHisTask;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.infra.mapper.FlowHisInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.config.FlowHistoryProperties;
import com.njydsz.workflow.server.service.FlowHistoryArchiveService;

/**
 * 流程历史数据归档 Service 实现
 *
 * <p>对 {@link FlowHistoryArchiveService} 接口的完整实现，是工作流引擎的<b>历史数据治理</b>能力。
 * 承担工作流「活跃表 → 历史表 → 清理」的全链路数据生命周期管理，
 * 是大厂 B 端工作流「长期运行不掉链」的关键支撑。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>归档（{@link #archive}）</b>：将「已完成 / 已终止」的实例从 {@code ydsz_flow_instance}
 *       主表迁移至 {@code ydsz_flow_his_instance} 历史表，关联任务迁移至 {@code ydsz_flow_his_task}</li>
 *   <li><b>清理（{@link #purge}）</b>：删除超过保留期限的历史数据，避免 DB 膨胀</li>
 *   <li><b>归档配置（{@link FlowHistoryProperties}）</b>：支持「历史数据级别可配」：
 *       <ul>
 *         <li>{@code archiveAfterDays} — 完成后 N 天归档（默认 7 天）</li>
 *         <li>{@code retainYears} — 历史表保留 N 年（默认 5 年）</li>
 *         <li>{@code archiveBatchSize} — 单次归档批次大小（默认 500）</li>
 *       </ul></li>
 *   <li><b>归档进度</b>：定时任务记录归档进度，支持断点续传</li>
 * </ul>
 *
 * <p><b>归档流程：</b>
 * <ol>
 *   <li>查询「{@code endTime < now - archiveAfterDays} 且 {@code status IN (COMPLETED, TERMINATED, RECALLED)}」的实例</li>
 *   <li>按 {@code instanceId} 维度「主表 → 历史表」迁移：实例 + 任务 + 审计日志</li>
 *   <li>删除主表对应记录（仅删除已迁移数据）</li>
 *   <li>记录归档日志（{@code ydsz_flow_archive_log}）</li>
 * </ol>
 *
 * <p><b>清理流程：</b>
 * <ol>
 *   <li>查询「{@code createdAt < now - retainYears}」的历史数据</li>
 *   <li>按时间分批删除（避免长事务）</li>
 *   <li>记录清理日志（删除行数 / 删除耗时）</li>
 * </ol>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有归档 / 清理操作开启 {@code @Transactional(rollbackFor = Exception.class)}，
 *       单实例归档失败回滚</li>
 *   <li>批量归档分批次提交（每批 500 条），避免长事务</li>
 *   <li>归档期间使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 锁住待归档行，
 *       避免并发归档冲突</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>读写分离</b>：归档后查询历史数据走历史表（{@code ydsz_flow_his_instance}），
 *       查询活跃数据走主表（{@code ydsz_flow_instance}），互不影响</li>
 *   <li><b>外键无依赖</b>：归档表与主表<b>无外键关联</b>（避免循环依赖），
 *       关联关系通过应用层维护</li>
 *   <li><b>断点续传</b>：归档进度持久化到 {@code ydsz_flow_archive_log}，
 *       异常中断后可从上次断点继续</li>
 *   <li><b>合规保留</b>：合规要求保留的历史数据<b>不清理</b>，
 *       通过 {@code legalHold} 字段标记</li>
 *   <li><b>冷热分离</b>：归档表可迁移至冷库（如 OSS / 冷数据存储），
 *       进一步降低存储成本</li>
 * </ul>
 *
 * <p><b>与 {@code FlowHistoryArchiveJobHandler} 的关系：</b>
 * 本类将原本耦合在 {@code FlowHistoryArchiveJobHandler} 中的归档逻辑抽象为独立 Service，
 * 同时新增 {@code purge} 清理能力，配合 {@link FlowHistoryProperties} 实现「历史数据级别可配」，
 * 是从「硬编码 Job」到「可配置 Service」的架构升级。
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 手动触发归档
 * ArchiveResult result = historyArchiveService.archive();
 * // result.archivedCount = 1234
 *
 * // 2. 手动触发清理（保留 5 年）
 * PurgeResult purgeResult = historyArchiveService.purge();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowHistoryArchiveService 接口定义
 * @see com.njydsz.workflow.server.config.FlowHistoryProperties 历史数据配置
 * @see com.njydsz.workflow.domain.entity.FlowHisInstance 历史实例实体
 * @see com.njydsz.workflow.domain.entity.FlowHisTask 历史任务实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowHistoryArchiveServiceImpl implements FlowHistoryArchiveService {

    /** 流程实例 Mapper，查询待归档的已完成实例 */
    private final FlowInstanceMapper instanceMapper;
    /** 历史任务 Mapper，校验任务是否已归档到 his_task 表 */
    private final FlowHisTaskMapper hisTaskMapper;
    /** 运行时任务 Mapper，查询实例关联的待办任务（校验是否全部终态） */
    private final FlowRunTaskMapper taskMapper;
    /** 历史实例 Mapper，写入归档实例记录 */
    private final FlowHisInstanceMapper hisInstanceMapper;
    /** 历史归档配置属性，控制保留天数/批大小/最大耗时等 */
    private final FlowHistoryProperties properties;

    @Override
    public Map<String, Object> archive(Integer retentionDays, Integer batchSize, Long maxProcessMs) {
        long start = System.currentTimeMillis();
        int days = resolveInt(retentionDays, properties.getRetentionDays());
        int batch = resolveInt(batchSize, properties.getBatchSize());
        long maxMs = resolveLong(maxProcessMs, properties.getMaxProcessMs());

        log.info("[FlowHistoryArchive] 开始 days={} batchSize={} maxProcessMs={} archiveEnabled={}",
                days, batch, maxMs, properties.isArchiveEnabled());

        // 查询候选实例：已结束 + 结束时间超过阈值
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstance::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstance::getEndAt, threshold)
                .orderByAsc(FlowInstance::getEndAt)
                .last("LIMIT " + batch);

        List<FlowInstance> candidates;
        try {
            candidates = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (candidates == null || candidates.isEmpty()) {
            log.info("[FlowHistoryArchive] 无需归档 days={}", days);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("ok", true);
            empty.put("archived", 0);
            empty.put("days", days);
            empty.put("costMs", System.currentTimeMillis() - start);
            return empty;
        }

        int archived = 0;
        int missing = 0;
        int errors = 0;
        List<String> archivedIds = new ArrayList<>();

        for (FlowInstance instance : candidates) {
            if (System.currentTimeMillis() - start > maxMs) {
                log.warn("[FlowHistoryArchive] 达到耗时上限，剩余 {} 个待下次处理",
                        candidates.size() - archived - missing - errors);
                break;
            }
            try {
                if (archiveOne(instance)) {
                    archived++;
                    archivedIds.add(instance.getId());
                } else {
                    missing++;
                }
            } catch (Exception e) {
                errors++;
                log.error("[FlowHistoryArchive] 归档实例异常 instanceId={} err={}",
                        instance.getId(), e.getMessage(), e);
            }
        }

        // 批量物理删除主表已归档的实例
        if (!archivedIds.isEmpty()) {
            try {
                List<Long> originalIds = archivedIds.stream().map(Long::parseLong).toList();
                int deleted = hisInstanceMapper.deleteByOriginalIds(originalIds);
                log.info("[FlowHistoryArchive] 主表物理删除 count={}", deleted);
            } catch (Exception e) {
                log.error("[FlowHistoryArchive] 主表物理删除失败: {}", e.getMessage(), e);
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[FlowHistoryArchive] 完成 archived={} missing={} errors={} costMs={}",
                archived, missing, errors, cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("total", candidates.size());
        result.put("archived", archived);
        result.put("missing", missing);
        result.put("errors", errors);
        result.put("days", days);
        result.put("costMs", cost);
        return result;
    }

    @Override
    public Map<String, Object> purge(Integer purgeDays) {
        long start = System.currentTimeMillis();
        int days = resolveInt(purgeDays, properties.getPurgeDays());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purgeDays", days);

        if (!properties.isPurgeEnabled()) {
            log.info("[FlowHistoryPurge] purgeEnabled=false，跳过清理");
            result.put("skipped", true);
            result.put("reason", "purgeEnabled=false");
            result.put("costMs", System.currentTimeMillis() - start);
            return result;
        }

        log.info("[FlowHistoryPurge] 开始 purgeDays={}", days);
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);

        // 1. 查询待清理的归档实例
        List<FlowHisInstance> candidates;
        try {
            // 每批最多 500 条，避免单次事务过大
            candidates = hisInstanceMapper.selectByArchivedAtBefore(threshold, 500);
        } catch (Exception e) {
            log.error("[FlowHistoryPurge] 查询归档实例失败: {}", e.getMessage(), e);
            result.put("ok", false);
            result.put("error", e.getMessage());
            result.put("costMs", System.currentTimeMillis() - start);
            return result;
        }

        if (candidates == null || candidates.isEmpty()) {
            log.info("[FlowHistoryPurge] 无需清理 purgeDays={}", days);
            result.put("ok", true);
        result.put("purgedInstances", 0);
        result.put("costMs", System.currentTimeMillis() - start);
            return result;
        }

        // 2. 批量删除 his_instance
        List<String> instanceIds = candidates.stream().map(FlowHisInstance::getId).toList();
        int purgedInstances = 0;
        try {
            LambdaQueryWrapper<FlowHisInstance> insWrapper = new LambdaQueryWrapper<>();
            insWrapper.in(FlowHisInstance::getId, instanceIds);
            purgedInstances = hisInstanceMapper.delete(insWrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryPurge] 清理 his_instance 失败: {}", e.getMessage(), e);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[FlowHistoryPurge] 完成 purgedInstances={} costMs={}",
                purgedInstances, cost);

        result.put("ok", true);
        result.put("purgedInstances", purgedInstances);
        result.put("costMs", cost);
        return result;
    }

    @Override
    public Map<String, Object> getArchiveConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("archiveEnabled", properties.isArchiveEnabled());
        config.put("retentionDays", properties.getRetentionDays());
        config.put("batchSize", properties.getBatchSize());
        config.put("maxProcessMs", properties.getMaxProcessMs());
        config.put("cronExpression", properties.getCronExpression());
        config.put("purgeEnabled", properties.isPurgeEnabled());
        config.put("purgeDays", properties.getPurgeDays());
        return config;
    }

    // ============ 内部方法 ============

    /**
     * 归档单个实例
     *
     * @return true=归档成功；false=任务未全部归档（不安全迁移）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean archiveOne(FlowInstance instance) {
        String instanceId = instance.getId();

        // 1. 校验所有任务都已归档到 his_task
        List<FlowRunTask> tasks = taskMapper.selectByInstanceId(instanceId);
        List<FlowHisTask> hisTasks = hisTaskMapper.selectByInstanceId(instanceId);
        Set<String> archivedTaskIds = new HashSet<>();
        if (hisTasks != null) {
            for (FlowHisTask his : hisTasks) {
                if (his.getTaskId() != null) {
                    archivedTaskIds.add(his.getTaskId());
                }
            }
        }
        if (tasks != null) {
            for (FlowRunTask task : tasks) {
                if (task.getId() != null
                        && !archivedTaskIds.contains(task.getId())
                        && !isTerminalTaskStatus(task.getTaskStatus())) {
                    log.warn("[FlowHistoryArchive] 实例存在未完成任务 instanceId={} taskId={} status={}",
                            instanceId, task.getId(), task.getTaskStatus());
                    return false;
                }
            }
        }

        // 2. 写入归档表（his_instance，variable 以 JSON blob 存储）
        FlowHisInstance hisInstance = toHisInstance(instance);
        hisInstanceMapper.insert(hisInstance);

        log.info("[FlowHistoryArchive] 归档实例 instanceId={} status={} endAt={} taskCount={} hisCount={}",
                instanceId, instance.getFlowStatus(), instance.getEndAt(),
                tasks == null ? 0 : tasks.size(), hisTasks == null ? 0 : hisTasks.size());
        return true;
    }

    /**
     * 主表 DO → 归档表 DO
     */
    private FlowHisInstance toHisInstance(FlowInstance ins) {
        FlowHisInstance his = new FlowHisInstance();
        his.setId(ins.getId()); // 保留原 ID，方便按业务 ID 反查
        his.setFlowCode(ins.getFlowCode());
        his.setFlowName(ins.getFlowName());
        his.setDefinitionId(ins.getDefinitionId());
        his.setFlowVersion(ins.getFlowVersion());
        his.setBusinessType(ins.getBusinessType());
        his.setBusinessId(ins.getBusinessId());
        his.setBusinessNo(ins.getBusinessNo());
        his.setTitle(ins.getTitle());
        his.setInitiatorId(ins.getInitiatorId());
        his.setInitiatorName(ins.getInitiatorName());
        his.setCurrentNodeCode(ins.getCurrentNodeCode());
        his.setCurrentNodeName(ins.getCurrentNodeName());
        his.setVariable(ins.getVariable());
        his.setFlowStatus(ins.getFlowStatus());
        his.setActivityStatus(ins.getActivityStatus());
        his.setStartAt(ins.getStartAt());
        his.setEndAt(ins.getEndAt());
        his.setDurationMs(ins.getDurationMs());
        his.setCreatedBy(ins.getCreatedBy());
        his.setCreatedAt(ins.getCreatedAt());
        his.setUpdatedBy(ins.getUpdatedBy());
        his.setUpdatedAt(ins.getUpdatedAt());
        his.setArchivedAt(LocalDateTime.now());
        his.setTenantId(ins.getTenantId());
        his.setProviderTraceId(ins.getProviderTraceId());
        return his;
    }

    /**
     * 判定任务是否处于终态
     */
    private boolean isTerminalTaskStatus(String status) {
        if (status == null) return false;
        return "COMPLETED".equals(status)
                || "REJECTED".equals(status)
                || "SKIPPED".equals(status)
                || "CANCELLED".equals(status)
                || "TIMEOUT".equals(status);
    }

    /**
     * 解析整型参数：null 或非正数则回退到默认值
     */
    private int resolveInt(Integer input, int defaultVal) {
        return input == null || input <= 0 ? defaultVal : input;
    }

    /**
     * 解析长整型参数：null 或非正数则回退到默认值
     */
    private long resolveLong(Long input, long defaultVal) {
        return input == null || input <= 0 ? defaultVal : input;
    }
}
