package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.config.FlowHistoryProperties;
import com.njydsz.pmis.workflow.entity.FlowHisInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowHisVariableDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowHisInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisVariableMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.FlowHistoryArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程历史数据归档 Service 实现
 *
 * <p>P2-8：将原本耦合在 {@code FlowHistoryArchiveJobHandler} 中的归档逻辑抽象为独立 Service，
 * 同时新增 purge 清理能力，配合 {@link FlowHistoryProperties} 实现"历史数据级别可配"。
 *
 * <p>归档流程：
 * <ol>
 *   <li>查询已结束 + 结束时间超过阈值的实例（最多 batchSize 条）</li>
 *   <li>逐实例校验所有任务均已归档到 his_task</li>
 *   <li>写入 his_instance + his_variable（拆分 JSON）</li>
 *   <li>批量物理删除主表已归档实例</li>
 *   <li>达到 maxProcessMs 上限时剩余实例留待下次执行</li>
 * </ol>
 *
 * <p>清理流程：
 * <ol>
 *   <li>查询 his_instance 中 archived_at 早于阈值的记录</li>
 *   <li>批量删除 his_variable（按 instance_id 外键级联）</li>
 *   <li>批量删除 his_instance</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowHistoryArchiveServiceImpl implements FlowHistoryArchiveService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowHisInstanceMapper hisInstanceMapper;
    private final FlowHisVariableMapper hisVariableMapper;
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
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt)
                .last("LIMIT " + batch);

        List<FlowInstanceDO> candidates;
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

        for (FlowInstanceDO instance : candidates) {
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
        List<FlowHisInstanceDO> candidates;
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
            result.put("purgedVariables", 0);
            result.put("costMs", System.currentTimeMillis() - start);
            return result;
        }

        // 2. 批量删除 his_variable（按 instance_id）
        List<String> instanceIds = candidates.stream().map(FlowHisInstanceDO::getId).toList();
        int purgedVariables = 0;
        try {
            LambdaQueryWrapper<FlowHisVariableDO> varWrapper = new LambdaQueryWrapper<>();
            varWrapper.in(FlowHisVariableDO::getInstanceId, instanceIds);
            purgedVariables = hisVariableMapper.delete(varWrapper);
        } catch (Exception e) {
            log.warn("[FlowHistoryPurge] 清理 his_variable 失败: {}", e.getMessage(), e);
        }

        // 3. 批量删除 his_instance
        int purgedInstances = 0;
        try {
            LambdaQueryWrapper<FlowHisInstanceDO> insWrapper = new LambdaQueryWrapper<>();
            insWrapper.in(FlowHisInstanceDO::getId, instanceIds);
            purgedInstances = hisInstanceMapper.delete(insWrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryPurge] 清理 his_instance 失败: {}", e.getMessage(), e);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[FlowHistoryPurge] 完成 purgedInstances={} purgedVariables={} costMs={}",
                purgedInstances, purgedVariables, cost);

        result.put("ok", true);
        result.put("purgedInstances", purgedInstances);
        result.put("purgedVariables", purgedVariables);
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
    public boolean archiveOne(FlowInstanceDO instance) {
        String instanceId = instance.getId();

        // 1. 校验所有任务都已归档到 his_task
        List<FlowRunTaskDO> tasks = taskMapper.selectByInstanceId(instanceId);
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(instanceId);
        Set<String> archivedTaskIds = new HashSet<>();
        if (hisTasks != null) {
            for (FlowHisTaskDO his : hisTasks) {
                if (his.getTaskId() != null) {
                    archivedTaskIds.add(his.getTaskId());
                }
            }
        }
        if (tasks != null) {
            for (FlowRunTaskDO task : tasks) {
                if (task.getId() != null
                        && !archivedTaskIds.contains(task.getId())
                        && !isTerminalTaskStatus(task.getTaskStatus())) {
                    log.warn("[FlowHistoryArchive] 实例存在未完成任务 instanceId={} taskId={} status={}",
                            instanceId, task.getId(), task.getTaskStatus());
                    return false;
                }
            }
        }

        // 2. 写入归档表（his_instance + his_variable）
        FlowHisInstanceDO hisInstance = toHisInstance(instance);
        hisInstanceMapper.insert(hisInstance);

        // 拆分 variable JSON 到独立行
        if (instance.getVariable() != null && !instance.getVariable().isBlank()) {
            try {
                List<FlowHisVariableDO> variables = parseVariables(hisInstance.getId(), instance.getVariable());
                if (!variables.isEmpty()) {
                    hisVariableMapper.batchInsert(variables);
                }
            } catch (Exception e) {
                log.warn("[FlowHistoryArchive] 拆分 variable 失败 instanceId={} err={}",
                        instanceId, e.getMessage());
            }
        }

        log.info("[FlowHistoryArchive] 归档实例 instanceId={} status={} endAt={} taskCount={} hisCount={}",
                instanceId, instance.getFlowStatus(), instance.getEndAt(),
                tasks == null ? 0 : tasks.size(), hisTasks == null ? 0 : hisTasks.size());
        return true;
    }

    /**
     * 主表 DO → 归档表 DO
     */
    private FlowHisInstanceDO toHisInstance(FlowInstanceDO ins) {
        FlowHisInstanceDO his = new FlowHisInstanceDO();
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
     * 解析 variable JSON 字符串为变量行列表
     */
    private List<FlowHisVariableDO> parseVariables(String instanceId, String variableJson) {
        List<FlowHisVariableDO> out = new ArrayList<>();
        try {
            JSONObject obj = JSON.parseObject(variableJson);
            if (obj == null) return out;
            for (String key : obj.keySet()) {
                FlowHisVariableDO v = new FlowHisVariableDO();
                v.setInstanceId(instanceId);
                v.setVarKey(key);
                Object value = obj.get(key);
                v.setVarValue(value == null ? null : JSON.toJSONString(value));
                v.setArchivedAt(LocalDateTime.now());
                out.add(v);
            }
        } catch (Exception e) {
            // 非 JSON 格式（可能是简单字符串）整行存储
            FlowHisVariableDO v = new FlowHisVariableDO();
            v.setInstanceId(instanceId);
            v.setVarKey("__raw__");
            v.setVarValue(variableJson);
            v.setArchivedAt(LocalDateTime.now());
            out.add(v);
        }
        return out;
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
