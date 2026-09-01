package com.njydsz.message.server.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.message.domain.dto.ChannelStatsDTO;
import com.njydsz.message.domain.dto.CostStatsDTO;
import com.njydsz.message.domain.dto.FunnelStatsDTO;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.MessageStatsDTO;
import com.njydsz.message.domain.dto.ReceiptStatsDTO;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.core.MessageStatsService;

/**
 * 消息统计服务实现。
 *
 * <p>提供消息发送的多维度统计：渠道分布、模板 TOP、用户活跃度、回执率、失败率、转化漏斗。
 *
 * <p>数据按小时/天/周/月聚合，支持自定义时间区间导出报表。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatsServiceImpl implements MessageStatsService {
  /** 统计时间窗口（小时） */
  private static final int STATS_WINDOW_HOURS = 24;


  /** 消息日志 Repository（聚合统计查询） */
  private final MsgLogRepository msgLogRepository;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  @Override
  public MessageStatsDTO getOverview(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    long success = countByStatus(MessageStatusEnum.SUCCESS, actualStart, actualEnd);
    long failed = countByStatus(MessageStatusEnum.FAILED, actualStart, actualEnd);
    long retry = countByStatus(MessageStatusEnum.RETRY, actualStart, actualEnd);
    long dead = countByStatus(MessageStatusEnum.DEAD, actualStart, actualEnd);
    long recalled = countByStatus(MessageStatusEnum.RECALLED, actualStart, actualEnd);
    long total = success + failed + retry + dead + recalled;

    MessageStatsDTO vo = new MessageStatsDTO();
    vo.setTotal(total);
    vo.setSuccess(success);
    vo.setFailed(failed);
    vo.setRetry(retry);
    vo.setDead(dead);
    vo.setRecalled(recalled);
    vo.setSuccessRate(total > 0 ? round2(success * 100.0 / total) : 0.0);
    vo.setDeadRate(total > 0 ? round2(dead * 100.0 / total) : 0.0);
    vo.setStart(actualStart.toString());
    vo.setEnd(actualEnd.toString());
    return vo;
  }

  @Override
  public List<ChannelStatsDTO> getChannelStats(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    List<ChannelStatsDTO> result = new ArrayList<>();
    for (MessageChannelEnum ch : MessageChannelEnum.values()) {
      String channel = ch.name();
      long success =
          countByStatusAndChannel(MessageStatusEnum.SUCCESS, channel, actualStart, actualEnd);
      long failed =
          countByStatusAndChannel(MessageStatusEnum.FAILED, channel, actualStart, actualEnd);
      long retry =
          countByStatusAndChannel(MessageStatusEnum.RETRY, channel, actualStart, actualEnd);
      long dead = countByStatusAndChannel(MessageStatusEnum.DEAD, channel, actualStart, actualEnd);
      long total = success + failed + retry + dead;

      // 只输出有数据的通道
      if (total == 0) {
        continue;
      }

      ChannelStatsDTO vo = new ChannelStatsDTO();
      vo.setChannel(channel);
      vo.setTotal(total);
      vo.setSuccess(success);
      vo.setFailed(failed);
      vo.setRetry(retry);
      vo.setDead(dead);
      vo.setSuccessRate(total > 0 ? round2(success * 100.0 / total) : 0.0);
      vo.setDeadRate(total > 0 ? round2(dead * 100.0 / total) : 0.0);
      result.add(vo);
    }
    return result;
  }

  @Override
  public ReceiptStatsDTO getReceiptStats(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    // 回执分母 = 成功发送数
    long total = countByStatus(MessageStatusEnum.SUCCESS, actualStart, actualEnd);
    long delivered = countByReceiptStatus(ReceiptStatusEnum.DELIVERED, actualStart, actualEnd);
    long read = countByReceiptStatus(ReceiptStatusEnum.READ, actualStart, actualEnd);
    long clicked = countByReceiptStatus(ReceiptStatusEnum.CLICKED, actualStart, actualEnd);
    long failed = countByReceiptStatus(ReceiptStatusEnum.FAILED, actualStart, actualEnd);
    long timeout = countByReceiptStatus(ReceiptStatusEnum.TIMEOUT, actualStart, actualEnd);
    long none = countByReceiptStatus(ReceiptStatusEnum.NONE, actualStart, actualEnd);

    ReceiptStatsDTO vo = new ReceiptStatsDTO();
    vo.setTotal(total);
    vo.setDelivered(delivered);
    vo.setRead(read);
    vo.setClicked(clicked);
    vo.setFailed(failed);
    vo.setTimeout(timeout);
    vo.setNone(none);
    vo.setDeliveryRate(total > 0 ? round2((delivered + read + clicked) * 100.0 / total) : 0.0);
    vo.setReadRate(total > 0 ? round2((read + clicked) * 100.0 / total) : 0.0);
    return vo;
  }

  /**
   * 按状态统计数量（带时间范围）。
   *
   * @param status 参数说明
   * @param start 参数说明
   * @param end 参数说明
   * @return 返回值说明
   */
  private long countByStatus(MessageStatusEnum status, LocalDateTime start, LocalDateTime end) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setStatus(status.name());
    query.setStartTime(start.toString());
    query.setEndTime(end.toString());
    return msgLogRepository.count(query);
  }

  /**
   * 按状态 + 通道统计数量（带时间范围）。
   *
   * @param status 参数说明
   * @param channel 参数说明
   * @param start 参数说明
   * @param end 参数说明
   * @return 返回值说明
   */
  private long countByStatusAndChannel(
      MessageStatusEnum status, String channel, LocalDateTime start, LocalDateTime end) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setStatus(status.name());
    query.setChannel(channel);
    query.setStartTime(start.toString());
    query.setEndTime(end.toString());
    return msgLogRepository.count(query);
  }

  /**
   * 按回执状态统计数量（带时间范围）。
   *
   * @param status 参数说明
   * @param start 参数说明
   * @param end 参数说明
   * @return 返回值说明
   */
  private long countByReceiptStatus(
      ReceiptStatusEnum status, LocalDateTime start, LocalDateTime end) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setReceiptStatus(status.name());
    query.setStartTime(start.toString());
    query.setEndTime(end.toString());
    return msgLogRepository.count(query);
  }

  @Override
  public FunnelStatsDTO getFunnel(
      LocalDateTime start, LocalDateTime end, String channel, String templateCode) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    // 漏斗第1层：已发送 = status = SUCCESS
    long sent =
        countForFunnel(
            MessageStatusEnum.SUCCESS.name(), null, channel, templateCode, actualStart, actualEnd);
    // 漏斗第2层：已送达 = receiptStatus IN (DELIVERED, READ, CLICKED)（累积）
    long delivered =
        countForFunnel(
            null,
            Arrays.asList(
                ReceiptStatusEnum.DELIVERED.name(),
                ReceiptStatusEnum.READ.name(),
                ReceiptStatusEnum.CLICKED.name()),
            channel,
            templateCode,
            actualStart,
            actualEnd);
    // 漏斗第3层：已读 = receiptStatus IN (READ, CLICKED)（累积）
    long read =
        countForFunnel(
            null,
            Arrays.asList(ReceiptStatusEnum.READ.name(), ReceiptStatusEnum.CLICKED.name()),
            channel,
            templateCode,
            actualStart,
            actualEnd);
    // 漏斗第4层：已点击 = receiptStatus = CLICKED
    long clicked =
        countForFunnel(
            null,
            Collections.singletonList(ReceiptStatusEnum.CLICKED.name()),
            channel,
            templateCode,
            actualStart,
            actualEnd);

    FunnelStatsDTO vo = new FunnelStatsDTO();
    vo.setSent(sent);
    vo.setDelivered(delivered);
    vo.setRead(read);
    vo.setClicked(clicked);
    vo.setDeliveryRate(sent > 0 ? round2(delivered * 100.0 / sent) : 0.0);
    vo.setReadRate(sent > 0 ? round2(read * 100.0 / sent) : 0.0);
    vo.setClickRate(sent > 0 ? round2(clicked * 100.0 / sent) : 0.0);
    vo.setDeliveredToReadRate(delivered > 0 ? round2(read * 100.0 / delivered) : 0.0);
    vo.setReadToClickRate(read > 0 ? round2(clicked * 100.0 / read) : 0.0);
    vo.setOverallConversionRate(sent > 0 ? round2(clicked * 100.0 / sent) : 0.0);
    vo.setChannel(channel);
    vo.setTemplateCode(templateCode);
    vo.setStart(actualStart.toString());
    vo.setEnd(actualEnd.toString());
    return vo;
  }

  @Override
  public CostStatsDTO getCostStats(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    MessageProperties.CostConfig costCfg = messageProperties.getCost();
    Map<String, BigDecimal> unitPrices =
        costCfg != null && costCfg.getUnitPrices() != null
            ? costCfg.getUnitPrices()
            : Collections.emptyMap();

    List<CostStatsDTO.ChannelCost> channelCosts = new ArrayList<>();
    BigDecimal totalCost = BigDecimal.ZERO;

    for (Map.Entry<String, BigDecimal> entry : unitPrices.entrySet()) {
      String channel = entry.getKey();
      BigDecimal unitPrice = entry.getValue();
      // 统计该通道 SUCCESS 消息数
      MessageLogQueryDTO query = new MessageLogQueryDTO();
      query.setChannel(channel);
      query.setStatus(MessageStatusEnum.SUCCESS.name());
      query.setStartTime(actualStart.toString());
      query.setEndTime(actualEnd.toString());
      long msgCount = msgLogRepository.count(query);

      BigDecimal channelCost = unitPrice.multiply(BigDecimal.valueOf(msgCount));

      CostStatsDTO.ChannelCost cc = new CostStatsDTO.ChannelCost();
      cc.setChannel(channel);
      cc.setMessageCount(msgCount);
      cc.setUnitPrice(unitPrice);
      cc.setTotalCost(channelCost);
      channelCosts.add(cc);
      totalCost = totalCost.add(channelCost);
    }

    CostStatsDTO vo = new CostStatsDTO();
    vo.setTotalCost(totalCost);
    vo.setChannels(channelCosts);
    vo.setStart(actualStart.toString());
    vo.setEnd(actualEnd.toString());
    return vo;
  }

  /**
   * P2-2: 漏斗通用计数查询。
   *
   * <p>按 status（精确）或 receiptStatusList（多值）过滤,同时支持可选的 channel / templateCode 维度过滤。 status 与
   * receiptStatusList 互斥：status 非空时按 status 查,否则按 receiptStatusList 查。
   *
   * @param status 发送状态（非空时按此过滤）
   * @param receiptStatusList 回执状态集合（status 为空时按此查）
   * @param channel 通道过滤（可选）
   * @param templateCode 模板编码过滤（可选）
   * @param start 起始时间
   * @param end 结束时间
   * @return 计数
   */
  private long countForFunnel(
      String status,
      List<String> receiptStatusList,
      String channel,
      String templateCode,
      LocalDateTime start,
      LocalDateTime end) {
    // status 优先
    if (status != null) {
      MessageLogQueryDTO query = new MessageLogQueryDTO();
      query.setStatus(status);
      if (channel != null && !channel.isBlank()) {
        query.setChannel(channel);
      }
      if (templateCode != null && !templateCode.isBlank()) {
        // templateCode 通过 keyword 模糊匹配
        query.setKeyword(templateCode);
      }
      query.setStartTime(start.toString());
      query.setEndTime(end.toString());
      return msgLogRepository.count(query);
    }
    // receiptStatusList 需要逐项查询后累加（因为 DTO 只支持单值）
    if (receiptStatusList != null && !receiptStatusList.isEmpty()) {
      long total = 0;
      for (String rs : receiptStatusList) {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setReceiptStatus(rs);
        if (channel != null && !channel.isBlank()) {
          query.setChannel(channel);
        }
        if (templateCode != null && !templateCode.isBlank()) {
          query.setKeyword(templateCode);
        }
        query.setStartTime(start.toString());
        query.setEndTime(end.toString());
        total += msgLogRepository.count(query);
      }
      return total;
    }
    return 0;
  }

  /**
   * 规范化时间范围：start 为 null 时取 24h 前，end 为 null 时取当前时间。
   *
   * @param start 参数说明
   * @param end 参数说明
   * @return 返回值说明
   */
  private LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
    LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
    LocalDateTime actualStart = start != null ? start : actualEnd.minusHours(STATS_WINDOW_HOURS);
    return new LocalDateTime[] {actualStart, actualEnd};
  }

  /**
   * 保留两位小数。
   *
   * @param value 参数说明
   * @return 返回值说明
   */
  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
