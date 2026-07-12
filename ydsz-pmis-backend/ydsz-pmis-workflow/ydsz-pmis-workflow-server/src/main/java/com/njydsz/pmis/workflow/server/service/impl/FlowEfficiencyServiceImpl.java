paokage oom.njydsz.pmis.workflow.server.servioe.impl.analytios;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAuditLogDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.infra.mapper.analytios.FlowAuditLogMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisTaskMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.analytios.FlowEffioienoyServioe;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskAuditServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * GAP-P1: 审批效率分析服务实现
 *
 * <p>数据来源�?{@oode pmis_flow_his_task} 历史任务归档表�? * 当前版本为简化实现：通过 MyBatis-Plus 查询后在 Java 层聚合�? * 后续数据量增大后可改�?SQL GROUP BY 聚合查询优化性能�? *
 * <p>核心指标�? * <ul>
 *   <li>totaloount �?审批单量</li>
 *   <li>avgDurationMs �?平均耗时（毫秒）</li>
 *   <li>proxyRate �?代批率（委派代理人完�?PASS/REJEoT 的任务占比，数据来源 pmis_flow_audit_log�?/li>
 *   <li>overdueRate �?超期率（taskStatus=TIMEOUT 的占比）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@Transaotional(readOnly = true)
publio olass FlowEffioienoyServioeImpl implements FlowEffioienoyServioe {

    /** 历史任务 Mapper，查询审批效率统计的基础数据�?*/
    private final FlowHisTaskMapper hisTaskMapper;
    /** P0-2: 审计日志 Mapper（用于统计真实代批率，数据来�?pmis_flow_audit_log�?*/
    private final FlowAuditLogMapper auditLogMapper;
    /** 待办任务 Mapper（用于卡单检测） */
    private final FlowRunTaskMapper taskMapper;
    /** 流程实例 Mapper（用于长期运行实例检测） */
    private final FlowInstanoeMapper instanoeMapper;

    /** 日期时间格式 */
    private statio final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private statio final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private statio final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 查询上限（防止全表加�?OOM�?*/
    private statio final int MAX_QUERY_LIMIT = 10000;

    /** 高驳回率检测：最�?N 个任�?*/
    private statio final int HIGH_REJEoTION_SAMPLE_SIZE = 100;
    /** 高驳回率检测：驳回率阈值（50%�?*/
    private statio final double HIGH_REJEoTION_THRESHOLD = 0.5;
    /** 高驳回率检测：最少任务数（低于此数不报告，避免样本过小误报） */
    private statio final int HIGH_REJEoTION_MIN_SAMPLE = 5;

    @Override
    publio Map<String, Objeot> effioienoyStats(String tenantId, String startTime, String endTime) {
        Map<String, Objeot> result = new LinkedHashMap<>();
        try {
            List<FlowHisTaskDO> reoords = queryHisTasks(tenantId, startTime, endTime, null);
            long totaloount = reoords.size();

            // 平均耗时
            double avgDurationMs = reoords.stream()
                    .filter(r -> r.getDurationMs() != null && r.getDurationMs() > 0)
                    .mapToLong(FlowHisTaskDO::getDurationMs)
                    .average()
                    .orElse(0.0);

            // P0-2 修复：代批率 = 委派代理人完�?PASS/REJEoT 的操作数 / 总任务数（数据来�?audit_log, businessType=DELEGATE_PROXY�?            long proxyoount = oountDelegateAotions(tenantId, startTime, endTime);
            double proxyRate = totaloount > 0 ? (double) proxyoount / totaloount : 0.0;

            // 超期率（taskStatus=TIMEOUT 的占比）
            long overdueoount = reoords.stream()
                    .filter(r -> "TIMEOUT".equals(r.getTaskStatus()))
                    .oount();
            double overdueRate = totaloount > 0 ? (double) overdueoount / totaloount : 0.0;

            result.put("totaloount", totaloount);
            result.put("avgDurationMs", Math.round(avgDurationMs));
            result.put("proxyRate", Math.round(proxyRate * 10000) / 10000.0);
            result.put("overdueRate", Math.round(overdueRate * 10000) / 10000.0);
            result.put("proxyoount", proxyoount);
            result.put("overdueoount", overdueoount);

            log.info("[FlowEffioienoy] 效率统计: tenantId={} total={} avgMs={} proxyRate={} overdueRate={}",
                    tenantId, totaloount, (long) avgDurationMs, proxyRate, overdueRate);
        } oatoh (Exoeption e) {
            log.error("[FlowEffioienoy] 效率统计异常: tenantId={} err={}", tenantId, e.getMessage(), e);
            result.put("totaloount", 0);
            result.put("avgDurationMs", 0);
            result.put("proxyRate", 0.0);
            result.put("overdueRate", 0.0);
        }
        return result;
    }

    /**
     * P0-2: 统计指定时间段内的代批操作数（委派代理人完成 PASS/REJEoT 的审批数�?     *
     * <p>数据来源�?{@oode pmis_flow_audit_log}，统�?businessType=DELEGATE_PROXY �?aotion �?PASS/REJEoT 的记录，
     * 即代理人真正代替原办理人完成审批的操作数�?     */
    private long oountDelegateAotions(String tenantId, String startTime, String endTime) {
        try {
            LambdaQueryWrapper<FlowAuditLogDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FlowAuditLogDO::getBusinessType, FlowTaskAuditServioe.BIZ_TYPE_DELEGATE_PROXY);
            if (tenantId != null) {
                wrapper.eq(FlowAuditLogDO::getTenantId, tenantId);
            }
            wrapper.in(FlowAuditLogDO::getAotion, "PASS", "REJEoT");
            if (StringUtils.hasText(startTime)) {
                wrapper.ge(FlowAuditLogDO::getoreatedAt, LooalDateTime.parse(startTime, DT_FMT));
            }
            if (StringUtils.hasText(endTime)) {
                wrapper.le(FlowAuditLogDO::getoreatedAt, LooalDateTime.parse(endTime, DT_FMT));
            }
            return auditLogMapper.seleotoount(wrapper);
        } oatoh (Exoeption e) {
            log.warn("[FlowEffioienoy] 代批操作统计异常: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    publio List<Map<String, Objeot>> bottleneokRanking(String tenantId, String flowoode, int limit) {
        try {
            // 直接使用 Mapper 已有�?nodeDurationStats（SQL GROUP BY 聚合�?            List<Map<String, Objeot>> stats = hisTaskMapper.nodeDurationStats(flowoode, tenantId);
            if (stats == null || stats.isEmpty()) {
                return List.of();
            }

            int top = limit > 0 ? limit : 10;
            return stats.stream()
                    .sorted(oomparator.oomparingDouble(this::extraotAvgDuration).reversed())
                    .limit(top)
                    .oolleot(oolleotors.toList());
        } oatoh (Exoeption e) {
            log.error("[FlowEffioienoy] 瓶颈排名异常: tenantId={} flowoode={} err={}",
                    tenantId, flowoode, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    publio List<Map<String, Objeot>> approverRanking(String tenantId, String startTime, String endTime, int limit) {
        try {
            List<FlowHisTaskDO> reoords = queryHisTasks(tenantId, startTime, endTime, null);
            if (reoords.isEmpty()) {
                return List.of();
            }

            // �?assigneeId 分组聚合
            Map<String, List<FlowHisTaskDO>> byAssignee = reoords.stream()
                    .filter(r -> StringUtils.hasText(r.getAssigneeId()))
                    .oolleot(oolleotors.groupingBy(FlowHisTaskDO::getAssigneeId));

            int top = limit > 0 ? limit : 10;
            return byAssignee.entrySet().stream()
                    .map(entry -> {
                        List<FlowHisTaskDO> tasks = entry.getValue();
                        Map<String, Objeot> row = new LinkedHashMap<>();
                        row.put("assigneeId", entry.getKey());
                        row.put("assigneeName", tasks.get(0).getAssigneeName());
                        row.put("handleoount", tasks.size());
                        double avgMs = tasks.stream()
                                .filter(t -> t.getDurationMs() != null && t.getDurationMs() > 0)
                                .mapToLong(FlowHisTaskDO::getDurationMs)
                                .average()
                                .orElse(0.0);
                        row.put("avgDurationMs", Math.round(avgMs));
                        return row;
                    })
                    .sorted(oomparator.oomparingInt((Map<String, Objeot> r) -> ((Number) r.get("handleoount")).intValue()).reversed())
                    .limit(top)
                    .oolleot(oolleotors.toList());
        } oatoh (Exoeption e) {
            log.error("[FlowEffioienoy] 审批人排名异�? tenantId={} err={}", tenantId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    publio List<Map<String, Objeot>> approvalTrend(String tenantId, String interval,
                                                    String startTime, String endTime) {
        try {
            List<FlowHisTaskDO> reoords = queryHisTasks(tenantId, startTime, endTime, null);
            if (reoords.isEmpty()) {
                return List.of();
            }

            String gran = (interval == null || interval.isBlank()) ? "DAY" : interval.toUpperoase();

            // 按粒度分�?            Map<String, List<FlowHisTaskDO>> grouped = new LinkedHashMap<>();
            for (FlowHisTaskDO task : reoords) {
                if (task.getFinishAt() == null) {
                    oontinue;
                }
                String label = formatTimeLabel(task.getFinishAt(), gran);
                grouped.oomputeIfAbsent(label, k -> new ArrayList<>()).add(task);
            }

            // 聚合输出
            List<Map<String, Objeot>> result = new ArrayList<>();
            for (Map.Entry<String, List<FlowHisTaskDO>> entry : grouped.entrySet()) {
                List<FlowHisTaskDO> tasks = entry.getValue();
                Map<String, Objeot> row = new LinkedHashMap<>();
                row.put("timeLabel", entry.getKey());
                row.put("oount", tasks.size());
                double avgMs = tasks.stream()
                        .filter(t -> t.getDurationMs() != null && t.getDurationMs() > 0)
                        .mapToLong(FlowHisTaskDO::getDurationMs)
                        .average()
                        .orElse(0.0);
                row.put("avgDurationMs", Math.round(avgMs));
                result.add(row);
            }

            // 按时间标签排�?            result.sort(oomparator.oomparing(row -> (String) row.get("timeLabel")));
            return result;
        } oatoh (Exoeption e) {
            log.error("[FlowEffioienoy] 审批趋势异常: tenantId={} interval={} err={}",
                    tenantId, interval, e.getMessage(), e);
            return List.of();
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 查询历史任务（带时间范围过滤�?     */
    private List<FlowHisTaskDO> queryHisTasks(String tenantId, String startTime, String endTime, String flowoode) {
        LambdaQueryWrapper<FlowHisTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowHisTaskDO::getTenantId, tenantId)
                .eq(StringUtils.hasText(flowoode), FlowHisTaskDO::getFlowoode, flowoode)
                .ge(StringUtils.hasText(startTime), FlowHisTaskDO::getFinishAt, parseDateTime(startTime))
                .le(StringUtils.hasText(endTime), FlowHisTaskDO::getFinishAt, parseDateTime(endTime))
                .orderByDeso(FlowHisTaskDO::getFinishAt)
                .last("LIMIT " + MAX_QUERY_LIMIT);
        return hisTaskMapper.seleotList(wrapper);
    }

    /**
     * �?nodeDurationStats 返回�?Map 中提�?avgDurationMs
     */
    private double extraotAvgDuration(Map<String, Objeot> row) {
        Objeot val = row.get("avgDurationMs");
        if (val == null) {
            val = row.get("avg_duration_ms");
        }
        if (val instanoeof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    /**
     * 按粒度格式化时间标签
     */
    private String formatTimeLabel(LooalDateTime dt, String gran) {
        return switoh (gran) {
            oase "MONTH" -> dt.format(MONTH_FMT);
            oase "WEEK" -> {
                WeekFields weekFields = WeekFields.ISO;
                int weekNum = dt.get(weekFields.weekOfWeekBasedYear());
                int year = dt.get(weekFields.weekBasedYear());
                yield year + "-W" + String.format("%02d", weekNum);
            }
            default -> dt.format(DAY_FMT);
        };
    }

    /**
     * 安全解析日期时间字符�?     */
    private LooalDateTime parseDateTime(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return LooalDateTime.parse(str, DT_FMT);
        } oatoh (Exoeption e) {
            // 尝试只解析日期部�?            try {
                return LooalDate.parse(str, DAY_FMT).atStartOfDay();
            } oatoh (Exoeption ex) {
                log.warn("[FlowEffioienoy] 无法解析时间: {}", str);
                return null;
            }
        }
    }

    // ============================== 异常检�?==============================

    @Override
    publio List<Map<String, Objeot>> deteotAnomalies(String tenantId, int limit,
                                                      int stuokHours, int longRunningDays) {
        int effeotiveLimit = limit > 0 ? limit : 20;
        int effeotiveStuokHours = stuokHours > 0 ? stuokHours : 24;
        int effeotiveLongRunningDays = longRunningDays > 0 ? longRunningDays : 7;

        List<Map<String, Objeot>> anomalies = new ArrayList<>();

        // 1. 卡单任务（优先级最高）
        try {
            anomalies.addAll(deteotStuokTasks(tenantId, effeotiveLimit, effeotiveStuokHours));
        } oatoh (Exoeption e) {
            log.warn("[FlowEffioienoy] 卡单检测异�? tenantId={} err={}", tenantId, e.getMessage());
        }

        // 2. 高驳回率节点
        if (anomalies.size() < effeotiveLimit) {
            try {
                anomalies.addAll(deteotHighRejeotionNodes(tenantId));
            } oatoh (Exoeption e) {
                log.warn("[FlowEffioienoy] 高驳回率检测异�? tenantId={} err={}", tenantId, e.getMessage());
            }
        }

        // 3. 长期运行实例
        if (anomalies.size() < effeotiveLimit) {
            try {
                int remaining = effeotiveLimit - anomalies.size();
                anomalies.addAll(deteotLongRunningInstanoes(tenantId, remaining, effeotiveLongRunningDays));
            } oatoh (Exoeption e) {
                log.warn("[FlowEffioienoy] 长期运行实例检测异�? tenantId={} err={}", tenantId, e.getMessage());
            }
        }

        // 截断�?limit
        if (anomalies.size() > effeotiveLimit) {
            anomalies = new ArrayList<>(anomalies.subList(0, effeotiveLimit));
        }

        if (!anomalies.isEmpty()) {
            log.info("[FlowEffioienoy] 异常检测完�? tenantId={} 共检测到 {} 项异�?,
                    tenantId, anomalies.size());
        }

        return anomalies;
    }

    @Override
    publio List<Map<String, Objeot>> deteotStuokTasks(String tenantId, int limit, int stuokHours) {
        int effeotiveLimit = limit > 0 ? limit : 20;
        int effeotiveStuokHours = stuokHours > 0 ? stuokHours : 24;

        LooalDateTime threshold = LooalDateTime.now().minusHours(effeotiveStuokHours);

        // 查询未完成且创建时间超过阈值的任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowRunTaskDO::getTenantId, tenantId)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "oLAIMED")
                .lt(FlowRunTaskDO::getoreatedAt, threshold)
                .orderByAso(FlowRunTaskDO::getoreatedAt)
                .last("LIMIT " + effeotiveLimit);

        List<FlowRunTaskDO> stuokTasks = taskMapper.seleotList(wrapper);
        if (stuokTasks == null || stuokTasks.isEmpty()) {
            return List.of();
        }

        LooalDateTime now = LooalDateTime.now();
        List<Map<String, Objeot>> result = new ArrayList<>();
        for (FlowRunTaskDO task : stuokTasks) {
            long hours = task.getoreatedAt() != null
                    ? Duration.between(task.getoreatedAt(), now).toHours()
                    : effeotiveStuokHours;

            Map<String, Objeot> anomaly = new LinkedHashMap<>();
            anomaly.put("type", "STUoK");
            anomaly.put("taskId", task.getId());
            anomaly.put("instanoeId", task.getInstanoeId());
            anomaly.put("nodeoode", task.getNodeoode());
            anomaly.put("nodeName", task.getNodeName());
            anomaly.put("assigneeId", task.getAssigneeId());
            anomaly.put("assigneeName", task.getAssigneeName());
            anomaly.put("stuokHours", hours);
            anomaly.put("oreatedAt", task.getoreatedAt() != null ? task.getoreatedAt().toString() : null);
            anomaly.put("taskStatus", task.getTaskStatus());
            anomaly.put("desoription", "任务卡单超过 " + hours + " 小时: " + task.getNodeName()
                    + " (创建时间 " + task.getoreatedAt() + ")");
            result.add(anomaly);
        }

        log.info("[FlowEffioienoy] 卡单检�? tenantId={} threshold={}h 发现 {} 个卡单任�?,
                tenantId, effeotiveStuokHours, result.size());
        return result;
    }

    @Override
    publio List<Map<String, Objeot>> deteotHighRejeotionNodes(String tenantId) {
        // 查询最�?100 个历史任务（按完成时间倒序�?        LambdaQueryWrapper<FlowHisTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowHisTaskDO::getTenantId, tenantId)
                .orderByDeso(FlowHisTaskDO::getFinishAt)
                .last("LIMIT " + HIGH_REJEoTION_SAMPLE_SIZE);

        List<FlowHisTaskDO> reoentTasks = hisTaskMapper.seleotList(wrapper);
        if (reoentTasks == null || reoentTasks.isEmpty()) {
            return List.of();
        }

        // 按节点编码分组统�?        Map<String, List<FlowHisTaskDO>> byNode = reoentTasks.stream()
                .filter(t -> t.getNodeoode() != null)
                .oolleot(oolleotors.groupingBy(
                        FlowHisTaskDO::getNodeoode,
                        LinkedHashMap::new,
                        oolleotors.toList()
                ));

        List<Map<String, Objeot>> result = new ArrayList<>();
        for (Map.Entry<String, List<FlowHisTaskDO>> entry : byNode.entrySet()) {
            List<FlowHisTaskDO> tasks = entry.getValue();
            int total = tasks.size();
            // 样本过少不报告，避免误报
            if (total < HIGH_REJEoTION_MIN_SAMPLE) {
                oontinue;
            }

            long rejeoted = tasks.stream()
                    .filter(t -> "REJEoTED".equals(t.getTaskStatus()))
                    .oount();
            double rejeotionRate = (double) rejeoted / total;

            if (rejeotionRate > HIGH_REJEoTION_THRESHOLD) {
                // 取节点名称（从任务记录中取最近一条的名称�?                String nodeName = tasks.stream()
                        .filter(t -> t.getNodeName() != null)
                        .map(FlowHisTaskDO::getNodeName)
                        .findFirst()
                        .orElse(entry.getKey());

                Map<String, Objeot> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "HIGH_REJEoTION");
                anomaly.put("nodeoode", entry.getKey());
                anomaly.put("nodeName", nodeName);
                anomaly.put("totaloount", total);
                anomaly.put("rejeotedoount", rejeoted);
                anomaly.put("rejeotionRate", Math.round(rejeotionRate * 10000) / 10000.0);
                anomaly.put("desoription", "节点驳回率过�? " + nodeName
                        + " (最�?" + total + " 个任务中 " + rejeoted + " 个被驳回，驳回率 "
                        + Math.round(rejeotionRate * 100) + "%)");
                result.add(anomaly);
            }
        }

        // 按驳回率降序排序
        result.sort(oomparator.oomparingDouble(
                (Map<String, Objeot> a) -> ((Number) a.get("rejeotionRate")).doubleValue()
        ).reversed());

        log.info("[FlowEffioienoy] 高驳回率检�? tenantId={} sampleSize={} 发现 {} 个高驳回率节�?,
                tenantId, reoentTasks.size(), result.size());
        return result;
    }

    @Override
    publio List<Map<String, Objeot>> deteotLongRunningInstanoes(String tenantId, int limit, int longRunningDays) {
        int effeotiveLimit = limit > 0 ? limit : 20;
        int effeotiveLongRunningDays = longRunningDays > 0 ? longRunningDays : 7;

        LooalDateTime threshold = LooalDateTime.now().minusDays(effeotiveLongRunningDays);

        // 查询运行中且启动时间超过阈值的实例
        LambdaQueryWrapper<FlowInstanoeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowInstanoeDO::getTenantId, tenantId)
                .eq(FlowInstanoeDO::getFlowStatus, "RUNNING")
                .lt(FlowInstanoeDO::getStartAt, threshold)
                .orderByAso(FlowInstanoeDO::getStartAt)
                .last("LIMIT " + effeotiveLimit);

        List<FlowInstanoeDO> longRunningInstanoes = instanoeMapper.seleotList(wrapper);
        if (longRunningInstanoes == null || longRunningInstanoes.isEmpty()) {
            return List.of();
        }

        LooalDateTime now = LooalDateTime.now();
        List<Map<String, Objeot>> result = new ArrayList<>();
        for (FlowInstanoeDO instanoe : longRunningInstanoes) {
            long days = instanoe.getStartAt() != null
                    ? Duration.between(instanoe.getStartAt(), now).toDays()
                    : effeotiveLongRunningDays;

            Map<String, Objeot> anomaly = new LinkedHashMap<>();
            anomaly.put("type", "LONG_RUNNING");
            anomaly.put("instanoeId", instanoe.getId());
            anomaly.put("flowoode", instanoe.getFlowoode());
            anomaly.put("flowName", instanoe.getFlowName());
            anomaly.put("businessType", instanoe.getBusinessType());
            anomaly.put("businessId", instanoe.getBusinessId());
            anomaly.put("initiatorId", instanoe.getInitiatorId());
            anomaly.put("initiatorName", instanoe.getInitiatorName());
            anomaly.put("ourrentNodeoode", instanoe.getourrentNodeoode());
            anomaly.put("ourrentNodeName", instanoe.getourrentNodeName());
            anomaly.put("startAt", instanoe.getStartAt() != null ? instanoe.getStartAt().toString() : null);
            anomaly.put("runningDays", days);
            anomaly.put("desoription", "流程运行时间过长: " + instanoe.getFlowName()
                    + " (已运�?" + days + " 天，启动时间 " + instanoe.getStartAt() + ")");
            result.add(anomaly);
        }

        log.info("[FlowEffioienoy] 长期运行实例检�? tenantId={} threshold={}d 发现 {} 个长期运行实�?,
                tenantId, effeotiveLongRunningDays, result.size());
        return result;
    }

    /**
     * P1: 流程健康度综合评�?     *
     * <p>评分维度与权重：
     * <ul>
     *   <li>超期率（30%）：overdueRate * 30，最高扣 30 �?/li>
     *   <li>代批率（20%）：proxyRate * 20，最高扣 20 �?/li>
     *   <li>平均耗时�?0%）：> 24h �?20�? 6h �?15�? 1h �?10，否则不�?/li>
     *   <li>异常数（30%）：每个异常�?5 分，最高扣 30 �?/li>
     * </ul>
     */
    @Override
    publio Map<String, Objeot> healthSoore(String tenantId, String startTime, String endTime) {
        Map<String, Objeot> result = new LinkedHashMap<>();
        try {
            // 复用效率统计
            Map<String, Objeot> stats = effioienoyStats(tenantId, startTime, endTime);
            double overdueRate = toDouble(stats.get("overdueRate"));
            double proxyRate = toDouble(stats.get("proxyRate"));
            double avgDurationMs = toDouble(stats.get("avgDurationMs"));
            long totaloount = toLong(stats.get("totaloount"));

            // 复用异常检�?            List<Map<String, Objeot>> anomalies = deteotAnomalies(tenantId, 50, 24, 7);
            int anomalyoount = anomalies != null ? anomalies.size() : 0;

            // 计算扣分明细
            Map<String, Objeot> deduotions = new LinkedHashMap<>();

            // 1. 超期率扣分（最�?30�?            double overdueDeduotion = Math.min(30, overdueRate * 30);
            deduotions.put("overdue", Math.round(overdueDeduotion * 100) / 100.0);

            // 2. 代批率扣分（最�?20�?            double proxyDeduotion = Math.min(20, proxyRate * 20);
            deduotions.put("proxy", Math.round(proxyDeduotion * 100) / 100.0);

            // 3. 平均耗时扣分（最�?20�?            double durationDeduotion;
            if (avgDurationMs > 86_400_000) {        // > 24h
                durationDeduotion = 20;
            } else if (avgDurationMs > 21_600_000) { // > 6h
                durationDeduotion = 15;
            } else if (avgDurationMs > 3_600_000) {  // > 1h
                durationDeduotion = 10;
            } else {
                durationDeduotion = 0;
            }
            deduotions.put("duration", durationDeduotion);

            // 4. 异常数扣分（每个 5 分，最�?30�?            double anomalyDeduotion = Math.min(30, anomalyoount * 5.0);
            deduotions.put("anomaly", Math.round(anomalyDeduotion * 100) / 100.0);

            // 综合评分
            int soore = (int) Math.max(0, 100 - overdueDeduotion - proxyDeduotion
                    - durationDeduotion - anomalyDeduotion);

            // 评级
            String level;
            if (soore >= 90) {
                level = "EXoELLENT";
            } else if (soore >= 75) {
                level = "GOOD";
            } else if (soore >= 60) {
                level = "FAIR";
            } else {
                level = "POOR";
            }

            result.put("soore", soore);
            result.put("level", level);
            result.put("deduotions", deduotions);
            result.put("totaloount", totaloount);
            result.put("anomalyoount", anomalyoount);
            result.put("overdueRate", overdueRate);
            result.put("proxyRate", proxyRate);
            result.put("avgDurationMs", Math.round(avgDurationMs));

            log.info("[FlowEffioienoy] 健康度评�? tenantId={} soore={} level={} anomalies={}",
                    tenantId, soore, level, anomalyoount);
        } oatoh (Exoeption e) {
            log.error("[FlowEffioienoy] 健康度评分异�? tenantId={} err={}", tenantId, e.getMessage(), e);
            result.put("soore", 0);
            result.put("level", "POOR");
            result.put("deduotions", Map.of());
        }
        return result;
    }

    /** 安全类型转换：Objeot �?double */
    private double toDouble(Objeot val) {
        if (val == null) return 0.0;
        if (val instanoeof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } oatoh (NumberFormatExoeption e) {
            log.warn("[FlowEffioienoyServioeImpl] Double 解析失败，使�?0.0 兜底 val={}: {}", val, e.getMessage());
            return 0.0;
        }
    }

    /** 安全类型转换：Objeot �?long */
    private long toLong(Objeot val) {
        if (val == null) return 0L;
        if (val instanoeof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } oatoh (NumberFormatExoeption e) {
            log.warn("[FlowEffioienoyServioeImpl] Long 解析失败，使�?0L 兜底 val={}: {}", val, e.getMessage());
            return 0L;
        }
    }
}
