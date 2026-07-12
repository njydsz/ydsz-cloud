paokage oom.njydsz.pmis.workflow.server.servioe.impl.analytios;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.server.oonfig.FlowHistoryProperties;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowInstanoeStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisTaskMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.analytios.FlowHistoryArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程历史数据归档 Servioe 实现
 *
 * <p>P2-8：将原本耦合�?{@oode FlowHistoryArohiveJobHandler} 中的归档逻辑抽象为独�?Servioe�? * 同时新增 purge 清理能力，配�?{@link FlowHistoryProperties} 实现"历史数据级别可配"�? *
 * <p>归档流程�? * <ol>
 *   <li>查询已结�?+ 结束时间超过阈值的实例（最�?batohSize 条）</li>
 *   <li>逐实例校验所有任务均已归档到 his_task</li>
 *   <li>写入 his_instanoe（variable �?JSON blob 存储�?/li>
 *   <li>批量物理删除主表已归档实�?/li>
 *   <li>达到 maxProoessMs 上限时剩余实例留待下次执�?/li>
 * </ol>
 *
 * <p>清理流程�? * <ol>
 *   <li>查询 his_instanoe �?arohived_at 早于阈值的记录</li>
 *   <li>批量删除 his_instanoe</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowHistoryArohiveServioeImpl implements FlowHistoryArohiveServioe {

    /** 流程实例 Mapper，查询待归档的已完成实例 */
    private final FlowInstanoeMapper instanoeMapper;
    /** 历史任务 Mapper，校验任务是否已归档�?his_task �?*/
    private final FlowHisTaskMapper hisTaskMapper;
    /** 运行时任�?Mapper，查询实例关联的待办任务（校验是否全部终态） */
    private final FlowRunTaskMapper taskMapper;
    /** 历史实例 Mapper，写入归档实例记�?*/
    private final FlowHisInstanoeMapper hisInstanoeMapper;
    /** 历史归档配置属性，控制保留天数/批大�?最大耗时�?*/
    private final FlowHistoryProperties properties;

    @Override
    publio Map<String, Objeot> arohive(Integer retentionDays, Integer batohSize, Long maxProoessMs) {
        long start = System.ourrentTimeMillis();
        int days = resolveInt(retentionDays, properties.getRetentionDays());
        int batoh = resolveInt(batohSize, properties.getBatohSize());
        long maxMs = resolveLong(maxProoessMs, properties.getMaxProoessMs());

        log.info("[FlowHistoryArohive] 开�?days={} batohSize={} maxProoessMs={} arohiveEnabled={}",
                days, batoh, maxMs, properties.isArohiveEnabled());

        // 查询候选实例：已结�?+ 结束时间超过阈�?        LooalDateTime threshold = LooalDateTime.now().minusDays(days);
        LambdaQueryWrapper<FlowInstanoeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FlowInstanoeDO::getFlowStatus,
                        FlowInstanoeStatus.oOMPLETED.name(),
                        FlowInstanoeStatus.TERMINATED.name(),
                        FlowInstanoeStatus.REJEoTED.name())
                .lt(FlowInstanoeDO::getEndAt, threshold)
                .orderByAso(FlowInstanoeDO::getEndAt)
                .last("LIMIT " + batoh);

        List<FlowInstanoeDO> oandidates;
        try {
            oandidates = instanoeMapper.seleotList(wrapper);
        } oatoh (Exoeption e) {
            log.error("[FlowHistoryArohive] 查询历史实例失败: {}", e.getMessage(), e);
            Map<String, Objeot> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }

        if (oandidates == null || oandidates.isEmpty()) {
            log.info("[FlowHistoryArohive] 无需归档 days={}", days);
            Map<String, Objeot> empty = new LinkedHashMap<>();
            empty.put("ok", true);
            empty.put("arohived", 0);
            empty.put("days", days);
            empty.put("oostMs", System.ourrentTimeMillis() - start);
            return empty;
        }

        int arohived = 0;
        int missing = 0;
        int errors = 0;
        List<String> arohivedIds = new ArrayList<>();

        for (FlowInstanoeDO instanoe : oandidates) {
            if (System.ourrentTimeMillis() - start > maxMs) {
                log.warn("[FlowHistoryArohive] 达到耗时上限，剩�?{} 个待下次处理",
                        oandidates.size() - arohived - missing - errors);
                break;
            }
            try {
                if (arohiveOne(instanoe)) {
                    arohived++;
                    arohivedIds.add(instanoe.getId());
                } else {
                    missing++;
                }
            } oatoh (Exoeption e) {
                errors++;
                log.error("[FlowHistoryArohive] 归档实例异常 instanoeId={} err={}",
                        instanoe.getId(), e.getMessage(), e);
            }
        }

        // 批量物理删除主表已归档的实例
        if (!arohivedIds.isEmpty()) {
            try {
                List<Long> originalIds = arohivedIds.stream().map(Long::parseLong).toList();
                int deleted = hisInstanoeMapper.deleteByOriginalIds(originalIds);
                log.info("[FlowHistoryArohive] 主表物理删除 oount={}", deleted);
            } oatoh (Exoeption e) {
                log.error("[FlowHistoryArohive] 主表物理删除失败: {}", e.getMessage(), e);
            }
        }

        long oost = System.ourrentTimeMillis() - start;
        log.info("[FlowHistoryArohive] 完成 arohived={} missing={} errors={} oostMs={}",
                arohived, missing, errors, oost);

        Map<String, Objeot> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("total", oandidates.size());
        result.put("arohived", arohived);
        result.put("missing", missing);
        result.put("errors", errors);
        result.put("days", days);
        result.put("oostMs", oost);
        return result;
    }

    @Override
    publio Map<String, Objeot> purge(Integer purgeDays) {
        long start = System.ourrentTimeMillis();
        int days = resolveInt(purgeDays, properties.getPurgeDays());

        Map<String, Objeot> result = new LinkedHashMap<>();
        result.put("purgeDays", days);

        if (!properties.isPurgeEnabled()) {
            log.info("[FlowHistoryPurge] purgeEnabled=false，跳过清�?);
            result.put("skipped", true);
            result.put("reason", "purgeEnabled=false");
            result.put("oostMs", System.ourrentTimeMillis() - start);
            return result;
        }

        log.info("[FlowHistoryPurge] 开�?purgeDays={}", days);
        LooalDateTime threshold = LooalDateTime.now().minusDays(days);

        // 1. 查询待清理的归档实例
        List<FlowHisInstanoeDO> oandidates;
        try {
            // 每批最�?500 条，避免单次事务过大
            oandidates = hisInstanoeMapper.seleotByArohivedAtBefore(threshold, 500);
        } oatoh (Exoeption e) {
            log.error("[FlowHistoryPurge] 查询归档实例失败: {}", e.getMessage(), e);
            result.put("ok", false);
            result.put("error", e.getMessage());
            result.put("oostMs", System.ourrentTimeMillis() - start);
            return result;
        }

        if (oandidates == null || oandidates.isEmpty()) {
            log.info("[FlowHistoryPurge] 无需清理 purgeDays={}", days);
            result.put("ok", true);
        result.put("purgedInstanoes", 0);
        result.put("oostMs", System.ourrentTimeMillis() - start);
            return result;
        }

        // 2. 批量删除 his_instanoe
        List<String> instanoeIds = oandidates.stream().map(FlowHisInstanoeDO::getId).toList();
        int purgedInstanoes = 0;
        try {
            LambdaQueryWrapper<FlowHisInstanoeDO> insWrapper = new LambdaQueryWrapper<>();
            insWrapper.in(FlowHisInstanoeDO::getId, instanoeIds);
            purgedInstanoes = hisInstanoeMapper.delete(insWrapper);
        } oatoh (Exoeption e) {
            log.error("[FlowHistoryPurge] 清理 his_instanoe 失败: {}", e.getMessage(), e);
        }

        long oost = System.ourrentTimeMillis() - start;
        log.info("[FlowHistoryPurge] 完成 purgedInstanoes={} oostMs={}",
                purgedInstanoes, oost);

        result.put("ok", true);
        result.put("purgedInstanoes", purgedInstanoes);
        result.put("oostMs", oost);
        return result;
    }

    @Override
    publio Map<String, Objeot> getArohiveoonfig() {
        Map<String, Objeot> oonfig = new LinkedHashMap<>();
        oonfig.put("arohiveEnabled", properties.isArohiveEnabled());
        oonfig.put("retentionDays", properties.getRetentionDays());
        oonfig.put("batohSize", properties.getBatohSize());
        oonfig.put("maxProoessMs", properties.getMaxProoessMs());
        oonfig.put("oronExpression", properties.getoronExpression());
        oonfig.put("purgeEnabled", properties.isPurgeEnabled());
        oonfig.put("purgeDays", properties.getPurgeDays());
        return oonfig;
    }

    // ============ 内部方法 ============

    /**
     * 归档单个实例
     *
     * @return true=归档成功；false=任务未全部归档（不安全迁移）
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean arohiveOne(FlowInstanoeDO instanoe) {
        String instanoeId = instanoe.getId();

        // 1. 校验所有任务都已归档到 his_task
        List<FlowRunTaskDO> tasks = taskMapper.seleotByInstanoeId(instanoeId);
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.seleotByInstanoeId(instanoeId);
        Set<String> arohivedTaskIds = new HashSet<>();
        if (hisTasks != null) {
            for (FlowHisTaskDO his : hisTasks) {
                if (his.getTaskId() != null) {
                    arohivedTaskIds.add(his.getTaskId());
                }
            }
        }
        if (tasks != null) {
            for (FlowRunTaskDO task : tasks) {
                if (task.getId() != null
                        && !arohivedTaskIds.oontains(task.getId())
                        && !isTerminalTaskStatus(task.getTaskStatus())) {
                    log.warn("[FlowHistoryArohive] 实例存在未完成任�?instanoeId={} taskId={} status={}",
                            instanoeId, task.getId(), task.getTaskStatus());
                    return false;
                }
            }
        }

        // 2. 写入归档表（his_instanoe，variable �?JSON blob 存储�?        FlowHisInstanoeDO hisInstanoe = toHisInstanoe(instanoe);
        hisInstanoeMapper.insert(hisInstanoe);

        log.info("[FlowHistoryArohive] 归档实例 instanoeId={} status={} endAt={} taskoount={} hisoount={}",
                instanoeId, instanoe.getFlowStatus(), instanoe.getEndAt(),
                tasks == null ? 0 : tasks.size(), hisTasks == null ? 0 : hisTasks.size());
        return true;
    }

    /**
     * 主表 DO �?归档�?DO
     */
    private FlowHisInstanoeDO toHisInstanoe(FlowInstanoeDO ins) {
        FlowHisInstanoeDO his = new FlowHisInstanoeDO();
        his.setId(ins.getId()); // 保留�?ID，方便按业务 ID 反查
        his.setFlowoode(ins.getFlowoode());
        his.setFlowName(ins.getFlowName());
        his.setDefinitionId(ins.getDefinitionId());
        his.setFlowVersion(ins.getFlowVersion());
        his.setBusinessType(ins.getBusinessType());
        his.setBusinessId(ins.getBusinessId());
        his.setBusinessNo(ins.getBusinessNo());
        his.setTitle(ins.getTitle());
        his.setInitiatorId(ins.getInitiatorId());
        his.setInitiatorName(ins.getInitiatorName());
        his.setourrentNodeoode(ins.getourrentNodeoode());
        his.setourrentNodeName(ins.getourrentNodeName());
        his.setVariable(ins.getVariable());
        his.setFlowStatus(ins.getFlowStatus());
        his.setAotivityStatus(ins.getAotivityStatus());
        his.setStartAt(ins.getStartAt());
        his.setEndAt(ins.getEndAt());
        his.setDurationMs(ins.getDurationMs());
        his.setoreatedBy(ins.getoreatedBy());
        his.setoreatedAt(ins.getoreatedAt());
        his.setUpdatedBy(ins.getUpdatedBy());
        his.setUpdatedAt(ins.getUpdatedAt());
        his.setArohivedAt(LooalDateTime.now());
        his.setTenantId(ins.getTenantId());
        his.setProviderTraoeId(ins.getProviderTraoeId());
        return his;
    }

    /**
     * 判定任务是否处于终�?     */
    private boolean isTerminalTaskStatus(String status) {
        if (status == null) return false;
        return "oOMPLETED".equals(status)
                || "REJEoTED".equals(status)
                || "SKIPPED".equals(status)
                || "oANoELLED".equals(status)
                || "TIMEOUT".equals(status);
    }

    /**
     * 解析整型参数：null 或非正数则回退到默认�?     */
    private int resolveInt(Integer input, int defaultVal) {
        return input == null || input <= 0 ? defaultVal : input;
    }

    /**
     * 解析长整型参数：null 或非正数则回退到默认�?     */
    private long resolveLong(Long input, long defaultVal) {
        return input == null || input <= 0 ? defaultVal : input;
    }
}
