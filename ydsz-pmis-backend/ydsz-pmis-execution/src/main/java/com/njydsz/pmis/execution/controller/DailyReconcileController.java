package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.service.DailyReconcileService;
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
 * 每日对账 Controller（P4-3）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "每日自动对账")
@RestController
@RequestMapping("/api/v1/execution/daily-reconcile")
@RequiredArgsConstructor
public class DailyReconcileController {

    private final DailyReconcileService service;

    @Operation(summary = "运行某天的对账（默认今天）")
    @PostMapping("/run")
    public Result<Integer> run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.runDaily(date));
    }

    @Operation(summary = "按日期范围查询对账记录")
    @GetMapping("/query")
    public Result<List<Map<String, Object>>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return Result.ok(service.queryByDateRange(from, to, status));
    }

    @Operation(summary = "状态统计 OK / WARN / ERROR")
    @GetMapping("/aggregate")
    public Result<List<Map<String, Object>>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.aggregateStatus(from, to));
    }

    @Operation(summary = "纯计算：按阈值分类差异（OK / WARN / ERROR）")
    @GetMapping("/classify")
    public Result<String> classify(
            @RequestParam double expected,
            @RequestParam double actual,
            @RequestParam(defaultValue = "0.01") double warnPct,
            @RequestParam(defaultValue = "0.05") double errorPct) {
        return Result.ok(service.classify(expected, actual, warnPct, errorPct));
    }
}
