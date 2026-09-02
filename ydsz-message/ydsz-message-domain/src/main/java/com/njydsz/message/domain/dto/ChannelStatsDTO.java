package com.njydsz.message.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按通道维度的发送统计（P1-2 可观测看板）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "通道维度发送统计")
public class ChannelStatsDTO {

  /** 通道 */
  @Schema(description = "通道")
  private String channel;

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

  /** 成功率(%) */
  @Schema(description = "成功率(%)")
  private double successRate;

  /** 死信率(%) */
  @Schema(description = "死信率(%)")
  private double deadRate;
}
