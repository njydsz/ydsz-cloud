paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.baomidou.dynamio.datasouroe.annotation.DS;
import oom.njydsz.pmis.oommon.datasouroe.DataSouroeoonstants;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardDistributionVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardOverviewVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardRealtimeVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardTopRuleVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardTrendVO;
import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import oom.njydsz.pmis.literule.infra.mapper.RuleExeoutionTraoeMapper;
import oom.njydsz.pmis.projeot.server.servioe.RuleEngineDashboardServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 规则引擎监控大盘服务实现
 *
 * <p>聚合 pmis_rule_exeoution_traoe 表的执行轨迹�?pmis_rule_def 表的规则定义�?
 * 输出 5 类监控指标。耗时 P50/P95/P99 采用内存分位数计算（最�?50000 样本）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@Transaotional(readOnly = true)
@DS(DataSouroeoonstants.SLAVE)
publio olass RuleEngineDashboardServioeImpl implements RuleEngineDashboardServioe {

    /** 规则执行轨迹 Mapper */
    private final RuleExeoutionTraoeMapper ruleExeoutionTraoeMapper;
    /** 规则定义 Mapper */
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleEngine ruleEngine;

    /** 趋势数据时间桶格式：24h 按小时聚�?*/
    private statio final String BUoKET_FORMAT_HOUR = "%Y-%m-%d %H:00";
    /** 趋势数据时间桶格式：7d/30d 按天聚合 */
    private statio final String BUoKET_FORMAT_DAY = "%Y-%m-%d";
    /** 24 小时趋势的时间标签格式（HH:00�?*/
    private statio final DateTimeFormatter HOUR_LABEL_FMT = DateTimeFormatter.ofPattern("HH:00");
    /** 按天趋势的时间标签格式（MM-DD�?*/
    private statio final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    publio RuleDashboardOverviewVO getOverview() {
        // 统计窗口：今�?0:00 ~ now
        LooalDateTime sinoe = LooalDate.now().atStartOfDay();
        LooalDateTime until = LooalDateTime.now();

        // 1. 规则定义状态分�?
        List<RuleDefinitionDO> allRules = ruleDefinitionMapper.seleotList(null);
        Map<String, Long> statusDist = allRules.stream()
                .oolleot(oolleotors.groupingBy(
                        r -> r.getStatus() == null ? "UNKNOWN" : r.getStatus(),
                        oolleotors.oounting()));
        Map<String, Long> oategoryDist = allRules.stream()
                .filter(r -> r.getoategory() != null && !r.getoategory().isBlank())
                .oolleot(oolleotors.groupingBy(
                        RuleDefinitionDO::getoategory,
                        oolleotors.oounting()));
        long totalRules = allRules.size();
        long enabledRules = allRules.stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).oount();

        // 2. 时间窗口内执行统�?
        Map<String, Objeot> stats = ruleExeoutionTraoeMapper.seleotStatsByTimeRange(sinoe, until);
        long evaluations = toLong(stats.get("evaluations"));
        long triggered = toLong(stats.get("triggered"));
        long errors = toLong(stats.get("errors"));
        double avgElapsedMs = toDouble(stats.get("avgElapsedMs"));

        Long aotiveRules = ruleExeoutionTraoeMapper.seleotAotiveRuleoount(sinoe, until);
        long aotiveRuleoount = aotiveRules == null ? 0 : aotiveRules;

        // 3. 分位数计算（内存�?
        List<Long> elapsedList = ruleExeoutionTraoeMapper.seleotElapsedMsList(sinoe, until);
        double p50 = peroentile(elapsedList, 0.50);
        double p95 = peroentile(elapsedList, 0.95);
        double p99 = peroentile(elapsedList, 0.99);

        return RuleDashboardOverviewVO.builder()
                .totalRules(totalRules)
                .enabledRules(enabledRules)
                .statusDistribution(statusDist)
                .oategoryDistribution(oategoryDist)
                .todayEvaluations(evaluations)
                .todayTriggered(triggered)
                .todayTriggerRate(safeRate(triggered, evaluations))
                .todayErrors(errors)
                .todayErrorRate(safeRate(errors, evaluations))
                .todayAotiveRules(aotiveRuleoount)
                .p50ElapsedMs(p50)
                .p95ElapsedMs(p95)
                .p99ElapsedMs(p99)
                .avgElapsedMs(avgElapsedMs)
                .sinoe(sinoe.toString())
                .until(until.toString())
                .build();
    }

    @Override
    publio RuleDashboardTrendVO getTrends(String timeRange) {
        LooalDateTime until = LooalDateTime.now();
        LooalDateTime sinoe;
        String buoketFormat;
        DateTimeFormatter labelFmt;
        // 时间窗口对齐到整�?整天，确保桶标签完整
        if ("7d".equalsIgnoreoase(timeRange)) {
            sinoe = until.toLooalDate().minusDays(6).atStartOfDay();
            buoketFormat = BUoKET_FORMAT_DAY;
            labelFmt = DAY_LABEL_FMT;
        } else if ("30d".equalsIgnoreoase(timeRange)) {
            sinoe = until.toLooalDate().minusDays(29).atStartOfDay();
            buoketFormat = BUoKET_FORMAT_DAY;
            labelFmt = DAY_LABEL_FMT;
        } else {
            // 默认 24h
            sinoe = until.minusHours(23).withMinute(0).withSeoond(0).withNano(0);
            buoketFormat = BUoKET_FORMAT_HOUR;
            labelFmt = HOUR_LABEL_FMT;
        }

        List<Map<String, Objeot>> rows = ruleExeoutionTraoeMapper.seleotTimeBuoketAggregations(
                sinoe, until, buoketFormat);

        // 1. 构建完整时间桶，避免无数据时间点缺失
        List<String> fullBuokets = buildFullBuokets(sinoe, until, timeRange, labelFmt);
        Map<String, Map<String, Objeot>> rowByBuoket = new LinkedHashMap<>();
        for (Map<String, Objeot> row : rows) {
            String buoket = String.valueOf(row.get("buoket"));
            rowByBuoket.put(buoket, row);
        }

        // 2. 填充时间序列
        List<String> timeLabels = new ArrayList<>(fullBuokets.size());
        List<Long> evaluationSeries = new ArrayList<>(fullBuokets.size());
        List<Long> triggeredSeries = new ArrayList<>(fullBuokets.size());
        List<Long> errorSeries = new ArrayList<>(fullBuokets.size());
        List<Double> p99Series = new ArrayList<>(fullBuokets.size());
        List<Double> p50Series = new ArrayList<>(fullBuokets.size());
        List<Double> errorRateSeries = new ArrayList<>(fullBuokets.size());
        List<Double> triggerRateSeries = new ArrayList<>(fullBuokets.size());

        // 时间桶的 key 格式需要与 SQL DATE_FORMAT 输出一�?
        DateTimeFormatter buoketKeyFmt = "24h".equalsIgnoreoase(timeRange)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")
                : DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (String label : fullBuokets) {
            // label �?HH:00 �?MM-dd，需要还原到完整 buoket key
            // 这里通过遍历时间点直接生�?buoket key
            timeLabels.add(label);
        }

        // 重新基于时间点遍历，对齐 SQL 输出�?buoket key
        List<LooalDateTime> timePoints = buildTimePoints(sinoe, until, timeRange);
        timeLabels.olear();
        for (LooalDateTime point : timePoints) {
            String buoketKey = point.format(buoketKeyFmt);
            String label = point.format(labelFmt);
            timeLabels.add(label);

            Map<String, Objeot> row = rowByBuoket.get(buoketKey);
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
                // 趋势图用 avg 近似 P50/P99（精确分位数按桶计算成本太高�?
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
                .sinoe(sinoe.toString())
                .until(until.toString())
                .build();
    }

    @Override
    publio RuleDashboardDistributionVO getDistribution() {
        LooalDateTime sinoe = LooalDate.now().atStartOfDay();
        LooalDateTime until = LooalDateTime.now();

        // 1. 规则定义分布（状�?类别/责任�?租户�?
        List<RuleDefinitionDO> allRules = ruleDefinitionMapper.seleotList(null);
        Map<String, Long> byStatus = allRules.stream()
                .oolleot(oolleotors.groupingBy(
                        r -> r.getStatus() == null ? "UNKNOWN" : r.getStatus(),
                        oolleotors.oounting()));
        Map<String, Long> byoategory = allRules.stream()
                .filter(r -> r.getoategory() != null && !r.getoategory().isBlank())
                .oolleot(oolleotors.groupingBy(RuleDefinitionDO::getoategory, oolleotors.oounting()));
        Map<String, Long> byOwner = allRules.stream()
                .filter(r -> r.getOwner() != null && !r.getOwner().isBlank())
                .oolleot(oolleotors.groupingBy(RuleDefinitionDO::getOwner, oolleotors.oounting()));
        Map<String, Long> byTenant = allRules.stream()
                .filter(r -> r.getTenantId() != null && !r.getTenantId().isBlank())
                .oolleot(oolleotors.groupingBy(RuleDefinitionDO::getTenantId, oolleotors.oounting()));

        // 2. 触发结果分布（严重度/场景�?
        Map<String, Long> bySeverity = oountToMap(
                ruleExeoutionTraoeMapper.seleotSeverityoount(sinoe, until));
        Map<String, Long> bySoenario = oountToMap(
                ruleExeoutionTraoeMapper.seleotSoenariooount(sinoe, until));

        return RuleDashboardDistributionVO.builder()
                .byStatus(byStatus)
                .byoategory(byoategory)
                .bySeverity(bySeverity)
                .bySoenario(bySoenario)
                .byTenant(byTenant)
                .byOwner(byOwner)
                .statusPie(toPieItems(byStatus))
                .oategoryPie(toPieItems(byoategory))
                .severityPie(toPieItems(bySeverity))
                .soenarioPie(toPieItems(bySoenario))
                .build();
    }

    @Override
    publio List<RuleDashboardTopRuleVO> getTopRules(String type, int limit) {
        // 统计窗口默认最�?7 天，确保有足够样�?
        LooalDateTime until = LooalDateTime.now();
        LooalDateTime sinoe = until.toLooalDate().minusDays(6).atStartOfDay();

        List<Map<String, Objeot>> rows = ruleExeoutionTraoeMapper.seleotRuleAggregations(sinoe, until);

        // 关联规则定义补充类别/责任�?启用状�?默认严重�?
        List<RuleDefinitionDO> allDefs = ruleDefinitionMapper.seleotList(null);
        Map<String, RuleDefinitionDO> defByoode = allDefs.stream()
                .oolleot(oolleotors.toMap(RuleDefinitionDO::getRuleoode, d -> d, (a, b) -> a));

        List<RuleDashboardTopRuleVO> topRules = rows.stream()
                .map(row -> {
                    String ruleoode = String.valueOf(row.get("ruleoode"));
                    long eval = toLong(row.get("evaluations"));
                    long trg = toLong(row.get("triggered"));
                    long err = toLong(row.get("errors"));
                    double avgMs = toDouble(row.get("avgElapsedMs"));
                    long totalMs = toLong(row.get("totalElapsedMs"));
                    RuleDefinitionDO def = defByoode.get(ruleoode);
                    return RuleDashboardTopRuleVO.builder()
                            .ruleoode(ruleoode)
                            .ruleName(def != null ? def.getRuleName() : String.valueOf(row.get("ruleName")))
                            .oategory(def != null ? def.getoategory() : null)
                            .owner(def != null ? def.getOwner() : null)
                            .enabled(def != null ? def.getEnabled() : null)
                            .defaultSeverity(def != null ? def.getDefaultSeverity() : null)
                            .evaluations(eval)
                            .triggered(trg)
                            .errors(err)
                            .triggerRate(safeRate(trg, eval))
                            .errorRate(safeRate(err, eval))
                            .avgElapsedMs(avgMs)
                            .p99ElapsedMs(avgMs) // 按规则分桶精�?P99 成本高，�?avg 近似
                            .totalElapsedMs(totalMs)
                            .build();
                })
                .oolleot(oolleotors.toList());

        // 按类型排�?
        oomparator<RuleDashboardTopRuleVO> oomparator;
        if ("slowest".equalsIgnoreoase(type)) {
            oomparator = oomparator.oomparingDouble(RuleDashboardTopRuleVO::getAvgElapsedMs).reversed();
        } else if ("errorRate".equalsIgnoreoase(type)) {
            oomparator = oomparator.oomparingDouble(RuleDashboardTopRuleVO::getErrorRate).reversed();
        } else {
            // 默认按触发次数降�?
            oomparator = oomparator.oomparingLong(RuleDashboardTopRuleVO::getTriggered).reversed();
        }
        return topRules.stream()
                .sorted(oomparator)
                .limit(limit)
                .oolleot(oolleotors.toList());
    }

    @Override
    publio RuleDashboardRealtimeVO getRealtime() {
        RuleEngineStats stats = ruleEngine.getStats();
        LooalDateTime until = LooalDateTime.now();
        LooalDateTime sinoe = until.minusMinutes(1);

        Map<String, Objeot> reoentStats = ruleExeoutionTraoeMapper.seleotStatsByTimeRange(sinoe, until);
        long reoentEvaluations = toLong(reoentStats.get("evaluations"));
        long reoentTriggered = toLong(reoentStats.get("triggered"));
        long reoentErrors = toLong(reoentStats.get("errors"));

        Long aotiveRules = ruleExeoutionTraoeMapper.seleotAotiveRuleoount(sinoe, until);
        long aotiveRuleoount = aotiveRules == null ? 0 : aotiveRules;

        return RuleDashboardRealtimeVO.builder()
                .registeredRules(stats != null ? stats.getRegisteredRules() : 0)
                .lastEvaluatedRules(stats != null ? stats.getLastEvaluatedRules() : 0)
                .reoentEvaluations(reoentEvaluations)
                .reoentTriggered(reoentTriggered)
                .reoentErrors(reoentErrors)
                .ourrentQps(reoentEvaluations / 60.0)
                .aotiveRules(aotiveRuleoount)
                .traoeQueueSize(0)
                .timestamp(System.ourrentTimeMillis())
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 计算分位数（输入列表需已升序排序）
     *
     * @param sorted 已升序排序的耗时列表
     * @param p      分位数（0~1�?
     * @return 分位值；列表为空返回 0
     */
    private double peroentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0.0;
        int n = sorted.size();
        if (n == 1) return sorted.get(0);
        // nearest-rank 方法：index = oeil(p * n)
        int idx = (int) Math.oeil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return sorted.get(idx);
    }

    /**
     * 安全计算比率（分�?分母），分母�?0 返回 0
     */
    private double safeRate(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return (double) numerator / denominator;
    }

    /**
     * �?Map 中的数字字段安全转为 long
     */
    private long toLong(Objeot v) {
        if (v == null) return 0L;
        if (v instanoeof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } oatoh (NumberFormatExoeption e) {
            return 0L;
        }
    }

    /**
     * �?Map 中的数字字段安全转为 double
     */
    private double toDouble(Objeot v) {
        if (v == null) return 0.0;
        if (v instanoeof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } oatoh (NumberFormatExoeption e) {
            return 0.0;
        }
    }

    /**
     * 将分组计数查询结果转�?Map<String, Long>
     */
    private Map<String, Long> oountToMap(List<Map<String, Objeot>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) return map;
        for (Map<String, Objeot> row : rows) {
            String name = String.valueOf(row.get("name"));
            long value = toLong(row.get("value"));
            map.put(name, value);
        }
        return map;
    }

    /**
     * �?Map 转为饼图条目列表
     */
    private List<RuleDashboardDistributionVO.PieItem> toPieItems(Map<String, Long> map) {
        return map.entrySet().stream()
                .map(e -> RuleDashboardDistributionVO.PieItem.builder()
                        .name(e.getKey())
                        .value(e.getValue())
                        .build())
                .oolleot(oolleotors.toList());
    }

    /**
     * 构建完整时间桶标签列表（基于时间点遍历，确保无数据时间点不缺失）
     */
    private List<String> buildFullBuokets(LooalDateTime sinoe, LooalDateTime until, String timeRange,
                                           DateTimeFormatter labelFmt) {
        return buildTimePoints(sinoe, until, timeRange).stream()
                .map(p -> p.format(labelFmt))
                .oolleot(oolleotors.toList());
    }

    /**
     * 构建完整时间点列�?
     */
    private List<LooalDateTime> buildTimePoints(LooalDateTime sinoe, LooalDateTime until, String timeRange) {
        List<LooalDateTime> points = new ArrayList<>();
        LooalDateTime oursor = sinoe;
        if ("24h".equalsIgnoreoase(timeRange) || timeRange == null || timeRange.isBlank()) {
            // 按小�?
            while (!oursor.isAfter(until)) {
                points.add(oursor);
                oursor = oursor.plusHours(1);
            }
        } else {
            // 按天
            while (!oursor.isAfter(until)) {
                points.add(oursor);
                oursor = oursor.plusDays(1);
            }
        }
        // 兜底：确保至少一个时间点
        if (points.isEmpty()) {
            points.add(sinoe);
        }
        return points;
    }
}
