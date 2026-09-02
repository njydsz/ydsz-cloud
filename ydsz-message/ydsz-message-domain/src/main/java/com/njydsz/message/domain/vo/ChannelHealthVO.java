package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通道健康状态视图对象。
 *
 * <p>反映单个消息通道的实时运行状态，包含熔断器状态、启用状态、滑动窗口失败计数等运维关键指标，供管理后台通道监控面板和系统健康检查接口使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通道健康状态")
public class ChannelHealthVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 通道名称（大写，如 SMS/EMAIL/PUSH） */
  @Schema(description = "通道名称", example = "SMS")
  private String channel;

  /** 通道是否启用 */
  @Schema(description = "是否启用", example = "true")
  private boolean enabled;

  /** 熔断器状态：CLOSED / OPEN / HALF_OPEN */
  @Schema(description = "熔断器状态", example = "CLOSED")
  private String circuitBreakerState;

  /** 滑动窗口内失败次数 */
  @Schema(description = "滑动窗口内失败次数", example = "3")
  private int failureCount;

  /** 滑动窗口内总请求次数 */
  @Schema(description = "滑动窗口内总请求次数", example = "100")
  private int totalCount;

  /** 失败率（失败次数 / 总请求次数，总计为 0 时返回 0） */
  @Schema(description = "失败率", example = "0.03")
  private double failureRate;
}
