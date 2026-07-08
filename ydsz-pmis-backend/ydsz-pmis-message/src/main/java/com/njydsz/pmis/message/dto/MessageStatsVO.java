package com.njydsz.pmis.message.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 消息发送总览统计（P1-2 可观测看板）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "消息发送总览统计")
public class MessageStatsVO {

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

    /** 已撤回数 */
    @Schema(description = "已撤回数")
    private long recalled;

    /** 成功率(%) = success / total * 100 */
    @Schema(description = "成功率(%)")
    private double successRate;

    /** 死信率(%) = dead / total * 100 */
    @Schema(description = "死信率(%)")
    private double deadRate;

    /** 统计起始时间 */
    @Schema(description = "统计起始时间")
    private String start;

    /** 统计结束时间 */
    @Schema(description = "统计结束时间")
    private String end;
}
