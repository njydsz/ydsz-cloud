package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import com.njydsz.pmis.execution.service.AdvancedReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
import java.util.stream.Collectors;

/**
 * 高级报表 Service 实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedReportServiceImpl implements AdvancedReportService {

    private final EvmMeasureMapper evmMapper;
    private final RateCardMapper rateCardMapper;
    private final RateInternalMapper rateInternalMapper;
    private final RiskMapper riskMapper;
    private final TimeEntryMapper timeEntryMapper;

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
            if (benchDays.compareTo(new BigDecimal("15")) >= 0) {
                alertLevel = "RED";
            } else if (benchDays.compareTo(new BigDecimal("7")) >= 0) {
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
        return out;
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
        // 跨模块：当前返回空，由后续集成 user Feign 后填充
        return new ArrayList<>();
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

    private <T, R> List<R> safeAll(T mapper, java.util.function.Function<T, List<R>> fn) {
        try {
            return fn.apply(mapper);
        } catch (Exception e) {
            log.warn("[AdvancedReport] 聚合查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private <T, R> R safeOne(T mapper, java.util.function.Function<T, R> fn) {
        try {
            return fn.apply(mapper);
        } catch (Exception e) {
            log.warn("[AdvancedReport] 单值聚合查询失败: {}", e.getMessage());
            return null;
        }
    }
}
