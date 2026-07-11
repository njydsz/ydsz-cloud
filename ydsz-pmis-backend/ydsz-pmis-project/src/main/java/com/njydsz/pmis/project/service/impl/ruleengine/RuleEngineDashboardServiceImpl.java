package com.njydsz.pmis.project.service.impl.ruleengine;

import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.project.dto.ruleengine.RuleDashboardDistributionVO;
import com.njydsz.pmis.project.dto.ruleengine.RuleDashboardOverviewVO;
import com.njydsz.pmis.project.dto.ruleengine.RuleDashboardRealtimeVO;
import com.njydsz.pmis.project.dto.ruleengine.RuleDashboardTopRuleVO;
import com.njydsz.pmis.project.dto.ruleengine.RuleDashboardTrendVO;
import com.njydsz.pmis.literule.entity.RuleDefinitionDO;
import com.njydsz.pmis.literule.mapper.RuleDefinitionMapper;
import com.njydsz.pmis.literule.mapper.RuleExecutionTraceMapper;
import com.njydsz.pmis.project.service.ruleengine.RuleEngineDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则引擎监控大盘服务实现
 *
 * <p>聚合 pmis_rule_execution_trace 表的执行轨迹和 pmis_rule_def 表的规则定义，
 * 输出 5 类监控指标。耗时 P50/P95/P99 采用内存分位数计算（最多 50000 样本）。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@com.baomidou.dynamic.datasource.annotation.DS(com.njydsz.pmis.common.datasource.DataSourceConstants.SLAVE)
public class RuleEngineDashboardServiceImpl implements RuleEngineDashboardService {

    /** 规则执行轨迹 Mapper */
    private final RuleExecutionTraceMapper ruleExecutionTraceMapper;
    /** 规则定义 Mapper */
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleEngine ruleEngine;

    /** 趋势数据时间桶格式：24h 按小时聚合 */
    private static final String BUCKET_FORMAT_HOUR = "%Y-%m-%d %H:00";
    /** 趋势数据时间桶格式：7d/30d 按天聚合 */
    private static final String BUCKET_FORMAT_DAY = "%Y-%m-%d";
    /** 24 小时趋势的时间标签格式（HH:00） */
    private static final DateTimeFormatter HOUR_LABEL_FMT = DateTimeFormatter.ofPattern("HH:00");
    /** 按天趋势的时间标签格式（MM-DD） */
    private static final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public RuleDashboardOverviewVO getOverview() {
        // 统计窗口：今日 0:00 ~ now
        LocalDateTime since = LocalDate.now().atStartOfDay();
        LocalDateTime until = LocalDateTime.now();

        // 1. 规则定义状态分布
        List<RuleDefinitionDO> allRules = ruleDefinitionMapper.selectList(null);
        Map<String, Long> statusDist = allRules.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus() == null ? "UNKNOWN" : r.getStatus(),
                        Collectors.counting()));
        Map<String, Long> categoryDist = allRules.stream()
                .filter(r -> r.getCategory() != null && !r.getCategory().isBlank())
                .collect(Collectors.groupingBy(
                        RuleDefinitionDO::getCategory,
                        Collectors.counting()));
        long totalRules = allRules.size();
        long enabledRules = allRules.stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).count();

        // 2. 时间窗口内执行统计
        Map<String, Object> stats = ruleExecutionTraceMapper.selectStatsByTimeRange(since, until);
        long evaluations = toLong(stats.get("evaluations"));
        long triggered = toLong(stats.get("triggered"));
        long errors = toLong(stats.get("errors"));
        double avgElapsedMs = toDouble(stats.get("avgElapsedMs"));

        Long activeRules = ruleExecutionTraceMapper.selectActiveRuleCount(since, until);
        long activeRuleCount = activeRules == null ? 0 : activeRules;

        // 3. 分位数计算（内存）
        List<Long> elapsedList = ruleExecutionTraceMapper.selectElapsedMsList(since, until);
        double p50 = percentile(elapsedList, 0.50);
        double p95 = percentile(elapsedList, 0.95);
        double p99 = percentile(elapsedList, 0.99);

        return RuleDashboardOverviewVO.builder()
                .totalRules(totalRules)
                .enabledRules(enabledRules)
                .statusDistribution(statusDist)
                .categoryDistribution(categoryDist)
                .todayEvaluations(evaluations)
                .todayTriggered(triggered)
                .todayTriggerRate(safeRate(triggered, evaluations))
                .todayErrors(errors)
                .todayErrorRate(safeRate(errors, evaluations))
                .todayActiveRules(activeRuleCount)
                .p50ElapsedMs(p50)
                .p95ElapsedMs(p95)
                .p99ElapsedMs(p99)
                .avgElapsedMs(avgElapsedMs)
                .since(since.toString())
                .until(until.toString())
                .build();
    }

    @Override
    public RuleDashboardTrendVO getTrends(String timeRange) {
        LocalDateTime until = LocalDateTime.now();
        LocalDateTime since;
        String bucketFormat;
        DateTimeFormatter labelFmt;
        // 时间窗口对齐到整点/整天，确保桶标签完整
        if ("7d".equalsIgnoreCase(timeRange)) {
            since = until.toLocalDate().minusDays(6).atStartOfDay();
            bucketFormat = BUCKET_FORMAT_DAY;
            labelFmt = DAY_LABEL_FMT;
        } else if ("30d".equalsIgnoreCase(timeRange)) {
            since = until.toLocalDate().minusDays(29).atStartOfDay();
            bucketFormat = BUCKET_FORMAT_DAY;
            labelFmt = DAY_LABEL_FMT;
        } else {
            // 默认 24h
            since = until.minusHours(23).withMinute(0).withSecond(0).withNano(0);
            bucketFormat = BUCKET_FORMAT_HOUR;
            labelFmt = HOUR_LABEL_FMT;
        }

        List<Map<String, Object>> rows = ruleExecutionTraceMapper.selectTimeBucketAggregations(
                since, until, bucketFormat);

        // 1. 构建完整时间桶，避免无数据时间点缺失
        List<String> fullBuckets = buildFullBuckets(since, until, timeRange, labelFmt);
        Map<String, Map<String, Object>> rowByBucket = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String bucket = String.valueOf(row.get("bucket"));
            rowByBucket.put(bucket, row);
        }

        // 2. 填充时间序列
        List<String> timeLabels = new ArrayList<>(fullBuckets.size());
        List<Long> evaluationSeries = new ArrayList<>(fullBuckets.size());
        List<Long> triggeredSeries = new ArrayList<>(fullBuckets.size());
        List<Long> errorSeries = new ArrayList<>(fullBuckets.size());
        List<Double> p99Series = new ArrayList<>(fullBuckets.size());
        List<Double> p50Series = new ArrayList<>(fullBuckets.size());
        List<Double> errorRateSeries = new ArrayList<>(fullBuckets.size());
        List<Double> triggerRateSeries = new ArrayList<>(fullBuckets.size());

        // 时间桶的 key 格式需要与 SQL DATE_FORMAT 输出一致
        DateTimeFormatter bucketKeyFmt = "24h".equalsIgnoreCase(timeRange)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")
                : DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (String label : fullBuckets) {
            // label 是 HH:00 或 MM-dd，需要还原到完整 bucket key
            // 这里通过遍历时间点直接生成 bucket key
            timeLabels.add(label);
        }

        // 重新基于时间点遍历，对齐 SQL 输出的 bucket key
        List<LocalDateTime> timePoints = buildTimePoints(since, until, timeRange);
        timeLabels.clear();
        for (LocalDateTime point : timePoints) {
            String bucketKey = point.format(bucketKeyFmt);
            String label = point.format(labelFmt);
            timeLabels.add(label);

            Map<String, Object> row = rowByBucket.get(bucketKey);
            if (row == null) {
                evaluationSeries.add(0L);
                triggeredSeries.add(0L);
                errorSeries.add(0L);
                p99Series.add(0.0);
                p50Series.add(0.0);
                errorRateSeries.add(0.0);
                triggerRateSeries.add(0.0);
            } else {
                long eval = toLong(row.get("evaluations"));
                long trg = toLong(row.get("triggered"));
                long err = toLong(row.get("errors"));
                double avg = toDouble(row.get("avgElapsedMs"));
                evaluationSeries.add(eval);
                triggeredSeries.add(trg);
                errorSeries.add(err);
                // 趋势图用 avg 近似 P50/P99（精确分位数按桶计算成本太高）
                p50Series.add(avg);
                p99Series.add(avg);
                errorRateSeries.add(safeRate(err, eval));
                triggerRateSeries.add(safeRate(trg, eval));
            }
        }

        return RuleDashboardTrendVO.builder()
                .timeRange(timeRange)
                .timeLabels(timeLabels)
                .evaluationSeries(evaluationSeries)
                .triggeredSeries(triggeredSeries)
                .errorSeries(errorSeries)
                .p99ElapsedSeries(p99Series)
                .p50ElapsedSeries(p50Series)
                .errorRateSeries(errorRateSeries)
                .triggerRateSeries(triggerRateSeries)
                .since(since.toString())
                .until(until.toString())
                .build();
    }

    @Override
    public RuleDashboardDistributionVO getDistribution() {
        LocalDateTime since = LocalDate.now().atStartOfDay();
        LocalDateTime until = LocalDateTime.now();

        // 1. 规则定义分布（状态/类别/责任人/租户）
        List<RuleDefinitionDO> allRules = ruleDefinitionMapper.selectList(null);
        Map<String, Long> byStatus = allRules.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus() == null ? "UNKNOWN" : r.getStatus(),
                        Collectors.counting()));
        Map<String, Long> byCategory = allRules.stream()
                .filter(r -> r.getCategory() != null && !r.getCategory().isBlank())
                .collect(Collectors.groupingBy(RuleDefinitionDO::getCategory, Collectors.counting()));
        Map<String, Long> byOwner = allRules.stream()
                .filter(r -> r.getOwner() != null && !r.getOwner().isBlank())
                .collect(Collectors.groupingBy(RuleDefinitionDO::getOwner, Collectors.counting()));
        Map<String, Long> byTenant = allRules.stream()
                .filter(r -> r.getTenantId() != null && !r.getTenantId().isBlank())
                .collect(Collectors.groupingBy(RuleDefinitionDO::getTenantId, Collectors.counting()));

        // 2. 触发结果分布（严重度/场景）
        Map<String, Long> bySeverity = countToMap(
                ruleExecutionTraceMapper.selectSeverityCount(since, until));
        Map<String, Long> byScenario = countToMap(
                ruleExecutionTraceMapper.selectScenarioCount(since, until));

        return RuleDashboardDistributionVO.builder()
                .byStatus(byStatus)
                .byCategory(byCategory)
                .bySeverity(bySeverity)
                .byScenario(byScenario)
                .byTenant(byTenant)
                .byOwner(byOwner)
                .statusPie(toPieItems(byStatus))
                .categoryPie(toPieItems(byCategory))
                .severityPie(toPieItems(bySeverity))
                .scenarioPie(toPieItems(byScenario))
                .build();
    }

    @Override
    public List<RuleDashboardTopRuleVO> getTopRules(String type, int limit) {
        // 统计窗口默认最近 7 天，确保有足够样本
        LocalDateTime until = LocalDateTime.now();
        LocalDateTime since = until.toLocalDate().minusDays(6).atStartOfDay();

        List<Map<String, Object>> rows = ruleExecutionTraceMapper.selectRuleAggregations(since, until);

        // 关联规则定义补充类别/责任人/启用状态/默认严重度
        List<RuleDefinitionDO> allDefs = ruleDefinitionMapper.selectList(null);
        Map<String, RuleDefinitionDO> defByCode = allDefs.stream()
                .collect(Collectors.toMap(RuleDefinitionDO::getRuleCode, d -> d, (a, b) -> a));

        List<RuleDashboardTopRuleVO> topRules = rows.stream()
                .map(row -> {
                    String ruleCode = String.valueOf(row.get("ruleCode"));
                    long eval = toLong(row.get("evaluations"));
                    long trg = toLong(row.get("triggered"));
                    long err = toLong(row.get("errors"));
                    double avgMs = toDouble(row.get("avgElapsedMs"));
                    long totalMs = toLong(row.get("totalElapsedMs"));
                    RuleDefinitionDO def = defByCode.get(ruleCode);
                    return RuleDashboardTopRuleVO.builder()
                            .ruleCode(ruleCode)
                            .ruleName(def != null ? def.getRuleName() : String.valueOf(row.get("ruleName")))
                            .category(def != null ? def.getCategory() : null)
                            .owner(def != null ? def.getOwner() : null)
                            .enabled(def != null ? def.getEnabled() : null)
                            .defaultSeverity(def != null ? def.getDefaultSeverity() : null)
                            .evaluations(eval)
                            .triggered(trg)
                            .errors(err)
                            .triggerRate(safeRate(trg, eval))
                            .errorRate(safeRate(err, eval))
                            .avgElapsedMs(avgMs)
                            .p99ElapsedMs(avgMs) // 按规则分桶精确 P99 成本高，用 avg 近似
                            .totalElapsedMs(totalMs)
                            .build();
                })
                .collect(Collectors.toList());

        // 按类型排序
        Comparator<RuleDashboardTopRuleVO> comparator;
        if ("slowest".equalsIgnoreCase(type)) {
            comparator = Comparator.comparingDouble(RuleDashboardTopRuleVO::getAvgElapsedMs).reversed();
        } else if ("errorRate".equalsIgnoreCase(type)) {
            comparator = Comparator.comparingDouble(RuleDashboardTopRuleVO::getErrorRate).reversed();
        } else {
            // 默认按触发次数降序
            comparator = Comparator.comparingLong(RuleDashboardTopRuleVO::getTriggered).reversed();
        }
        return topRules.stream()
                .sorted(comparator)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public RuleDashboardRealtimeVO getRealtime() {
        RuleEngineStats stats = ruleEngine.getStats();
        LocalDateTime until = LocalDateTime.now();
        LocalDateTime since = until.minusMinutes(1);

        Map<String, Object> recentStats = ruleExecutionTraceMapper.selectStatsByTimeRange(since, until);
        long recentEvaluations = toLong(recentStats.get("evaluations"));
        long recentTriggered = toLong(recentStats.get("triggered"));
        long recentErrors = toLong(recentStats.get("errors"));

        Long activeRules = ruleExecutionTraceMapper.selectActiveRuleCount(since, until);
        long activeRuleCount = activeRules == null ? 0 : activeRules;

        return RuleDashboardRealtimeVO.builder()
                .registeredRules(stats != null ? stats.getRegisteredRules() : 0)
                .lastEvaluatedRules(stats != null ? stats.getLastEvaluatedRules() : 0)
                .recentEvaluations(recentEvaluations)
                .recentTriggered(recentTriggered)
                .recentErrors(recentErrors)
                .currentQps(recentEvaluations / 60.0)
                .activeRules(activeRuleCount)
                .traceQueueSize(0)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 计算分位数（输入列表需已升序排序）
     *
     * @param sorted 已升序排序的耗时列表
     * @param p      分位数（0~1）
     * @return 分位值；列表为空返回 0
     */
    private double percentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0.0;
        int n = sorted.size();
        if (n == 1) return sorted.get(0);
        // nearest-rank 方法：index = ceil(p * n)
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return sorted.get(idx);
    }

    /**
     * 安全计算比率（分子/分母），分母为 0 返回 0
     */
    private double safeRate(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return (double) numerator / denominator;
    }

    /**
     * 将 Map 中的数字字段安全转为 long
     */
    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 将 Map 中的数字字段安全转为 double
     */
    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 将分组计数查询结果转为 Map<String, Long>
     */
    private Map<String, Long> countToMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("name"));
            long value = toLong(row.get("value"));
            map.put(name, value);
        }
        return map;
    }

    /**
     * 将 Map 转为饼图条目列表
     */
    private List<RuleDashboardDistributionVO.PieItem> toPieItems(Map<String, Long> map) {
        return map.entrySet().stream()
                .map(e -> RuleDashboardDistributionVO.PieItem.builder()
                        .name(e.getKey())
                        .value(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 构建完整时间桶标签列表（基于时间点遍历，确保无数据时间点不缺失）
     */
    private List<String> buildFullBuckets(LocalDateTime since, LocalDateTime until, String timeRange,
                                           DateTimeFormatter labelFmt) {
        return buildTimePoints(since, until, timeRange).stream()
                .map(p -> p.format(labelFmt))
                .collect(Collectors.toList());
    }

    /**
     * 构建完整时间点列表
     */
    private List<LocalDateTime> buildTimePoints(LocalDateTime since, LocalDateTime until, String timeRange) {
        List<LocalDateTime> points = new ArrayList<>();
        LocalDateTime cursor = since;
        if ("24h".equalsIgnoreCase(timeRange) || timeRange == null || timeRange.isBlank()) {
            // 按小时
            while (!cursor.isAfter(until)) {
                points.add(cursor);
                cursor = cursor.plusHours(1);
            }
        } else {
            // 按天
            while (!cursor.isAfter(until)) {
                points.add(cursor);
                cursor = cursor.plusDays(1);
            }
        }
        // 兜底：确保至少一个时间点
        if (points.isEmpty()) {
            points.add(since);
        }
        return points;
    }
}
