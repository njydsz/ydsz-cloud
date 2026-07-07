package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.CostAllocationDO;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.enums.CostType;
import com.njydsz.pmis.project.enums.ReconcileLevel;
import com.njydsz.pmis.project.enums.ReconcileType;
import com.njydsz.pmis.project.enums.TimeEntryStatus;
import com.njydsz.pmis.project.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 财务-工时数据交叉对账引擎
 *
 * <p>核心职责：
 * <ul>
 *   <li>校验工时与成本归集的双向一致性</li>
 *   <li>检测工时异常（单日 / 单周超限、跨项目冲突）</li>
 *   <li>检测成本归集异常（漏算、幽灵成本、分配超前）</li>
 *   <li>计算工时×费率的金额偏差</li>
 * </ul>
 *
 * <p>所有方法返回 {@link ReconcileResult} 列表，
 * 业务层可通过 {@link #buildReport(Long, List)} 汇总为 {@link ReconcileReport}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileHandler {

    /** 金额漂移容忍度（默认 1 元） */
    public static final BigDecimal AMOUNT_DRIFT_TOLERANCE = new BigDecimal("1.00");

    /** 默认单人天费率（兜底，当 RateCard 无法解析时使用） */
    public static final BigDecimal DEFAULT_DAILY_RATE = new BigDecimal("800.00");

    private final TimeEntryMapper timeEntryMapper;
    private final CostAllocationMapper costAllocationMapper;

    /**
     * 完整对账 - 包含所有检查项
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账结果列表
     */
    public List<ReconcileResult> reconcile(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> results = new ArrayList<>();
        results.addAll(reconcileMissingCost(initiationId));
        results.addAll(reconcileGhostCost(initiationId));
        results.addAll(reconcileDailyOverflow(initiationId, from, to));
        results.addAll(reconcileWeeklyOverload(initiationId, from, to));
        results.addAll(reconcileCrossProject(initiationId, from, to));
        results.addAll(reconcileAmountDrift(initiationId, from, to));
        results.addAll(reconcileAllocatedBeforeApproval(initiationId));
        return results;
    }

    /**
     * 构建报告
     *
     * @param initiationId 项目立项 ID
     * @param results      对账结果列表
     * @return 汇总后的对账报告
     */
    public ReconcileReport buildReport(Long initiationId, List<ReconcileResult> results) {
        ReconcileReport report = new ReconcileReport();
        report.setInitiationId(initiationId);
        report.setCheckAt(LocalDateTime.now());
        report.setTotal(results == null ? 0 : results.size());
        int info = 0, warn = 0, err = 0;
        Map<String, Long> countByType = new HashMap<>();
        if (results != null) {
            for (ReconcileResult r : results) {
                if (r.getLevel() == ReconcileLevel.INFO) info++;
                else if (r.getLevel() == ReconcileLevel.WARN) warn++;
                else if (r.getLevel() == ReconcileLevel.ERROR) err++;
                String key = r.getType() == null ? "UNKNOWN" : r.getType().getCode();
                countByType.merge(key, "1", (a, b) -> a + b);
            }
        }
        report.setInfoCount(info);
        report.setWarnCount(warn);
        report.setErrorCount(err);
        report.setCountByType(countByType);
        report.setResults(results);
        return report;
    }

    // ----------------------------------------------------------------
    // 1. 工时已 APPROVED 但缺失成本归集 (漏算)
    // ----------------------------------------------------------------

    /**
     * 检查工时已 APPROVED 但缺失成本归集（漏算）
     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileMissingCost(Long initiationId) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        List<TimeEntryDO> approved = timeEntryMapper.selectByInitiationAndDateRange(
                initiationId, null, null).stream()
                .filter(e -> TimeEntryStatus.APPROVED.getCode().equals(e.getStatus()))
                .toList();
        if (approved.isEmpty()) return out;

        List<CostAllocationDO> costs = costAllocationMapper.selectByInitiationAndPeriod(initiationId, null);
        Set<Long> costSourceIds = costs.stream()
                .filter(c -> CostType.LABOR.getCode().equals(c.getCostType()))
                .map(CostAllocationDO::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (TimeEntryDO e : approved) {
            if (e.getId() == null) continue;
            if (!costSourceIds.contains(e.getId())) {
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.MISSING_COST_FOR_APPROVED_TIME)
                        .level(ReconcileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .sourceId(e.getId())
                        .sourceType("TIME_ENTRY")
                        .description(String.format(
                                "工时 id=%d 状态=APPROVED 但未生成成本归集记录,工时=%sh,人员=%s",
                                e.getId(),
                                e.getHours() == null ? "?" : e.getHours().toPlainString(),
                                e.getEmployeeName() == null ? "?" : e.getEmployeeName()))
                        .actualValue(e.getHours())
                        .suggestion("调用 costAllocationService.syncFromTimeEntry 补齐成本")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 2. 工时已 REJECTED 但存在成本归集 (幽灵成本)
    // ----------------------------------------------------------------

    /**
     * 检查工时已 REJECTED 但存在成本归集（幽灵成本）
     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileGhostCost(Long initiationId) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        List<CostAllocationDO> costs = costAllocationMapper.selectByInitiationAndPeriod(initiationId, null);
        if (costs.isEmpty()) return out;
        List<CostAllocationDO> laborCosts = costs.stream()
                .filter(c -> CostType.LABOR.getCode().equals(c.getCostType()))
                .toList();
        if (laborCosts.isEmpty()) return out;

        // 收集 sourceId 对应的工时状态
        Set<Long> sourceIds = laborCosts.stream()
                .map(CostAllocationDO::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, TimeEntryDO> entryMap = new HashMap<>();
        for (Long sid : sourceIds) {
            if (sid == null) continue;
            TimeEntryDO e = timeEntryMapper.selectById(sid);
            if (e != null) entryMap.put(sid, e);
        }

        for (CostAllocationDO c : laborCosts) {
            if (c.getSourceId() == null) continue;
            TimeEntryDO e = entryMap.get(c.getSourceId());
            if (e == null) continue;
            if (TimeEntryStatus.REJECTED.getCode().equals(e.getStatus())) {
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.GHOST_COST_FOR_REJECTED_TIME)
                        .level(ReconcileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .sourceId(c.getId())
                        .sourceType("COST_ALLOCATION")
                        .description(String.format(
                                "工时 id=%d 状态=REJECTED 但存在成本归集 costId=%d 金额=%s",
                                e.getId(), c.getId(), c.getAmount()))
                        .actualValue(c.getAmount())
                        .suggestion("删除该幽灵成本记录或恢复工时状态")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 3. 单人单日工时超 24h
    // ----------------------------------------------------------------

    /**
     * 检查单人单日工时超 24h
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileDailyOverflow(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        // 按 (employeeId, entryDate) 聚合
        Map<String, BigDecimal> sumMap = new HashMap<>();
        Map<String, List<TimeEntryDO>> groupMap = new HashMap<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null || e.getHours() == null) continue;
            String key = e.getEmployeeId() + "|" + e.getEntryDate();
            sumMap.merge(key, e.getHours(), BigDecimal::add);
            groupMap.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, BigDecimal> en : sumMap.entrySet()) {
            if (en.getValue().compareTo(TimeEntryValidator.MAX_DAILY_HOURS) > 0) {
                String[] parts = en.getKey().split("\\|");
                Long empId = Long.parseLong(parts[0]);
                LocalDate date = LocalDate.parse(parts[1]);
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.DAILY_HOURS_OVERFLOW)
                        .level(ReconcileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(empId)
                        .description(String.format("员工 %s 在 %s 当日工时合计 %sh > 24h 上限",
                                empId, date, en.getValue().toPlainString()))
                        .actualValue(en.getValue())
                        .expectedValue(TimeEntryValidator.MAX_DAILY_HOURS)
                        .drift(en.getValue().subtract(TimeEntryValidator.MAX_DAILY_HOURS))
                        .suggestion("复核工时填报,可能存在重复录入")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 4. 单人单周工时超 60h
    // ----------------------------------------------------------------

    /**
     * 检查单人单周工时超 60h
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileWeeklyOverload(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        WeekFields wf = WeekFields.of(Locale.CHINA);
        // key: employeeId|weekYear|weekNumber
        Map<String, BigDecimal> sumMap = new HashMap<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null || e.getHours() == null) continue;
            int wn = e.getEntryDate().get(wf.weekOfWeekBasedYear());
            int wy = e.getEntryDate().get(wf.weekBasedYear());
            String key = e.getEmployeeId() + "|" + wy + "|" + wn;
            sumMap.merge(key, e.getHours(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> en : sumMap.entrySet()) {
            if (en.getValue().compareTo(TimeEntryValidator.MAX_WEEKLY_HOURS) > 0) {
                String[] parts = en.getKey().split("\\|");
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.WEEKLY_HOURS_OVERLOAD)
                        .level(ReconcileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(Long.parseLong(parts[0]))
                        .description(String.format("员工 %s 第 %s-%s 周工时合计 %sh > 60h 警戒",
                                parts[0], parts[1], parts[2], en.getValue().toPlainString()))
                        .actualValue(en.getValue())
                        .expectedValue(TimeEntryValidator.MAX_WEEKLY_HOURS)
                        .drift(en.getValue().subtract(TimeEntryValidator.MAX_WEEKLY_HOURS))
                        .suggestion("关注员工健康,必要时调整项目分配")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 5. 跨项目冲突
    // ----------------------------------------------------------------

    /**
     * 检查跨项目冲突（同一员工同一天在多个项目填报工时）
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileCrossProject(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<TimeEntryDO> entries = timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
        if (entries.isEmpty()) return out;

        // 已检查的 (employeeId, date) 集合,避免重复告警
        Set<String> checked = new HashSet<>();
        for (TimeEntryDO e : entries) {
            if (e.getEmployeeId() == null || e.getEntryDate() == null) continue;
            String key = e.getEmployeeId() + "|" + e.getEntryDate();
            if (checked.contains(key)) continue;
            checked.add(key);

            List<Map<String, Object>> conflicts = timeEntryMapper.detectCrossProject(
                    e.getEmployeeId(), e.getEntryDate());
            if (conflicts != null && conflicts.size() > 1) {
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.CROSS_PROJECT_CONFLICT)
                        .level(ReconcileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .description(String.format("员工 %s 在 %s 跨 %d 个项目填写工时",
                                e.getEmployeeId(), e.getEntryDate(), conflicts.size()))
                        .suggestion("检查工时分摊比例是否合理")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 6. 金额漂移（工时×费率 vs 实际归集金额）
    // ----------------------------------------------------------------

    /**
     * 检查金额漂移（工时×费率 vs 实际归集金额）
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileAmountDrift(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;

        // 取出已审批工时
        List<TimeEntryDO> approved = timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to).stream()
                .filter(e -> TimeEntryStatus.APPROVED.getCode().equals(e.getStatus()))
                .toList();
        if (approved.isEmpty()) return out;

        // 取出 LABOR 成本
        List<CostAllocationDO> costs = costAllocationMapper.selectByInitiationAndPeriod(initiationId, null);
        Map<Long, CostAllocationDO> costBySource = costs.stream()
                .filter(c -> CostType.LABOR.getCode().equals(c.getCostType()))
                .filter(c -> c.getSourceId() != null)
                .collect(Collectors.toMap(CostAllocationDO::getSourceId, c -> c, (a, b) -> a));

        for (TimeEntryDO e : approved) {
            if (e.getId() == null || e.getHours() == null) continue;
            CostAllocationDO c = costBySource.get(e.getId());
            if (c == null) continue; // 漏算由 MissingCost 单独处理
            BigDecimal days = e.getDays() == null
                    ? TimeEntryValidator.toDays(e.getHours())
                    : e.getDays();
            BigDecimal expected = days.multiply(DEFAULT_DAILY_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
            BigDecimal drift = expected.subtract(actual).abs();
            if (drift.compareTo(AMOUNT_DRIFT_TOLERANCE) > 0) {
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.AMOUNT_DRIFT)
                        .level(ReconcileLevel.WARN)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .sourceId(c.getId())
                        .sourceType("COST_ALLOCATION")
                        .description(String.format(
                                "工时 id=%d (人天=%s) 期望成本 %s 元,实际 %s 元,偏差 %s 元",
                                e.getId(), days.toPlainString(),
                                expected.toPlainString(), actual.toPlainString(), drift.toPlainString()))
                        .actualValue(actual)
                        .expectedValue(expected)
                        .drift(drift)
                        .suggestion("按工时×职级费率重新计算成本金额")
                        .build());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------
    // 7. 成本已分配但工时未审批
    // ----------------------------------------------------------------

    /**
     * 检查成本已分配但工时未审批
     *
     * @param initiationId 项目立项 ID
     * @return 异常结果列表
     */
    public List<ReconcileResult> reconcileAllocatedBeforeApproval(Long initiationId) {
        List<ReconcileResult> out = new ArrayList<>();
        if (initiationId == null) return out;
        List<CostAllocationDO> costs = costAllocationMapper.selectByInitiationAndPeriod(initiationId, null);
        if (costs.isEmpty()) return out;

        for (CostAllocationDO c : costs) {
            if (c.getAllocated() == null || c.getAllocated() != 1) continue;
            if (c.getSourceId() == null || !"TIME_ENTRY".equals(c.getSourceType())) continue;
            TimeEntryDO e = timeEntryMapper.selectById(c.getSourceId());
            if (e == null) continue;
            if (!TimeEntryStatus.APPROVED.getCode().equals(e.getStatus())) {
                out.add(ReconcileResult.builder()
                        .type(ReconcileType.ALLOCATED_BEFORE_APPROVAL)
                        .level(ReconcileLevel.ERROR)
                        .initiationId(initiationId)
                        .employeeId(e.getEmployeeId())
                        .sourceId(c.getId())
                        .sourceType("COST_ALLOCATION")
                        .description(String.format(
                                "成本 costId=%d 已标记 allocated=1,但工时 id=%d 状态=%s",
                                c.getId(), e.getId(), e.getStatus()))
                        .suggestion("回滚分配状态或审批工时")
                        .build());
            }
        }
        return out;
    }

    /**
     * 工具方法: 安全相加(BigDecimal 累加)
     *
     * @param a 被加数
     * @param b 加数
     * @return 和；任一为 null 时返回另一值
     */
    public static BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }
}
