package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.service.BillableUtilizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 可计费利用率统计与考核
 *
 * <p>P4-1: 提供个人/团队/排行榜/预警查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "可计费利用率")
@RestController
@RequestMapping("/api/v1/execution/billable-utilization")
@RequiredArgsConstructor
public class BillableUtilizationController {

    private final BillableUtilizationService service;

    @Operation(summary = "按月聚合所有员工利用率明细")
    @PrePermission("execution:utilization:view")
    @GetMapping("/aggregate")
    public Result<List<Map<String, Object>>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.aggregate(from, to));
    }

    @Operation(summary = "个人利用率（from-to 汇总）")
    @PrePermission("execution:utilization:view")
    @GetMapping("/personal")
    public Result<Map<String, Object>> personal(
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.personal(employeeId, from, to));
    }

    @Operation(summary = "排行榜（按 utilizationPct 倒序）")
    @PrePermission("execution:utilization:view")
    @GetMapping("/rank")
    public Result<List<Map<String, Object>>> rank(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") int top) {
        return Result.ok(service.rank(from, to, top));
    }

    @Operation(summary = "公司/团队整体均值")
    @PrePermission("execution:utilization:view")
    @GetMapping("/overall")
    public Result<Map<String, Object>> overall(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.overall(from, to));
    }

    @Operation(summary = "扫描预警员工（WARN/CRITICAL）")
    @PrePermission("execution:utilization:view")
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.scanAlerts(from, to));
    }

    @Operation(summary = "纯计算评估：给 total/billable 小时数返回利用率与考核等级")
    @PrePermission("execution:utilization:view")
    @GetMapping("/evaluate")
    public Result<Map<String, Object>> evaluate(
            @RequestParam double totalHours,
            @RequestParam double billableHours) {
        return Result.ok(service.evaluate(totalHours, billableHours));
    }

    @Operation(summary = "触发快照重算（Scheduler 调用 / 运维手工）")
    @PrePermission("execution:utilization:recompute")
    @PostMapping("/recompute")
    public Result<Map<String, Object>> recompute(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "false") boolean recomputeAll) {
        return Result.ok(service.recompute(period, recomputeAll));
    }

    @Operation(summary = "读取最新一期快照均值（驾驶舱取数，快照为空时实时聚合兜底）")
    @PrePermission("execution:utilization:view")
    @GetMapping("/snapshot-average")
    public Result<Map<String, Object>> snapshotAverage(
            @RequestParam(required = false) String period) {
        return Result.ok(service.snapshotAverage(period));
    }
}
