package com.njydsz.message.domain.dto.receipt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 回执统计（P1-2 可观测看板）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "回执统计")
public class ReceiptStatsVO {

  /** 成功发送总数（回执分母） */
  @Schema(description = "成功发送总数")
  private long total;

  /** 已送达 */
  @Schema(description = "已送达数")
  private long delivered;

  /** 已读 */
  @Schema(description = "已读数")
  private long read;

  /** 已点击 */
  @Schema(description = "已点击数")
  private long clicked;

  /** 投递失败 */
  @Schema(description = "投递失败数")
  private long failed;

  /** 回执超时 */
  @Schema(description = "回执超时数")
  private long timeout;

  /** 无回执 */
  @Schema(description = "无回执数")
  private long none;

  /** 送达率(%) = (delivered + read + clicked) / total * 100 */
  @Schema(description = "送达率(%)")
  private double deliveryRate;

  /** 已读率(%) = (read + clicked) / total * 100 */
  @Schema(description = "已读率(%)")
  private double readRate;
}
