package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowEfficiencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
 *   <li>proxyRate — 代批率（简化占位：taskStatus=DELEGATED 的占比）</li>
 *   <li>overdueRate — 超期率（taskStatus=TIMEOUT 的占比）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEfficiencyServiceImpl implements FlowEfficiencyService {

    private final FlowHisTaskMapper hisTaskMapper;

    /** 日期时间格式 */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 查询上限（防止全表加载 OOM） */
    private static final int MAX_QUERY_LIMIT = 10000;

    @Override
    public Map<String, Object> efficiencyStats(Long tenantId, String startTime, String endTime) {
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

            // 代批率（简化占位：委派任务占比）
            long proxyCount = records.stream()
                    .filter(r -> "DELEGATED".equals(r.getTaskStatus()))
                    .count();
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

    @Override
    public List<Map<String, Object>> bottleneckRanking(Long tenantId, String flowCode, int limit) {
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
    public List<Map<String, Object>> approverRanking(Long tenantId, String startTime, String endTime, int limit) {
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
    public List<Map<String, Object>> approvalTrend(Long tenantId, String interval,
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
    private List<FlowHisTaskDO> queryHisTasks(Long tenantId, String startTime, String endTime, String flowCode) {
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
}
