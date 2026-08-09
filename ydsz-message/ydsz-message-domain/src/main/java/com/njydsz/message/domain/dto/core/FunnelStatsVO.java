package com.njydsz.message.domain.dto.core;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 消息转化漏斗统计 VO（P2-2 漏斗分析）。
 *
 * <p>漏斗四阶段（逐层递减）：
 * <ol>
 *   <li>sent（已发送）：status = SUCCESS 的消息总量</li>
 *   <li>delivered（已送达）：receiptStatus IN (DELIVERED, READ, CLICKED)</li>
 *   <li>read（已读）：receiptStatus IN (READ, CLICKED)</li>
 *   <li>clicked（已点击）：receiptStatus = CLICKED</li>
 * </ol>
 *
 * <p>各阶段转化率 = 下一阶段 / 上一阶段 * 100，整体转化率 = clicked / sent * 100。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "消息转化漏斗统计")
public class FunnelStatsVO {

    @Schema(description = "已发送数(漏斗第1层:status=SUCCESS)")
    private long sent;

    @Schema(description = "已送达数(漏斗第2层:receiptStatus IN DELIVERED/READ/CLICKED)")
    private long delivered;

    @Schema(description = "已读数(漏斗第3层:receiptStatus IN READ/CLICKED)")
    private long read;

    @Schema(description = "已点击数(漏斗第4层:receiptStatus=CLICKED)")
    private long clicked;

    @Schema(description = "送达率(%)= delivered / sent * 100")
    private double deliveryRate;

    @Schema(description = "已读率(%)= read / sent * 100")
    private double readRate;

    @Schema(description = "点击率(%)= clicked / sent * 100")
    private double clickRate;

    @Schema(description = "送达→已读转化率(%)= read / delivered * 100")
    private double deliveredToReadRate;

    @Schema(description = "已读→点击转化率(%)= clicked / read * 100")
    private double readToClickRate;

    @Schema(description = "整体转化率(%)= clicked / sent * 100")
    private double overallConversionRate;

    @Schema(description = "查询通道(可选过滤维度)")
    private String channel;

    @Schema(description = "查询模板编码(可选过滤维度)")
    private String templateCode;

    @Schema(description = "起始时间")
    private String start;

    @Schema(description = "结束时间")
    private String end;
}
