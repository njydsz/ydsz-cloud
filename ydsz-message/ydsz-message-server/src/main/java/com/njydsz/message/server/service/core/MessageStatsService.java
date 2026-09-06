package com.njydsz.message.server.service.core;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.message.domain.dto.ChannelStatsDTO;
import com.njydsz.message.domain.dto.CostStatsDTO;
import com.njydsz.message.domain.dto.FunnelStatsDTO;
import com.njydsz.message.domain.dto.MessageStatsDTO;
import com.njydsz.message.domain.dto.ReceiptStatsDTO;

/**
 * 消息统计服务接口。
 *
 * <p>多维度统计消息发送数据。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MessageStatsService {

  /**
   * 发送总览统计。
   *
   * @param start 起始时间（含），null 则默认最近 24h
   * @param end 结束时间（含），null 则当前时间
   * @return 总览统计
   */
  MessageStatsDTO getOverview(LocalDateTime start, LocalDateTime end);

  /**
   * 按通道维度的发送统计。
   *
   * @param start 起始时间（含）
   * @param end 结束时间（含）
   * @return 各通道统计列表
   */
  List<ChannelStatsDTO> getChannelStats(LocalDateTime start, LocalDateTime end);

  /**
   * 回执统计。
   *
   * @param start 起始时间（含）
   * @param end 结束时间（含）
   * @return 回执统计
   */
  ReceiptStatsDTO getReceiptStats(LocalDateTime start, LocalDateTime end);

  /**
   * P2-2: 消息转化漏斗分析。
   *
   * <p>漏斗四阶段：sent(已发送) → delivered(已送达) → read(已读) → clicked(已点击)， 各阶段为累积计数（clicked 隐含 read 隐含
   * delivered）。 支持按通道和模板编码过滤，用于精细化分析特定渠道/模板的转化效果。
   *
   * @param start 起始时间（含），null 则默认最近 24h
   * @param end 结束时间（含），null 则当前时间
   * @param channel 通道过滤（可选，null/空 则不过滤）
   * @param templateCode 模板编码过滤（可选，null/空 则不过滤）
   * @return 漏斗统计（含各阶段数量与转化率）
   */
  FunnelStatsDTO getFunnel(
      LocalDateTime start, LocalDateTime end, String channel, String templateCode);

  /**
   * P2-4: 成本看板统计。
   *
   * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本。 通道单价由 {@code ydsz.message.cost.unit-prices} 配置。
   *
   * @param start 起始时间（含），null 则默认最近 24h
   * @param end 结束时间（含），null 则当前时间
   * @return 成本统计（含各通道明细与总成本）
   */
  CostStatsDTO getCostStats(LocalDateTime start, LocalDateTime end);
}
