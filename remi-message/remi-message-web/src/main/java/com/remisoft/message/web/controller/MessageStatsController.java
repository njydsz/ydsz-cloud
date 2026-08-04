package com.remisoft.message.web.controller.core;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.message.domain.dto.core.ChannelStatsVO;
import com.remisoft.message.domain.dto.core.CostStatsVO;
import com.remisoft.message.domain.dto.core.FunnelStatsVO;
import com.remisoft.message.domain.dto.core.MessageStatsVO;
import com.remisoft.message.domain.dto.receipt.ReceiptStatsVO;
import com.remisoft.message.server.service.core.MessageStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息统计看板（Message Stats）Controller。
 *
 * <p>提供<b>消息发送 / 重试 / 死信 / 回执</b>的聚合统计指标端点，
 * 是 P1-2「可观测看板」的核心入口，供运营管理后台渲染发送看板、漏斗分析、成本分析。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/stats/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>总览统计</b>：{@code GET /overview} — 发送总数 / 成功数 / 失败数 / 重试数 / 死信数 / 成功率</li>
 *   <li><b>通道统计</b>：{@code GET /channel} — 按通道（SMS / EMAIL / IN_APP / ...）分维度的发送指标</li>
 *   <li><b>回执统计</b>：{@code GET /receipt} — DELIVERED / READ / CLICKED / FAILED 等回执状态分布</li>
 *   <li><b>转化漏斗</b>：{@code GET /funnel} — P2-2 四阶段漏斗（sent → delivered → read → clicked）</li>
 *   <li><b>成本看板</b>：{@code GET /cost} — P2-4 各通道的成本统计（单条成本 × 发送数）</li>
 * </ul>
 *
 * <p><b>转化漏斗（P2-2）：</b>四阶段定义：
 * <ol>
 *   <li>{@code sent}：已发送给服务商（{@code status = SENT}）</li>
 *   <li>{@code delivered}：已送达接收人（{@code receiptStatus = DELIVERED}）</li>
 *   <li>{@code read}：已读（{@code receiptStatus = READ}）</li>
 *   <li>{@code clicked}：已点击（{@code receiptStatus = CLICKED}）</li>
 * </ol>
 * 每阶段返回数量 + 相对上一阶段的转化率，用于精细化分析特定渠道/模板的转化效果。
 *
 * <p><b>成本看板（P2-4）：</b>按通道维度统计发送成本：
 * <ul>
 *   <li>单条成本：来自供应商合同价（{@code remi_msg_channel.unitPrice} 配置）</li>
 *   <li>通道总成本：{@code unitPrice × successCount}（仅统计成功发送的部分）</li>
 *   <li>用于财务对账 / 预算控制 / 渠道比价</li>
 * </ul>
 *
 * <p><b>时间范围：</b>{@code start / end} 不指定时默认最近 24 小时。
 * 所有统计接口都基于 {@code remi_msg_log} 聚合查询，建议对大表建立按天分区或物化视图提升性能。
 *
 * <p><b>多租户隔离：</b>所有统计按 {@code tenantId} 隔离，跨租户数据不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>只读接口，仅启用 {@code @AuthApiPermission} 权限校验</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_LOG_VIEW} 权限码</li>
 *   <li>统计查询走专门的物化视图或预聚合表，避免实时计算大表</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see com.remisoft.message.server.service.core.MessageStatsService 消息统计服务
 * @see com.remisoft.message.domain.dto.core.MessageStatsVO 总览 VO
 * @see com.remisoft.message.domain.dto.core.FunnelStatsVO 漏斗 VO
 * @see com.remisoft.message.domain.dto.core.CostStatsVO 成本 VO
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
