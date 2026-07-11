package com.njydsz.pmis.message.web.controller.canary;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.canary.CanaryReportVO;
import com.njydsz.pmis.message.server.service.canary.CanaryReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 灰度 A/B 报表 Controller（P1-6）。
 *
 * <p>暴露灰度实验命中/转化对比数据端点,供运营管理后台对比实验模板/通道
 * 与基线模板/通道的发送成功率 / 送达率 / 阅读率差异。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "灰度A/B报表", description = "灰度实验命中/转化对比统计")
@RestController
@RequestMapping("/message/canary/report")
@RequiredArgsConstructor
public class CanaryReportController {

    /** 灰度报表服务 */
    private final CanaryReportService canaryReportService;

    /**
     * 获取灰度 A/B 实验报表。
     *
     * @param canaryKey 灰度键（原始模板编码），必填
     * @param start     起始时间（ISO 格式 yyyy-MM-dd'T'HH:mm:ss，可选，默认最近 7 天）
     * @param end       结束时间（ISO 格式，可选，默认当前时间）
     * @return A/B 报表（含对照组与实验组统计）
     */
    @Operation(summary = "获取灰度A/B实验报表")
    @PrePermission(PermissionCodes.MESSAGE_CANARY_REPORT)
    @GetMapping
    public Result<CanaryReportVO> getReport(
            @Parameter(description = "灰度键(原始模板编码)", required = true)
            @RequestParam String canaryKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(canaryReportService.getReport(canaryKey, start, end));
    }
}
