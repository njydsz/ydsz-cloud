package com.njydsz.pmis.project.controller.finance;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.engine.ReconcileReport;
import com.njydsz.pmis.project.engine.ReconcileResult;
import com.njydsz.pmis.project.service.finance.ReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/finance/reconcile")
@RequiredArgsConstructor
@Validated
public class ReconcileController {

    /** 对账服务 */
    private final ReconcileService reconcileService;

    /**
     * 查询全量对账报告
     *
     * @param initiationId 项目立项 ID，可选
     * @param from         起始日期，可选
     * @param to           截止日期，可选
     * @return 对账报告
     */
    @Operation(summary = "全量对账报告")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/report")
    public Result<ReconcileReport> report(
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reconcileService.reconcileAll(initiationId, from, to));
    }

    /**
     * 检查工时漏算/幽灵成本
     *
     * @param initiationId 项目立项 ID，可选
     * @return 对账差异结果列表
     */
    @Operation(summary = "工时漏算 / 幽灵成本")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/missing-cost")
    public Result<List<ReconcileResult>> missingCost(@RequestParam(required = false) String initiationId) {
        return Result.ok(reconcileService.checkMissingCost(initiationId));
    }

    /**
     * 检查工时异常（单日/单周/跨项目）
     *
     * @param initiationId 项目立项 ID，可选
     * @param from         起始日期，可选
     * @param to           截止日期，可选
     * @return 对账差异结果列表
     */
    @Operation(summary = "工时异常(单日/单周/跨项目)")
    @PrePermission("execution:reconcile:view")
    @GetMapping("/time-anomaly")
    public Result<List<ReconcileResult>> timeAnomaly(
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reconcileService.checkTimeEntryAnomaly(initiationId, from, to));
    }
}
