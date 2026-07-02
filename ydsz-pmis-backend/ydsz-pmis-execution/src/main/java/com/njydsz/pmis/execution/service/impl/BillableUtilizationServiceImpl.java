package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.BillableUtilizationSnapshotDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.enums.UtilizationGrade;
import com.njydsz.pmis.execution.mapper.BillableUtilizationSnapshotMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import com.njydsz.pmis.execution.service.BillableUtilizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可计费利用率服务实现
 *
 * <p>数据源：pmis_execution_time_entry 表中 status='APPROVED' 的工时
 * <p>算法：utilization = billable / total * 100
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillableUtilizationServiceImpl implements BillableUtilizationService {

    private final TimeEntryMapper timeEntryMapper;
    private final BillableUtilizationSnapshotMapper snapshotMapper;
    private final RateInternalMapper rateInternalMapper;

    // HUNDRED reserved for future percentage calculations
    private static final int DEFAULT_TOP = 20;
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public List<Map<String, Object>> aggregate(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        List<Map<String, Object>> rows = safe(() -> timeEntryMapper.aggregateBillableByEmployee(range[0], range[1]));
        if (rows == null) rows = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            enrich(row);
            out.add(row);
        }
        return out;
    }

    @Override
    public Map<String, Object> personal(Long employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null) {
            throw new IllegalArgumentException("员工 ID 不能为空");
        }
        LocalDate[] range = normalizeRange(from, to);
        Map<String, Object> row = safe(() -> timeEntryMapper.aggregateBillableOne(employeeId, range[0], range[1]));
        if (row == null) row = new HashMap<>();
        row.put("employeeId", employeeId);
        enrich(row);
        return row;
    }

    @Override
    public List<Map<String, Object>> rank(LocalDate from, LocalDate to, int top) {
        if (top <= 0) top = DEFAULT_TOP;
        List<Map<String, Object>> all = aggregate(from, to);
        // 个人聚合（跨月合并）
        Map<Long, double[]> byEmp = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, String> levelMap = new HashMap<>();
        for (Map<String, Object> r : all) {
            Long emp = toLong(r.get("employeeId"));
            if (emp == null) continue;
            double[] acc = byEmp.computeIfAbsent(emp, k -> new double[2]);
            acc[0] += toDouble(r.get("totalHours"));
            acc[1] += toDouble(r.get("billableHours"));
            nameMap.putIfAbsent(emp, str(r.get("employeeName")));
            levelMap.putIfAbsent(emp, str(r.get("levelCode")));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : byEmp.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("employeeId", e.getKey());
            row.put("employeeName", nameMap.getOrDefault(e.getKey(), ""));
            row.put("levelCode", levelMap.getOrDefault(e.getKey(), ""));
            row.put("totalHours", e.getValue()[0]);
            row.put("billableHours", e.getValue()[1]);
            enrich(row);
            out.add(row);
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> m) -> toDouble(m.get("utilizationPct"))).reversed());
        if (out.size() > top) return out.subList(0, top);
        return out;
    }

    @Override
    public Map<String, Object> overall(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        List<Map<String, Object>> rows = aggregate(range[0], range[1]);
        double total = 0, billable = 0;
        for (Map<String, Object> r : rows) {
            total += toDouble(r.get("totalHours"));
            billable += toDouble(r.get("billableHours"));
        }
        Map<String, Object> row = new HashMap<>();
        row.put("totalHours", total);
        row.put("billableHours", billable);
        row.put("employeeCount", countDistinctEmployee(rows));
        enrich(row);
        return row;
    }

    @Override
    public List<Map<String, Object>> scanAlerts(LocalDate from, LocalDate to) {
        List<Map<String, Object>> all = aggregate(from, to);
        // 个人合并
        Map<Long, double[]> byEmp = new HashMap<>();
        Map<Long, Map<String, Object>> meta = new HashMap<>();
        for (Map<String, Object> r : all) {
            Long emp = toLong(r.get("employeeId"));
            if (emp == null) continue;
            double[] acc = byEmp.computeIfAbsent(emp, k -> new double[2]);
            acc[0] += toDouble(r.get("totalHours"));
            acc[1] += toDouble(r.get("billableHours"));
            meta.computeIfAbsent(emp, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("employeeId", emp);
                m.put("employeeName", r.get("employeeName"));
                m.put("levelCode", r.get("levelCode"));
                return m;
            });
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : byEmp.entrySet()) {
            Map<String, Object> row = new HashMap<>(meta.get(e.getKey()));
            row.put("totalHours", e.getValue()[0]);
            row.put("billableHours", e.getValue()[1]);
            enrich(row);
            String g = str(row.get("grade"));
            if ("WARN".equalsIgnoreCase(g) || "CRITICAL".equalsIgnoreCase(g)) {
                out.add(row);
            }
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> m) -> toDouble(m.get("utilizationPct"))));
        return out;
    }

    @Override
    public Map<String, Object> evaluate(double totalHours, double billableHours) {
        Map<String, Object> row = new HashMap<>();
        row.put("totalHours", totalHours);
        row.put("billableHours", billableHours);
        enrich(row);
        return row;
    }

    @Override
    public Map<String, Object> recompute(String period, boolean recomputeAll) {
        long start = System.currentTimeMillis();
        String p = (period == null || period.isBlank())
                ? LocalDate.now().minusMonths(1).format(PERIOD_FMT)
                : period;
        YearMonth ym;
        try {
            ym = YearMonth.parse(p, PERIOD_FMT);
        } catch (Exception e) {
            throw new IllegalArgumentException("period 必须为 yyyy-MM 格式: " + period);
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        if (recomputeAll) {
            try {
                int removed = snapshotMapper.deleteByPeriod(p);
                log.info("[BillableUtilization] recompute 软删 period={} count={}", p, removed);
            } catch (Exception e) {
                log.warn("[BillableUtilization] 软删失败: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> rows = safe(
                () -> timeEntryMapper.aggregateBillableByEmployee(from, to));
        if (rows == null) rows = new ArrayList<>();

        // 拼接部门（来自 RateInternal）
        Map<String, String> deptByLevel = deptByLevelSafe();

        int affected = 0;
        for (Map<String, Object> raw : rows) {
            try {
                BillableUtilizationSnapshotDO snap = toSnapshot(p, raw, from, to, deptByLevel);
                int n = snapshotMapper.upsert(snap);
                affected += Math.max(n, 0);
            } catch (Exception e) {
                log.warn("[BillableUtilization] 写入快照失败 employee={} : {}",
                        raw.get("employee_id"), e.getMessage());
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("period", p);
        out.put("recomputeAll", recomputeAll);
        out.put("affectedCount", affected);
        out.put("rangeFrom", from.toString());
        out.put("rangeTo", to.toString());
        out.put("recomputeAt", LocalDateTime.now().toString());
        out.put("costMs", System.currentTimeMillis() - start);
        return out;
    }

    @Override
    public Map<String, Object> snapshotAverage(String period) {
        String p = (period == null || period.isBlank())
                ? LocalDate.now().format(PERIOD_FMT)
                : period;
        Map<String, Object> out = safe(() -> snapshotMapper.averageByPeriod(p));
        if (out == null) out = new HashMap<>();
        // 兜底：快照表无数据 → 实时聚合
        if (out.isEmpty() || out.get("headcount") == null
                || "0".equals(String.valueOf(out.get("headcount")))) {
            return realtimeAverageFallback(p);
        }
        // 补齐展示字段
        out.put("source", "SNAPSHOT");
        out.put("period", p);
        return out;
    }

    // ----------------- 私有 -----------------

    private void enrich(Map<String, Object> row) {
        double total = toDouble(firstNonNull(row, "totalHours", "total_hours"));
        double billable = toDouble(firstNonNull(row, "billableHours", "billable_hours"));
        BigDecimal pct;
        if (total <= 0.0001) {
            pct = BigDecimal.ZERO;
        } else {
            double raw = billable / total * 100d;
            // billable > total 钳制为 100%
            if (raw > 100d) raw = 100d;
            if (raw < 0d) raw = 0d;
            pct = BigDecimal.valueOf(raw).setScale(4, RoundingMode.HALF_UP);
        }
        UtilizationGrade grade = UtilizationGrade.of(pct.doubleValue());
        // 标准化为 camelCase，便于下游直接 get("totalHours") / get("billableHours")
        row.put("totalHours", total);
        row.put("billableHours", billable);
        row.put("utilizationPct", pct);
        row.put("utilizationPctDisplay", pct.setScale(2, RoundingMode.HALF_UP));
        row.put("grade", grade.getCode());
        row.put("gradeDesc", grade.getDesc());
        row.put("alert", grade.isAlert());
        // 同时标准化 employeeId / employeeName / levelCode（MyBatis 默认下划线）
        normalizeKey(row, "employeeId", "employee_id");
        normalizeKey(row, "employeeName", "employee_name");
        normalizeKey(row, "levelCode", "level_code");
    }

    private static void normalizeKey(Map<String, Object> row, String camelKey, String snakeKey) {
        Object v = row.get(camelKey);
        if (v != null) return;
        Object src = row.get(snakeKey);
        if (src != null) {
            row.put(camelKey, src);
        }
    }

    private static Object firstNonNull(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) {
            throw new IllegalArgumentException("截止日期不能早于起始日期");
        }
        return new LocalDate[]{f, t};
    }

    private long countDistinctEmployee(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(r -> toLong(r.get("employeeId")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    }

    private static double toDouble(Object o) {
        if (o == null) return 0d;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @FunctionalInterface
    private interface SupplierX<T> {
        T get();
    }

    private <T> T safe(SupplierX<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            log.warn("[Utilization] 数据查询失败: {}", e.getMessage());
            return null;
        }
    }

    private BillableUtilizationSnapshotDO toSnapshot(String period, Map<String, Object> raw,
                                                     LocalDate from, LocalDate to,
                                                     Map<String, String> deptByLevel) {
        BillableUtilizationSnapshotDO snap = new BillableUtilizationSnapshotDO();
        snap.setPeriod(period);
        Long empId = toLong(firstNonNull(raw, "employeeId", "employee_id"));
        if (empId == null) {
            throw new IllegalArgumentException("employee_id 缺失");
        }
        snap.setEmployeeId(empId);
        snap.setEmployeeName(str(firstNonNull(raw, "employeeName", "employee_name")));
        String level = str(firstNonNull(raw, "levelCode", "level_code"));
        snap.setLevelCode(level);
        snap.setDepartment(deptByLevel.getOrDefault(level, ""));

        BigDecimal total = toBd(firstNonNull(raw, "totalHours", "total_hours"));
        BigDecimal billable = toBd(firstNonNull(raw, "billableHours", "billable_hours"));
        BigDecimal overtime = toBd(firstNonNull(raw, "overtimeHours", "overtime_hours"));
        BigDecimal leave = toBd(firstNonNull(raw, "leaveHours", "leave_hours"));
        BigDecimal training = toBd(firstNonNull(raw, "trainingHours", "training_hours"));

        snap.setTotalHours(total);
        snap.setBillableHours(billable);
        snap.setOvertimeHours(overtime);
        snap.setLeaveHours(leave);
        snap.setTrainingHours(training);

        // bench = total - billable - leave - training（钳制为 0）
        BigDecimal bench = total.subtract(billable).subtract(leave).subtract(training);
        if (bench.signum() < 0) bench = BigDecimal.ZERO;
        snap.setBenchHours(bench);

        BigDecimal pct;
        if (total.signum() == 0) {
            pct = BigDecimal.ZERO;
        } else {
            double raw2 = billable.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
            if (raw2 > 1d) raw2 = 1d;
            if (raw2 < 0d) raw2 = 0d;
            pct = BigDecimal.valueOf(raw2);
        }
        snap.setUtilizationPct(pct);
        UtilizationGrade g = UtilizationGrade.of(pct.doubleValue() * 100d);
        snap.setGrade(g.getCode());
        snap.setRangeFrom(from);
        snap.setRangeTo(to);
        snap.setSnapshotAt(LocalDateTime.now());
        snap.setSource("SCHEDULER");
        snap.setDeleted(0);
        return snap;
    }

    private Map<String, String> deptByLevelSafe() {
        Map<String, String> out = new HashMap<>();
        try {
            List<RateInternalDO> all = rateInternalMapper.selectAll();
            if (all != null) {
                for (RateInternalDO r : all) {
                    String lvl = r.getLevelCode();
                    String dept = r.getDepartmentName();
                    if (lvl != null && !lvl.isBlank() && dept != null && !dept.isBlank()) {
                        out.putIfAbsent(lvl, dept);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Utilization] 读取 RateInternal 失败: {}", e.getMessage());
        }
        return out;
    }

    private Map<String, Object> realtimeAverageFallback(String period) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period, PERIOD_FMT);
        } catch (Exception e) {
            ym = YearMonth.now();
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<Map<String, Object>> rows = aggregate(from, to);
        double total = 0, billable = 0;
        long head = 0;
        for (Map<String, Object> r : rows) {
            total += toDouble(r.get("totalHours"));
            billable += toDouble(r.get("billableHours"));
        }
        head = countDistinctEmployee(rows);
        Map<String, Object> out = new HashMap<>();
        double pct = total <= 0.0001 ? 0d : Math.min(1d, billable / total);
        out.put("avg_pct", pct);
        out.put("sum_total", total);
        out.put("sum_billable", billable);
        out.put("sum_bench", Math.max(0d, total - billable));
        out.put("headcount", head);
        out.put("source", "REALTIME");
        out.put("period", period);
        return out;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
