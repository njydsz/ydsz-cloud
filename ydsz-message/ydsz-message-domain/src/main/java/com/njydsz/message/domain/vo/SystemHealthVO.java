package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统健康状态视图对象。
 *
 * <p>汇总消息模块的整体健康状态，包含全局摘要和各通道详细健康指标，是系统健康检查接口的顶层返回对象，供运维监控和大屏展示使用。
 *
 * <p><b>健康判定规则：</b>
 *
 * <ul>
 *   <li>任一通道熔断器状态为 OPEN → 整体状态 DEGRADED
 *   <li>所有通道熔断器状态为 CLOSED → 整体状态 UP
 *   <li>无可用通道 → 整体状态 DOWN
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统健康状态")
public class SystemHealthVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 整体健康状态：UP（正常）/ DEGRADED（降级）/ DOWN（不可用） */
  @Schema(description = "整体健康状态", example = "UP")
  private String status;

  /** 已注册通道总数 */
  @Schema(description = "已注册通道总数", example = "8")
  private int totalChannels;

  /** 启用通道数 */
  @Schema(description = "启用通道数", example = "6")
  private int enabledChannels;

  /** 熔断打开的通道数 */
  @Schema(description = "熔断打开的通道数", example = "0")
  private int openBreakers;

  /** 各通道详细健康状态列表 */
  @Schema(description = "各通道详细健康状态")
  private List<ChannelHealthVO> channels;
}
