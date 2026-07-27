package com.njydsz.message.web.controller.core;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.core.ChannelStatsVO;
import com.njydsz.message.domain.dto.core.CostStatsVO;
import com.njydsz.message.domain.dto.core.FunnelStatsVO;
import com.njydsz.message.domain.dto.core.MessageStatsVO;
import com.njydsz.message.domain.dto.receipt.ReceiptStatsVO;
import com.njydsz.message.server.service.core.MessageStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息统计看板 Controller（P1-2 可观测看板）。
 *
 * <p>提供发送总览 / 通道维度 / 回执统计三个聚合查询端点,
 * 供运营管理后台渲染可观测看板。时间范围通过 start / end 查询参数指定,
 * 未指定时默认最近 24 小时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "消息统计看板", description = "发送/重试/死信/回执聚合指标")
@RestController
@RequestMapping("/api/v1/message/stats")
@RequiredArgsConstructor
public class MessageStatsController {

    /** 消息统计服务 */
    private final MessageStatsService messageStatsService;

    /**
     * 发送总览统计。
     *
     * @param start 起始时间（ISO 格式 yyyy-MM-dd'T'HH:mm:ss，可选）
     * @param end   结束时间（ISO 格式，可选）
     * @return 总览统计
     */
    @Operation(summary = "发送总览统计")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/overview")
    public BaseResponse<MessageStatsVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return BaseResponse.success(messageStatsService.getOverview(start, end));
    }

    /**
     * 按通道维度的发送统计。
     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 各通道统计列表
     */
    @Operation(summary = "通道维度发送统计")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/channel")
    public BaseResponse<List<ChannelStatsVO>> channelStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return BaseResponse.success(messageStatsService.getChannelStats(start, end));
    }

    /**
     * 回执统计。
     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 回执统计
     */
    @Operation(summary = "回执统计")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/receipt")
    public BaseResponse<ReceiptStatsVO> receiptStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return BaseResponse.success(messageStatsService.getReceiptStats(start, end));
    }

    /**
     * P2-2: 消息转化漏斗分析。
     *
     * <p>漏斗四阶段：sent(已发送) → delivered(已送达) → read(已读) → clicked(已点击)。
     * 支持按通道和模板编码过滤，用于精细化分析特定渠道/模板的转化效果。
     *
     * @param start       起始时间（可选）
     * @param end         结束时间（可选）
     * @param channel     通道过滤（可选，如 SMS/EMAIL）
     * @param templateCode 模板编码过滤（可选）
     * @return 漏斗统计
     */
    @Operation(summary = "消息转化漏斗分析")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/funnel")
    public BaseResponse<FunnelStatsVO> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String templateCode) {
        return BaseResponse.success(messageStatsService.getFunnel(start, end, channel, templateCode));
    }

    /**
     * P2-4: 成本看板。
     *
     * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本。
     *
     * @param start 起始时间（可选）
     * @param end   结束时间（可选）
     * @return 成本统计
     */
    @Operation(summary = "成本看板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/cost")
    public BaseResponse<CostStatsVO> cost(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return BaseResponse.success(messageStatsService.getCostStats(start, end));
    }
}
