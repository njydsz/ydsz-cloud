package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.project.entity.EvmMeasureDO;
import com.njydsz.pmis.project.entity.ProfitSnapshotDO;
import com.njydsz.pmis.project.entity.RateCardDO;
import com.njydsz.pmis.project.entity.RateInternalDO;
import com.njydsz.pmis.project.entity.RiskDO;
import com.njydsz.pmis.project.feign.BenchResourceClient;
import com.njydsz.pmis.project.mapper.EvmMeasureMapper;
import com.njydsz.pmis.project.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.project.mapper.RateCardMapper;
import com.njydsz.pmis.project.mapper.RateInternalMapper;
import com.njydsz.pmis.project.mapper.RiskMapper;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import com.njydsz.pmis.project.service.AdvancedReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 高级报表 Service 实现
 *
 * <p>提供 EVM 报表、利用率排名、待岗成本、双费率利润对比、资源甘特图、风险看板六类高级报表。
 * 跨模块数据通过 Feign + try-catch 回退到 0，避免单模块故障导致报表整体不可用。
 *
 * <p>历史版本曾使用类级 {@code @SuppressWarnings("null")} 抑制 Eclipse JDT 的 null 分析警告，
 * 但该 token 仅 Eclipse 识别（Maven/javac 不识别），且会掩盖真实的 null 风险。已移除，
 * 改为在 {@link #toBigDecimal(Object)}、{@link #toLong(Object)}、{@link #stringOf(Object)}
 * 等私有方法中统一处理 null，调用方无需关心。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvancedReportServiceImpl implements AdvancedReportService {

    private final EvmMeasureMapper evmMapper;
    private final RateCardMapper rateCardMapper;
    private final RateInternalMapper rateInternalMapper;
    private final RiskMapper riskMapper;
    private final TimeEntryMapper timeEntryMapper;
    private final ThresholdProvider thresholdProvider;
    private final BenchResourceClient benchResourceClient;
    private final ProfitSnapshotMapper profitSnapshotMapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 标准月工作时长（8h × 21.75 工作日） */
    private static final BigDecimal STANDARD_MONTHLY_HOURS = new BigDecimal("174");

    @Override
    public List<Map<String, Object>> evmReport(Long initiationId) {
        if (initiationId == null) {
            return new ArrayList<>();
        }
        List<EvmMeasureDO> list = evmMapper.selectByInitiation(initiationId);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (EvmMeasureDO m : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("period", m.getPeriod());
            row.put("wbsTaskId", m.getWbsTaskId());
            row.put("pv", m.getPv());
            row.put("ev", m.getEv());
            row.put("ac", m.getAc());
            row.put("bac", m.getBac());
            row.put("cpi", m.getCpi());
            row.put("spi", m.getSpi());
            row.put("cv", m.getCv());
            row.put("sv", m.getSv());
            row.put("vac", m.getVac());
            row.put("alertLevel", m.getAlertLevel());
            row.put("alertReason", m.getAlertReason());
            out.add(row);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> utilizationRank(int top) {
        // 默认近 3 个月
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(3).withDayOfMonth(1);
        return utilizationRank(top, from, to, null);
    }

    @Override
    public List<Map<String, Object>> utilizationRank(int top, LocalDate from, LocalDate to, String department) {
        int limit = top <= 0 ? 10 : top;
        LocalDate realFrom = from == null ? LocalDate.now().minusMonths(3).withDayOfMonth(1) : from;
        LocalDate realTo = to == null ? LocalDate.now() : to;

        // 1) 拉取工时聚合（员工 × 月份）
        List<Map<String, Object>> aggregates = safeAll(timeEntryMapper,
                m -> m.aggregateBillableByEmployee(realFrom, realTo));
        if (aggregates.isEmpty()) {
            return new ArrayList<>();
        }

        // 2) 加载职级内部成本率（用于折算人效金额）
        Map<String, BigDecimal> levelCostMap = safeAll(rateInternalMapper, RateInternalMapper::selectAll)
                .stream()
                .collect(Collectors.toMap(
                        RateInternalDO::getLevelCode,
                        r -> nz(r.getCostAmount()),
                        (a, b) -> a));

        // 3) 部门过滤 + 按员工汇总
        Map<Long, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : aggregates) {
            Long empId = toLong(row.get("employee_id"));
            if (empId == null) {
                continue;
            }
            String empName = stringOf(row.get("employee_name"));
            String levelCode = stringOf(row.get("level_code"));
            BigDecimal total = toBigDecimal(row.get("total_hours"));
            BigDecimal billable = toBigDecimal(row.get("billable_hours"));
            BigDecimal overtime = toBigDecimal(row.get("overtime_hours"));
            BigDecimal leave = toBigDecimal(row.get("leave_hours"));
            BigDecimal training = toBigDecimal(row.get("training_hours"));

            // 部门过滤
            if (StringUtils.hasText(department)) {
                String deptFromUser = resolveDepartment(empId);
                if (deptFromUser != null && !department.equalsIgnoreCase(deptFromUser)) {
                    continue;
                }
            }

            Map<String, Object> acc = merged.computeIfAbsent(empId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("employeeId", empId);
                m.put("employeeName", empName);
                m.put("levelCode", levelCode);
                m.put("totalHours", ZERO);
                m.put("billableHours", ZERO);
                m.put("overtimeHours", ZERO);
                m.put("leaveHours", ZERO);
                m.put("trainingHours", ZERO);
                m.put("periods", new ArrayList<String>());
                return m;
            });
            acc.put("totalHours", toBigDecimal(acc.get("totalHours")).add(total));
            acc.put("billableHours", toBigDecimal(acc.get("billableHours")).add(billable));
            acc.put("overtimeHours", toBigDecimal(acc.get("overtimeHours")).add(overtime));
            acc.put("leaveHours", toBigDecimal(acc.get("leaveHours")).add(leave));
            acc.put("trainingHours", toBigDecimal(acc.get("trainingHours")).add(training));
            @SuppressWarnings("unchecked")
            List<String> periods = (List<String>) acc.get("periods");
            Object period = row.get("period");
            if (period != null && !periods.contains(period.toString())) {
                periods.add(period.toString());
            }
        }

        // 4) 转换为输出行，按可计费利用率降序
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : merged.values()) {
            BigDecimal total = toBigDecimal(m.get("totalHours"));
            BigDecimal billable = toBigDecimal(m.get("billableHours"));
            BigDecimal leave = toBigDecimal(m.get("leaveHours"));
            BigDecimal working = total.subtract(leave);
            BigDecimal utilization = working.signum() == 0
                    ? ZERO
                    : billable.divide(working, 4, RoundingMode.HALF_UP);
            BigDecimal utilizationPct = utilization.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

            String levelCode = stringOf(m.get("levelCode"));
            BigDecimal costRate = levelCostMap.getOrDefault(levelCode, ZERO);
            // 人效贡献金额 = billable_hours / 8 * costRate
            BigDecimal efficiencyAmount = billable.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP)
                    .multiply(costRate)
                    .setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", m.get("employeeId"));
            row.put("employeeName", m.get("employeeName"));
            row.put("levelCode", levelCode);
            row.put("totalHours", total);
            row.put("billableHours", billable);
            row.put("workingHours", working);
            row.put("overtimeHours", m.get("overtimeHours"));
            row.put("leaveHours", leave);
            row.put("trainingHours", m.get("trainingHours"));
            row.put("utilizationRate", utilization);
            row.put("utilizationPct", utilizationPct);
            row.put("costRate", costRate);
            row.put("efficiencyAmount", efficiencyAmount);
            row.put("periodCount", ((List<?>) m.get("periods")).size());
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) ->
                toBigDecimal(m.get("utilizationPct"))).reversed());
        if (out.size() > limit) {
            return out.subList(0, limit);
        }
        return out;
    }

    @Override
    public Map<String, Object> utilizationOf(Long employeeId, LocalDate from, LocalDate to) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (employeeId == null) {
            return out;
        }
        LocalDate realFrom = from == null ? LocalDate.now().minusMonths(3).withDayOfMonth(1) : from;
        LocalDate realTo = to == null ? LocalDate.now() : to;
        Map<String, Object> agg = safeOne(timeEntryMapper,
                m -> m.aggregateBillableOne(employeeId, realFrom, realTo));
        if (agg == null) {
            agg = new HashMap<>();
        }
        BigDecimal total = toBigDecimal(agg.get("total_hours"));
        BigDecimal billable = toBigDecimal(agg.get("billable_hours"));
        BigDecimal overtime = toBigDecimal(agg.get("overtime_hours"));
        BigDecimal leave = toBigDecimal(agg.get("leave_hours"));
        BigDecimal training = toBigDecimal(agg.get("training_hours"));
        BigDecimal working = total.subtract(leave);
        BigDecimal utilization = working.signum() == 0
                ? ZERO
                : billable.divide(working, 4, RoundingMode.HALF_UP);
        BigDecimal utilizationPct = utilization.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

        out.put("employeeId", employeeId);
        out.put("from", realFrom.toString());
        out.put("to", realTo.toString());
        out.put("totalHours", total);
        out.put("billableHours", billable);
        out.put("workingHours", working);
        out.put("overtimeHours", overtime);
        out.put("leaveHours", leave);
        out.put("trainingHours", training);
        out.put("utilizationRate", utilization);
        out.put("utilizationPct", utilizationPct);
        out.put("standardHours", STANDARD_MONTHLY_HOURS);
        return out;
    }

    @Override
    public List<Map<String, Object>> utilizationByDepartment(LocalDate from, LocalDate to) {
        LocalDate realFrom = from == null ? LocalDate.now().minusMonths(3).withDayOfMonth(1) : from;
        LocalDate realTo = to == null ? LocalDate.now() : to;
        List<Map<String, Object>> aggregates = safeAll(timeEntryMapper,
                m -> m.aggregateBillableByEmployee(realFrom, realTo));
        if (aggregates.isEmpty()) {
            return new ArrayList<>();
        }
        // 部门名通过 RateInternal 模糊匹配职级-部门表
        Map<String, String> levelDeptMap = safeAll(rateInternalMapper, RateInternalMapper::selectAll)
                .stream()
                .filter(r -> StringUtils.hasText(r.getLevelCode()) && StringUtils.hasText(r.getDepartmentName()))
                .collect(Collectors.toMap(
                        RateInternalDO::getLevelCode,
                        RateInternalDO::getDepartmentName,
                        (a, b) -> a));

        Map<String, BigDecimal> totalByDept = new HashMap<>();
        Map<String, BigDecimal> billableByDept = new HashMap<>();
        Map<String, BigDecimal> leaveByDept = new HashMap<>();
        Map<String, BigDecimal> overtimeByDept = new HashMap<>();
        Map<String, Long> headByDept = new HashMap<>();

        for (Map<String, Object> row : aggregates) {
            String levelCode = stringOf(row.get("level_code"));
            String dept = levelDeptMap.getOrDefault(levelCode, "未分配");
            BigDecimal total = toBigDecimal(row.get("total_hours"));
            BigDecimal billable = toBigDecimal(row.get("billable_hours"));
            BigDecimal overtime = toBigDecimal(row.get("overtime_hours"));
            BigDecimal leave = toBigDecimal(row.get("leave_hours"));
            totalByDept.merge(dept, total, BigDecimal::add);
            billableByDept.merge(dept, billable, BigDecimal::add);
            overtimeByDept.merge(dept, overtime, BigDecimal::add);
            leaveByDept.merge(dept, leave, BigDecimal::add);
            headByDept.merge(dept, 1L, Long::sum);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (String dept : totalByDept.keySet()) {
            BigDecimal total = totalByDept.getOrDefault(dept, ZERO);
            BigDecimal billable = billableByDept.getOrDefault(dept, ZERO);
            BigDecimal leave = leaveByDept.getOrDefault(dept, ZERO);
            BigDecimal overtime = overtimeByDept.getOrDefault(dept, ZERO);
            BigDecimal working = total.subtract(leave);
            BigDecimal utilization = working.signum() == 0
                    ? ZERO
                    : billable.divide(working, 4, RoundingMode.HALF_UP);
            BigDecimal utilizationPct = utilization.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("department", dept);
            row.put("headcount", headByDept.getOrDefault(dept, 0L));
            row.put("totalHours", total);
            row.put("billableHours", billable);
            row.put("workingHours", working);
            row.put("overtimeHours", overtime);
            row.put("leaveHours", leave);
            row.put("utilizationRate", utilization);
            row.put("utilizationPct", utilizationPct);
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) ->
                toBigDecimal(m.get("utilizationPct"))).reversed());
        return out;
    }

    @Override
    public List<Map<String, Object>> benchCostReport() {
        return benchCostReport(LocalDate.now().minusDays(30), LocalDate.now());
    }

    @Override
    public List<Map<String, Object>> benchCostReport(LocalDate from, LocalDate to) {
        LocalDate realFrom = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate realTo = to == null ? LocalDate.now() : to;
        // Bench 视为 workType = BENCH / INTERNAL_LEARNING / NON_BILLABLE 且 billable = 0
        List<Map<String, Object>> aggregates = safeAll(timeEntryMapper,
                m -> m.aggregateBillableByEmployee(realFrom, realTo));
        if (aggregates.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, BigDecimal> levelCostMap = safeAll(rateInternalMapper, RateInternalMapper::selectAll)
                .stream()
                .collect(Collectors.toMap(
                        RateInternalDO::getLevelCode,
                        r -> nz(r.getCostAmount()),
                        (a, b) -> a));

        Map<Long, BigDecimal> benchHoursByEmp = new HashMap<>();
        Map<Long, BigDecimal> billableHoursByEmp = new HashMap<>();
        Map<Long, Map<String, Object>> metaByEmp = new HashMap<>();
        for (Map<String, Object> row : aggregates) {
            Long empId = toLong(row.get("employee_id"));
            if (empId == null) {
                continue;
            }
            BigDecimal total = toBigDecimal(row.get("total_hours"));
            BigDecimal billable = toBigDecimal(row.get("billable_hours"));
            BigDecimal leave = toBigDecimal(row.get("leave_hours"));
            BigDecimal overtime = toBigDecimal(row.get("overtime_hours"));
            BigDecimal training = toBigDecimal(row.get("training_hours"));
            // 闲置 = 总工时 - 可计费工时 - 请假工时 - 培训工时
            BigDecimal bench = total.subtract(billable).subtract(leave).subtract(training);
            if (bench.signum() < 0) {
                bench = ZERO;
            }
            benchHoursByEmp.merge(empId, bench, BigDecimal::add);
            billableHoursByEmp.merge(empId, billable, BigDecimal::add);
            metaByEmp.computeIfAbsent(empId, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("employeeName", stringOf(row.get("employee_name")));
                m.put("levelCode", stringOf(row.get("level_code")));
                return m;
            });
            // 累加 overtime 作为加班参考
            metaByEmp.get(empId).put("overtimeHours",
                    toBigDecimal(metaByEmp.get(empId).get("overtimeHours")).add(overtime));
        }

        // 跨模块真实聚合：从 user 服务拉取 Bench 仪表盘作为池级汇总
        BigDecimal totalIdleCostFromUser = fetchUserBenchIdleCost();
        String benchSource = totalIdleCostFromUser.signum() > 0 ? "USER_FEIGN" : "LOCAL_AGG";

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : benchHoursByEmp.entrySet()) {
            Long empId = e.getKey();
            BigDecimal benchHours = e.getValue();
            if (benchHours.signum() <= 0) {
                continue;
            }
            BigDecimal billableHours = billableHoursByEmp.getOrDefault(empId, ZERO);
            Map<String, Object> meta = metaByEmp.getOrDefault(empId, Map.of());
            String levelCode = stringOf(meta.get("levelCode"));
            BigDecimal costRate = levelCostMap.getOrDefault(levelCode, ZERO);
            BigDecimal benchDays = benchHours.divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP);
            BigDecimal benchCost = benchDays.multiply(costRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = benchHours.add(billableHours);
            BigDecimal benchRate = total.signum() == 0
                    ? ZERO
                    : benchHours.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

            String alertLevel;
            int yellowDays = thresholdProvider.benchYellowDays();
            int redDays = thresholdProvider.benchRedDays();
            if (benchDays.compareTo(new BigDecimal(redDays)) >= 0) {
                alertLevel = "RED";
            } else if (benchDays.compareTo(new BigDecimal(yellowDays)) >= 0) {
                alertLevel = "YELLOW";
            } else {
                alertLevel = "GREEN";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", empId);
            row.put("employeeName", meta.get("employeeName"));
            row.put("levelCode", levelCode);
            row.put("benchHours", benchHours);
            row.put("benchDays", benchDays);
            row.put("billableHours", billableHours);
            row.put("costRate", costRate);
            row.put("benchCost", benchCost);
            row.put("benchRate", benchRate);
            row.put("alertLevel", alertLevel);
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) ->
                toBigDecimal(m.get("benchCost"))).reversed());
        // 附加 pool 级汇总（来自 user 服务）
        Map<String, Object> poolSummary = new LinkedHashMap<>();
        poolSummary.put("type", "POOL_SUMMARY");
        poolSummary.put("totalIdleCost", totalIdleCostFromUser);
        poolSummary.put("source", benchSource);
        poolSummary.put("fromDate", realFrom.toString());
        poolSummary.put("toDate", realTo.toString());
        out.add(0, poolSummary);
        return out;
    }

    /**
     * 跨模块真实聚合：从 user 服务拉取 Bench 累计闲置成本
     *
     * @return 累计闲置成本；user 服务不可用时返回 ZERO
     */
    private BigDecimal fetchUserBenchIdleCost() {
        try {
            Result<Map<String, Object>> resp = benchResourceClient.getBenchDashboard();
            if (resp == null || resp.getData() == null) {
                return ZERO;
            }
            Object cost = resp.getData().get("totalIdleCost");
            return toBigDecimal(cost);
        } catch (Exception e) {
            log.error("[AdvancedReport] Bench 仪表盘 Feign 调用失败: {}", e.getMessage());
            return ZERO;
        }
    }

    @Override
    public List<Map<String, Object>> dualRateProfitCompare(String period) {
        List<RateCardDO> cards = safeAll(rateCardMapper, RateCardMapper::selectAll);
        List<RateInternalDO> internals = safeAll(rateInternalMapper, RateInternalMapper::selectAll);
        Map<String, RateCardDO> cardMap = cards.stream()
                .collect(Collectors.toMap(RateCardDO::getLevelCode, c -> c, (a, b) -> a));
        Map<String, RateInternalDO> internalMap = internals.stream()
                .collect(Collectors.toMap(RateInternalDO::getLevelCode, c -> c, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String level : cardMap.keySet()) {
            RateCardDO card = cardMap.get(level);
            RateInternalDO internal = internalMap.get(level);
            BigDecimal external = card == null ? ZERO : nz(card.getRateAmount());
            BigDecimal internalCost = internal == null ? ZERO : nz(internal.getCostAmount());
            BigDecimal diff = external.subtract(internalCost);
            BigDecimal margin = external.signum() == 0
                    ? ZERO
                    : diff.divide(external, 4, RoundingMode.HALF_UP);
            Map<String, Object> row = new HashMap<>();
            row.put("levelCode", level);
            row.put("externalRate", external);
            row.put("internalCost", internalCost);
            row.put("diff", diff);
            row.put("margin", margin);
            out.add(row);
        }
        if (StringUtils.hasText(period)) {
            out.sort(Comparator.comparing((Map<String, Object> m) ->
                    toBigDecimal(m.get("diff"))).reversed());
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> resourceGantt(Long initiationId) {
        if (initiationId == null) {
            return new ArrayList<>();
        }
        // 跨模块真实聚合：调用 user 服务获取资源分配
        List<Map<String, Object>> assignments;
        try {
            Result<List<Map<String, Object>>> resp = benchResourceClient.listResourceAssignmentsByInitiation(initiationId);
            assignments = (resp == null || resp.getData() == null) ? List.of() : resp.getData();
        } catch (Exception e) {
            log.error("[AdvancedReport] 资源分配 Feign 调用失败 initiationId={} err={}",
                    initiationId, e.getMessage());
            return new ArrayList<>();
        }
        if (assignments.isEmpty()) {
            return new ArrayList<>();
        }
        // 转换为甘特图数据：每条分配 = 一行 (employeeId, employeeName, start, end, allocation, status, billable)
        List<Map<String, Object>> out = new ArrayList<>(assignments.size());
        for (Map<String, Object> a : assignments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.get("id"));
            row.put("employeeId", a.get("employeeId"));
            row.put("employeeName", a.get("employeeName"));
            row.put("levelCode", a.get("levelCode"));
            row.put("poolType", a.get("poolType"));
            row.put("allocation", a.get("allocation"));
            row.put("status", a.get("status"));
            row.put("billable", a.get("billable"));
            row.put("dailyHours", a.get("dailyHours"));
            row.put("startDate", a.get("actualStartDate") != null
                    ? a.get("actualStartDate")
                    : a.get("plannedStartDate"));
            row.put("endDate", a.get("actualEndDate") != null
                    ? a.get("actualEndDate")
                    : a.get("plannedEndDate"));
            out.add(row);
        }
        // 按员工+起始日期排序
        out.sort(Comparator
                .comparing((Map<String, Object> m) -> stringOf(m.get("employeeName")))
                .thenComparing(m -> stringOf(m.get("startDate")) == null
                        ? "" : stringOf(m.get("startDate"))));
        return out;
    }

    @Override
    public List<Map<String, Object>> riskDashboard() {
        List<RiskDO> risks = new ArrayList<>();
        try {
            risks = riskMapper.selectAll();
        } catch (Exception e) {
            log.warn("[AdvancedReport] 风险数据查询失败: {}", e.getMessage());
        }
        Map<String, Integer> byLevel = new HashMap<>();
        Map<Long, Integer> byInitiation = new HashMap<>();
        for (RiskDO r : risks) {
            String level = r.getRiskLevel() == null ? "UNKNOWN" : r.getRiskLevel();
            byLevel.merge(level, 1, Integer::sum);
            byInitiation.merge(r.getInitiationId(), 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byLevel.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "BY_LEVEL");
            row.put("key", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        for (Map.Entry<Long, Integer> e : byInitiation.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "BY_INITIATION");
            row.put("initiationId", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        return out;
    }

    /** 风险矩阵档位（从弱到强） */
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    @Override
    public Map<String, Object> riskMatrix(Long initiationId, String riskType, String status) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 1) 拉取风险列表（异常时降级为空）
        List<RiskDO> risks = new ArrayList<>();
        try {
            risks = riskMapper.selectAll();
        } catch (Exception e) {
            log.warn("[AdvancedReport] riskMatrix 风险数据查询失败: {}", e.getMessage());
        }

        // 2) 过滤：项目、类型、状态
        String realRiskType = StringUtils.hasText(riskType) ? riskType.trim().toUpperCase() : null;
        String realStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        List<RiskDO> filtered = new ArrayList<>();
        for (RiskDO r : risks) {
            if (r == null) continue;
            if (initiationId != null && !initiationId.equals(r.getInitiationId())) continue;
            if (realRiskType != null && !realRiskType.equalsIgnoreCase(
                    r.getRiskType() == null ? "" : r.getRiskType().trim().toUpperCase())) {
                continue;
            }
            if (realStatus != null && !realStatus.equalsIgnoreCase(
                    r.getStatus() == null ? "" : r.getStatus().trim().toUpperCase())) {
                continue;
            }
            filtered.add(r);
        }

        // 3) 初始化 3x3 矩阵（9 个格子）
        Map<String, Map<String, Object>> cellMap = new LinkedHashMap<>();
        for (String p : RISK_LEVELS) {
            for (String i : RISK_LEVELS) {
                String key = p + "|" + i;
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("probability", p);
                cell.put("impact", i);
                cell.put("count", 0);
                cell.put("projectCount", 0);
                cell.put("cellProjectIds", new ArrayList<Long>());
                cell.put("level", deriveLevel(p, i));
                cellMap.put(key, cell);
            }
        }

        // 4) 按 riskType 聚合
        Map<String, Integer> byType = new HashMap<>();
        // summary
        int total = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        Map<Long, Integer> projectCount = new HashMap<>();

        for (RiskDO r : filtered) {
            String p = normalize(r.getProbability());
            String im = normalize(r.getImpact());
            String key = p + "|" + im;
            Map<String, Object> cell = cellMap.get(key);
            if (cell == null) continue;
            cell.put("count", ((Number) cell.get("count")).intValue() + 1);
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) cell.get("cellProjectIds");
            if (r.getInitiationId() != null && !ids.contains(r.getInitiationId())) {
                ids.add(r.getInitiationId());
            }
            cell.put("projectCount", ((List<?>) cell.get("cellProjectIds")).size());

            if (StringUtils.hasText(r.getRiskType())) {
                byType.merge(r.getRiskType().toUpperCase(), 1, Integer::sum);
            }
            if (r.getInitiationId() != null) {
                projectCount.merge(r.getInitiationId(), 1, Integer::sum);
            }
            total++;
            String cellLevel = (String) cell.get("level");
            if ("HIGH".equals(cellLevel)) high++;
            else if ("MEDIUM".equals(cellLevel)) medium++;
            else low++;
        }

        // 5) 矩阵输出（按 概率从高到低 + 影响从低到高，方便前端 heatmap 渲染）
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (String p : List.of("HIGH", "MEDIUM", "LOW")) {
            for (String im : List.of("LOW", "MEDIUM", "HIGH")) {
                String key = p + "|" + im;
                matrix.add(cellMap.get(key));
            }
        }

        List<Map<String, Object>> typeRows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byType.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("riskType", e.getKey());
            row.put("count", e.getValue());
            typeRows.add(row);
        }
        typeRows.sort(Comparator.comparing((Map<String, Object> m) ->
                ((Number) m.get("count")).intValue()).reversed());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", total);
        summary.put("highCount", high);
        summary.put("mediumCount", medium);
        summary.put("lowCount", low);
        summary.put("projectCount", projectCount.size());

        out.put("matrix", matrix);
        out.put("axisX", List.of("LOW", "MEDIUM", "HIGH"));   // impact
        out.put("axisY", List.of("HIGH", "MEDIUM", "LOW"));   // probability
        out.put("byType", typeRows);
        out.put("summary", summary);
        out.put("filter", Map.of(
                "initiationId", initiationId == null ? "" : initiationId,
                "riskType", realRiskType == null ? "" : realRiskType,
                "status", realStatus == null ? "" : realStatus));
        return out;
    }

    /**
     * 派生风险等级：LOW*LOW=LOW；HIGH*HIGH=HIGH；其它 MEDIUM
     */
    private static String deriveLevel(String probability, String impact) {
        if ("HIGH".equals(probability) && "HIGH".equals(impact)) {
            return "HIGH";
        }
        if ("LOW".equals(probability) && "LOW".equals(impact)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    /**
     * 标准化概率/影响字符串，null 或未知值默认 MEDIUM
     */
    private static String normalize(String s) {
        if (s == null) return "MEDIUM";
        String up = s.trim().toUpperCase();
        if (RISK_LEVELS.contains(up)) return up;
        return "MEDIUM";
    }

    @Override
    public Map<String, Object> projectHealthDashboard(List<Long> initiationIds, String health) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 1) 加载 EVM 健康聚合
        List<Map<String, Object>> evmRows = safeAll(evmMapper, m -> m.aggregateHealthByInitiation());
        // 2) 加载 ProfitSnapshot 全部
        List<ProfitSnapshotDO> snaps = new ArrayList<>();
        try {
            LambdaQueryWrapper<ProfitSnapshotDO> w = new LambdaQueryWrapper<>();
            w.orderByDesc(ProfitSnapshotDO::getSnapshotAt);
            List<ProfitSnapshotDO> all = profitSnapshotMapper.selectList(w);
            if (all != null) snaps.addAll(all);
        } catch (Exception e) {
            log.warn("[AdvancedReport] projectHealthDashboard 快照查询失败: {}", e.getMessage());
        }

        // 3) 取每个项目最新 snapshot
        Map<Long, ProfitSnapshotDO> latestSnap = new LinkedHashMap<>();
        for (ProfitSnapshotDO s : snaps) {
            if (s == null || s.getInitiationId() == null) continue;
            ProfitSnapshotDO prev = latestSnap.get(s.getInitiationId());
            if (prev == null
                    || (s.getSnapshotAt() != null
                        && (prev.getSnapshotAt() == null
                            || s.getSnapshotAt().isAfter(prev.getSnapshotAt())))) {
                latestSnap.put(s.getInitiationId(), s);
            }
        }

        // 4) 合并生成项目行
        Map<Long, Map<String, Object>> projectMap = new LinkedHashMap<>();
        // 从 EVM 行入
        for (Map<String, Object> row : evmRows) {
            Long initId = toLong(row.get("initiation_id"));
            if (initId == null) continue;
            Map<String, Object> p = projectMap.computeIfAbsent(initId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("initiationId", k);
                return m;
            });
            p.put("cpi", toBigDecimal(row.get("cpi")));
            p.put("spi", toBigDecimal(row.get("spi")));
            p.put("eac", toBigDecimal(row.get("eac")));
            p.put("vac", toBigDecimal(row.get("vac")));
            p.put("topAlert", row.getOrDefault("top_alert", "NORMAL"));
        }
        // 从 snapshot 补 margin
        for (Map.Entry<Long, ProfitSnapshotDO> e : latestSnap.entrySet()) {
            Map<String, Object> p = projectMap.computeIfAbsent(e.getKey(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("initiationId", k);
                return m;
            });
            p.put("margin", nz(e.getValue().getGrossMargin()));
            p.put("totalCost", nz(e.getValue().getTotalCost()));
            p.put("grossProfit", nz(e.getValue().getGrossProfit()));
            p.put("contractAmount", nz(e.getValue().getContractAmount()));
            p.put("recognizedRevenue", nz(e.getValue().getRecognizedRevenue()));
            p.put("period", e.getValue().getPeriod());
            p.put("snapshotAt", e.getValue().getSnapshotAt());
        }

        // 5) 计算健康度评分
        List<Map<String, Object>> projects = new ArrayList<>();
        int green = 0, yellow = 0, red = 0, unknown = 0;
        for (Map<String, Object> p : projectMap.values()) {
            BigDecimal cpi = toBigDecimal(p.get("cpi"));
            BigDecimal spi = toBigDecimal(p.get("spi"));
            BigDecimal margin = toBigDecimal(p.get("margin"));

            // CPI 钳制 [0, 2]：cpi=1 -> 50; cpi>=1.2 -> 60; cpi<=0.8 -> 0
            BigDecimal cpiPart = cpi.signum() == 0 ? ZERO
                    : cpi.multiply(new BigDecimal("50")).setScale(4, RoundingMode.HALF_UP);
            if (cpiPart.compareTo(new BigDecimal("60")) > 0) {
                cpiPart = new BigDecimal("60");
            }
            if (cpi.compareTo(new BigDecimal("0.8")) < 0) {
                cpiPart = ZERO;
            }
            // SPI 钳制 [0, 30]
            BigDecimal spiPart = spi.signum() == 0 ? ZERO
                    : spi.multiply(new BigDecimal("30")).setScale(4, RoundingMode.HALF_UP);
            if (spiPart.compareTo(new BigDecimal("30")) > 0) {
                spiPart = new BigDecimal("30");
            }
            if (spi.compareTo(new BigDecimal("0.8")) < 0) {
                spiPart = ZERO;
            }
            // margin 分数：margin=0.5 -> 100; margin>=0.5 -> 100
            BigDecimal marginScore = margin.multiply(new BigDecimal("200"))
                    .setScale(4, RoundingMode.HALF_UP);
            if (marginScore.compareTo(ZERO) < 0) {
                marginScore = ZERO;
            }
            if (marginScore.compareTo(new BigDecimal("20")) > 0) {
                marginScore = new BigDecimal("20");
            }

            BigDecimal score = cpiPart.add(spiPart).add(marginScore)
                    .setScale(2, RoundingMode.HALF_UP);

            String level;
            if (cpi.signum() == 0 && spi.signum() == 0 && margin.signum() == 0) {
                level = "UNKNOWN";
            } else if (score.compareTo(new BigDecimal("80")) >= 0) {
                level = "GREEN";
            } else if (score.compareTo(new BigDecimal("60")) >= 0) {
                level = "YELLOW";
            } else {
                level = "RED";
            }
            p.put("healthScore", score);
            p.put("healthLevel", level);
            p.put("cpiPart", cpiPart);
            p.put("spiPart", spiPart);
            p.put("marginScore", marginScore);
            projects.add(p);

            if ("GREEN".equals(level)) green++;
            else if ("YELLOW".equals(level)) yellow++;
            else if ("RED".equals(level)) red++;
            else unknown++;
        }

        // 6) 应用过滤
        String realHealth = StringUtils.hasText(health) ? health.trim().toUpperCase() : null;
        List<Long> filterIds = initiationIds == null ? List.of() : initiationIds.stream()
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
        boolean filterByIds = !filterIds.isEmpty();
        if (realHealth != null || filterByIds) {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> p : projects) {
                if (realHealth != null && !realHealth.equals(p.get("healthLevel"))) {
                    continue;
                }
                if (filterByIds && !filterIds.contains(toLong(p.get("initiationId")))) {
                    continue;
                }
                filtered.add(p);
            }
            projects = filtered;
        }

        // 7) 排序：健康度低到高（优先关注红/黄）
        projects.sort(Comparator
                .comparing((Map<String, Object> m) -> toBigDecimal(m.get("healthScore")))
                .thenComparing(m -> toLong(m.get("initiationId")) == null ? 0L : toLong(m.get("initiationId"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", projects.size());
        summary.put("greenCount", green);
        summary.put("yellowCount", yellow);
        summary.put("redCount", red);
        summary.put("unknownCount", unknown);

        out.put("projects", projects);
        out.put("summary", summary);
        out.put("filter", Map.of(
                "initiationIds", filterIds,
                "health", realHealth == null ? "" : realHealth));
        return out;
    }

    @Override
    public Map<String, Object> resourceUtilizationTrend(LocalDate from, LocalDate to, String department) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 默认时间窗：近 6 个月
        LocalDate f = from == null ? LocalDate.now().minusMonths(5).withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        if (t.isBefore(f)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        final LocalDate realFrom = f;
        final LocalDate realTo = t;
        final String realDept = department == null ? "" : department;

        // 1) 拉取工时聚合
        List<Map<String, Object>> aggregates = safeAll(timeEntryMapper,
                m -> m.aggregateBillableByEmployee(realFrom, realTo));
        if (aggregates.isEmpty()) {
            out.put("periods", List.of());
            out.put("series", List.of());
            out.put("yAxisConfig", List.of(
                    Map.of("name", "工时（h）", "position", "left"),
                    Map.of("name", "利用率（%）", "position", "right", "max", 100)));
            out.put("summary", Map.of(
                    "avgUtilization", 0,
                    "peakPeriod", "",
                    "peakUtilization", 0,
                    "totalBillableHours", ZERO,
                    "totalWorkingHours", ZERO));
            out.put("filter", Map.of("from", realFrom.toString(), "to", realTo.toString(), "department", realDept));
            return out;
        }

        // 2) 部门过滤：通过 RateInternal 职级-部门表
        Map<String, String> levelDeptMap = safeAll(rateInternalMapper, RateInternalMapper::selectAll)
                .stream()
                .filter(r -> StringUtils.hasText(r.getLevelCode()) && StringUtils.hasText(r.getDepartmentName()))
                .collect(Collectors.toMap(RateInternalDO::getLevelCode, RateInternalDO::getDepartmentName, (a, b) -> a));

        // 3) 按月聚合 (period -> {total, billable, overtime, leave, working})
        Map<String, BigDecimal> totalByMonth = new TreeMap<>();
        Map<String, BigDecimal> billableByMonth = new TreeMap<>();
        Map<String, BigDecimal> overtimeByMonth = new TreeMap<>();
        Map<String, BigDecimal> leaveByMonth = new TreeMap<>();
        Map<String, BigDecimal> trainingByMonth = new TreeMap<>();

        for (Map<String, Object> row : aggregates) {
            String levelCode = stringOf(row.get("level_code"));
            // 部门过滤
            if (StringUtils.hasText(realDept)) {
                String dept = levelDeptMap.getOrDefault(levelCode, "");
                if (!realDept.equalsIgnoreCase(dept)) {
                    continue;
                }
            }
            Object periodObj = row.get("period");
            if (periodObj == null) continue;
            String period = periodObj.toString();
            totalByMonth.merge(period, toBigDecimal(row.get("total_hours")), BigDecimal::add);
            billableByMonth.merge(period, toBigDecimal(row.get("billable_hours")), BigDecimal::add);
            overtimeByMonth.merge(period, toBigDecimal(row.get("overtime_hours")), BigDecimal::add);
            leaveByMonth.merge(period, toBigDecimal(row.get("leave_hours")), BigDecimal::add);
            trainingByMonth.merge(period, toBigDecimal(row.get("training_hours")), BigDecimal::add);
        }

        // 4) 计算每个月的工作工时、利用率
        List<String> periods = new ArrayList<>(totalByMonth.keySet());
        List<BigDecimal> totalArr = new ArrayList<>();
        List<BigDecimal> billableArr = new ArrayList<>();
        List<BigDecimal> overtimeArr = new ArrayList<>();
        List<BigDecimal> utilPctArr = new ArrayList<>();
        BigDecimal sumUtil = ZERO;
        int validMonthCount = 0;
        String peakPeriod = "";
        BigDecimal peakUtil = ZERO;
        BigDecimal totalBillable = ZERO;
        BigDecimal totalWorking = ZERO;
        for (String p : periods) {
            BigDecimal total = totalByMonth.getOrDefault(p, ZERO);
            BigDecimal billable = billableByMonth.getOrDefault(p, ZERO);
            BigDecimal overtime = overtimeByMonth.getOrDefault(p, ZERO);
            BigDecimal leave = leaveByMonth.getOrDefault(p, ZERO);
            BigDecimal working = total.subtract(leave);
            BigDecimal util = working.signum() == 0
                    ? ZERO
                    : billable.divide(working, 4, RoundingMode.HALF_UP);
            BigDecimal utilPct = util.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            totalArr.add(total);
            billableArr.add(billable);
            overtimeArr.add(overtime);
            utilPctArr.add(utilPct);
            totalBillable = totalBillable.add(billable);
            totalWorking = totalWorking.add(working);
            if (working.signum() > 0) {
                sumUtil = sumUtil.add(util);
                validMonthCount++;
                if (util.compareTo(peakUtil) > 0) {
                    peakUtil = util;
                    peakPeriod = p;
                }
            }
        }
        BigDecimal avgUtil = validMonthCount == 0
                ? ZERO
                : sumUtil.divide(new BigDecimal(validMonthCount), 4, RoundingMode.HALF_UP);
        BigDecimal avgUtilPct = avgUtil.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        BigDecimal peakUtilPct = peakUtil.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("name", "总工时");
        s1.put("type", "bar");
        s1.put("yAxisIndex", 0);
        s1.put("data", totalArr);
        s1.put("unit", "h");
        series.add(s1);
        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("name", "可计费工时");
        s2.put("type", "bar");
        s2.put("yAxisIndex", 0);
        s2.put("data", billableArr);
        s2.put("unit", "h");
        series.add(s2);
        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("name", "加班工时");
        s3.put("type", "bar");
        s3.put("yAxisIndex", 0);
        s3.put("data", overtimeArr);
        s3.put("unit", "h");
        series.add(s3);
        Map<String, Object> s4 = new LinkedHashMap<>();
        s4.put("name", "可计费利用率");
        s4.put("type", "line");
        s4.put("yAxisIndex", 1);
        s4.put("data", utilPctArr);
        s4.put("unit", "%");
        s4.put("smooth", true);
        series.add(s4);

        List<Map<String, Object>> yAxis = new ArrayList<>();
        Map<String, Object> ya0 = new LinkedHashMap<>();
        ya0.put("name", "工时（h）");
        ya0.put("position", "left");
        ya0.put("type", "value");
        yAxis.add(ya0);
        Map<String, Object> ya1 = new LinkedHashMap<>();
        ya1.put("name", "利用率（%）");
        ya1.put("position", "right");
        ya1.put("type", "value");
        ya1.put("max", 100);
        ya1.put("min", 0);
        yAxis.add(ya1);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("avgUtilization", avgUtil);
        summary.put("avgUtilizationPct", avgUtilPct);
        summary.put("peakPeriod", peakPeriod);
        summary.put("peakUtilization", peakUtil);
        summary.put("peakUtilizationPct", peakUtilPct);
        summary.put("totalBillableHours", totalBillable.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalWorkingHours", totalWorking.setScale(2, RoundingMode.HALF_UP));
        summary.put("monthCount", periods.size());

        out.put("periods", periods);
        out.put("series", series);
        out.put("yAxisConfig", yAxis);
        out.put("summary", summary);
        out.put("filter", Map.of(
                "from", realFrom.toString(),
                "to", realTo.toString(),
                "department", realDept));
        return out;
    }

    // ----------------- 私有 -----------------

    private String resolveDepartment(Long employeeId) {
        // 当前 Feign 调用 user 服务；失败时返回 null 表示不过滤
        // 实际接入 UserServiceClient 后填充
        return null;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return ZERO;
        }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String stringOf(Object o) {
        return o == null ? null : o.toString();
    }

    private <T, U> List<U> safeAll(T mapper, java.util.function.Function<T, List<U>> fn) {
        try {
            return fn.apply(mapper);
        } catch (Exception e) {
            log.warn("[AdvancedReport] 聚合查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private <T, U> U safeOne(T mapper, java.util.function.Function<T, U> fn) {
        try {
            return fn.apply(mapper);
        } catch (Exception e) {
            log.warn("[AdvancedReport] 单值聚合查询失败: {}", e.getMessage());
            return null;
        }
    }
}
