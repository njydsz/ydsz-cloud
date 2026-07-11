package com.njydsz.pmis.finance.web.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.finance.server.service.finance.DailyReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/finance/dailyReconcile")
@RequiredArgsConstructor
@Validated
public class DailyReconcileController {

    /** 每日对账服务 */
    private final DailyReconcileService service;

    /**
     * 运行某天的对账（默认今天）
     *
     * @param date 对账日期，可选
     * @return 处理的对账记录数量
     */
    @Operation(summary = "运行某天的对账（默认今天）")
    @Idempotent(key = "dailyReconcile:run", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/run")
    public Result<Integer> run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.runDaily(date));
    }

    /**
     * 按日期范围查询对账记录
     *
     * @param from   起始日期，可选
     * @param to     截止日期，可选
     * @param status 状态过滤
     * @return 对账记录列表
     */
    @Operation(summary = "按日期范围查询对账记录")
    @GetMapping("/query")
    public Result<List<Map<String, Object>>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return Result.ok(service.queryByDateRange(from, to, status));
    }

    /**
     * 状态统计 OK / WARN / ERROR
     *
     * @param from 起始日期，可选
     * @param to   截止日期，可选
     * @return 各状态数量列表
     */
    @Operation(summary = "状态统计 OK / WARN / ERROR")
    @GetMapping("/aggregate")
    public Result<List<Map<String, Object>>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.aggregateStatus(from, to));
    }

    /**
     * 纯计算：按阈值分类差异（OK / WARN / ERROR）
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param warnPct  告警阈值百分比
     * @param errorPct 错误阈值百分比
     * @return 分类结果
     */
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
