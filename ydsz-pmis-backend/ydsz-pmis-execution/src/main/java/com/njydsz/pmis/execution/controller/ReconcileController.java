package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.engine.ReconcileReport;
import com.njydsz.pmis.execution.engine.ReconcileResult;
import com.njydsz.pmis.execution.service.ReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 财务-工时对账接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "执行-对账")
@RestController
@RequestMapping("/api/v1/execution/reconcile")
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileService reconcileService;

    @Operation(summary = "全量对账报告")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/report")
    public R<ReconcileReport> report(
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reconcileService.reconcileAll(initiationId, from, to));
    }

    @Operation(summary = "工时漏算 / 幽灵成本")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/missing-cost")
    public R<List<ReconcileResult>> missingCost(@RequestParam(required = false) Long initiationId) {
        return R.ok(reconcileService.checkMissingCost(initiationId));
    }

    @Operation(summary = "工时异常(单日/单周/跨项目)")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/time-anomaly")
    public R<List<ReconcileResult>> timeAnomaly(
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reconcileService.checkTimeEntryAnomaly(initiationId, from, to));
    }
}
