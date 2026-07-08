package com.njydsz.pmis.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 消息发送成本统计 VO（P2-4 成本看板）。
 *
 * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本。
 * 通道单价由 {@code pmis.message.cost.unit-prices} 配置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "消息发送成本统计")
public class CostStatsVO {

    @Schema(description = "总成本(元)")
    private BigDecimal totalCost;

    @Schema(description = "各通道成本明细")
    private List<ChannelCost> channels;

    @Schema(description = "起始时间")
    private String start;

    @Schema(description = "结束时间")
    private String end;

    /**
     * 单通道成本明细。
     */
    @Data
    @Schema(description = "通道成本明细")
    public static class ChannelCost {

        @Schema(description = "通道")
        private String channel;

        @Schema(description = "成功发送数")
        private long messageCount;

        @Schema(description = "单条成本(元)")
        private BigDecimal unitPrice;

        @Schema(description = "通道总成本(元)")
        private BigDecimal totalCost;
    }
}
