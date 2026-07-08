package com.njydsz.pmis.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 灰度 A/B 实验报表（P1-6）。
 *
 * <p>对照组（control）= 未命中灰度的消息：{@code template_code = canaryKey AND canary = 0 AND canary_key IS NULL}。
 * 实验组（treatment）= 命中灰度的消息：{@code canary_key = canaryKey}（canary=1 隐含）。
 * 两组分别统计发送量 / 成功 / 失败 / 重试 / 死信 / 送达 / 已读 / 点击 及对应比率,
 * 供运营对比实验模板/通道与基线模板/通道的效果差异。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "灰度 A/B 实验报表")
public class CanaryReportVO {

    /** 灰度键（原始模板编码） */
    @Schema(description = "灰度键(原始模板编码)")
    private String canaryKey;

    /** 对照组（未命中灰度）统计 */
    @Schema(description = "对照组(未命中灰度)统计")
    private GroupStats control;

    /** 实验组（命中灰度）统计 */
    @Schema(description = "实验组(命中灰度)统计")
    private GroupStats treatment;

    /** 统计起始时间 */
    @Schema(description = "统计起始时间")
    private String start;

    /** 统计结束时间 */
    @Schema(description = "统计结束时间")
    private String end;

    /**
     * 分组统计（对照组 / 实验组共用）。
     */
    @Data
    @Schema(description = "A/B 分组统计")
    public static class GroupStats {

        /** 总发送量 */
        @Schema(description = "总发送量")
        private long total;

        /** 发送成功数 */
        @Schema(description = "发送成功数")
        private long success;

        /** 发送失败数 */
        @Schema(description = "发送失败数")
        private long failed;

        /** 重试中数 */
        @Schema(description = "重试中数")
        private long retry;

        /** 死信数 */
        @Schema(description = "死信数")
        private long dead;

        /** 已送达数（receipt_status = DELIVERED） */
        @Schema(description = "已送达数")
        private long delivered;

        /** 已读数（receipt_status = READ） */
        @Schema(description = "已读数")
        private long read;

        /** 已点击数（receipt_status = CLICKED） */
        @Schema(description = "已点击数")
        private long clicked;

        /** 成功率(%) = success / total * 100 */
        @Schema(description = "成功率(%)")
        private double successRate;

        /** 送达率(%) = (delivered + read + clicked) / total * 100 */
        @Schema(description = "送达率(%)")
        private double deliveryRate;

        /** 阅读率(%) = (read + clicked) / total * 100 */
        @Schema(description = "阅读率(%)")
        private double readRate;
    }
}
