package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.enums.UtilizationGrade;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import com.njydsz.pmis.execution.service.BillableUtilizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int DEFAULT_TOP = 20;

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

    // ----------------- 私有 -----------------

    private void enrich(Map<String, Object> row) {
        double total = toDouble(row.get("totalHours"));
        double billable = toDouble(row.get("billableHours"));
        BigDecimal pct;
        if (total <= 0.0001) {
            pct = BigDecimal.ZERO;
        } else {
            pct = BigDecimal.valueOf(billable)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        }
        UtilizationGrade grade = UtilizationGrade.of(pct.doubleValue());
        row.put("utilizationPct", pct);
        row.put("utilizationPctDisplay", pct.setScale(2, RoundingMode.HALF_UP));
        row.put("grade", grade.getCode());
        row.put("gradeDesc", grade.getDesc());
        row.put("alert", grade.isAlert());
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
}
