package com.njydsz.pmis.workflow.service.impl.analytics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.entity.delegate.FlowDelegateLogDO;
import com.njydsz.pmis.workflow.entity.instance.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.delegate.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.analytics.FlowEfficiencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GAP-P1: 审批效率分析服务实现
 *
 * <p>数据来源为 {@code pmis_flow_his_task} 历史任务归档表。
 * 当前版本为简化实现：通过 MyBatis-Plus 查询后在 Java 层聚合。
 * 后续数据量增大后可改为 SQL GROUP BY 聚合查询优化性能。
 *
 * <p>核心指标：
 * <ul>
 *   <li>totalCount — 审批单量</li>
 *   <li>avgDurationMs — 平均耗时（毫秒）</li>
 *   <li>proxyRate — 代批率（委派代理人完成 PASS/REJECT 的任务占比，数据来源 pmis_flow_delegate_log）</li>
 *   <li>overdueRate — 超期率（taskStatus=TIMEOUT 的占比）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowEfficiencyServiceImpl implements FlowEfficiencyService {

    /** 历史任务 Mapper，查询审批效率统计的基础数据源 */
    private final FlowHisTaskMapper hisTaskMapper;
    /** P0-2: 委派代理日志 Mapper（用于统计真实代批率） */
    private final FlowDelegateLogMapper delegateLogMapper;
    /** 待办任务 Mapper（用于卡单检测） */
    private final FlowRunTaskMapper taskMapper;
    /** 流程实例 Mapper（用于长期运行实例检测） */
    private final FlowInstanceMapper instanceMapper;

    /** 日期时间格式 */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 查询上限（防止全表加载 OOM） */
    private static final int MAX_QUERY_LIMIT = 10000;

    /** 高驳回率检测：最近 N 个任务 */
    private static final int HIGH_REJECTION_SAMPLE_SIZE = 100;
    /** 高驳回率检测：驳回率阈值（50%） */
    private static final double HIGH_REJECTION_THRESHOLD = 0.5;
    /** 高驳回率检测：最少任务数（低于此数不报告，避免样本过小误报） */
    private static final int HIGH_REJECTION_MIN_SAMPLE = 5;

    @Override
    public Map<String, Object> efficiencyStats(String tenantId, String startTime, String endTime) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<FlowHisTaskDO> records = queryHisTasks(tenantId, startTime, endTime, null);
            long totalCount = records.size();

            // 平均耗时
            double avgDurationMs = records.stream()
                    .filter(r -> r.getDurationMs() != null && r.getDurationMs() > 0)
                    .mapToLong(FlowHisTaskDO::getDurationMs)
                    .average()
                    .orElse(0.0);

            // P0-2 修复：代批率 = 委派代理人完成 PASS/REJECT 的操作数 / 总任务数（数据来源 delegate_log）
            long proxyCount = countDelegateActions(tenantId, startTime, endTime);
            double proxyRate = totalCount > 0 ? (double) proxyCount / totalCount : 0.0;

            // 超期率（taskStatus=TIMEOUT 的占比）
            long overdueCount = records.stream()
                    .filter(r -> "TIMEOUT".equals(r.getTaskStatus()))
                    .count();
            double overdueRate = totalCount > 0 ? (double) overdueCount / totalCount : 0.0;

            result.put("totalCount", totalCount);
            result.put("avgDurationMs", Math.round(avgDurationMs));
            result.put("proxyRate", Math.round(proxyRate * 10000) / 10000.0);
            result.put("overdueRate", Math.round(overdueRate * 10000) / 10000.0);
            result.put("proxyCount", proxyCount);
            result.put("overdueCount", overdueCount);

            log.info("[FlowEfficiency] 效率统计: tenantId={} total={} avgMs={} proxyRate={} overdueRate={}",
                    tenantId, totalCount, (long) avgDurationMs, proxyRate, overdueRate);
        } catch (Exception e) {
            log.error("[FlowEfficiency] 效率统计异常: tenantId={} err={}", tenantId, e.getMessage(), e);
            result.put("totalCount", 0);
            result.put("avgDurationMs", 0);
            result.put("proxyRate", 0.0);
            result.put("overdueRate", 0.0);
        }
        return result;
    }

    /**
     * P0-2: 统计指定时间段内的代批操作数（委派代理人完成 PASS/REJECT 的审批数）
     *
     * <p>数据来源为 {@code pmis_flow_delegate_log}，仅统计 action 为 PASS/REJECT 的记录，
     * 即代理人真正代替原办理人完成审批的操作数。
     */
    private long countDelegateActions(String tenantId, String startTime, String endTime) {
        try {
            LambdaQueryWrapper<FlowDelegateLogDO> wrapper = new LambdaQueryWrapper<>();
            if (tenantId != null) {
                wrapper.eq(FlowDelegateLogDO::getTenantId, tenantId);
            }
            wrapper.in(FlowDelegateLogDO::getAction, "PASS", "REJECT");
            if (StringUtils.hasText(startTime)) {
                wrapper.ge(FlowDelegateLogDO::getCreatedAt, LocalDateTime.parse(startTime, DT_FMT));
            }
            if (StringUtils.hasText(endTime)) {
                wrapper.le(FlowDelegateLogDO::getCreatedAt, LocalDateTime.parse(endTime, DT_FMT));
            }
            return delegateLogMapper.selectCount(wrapper);
        } catch (Exception e) {
            log.warn("[FlowEfficiency] 代批操作统计异常: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public List<Map<String, Object>> bottleneckRanking(String tenantId, String flowCode, int limit) {
        try {
            // 直接使用 Mapper 已有的 nodeDurationStats（SQL GROUP BY 聚合）
            List<Map<String, Object>> stats = hisTaskMapper.nodeDurationStats(flowCode, tenantId);
            if (stats == null || stats.isEmpty()) {
                return List.of();
            }

            int top = limit > 0 ? limit : 10;
            return stats.stream()
                    .sorted(Comparator.comparingDouble(this::extractAvgDuration).reversed())
                    .limit(top)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[FlowEfficiency] 瓶颈排名异常: tenantId={} flowCode={} err={}",
                    tenantId, flowCode, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> approverRanking(String tenantId, String startTime, String endTime, int limit) {
        try {
            List<FlowHisTaskDO> records = queryHisTasks(tenantId, startTime, endTime, null);
            if (records.isEmpty()) {
                return List.of();
            }

            // 按 assigneeId 分组聚合
            Map<String, List<FlowHisTaskDO>> byAssignee = records.stream()
                    .filter(r -> StringUtils.hasText(r.getAssigneeId()))
                    .collect(Collectors.groupingBy(FlowHisTaskDO::getAssigneeId));

            int top = limit > 0 ? limit : 10;
            return byAssignee.entrySet().stream()
                    .map(entry -> {
                        List<FlowHisTaskDO> tasks = entry.getValue();
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("assigneeId", entry.getKey());
                        row.put("assigneeName", tasks.get(0).getAssigneeName());
                        row.put("handleCount", tasks.size());
                        double avgMs = tasks.stream()
                                .filter(t -> t.getDurationMs() != null && t.getDurationMs() > 0)
                                .mapToLong(FlowHisTaskDO::getDurationMs)
                                .average()
                                .orElse(0.0);
                        row.put("avgDurationMs", Math.round(avgMs));
                        return row;
                    })
                    .sorted(Comparator.comparingInt((Map<String, Object> r) -> ((Number) r.get("handleCount")).intValue()).reversed())
                    .limit(top)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[FlowEfficiency] 审批人排名异常: tenantId={} err={}", tenantId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> approvalTrend(String tenantId, String interval,
                                                    String startTime, String endTime) {
        try {
            List<FlowHisTaskDO> records = queryHisTasks(tenantId, startTime, endTime, null);
            if (records.isEmpty()) {
                return List.of();
            }

            String gran = (interval == null || interval.isBlank()) ? "DAY" : interval.toUpperCase();

            // 按粒度分组
            Map<String, List<FlowHisTaskDO>> grouped = new LinkedHashMap<>();
            for (FlowHisTaskDO task : records) {
                if (task.getFinishAt() == null) {
                    continue;
                }
                String label = formatTimeLabel(task.getFinishAt(), gran);
                grouped.computeIfAbsent(label, k -> new ArrayList<>()).add(task);
            }

            // 聚合输出
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, List<FlowHisTaskDO>> entry : grouped.entrySet()) {
                List<FlowHisTaskDO> tasks = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("timeLabel", entry.getKey());
                row.put("count", tasks.size());
                double avgMs = tasks.stream()
                        .filter(t -> t.getDurationMs() != null && t.getDurationMs() > 0)
                        .mapToLong(FlowHisTaskDO::getDurationMs)
                        .average()
                        .orElse(0.0);
                row.put("avgDurationMs", Math.round(avgMs));
                result.add(row);
            }

            // 按时间标签排序
            result.sort(Comparator.comparing(row -> (String) row.get("timeLabel")));
            return result;
        } catch (Exception e) {
            log.error("[FlowEfficiency] 审批趋势异常: tenantId={} interval={} err={}",
                    tenantId, interval, e.getMessage(), e);
            return List.of();
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 查询历史任务（带时间范围过滤）
     */
    private List<FlowHisTaskDO> queryHisTasks(String tenantId, String startTime, String endTime, String flowCode) {
        LambdaQueryWrapper<FlowHisTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowHisTaskDO::getTenantId, tenantId)
                .eq(StringUtils.hasText(flowCode), FlowHisTaskDO::getFlowCode, flowCode)
                .ge(StringUtils.hasText(startTime), FlowHisTaskDO::getFinishAt, parseDateTime(startTime))
                .le(StringUtils.hasText(endTime), FlowHisTaskDO::getFinishAt, parseDateTime(endTime))
                .orderByDesc(FlowHisTaskDO::getFinishAt)
                .last("LIMIT " + MAX_QUERY_LIMIT);
        return hisTaskMapper.selectList(wrapper);
    }

    /**
     * 从 nodeDurationStats 返回的 Map 中提取 avgDurationMs
     */
    private double extractAvgDuration(Map<String, Object> row) {
        Object val = row.get("avgDurationMs");
        if (val == null) {
            val = row.get("avg_duration_ms");
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    /**
     * 按粒度格式化时间标签
     */
    private String formatTimeLabel(LocalDateTime dt, String gran) {
        return switch (gran) {
            case "MONTH" -> dt.format(MONTH_FMT);
            case "WEEK" -> {
                WeekFields weekFields = WeekFields.ISO;
                int weekNum = dt.get(weekFields.weekOfWeekBasedYear());
                int year = dt.get(weekFields.weekBasedYear());
                yield year + "-W" + String.format("%02d", weekNum);
            }
            default -> dt.format(DAY_FMT);
        };
    }

    /**
     * 安全解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return LocalDateTime.parse(str, DT_FMT);
        } catch (Exception e) {
            // 尝试只解析日期部分
            try {
                return LocalDate.parse(str, DAY_FMT).atStartOfDay();
            } catch (Exception ex) {
                log.warn("[FlowEfficiency] 无法解析时间: {}", str);
                return null;
            }
        }
    }

    // ============================== 异常检测 ==============================

    @Override
    public List<Map<String, Object>> detectAnomalies(String tenantId, int limit,
                                                      int stuckHours, int longRunningDays) {
        int effectiveLimit = limit > 0 ? limit : 20;
        int effectiveStuckHours = stuckHours > 0 ? stuckHours : 24;
        int effectiveLongRunningDays = longRunningDays > 0 ? longRunningDays : 7;

        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 1. 卡单任务（优先级最高）
        try {
            anomalies.addAll(detectStuckTasks(tenantId, effectiveLimit, effectiveStuckHours));
        } catch (Exception e) {
            log.warn("[FlowEfficiency] 卡单检测异常: tenantId={} err={}", tenantId, e.getMessage());
        }

        // 2. 高驳回率节点
        if (anomalies.size() < effectiveLimit) {
            try {
                anomalies.addAll(detectHighRejectionNodes(tenantId));
            } catch (Exception e) {
                log.warn("[FlowEfficiency] 高驳回率检测异常: tenantId={} err={}", tenantId, e.getMessage());
            }
        }

        // 3. 长期运行实例
        if (anomalies.size() < effectiveLimit) {
            try {
                int remaining = effectiveLimit - anomalies.size();
                anomalies.addAll(detectLongRunningInstances(tenantId, remaining, effectiveLongRunningDays));
            } catch (Exception e) {
                log.warn("[FlowEfficiency] 长期运行实例检测异常: tenantId={} err={}", tenantId, e.getMessage());
            }
        }

        // 截断到 limit
        if (anomalies.size() > effectiveLimit) {
            anomalies = new ArrayList<>(anomalies.subList(0, effectiveLimit));
        }

        if (!anomalies.isEmpty()) {
            log.info("[FlowEfficiency] 异常检测完成: tenantId={} 共检测到 {} 项异常",
                    tenantId, anomalies.size());
        }

        return anomalies;
    }

    @Override
    public List<Map<String, Object>> detectStuckTasks(String tenantId, int limit, int stuckHours) {
        int effectiveLimit = limit > 0 ? limit : 20;
        int effectiveStuckHours = stuckHours > 0 ? stuckHours : 24;

        LocalDateTime threshold = LocalDateTime.now().minusHours(effectiveStuckHours);

        // 查询未完成且创建时间超过阈值的任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowRunTaskDO::getTenantId, tenantId)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
                .lt(FlowRunTaskDO::getCreatedAt, threshold)
                .orderByAsc(FlowRunTaskDO::getCreatedAt)
                .last("LIMIT " + effectiveLimit);

        List<FlowRunTaskDO> stuckTasks = taskMapper.selectList(wrapper);
        if (stuckTasks == null || stuckTasks.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowRunTaskDO task : stuckTasks) {
            long hours = task.getCreatedAt() != null
                    ? Duration.between(task.getCreatedAt(), now).toHours()
                    : effectiveStuckHours;

            Map<String, Object> anomaly = new LinkedHashMap<>();
            anomaly.put("type", "STUCK");
            anomaly.put("taskId", task.getId());
            anomaly.put("instanceId", task.getInstanceId());
            anomaly.put("nodeCode", task.getNodeCode());
            anomaly.put("nodeName", task.getNodeName());
            anomaly.put("assigneeId", task.getAssigneeId());
            anomaly.put("assigneeName", task.getAssigneeName());
            anomaly.put("stuckHours", hours);
            anomaly.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
            anomaly.put("taskStatus", task.getTaskStatus());
            anomaly.put("description", "任务卡单超过 " + hours + " 小时: " + task.getNodeName()
                    + " (创建时间 " + task.getCreatedAt() + ")");
            result.add(anomaly);
        }

        log.info("[FlowEfficiency] 卡单检测: tenantId={} threshold={}h 发现 {} 个卡单任务",
                tenantId, effectiveStuckHours, result.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> detectHighRejectionNodes(String tenantId) {
        // 查询最近 100 个历史任务（按完成时间倒序）
        LambdaQueryWrapper<FlowHisTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowHisTaskDO::getTenantId, tenantId)
                .orderByDesc(FlowHisTaskDO::getFinishAt)
                .last("LIMIT " + HIGH_REJECTION_SAMPLE_SIZE);

        List<FlowHisTaskDO> recentTasks = hisTaskMapper.selectList(wrapper);
        if (recentTasks == null || recentTasks.isEmpty()) {
            return List.of();
        }

        // 按节点编码分组统计
        Map<String, List<FlowHisTaskDO>> byNode = recentTasks.stream()
                .filter(t -> t.getNodeCode() != null)
                .collect(Collectors.groupingBy(
                        FlowHisTaskDO::getNodeCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<FlowHisTaskDO>> entry : byNode.entrySet()) {
            List<FlowHisTaskDO> tasks = entry.getValue();
            int total = tasks.size();
            // 样本过少不报告，避免误报
            if (total < HIGH_REJECTION_MIN_SAMPLE) {
                continue;
            }

            long rejected = tasks.stream()
                    .filter(t -> "REJECTED".equals(t.getTaskStatus()))
                    .count();
            double rejectionRate = (double) rejected / total;

            if (rejectionRate > HIGH_REJECTION_THRESHOLD) {
                // 取节点名称（从任务记录中取最近一条的名称）
                String nodeName = tasks.stream()
                        .filter(t -> t.getNodeName() != null)
                        .map(FlowHisTaskDO::getNodeName)
                        .findFirst()
                        .orElse(entry.getKey());

                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "HIGH_REJECTION");
                anomaly.put("nodeCode", entry.getKey());
                anomaly.put("nodeName", nodeName);
                anomaly.put("totalCount", total);
                anomaly.put("rejectedCount", rejected);
                anomaly.put("rejectionRate", Math.round(rejectionRate * 10000) / 10000.0);
                anomaly.put("description", "节点驳回率过高: " + nodeName
                        + " (最近 " + total + " 个任务中 " + rejected + " 个被驳回，驳回率 "
                        + Math.round(rejectionRate * 100) + "%)");
                result.add(anomaly);
            }
        }

        // 按驳回率降序排序
        result.sort(Comparator.comparingDouble(
                (Map<String, Object> a) -> ((Number) a.get("rejectionRate")).doubleValue()
        ).reversed());

        log.info("[FlowEfficiency] 高驳回率检测: tenantId={} sampleSize={} 发现 {} 个高驳回率节点",
                tenantId, recentTasks.size(), result.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> detectLongRunningInstances(String tenantId, int limit, int longRunningDays) {
        int effectiveLimit = limit > 0 ? limit : 20;
        int effectiveLongRunningDays = longRunningDays > 0 ? longRunningDays : 7;

        LocalDateTime threshold = LocalDateTime.now().minusDays(effectiveLongRunningDays);

        // 查询运行中且启动时间超过阈值的实例
        LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, FlowInstanceDO::getTenantId, tenantId)
                .eq(FlowInstanceDO::getFlowStatus, "RUNNING")
                .lt(FlowInstanceDO::getStartAt, threshold)
                .orderByAsc(FlowInstanceDO::getStartAt)
                .last("LIMIT " + effectiveLimit);

        List<FlowInstanceDO> longRunningInstances = instanceMapper.selectList(wrapper);
        if (longRunningInstances == null || longRunningInstances.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowInstanceDO instance : longRunningInstances) {
            long days = instance.getStartAt() != null
                    ? Duration.between(instance.getStartAt(), now).toDays()
                    : effectiveLongRunningDays;

            Map<String, Object> anomaly = new LinkedHashMap<>();
            anomaly.put("type", "LONG_RUNNING");
            anomaly.put("instanceId", instance.getId());
            anomaly.put("flowCode", instance.getFlowCode());
            anomaly.put("flowName", instance.getFlowName());
            anomaly.put("businessType", instance.getBusinessType());
            anomaly.put("businessId", instance.getBusinessId());
            anomaly.put("initiatorId", instance.getInitiatorId());
            anomaly.put("initiatorName", instance.getInitiatorName());
            anomaly.put("currentNodeCode", instance.getCurrentNodeCode());
            anomaly.put("currentNodeName", instance.getCurrentNodeName());
            anomaly.put("startAt", instance.getStartAt() != null ? instance.getStartAt().toString() : null);
            anomaly.put("runningDays", days);
            anomaly.put("description", "流程运行时间过长: " + instance.getFlowName()
                    + " (已运行 " + days + " 天，启动时间 " + instance.getStartAt() + ")");
            result.add(anomaly);
        }

        log.info("[FlowEfficiency] 长期运行实例检测: tenantId={} threshold={}d 发现 {} 个长期运行实例",
                tenantId, effectiveLongRunningDays, result.size());
        return result;
    }

    /**
     * P1: 流程健康度综合评分
     *
     * <p>评分维度与权重：
     * <ul>
     *   <li>超期率（30%）：overdueRate * 30，最高扣 30 分</li>
     *   <li>代批率（20%）：proxyRate * 20，最高扣 20 分</li>
     *   <li>平均耗时（20%）：> 24h 扣 20、> 6h 扣 15、> 1h 扣 10，否则不扣</li>
     *   <li>异常数（30%）：每个异常扣 5 分，最高扣 30 分</li>
     * </ul>
     */
    @Override
    public Map<String, Object> healthScore(String tenantId, String startTime, String endTime) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 复用效率统计
            Map<String, Object> stats = efficiencyStats(tenantId, startTime, endTime);
            double overdueRate = toDouble(stats.get("overdueRate"));
            double proxyRate = toDouble(stats.get("proxyRate"));
            double avgDurationMs = toDouble(stats.get("avgDurationMs"));
            long totalCount = toLong(stats.get("totalCount"));

            // 复用异常检测
            List<Map<String, Object>> anomalies = detectAnomalies(tenantId, 50, 24, 7);
            int anomalyCount = anomalies != null ? anomalies.size() : 0;

            // 计算扣分明细
            Map<String, Object> deductions = new LinkedHashMap<>();

            // 1. 超期率扣分（最高 30）
            double overdueDeduction = Math.min(30, overdueRate * 30);
            deductions.put("overdue", Math.round(overdueDeduction * 100) / 100.0);

            // 2. 代批率扣分（最高 20）
            double proxyDeduction = Math.min(20, proxyRate * 20);
            deductions.put("proxy", Math.round(proxyDeduction * 100) / 100.0);

            // 3. 平均耗时扣分（最高 20）
            double durationDeduction;
            if (avgDurationMs > 86_400_000) {        // > 24h
                durationDeduction = 20;
            } else if (avgDurationMs > 21_600_000) { // > 6h
                durationDeduction = 15;
            } else if (avgDurationMs > 3_600_000) {  // > 1h
                durationDeduction = 10;
            } else {
                durationDeduction = 0;
            }
            deductions.put("duration", durationDeduction);

            // 4. 异常数扣分（每个 5 分，最高 30）
            double anomalyDeduction = Math.min(30, anomalyCount * 5.0);
            deductions.put("anomaly", Math.round(anomalyDeduction * 100) / 100.0);

            // 综合评分
            int score = (int) Math.max(0, 100 - overdueDeduction - proxyDeduction
                    - durationDeduction - anomalyDeduction);

            // 评级
            String level;
            if (score >= 90) {
                level = "EXCELLENT";
            } else if (score >= 75) {
                level = "GOOD";
            } else if (score >= 60) {
                level = "FAIR";
            } else {
                level = "POOR";
            }

            result.put("score", score);
            result.put("level", level);
            result.put("deductions", deductions);
            result.put("totalCount", totalCount);
            result.put("anomalyCount", anomalyCount);
            result.put("overdueRate", overdueRate);
            result.put("proxyRate", proxyRate);
            result.put("avgDurationMs", Math.round(avgDurationMs));

            log.info("[FlowEfficiency] 健康度评分: tenantId={} score={} level={} anomalies={}",
                    tenantId, score, level, anomalyCount);
        } catch (Exception e) {
            log.error("[FlowEfficiency] 健康度评分异常: tenantId={} err={}", tenantId, e.getMessage(), e);
            result.put("score", 0);
            result.put("level", "POOR");
            result.put("deductions", Map.of());
        }
        return result;
    }

    /** 安全类型转换：Object → double */
    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            log.warn("[FlowEfficiencyServiceImpl] Double 解析失败，使用 0.0 兜底 val={}: {}", val, e.getMessage());
            return 0.0;
        }
    }

    /** 安全类型转换：Object → long */
    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            log.warn("[FlowEfficiencyServiceImpl] Long 解析失败，使用 0L 兜底 val={}: {}", val, e.getMessage());
            return 0L;
        }
    }
}
