package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
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
    @GetMapping("/aggregate")
    public R<List<Map<String, Object>>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.aggregate(from, to));
    }

    @Operation(summary = "个人利用率（from-to 汇总）")
    @GetMapping("/personal")
    public R<Map<String, Object>> personal(
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.personal(employeeId, from, to));
    }

    @Operation(summary = "排行榜（按 utilizationPct 倒序）")
    @GetMapping("/rank")
    public R<List<Map<String, Object>>> rank(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") int top) {
        return R.ok(service.rank(from, to, top));
    }

    @Operation(summary = "公司/团队整体均值")
    @GetMapping("/overall")
    public R<Map<String, Object>> overall(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.overall(from, to));
    }

    @Operation(summary = "扫描预警员工（WARN/CRITICAL）")
    @GetMapping("/alerts")
    public R<List<Map<String, Object>>> alerts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.scanAlerts(from, to));
    }

    @Operation(summary = "纯计算评估：给 total/billable 小时数返回利用率与考核等级")
    @GetMapping("/evaluate")
    public R<Map<String, Object>> evaluate(
            @RequestParam double totalHours,
            @RequestParam double billableHours) {
        return R.ok(service.evaluate(totalHours, billableHours));
    }

    @Operation(summary = "触发快照重算（Scheduler 调用 / 运维手工）")
    @PostMapping("/recompute")
    public R<Map<String, Object>> recompute(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "false") boolean recomputeAll) {
        return R.ok(service.recompute(period, recomputeAll));
    }

    @Operation(summary = "读取最新一期快照均值（驾驶舱取数，快照为空时实时聚合兜底）")
    @GetMapping("/snapshot-average")
    public R<Map<String, Object>> snapshotAverage(
            @RequestParam(required = false) String period) {
        return R.ok(service.snapshotAverage(period));
    }
}
