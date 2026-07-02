package com.njydsz.pmis.workflow.job;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.entity.FlowHisInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowHisVariableDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowHisInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisVariableMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P2-3 历史数据归档任务处理器
 *
 * <p>每日 03:00 扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值（默认 30 天）的流程实例，
 * 将其从主表迁移到 {@code pmis_flow_his_instance} 冷存储表，同时归档关联的 variable。
 *
 * <p>归档策略：
 * <ul>
 *   <li>单次最大处理 batchSize（默认 100）条实例</li>
 *   <li>单次执行总耗时上限 maxProcessMs（默认 30 秒）</li>
 *   <li>逐实例事务：归档失败不影响其他实例</li>
 *   <li>主表归档后物理删除（避免逻辑删除后的二次扫描）</li>
 * </ul>
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    /** 默认归档阈值天数 */
    private static final int DEFAULT_ARCHIVE_DAYS = 30;
    /** 默认批次大小 */
    private static final int DEFAULT_BATCH_SIZE = 100;
    /** 单次执行最大耗时（毫秒） */
    private static final long DEFAULT_MAX_PROCESS_MS = 30_000L;

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowTaskMapper taskMapper;
    @Autowired(required = false)
    private FlowHisInstanceMapper hisInstanceMapper;
    @Autowired(required = false)
    private FlowHisVariableMapper hisVariableMapper;

    /**
     * 扫描并归档历史实例
     *
     * @param paramsJson 参数 JSON，可包含 days/batchSize/maxProcessMs
     * @return 执行结果摘要：archived/verified/missing/errors 等计数
     */
    @Override
    public Object execute(String paramsJson) {
        long start = System.currentTimeMillis();
        int days = parseInt(paramsJson, "days", DEFAULT_ARCHIVE_DAYS);
        int batchSize = parseInt(paramsJson, "batchSize", DEFAULT_BATCH_SIZE);
        long maxProcessMs = parseLong(paramsJson, "maxProcessMs", DEFAULT_MAX_PROCESS_MS);

        log.info("[FlowHistoryArchive] 开始 days={} batchSize={} maxProcessMs={}", days, batchSize, maxProcessMs);

        // 查询候选实例：已结束 + 结束时间超过阈值
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanceDO::getFlowStatus,
                        FlowInstanceStatus.COMPLETED.name(),
                        FlowInstanceStatus.TERMINATED.name(),
                        FlowInstanceStatus.REJECTED.name())
                .lt(FlowInstanceDO::getEndAt, threshold)
                .orderByAsc(FlowInstanceDO::getEndAt)
                .last("LIMIT " + batchSize);

        List<FlowInstanceDO> candidates;
        try {
            candidates = instanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
            return Map.of("ok", false, "error", e.getMessage());
        }

        if (candidates == null || candidates.isEmpty()) {
            log.info("[FlowHistoryArchive] 无需归档 days={}", days);
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
        List<Long> archivedIds = new ArrayList<>();

        for (FlowInstanceDO instance : candidates) {
            if (System.currentTimeMillis() - start > maxProcessMs) {
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
        if (!archivedIds.isEmpty() && hisInstanceMapper != null) {
            try {
                int deleted = hisInstanceMapper.deleteByOriginalIds(archivedIds);
                log.info("[FlowHistoryArchive] 主表物理删除 count={}", deleted);
            } catch (Exception e) {
                log.error("[FlowHistoryArchive] 主表物理删除失败: {}", e.getMessage(), e);
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[FlowHistoryArchive] 完成 archived={} missing={} errors={} costMs={}",
                archived, missing, errors, cost);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("total", candidates.size());
        result.put("archived", archived);
        result.put("missing", missing);
        result.put("errors", errors);
        result.put("days", days);
        result.put("costMs", cost);
        return result;
    }

    /**
     * 归档单个实例
     *
     * @return true=归档成功；false=任务未全部归档（不安全迁移）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean archiveOne(FlowInstanceDO instance) {
        Long instanceId = instance.getId();

        // 1. 校验所有任务都已归档到 his_task
        List<FlowTaskDO> tasks = taskMapper.selectByInstanceId(instanceId);
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(instanceId);
        Set<Long> archivedTaskIds = new HashSet<>();
        if (hisTasks != null) {
            for (FlowHisTaskDO his : hisTasks) {
                if (his.getTaskId() != null) {
                    archivedTaskIds.add(his.getTaskId());
                }
            }
        }
        if (tasks != null) {
            for (FlowTaskDO task : tasks) {
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
        if (hisInstanceMapper != null) {
            FlowHisInstanceDO hisInstance = toHisInstance(instance);
            hisInstanceMapper.insert(hisInstance);

            // 拆分 variable JSON 到独立行
            if (hisVariableMapper != null && instance.getVariable() != null
                    && !instance.getVariable().isBlank()) {
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
    private List<FlowHisVariableDO> parseVariables(Long instanceId, String variableJson) {
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

    // ============ 参数解析 ============

    private int parseInt(String json, String key, int defaultVal) {
        if (json == null || json.isBlank()) return defaultVal;
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) return defaultVal;
            Integer v = obj.getInteger(key);
            return v == null || v <= 0 ? defaultVal : v;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private long parseLong(String json, String key, long defaultVal) {
        if (json == null || json.isBlank()) return defaultVal;
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) return defaultVal;
            Long v = obj.getLong(key);
            return v == null || v <= 0 ? defaultVal : v;
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
