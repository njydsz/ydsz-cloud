package com.njydsz.message.server.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.message.domain.dto.core.ChannelStatsVO;
import com.njydsz.message.domain.dto.core.CostStatsVO;
import com.njydsz.message.domain.dto.core.FunnelStatsVO;
import com.njydsz.message.domain.dto.core.MessageStatsVO;
import com.njydsz.message.domain.dto.receipt.ReceiptStatsVO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.infra.repository.MsgLogRepository;
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

  /** 消息日志 Repository（聚合统计查询） */
  private final MsgLogRepository msgLogRepository;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  @Override
  public MessageStatsVO getOverview(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    long success = countByStatus(MessageStatusEnum.SUCCESS, actualStart, actualEnd);
    long failed = countByStatus(MessageStatusEnum.FAILED, actualStart, actualEnd);
    long retry = countByStatus(MessageStatusEnum.RETRY, actualStart, actualEnd);
    long dead = countByStatus(MessageStatusEnum.DEAD, actualStart, actualEnd);
    long recalled = countByStatus(MessageStatusEnum.RECALLED, actualStart, actualEnd);
    long total = success + failed + retry + dead + recalled;

    MessageStatsVO vo = new MessageStatsVO();
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
  public List<ChannelStatsVO> getChannelStats(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    List<ChannelStatsVO> result = new ArrayList<>();
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

      ChannelStatsVO vo = new ChannelStatsVO();
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
  public ReceiptStatsVO getReceiptStats(LocalDateTime start, LocalDateTime end) {
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

    ReceiptStatsVO vo = new ReceiptStatsVO();
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

  /** 按状态统计数量（带时间范围）。 */
  private long countByStatus(MessageStatusEnum status, LocalDateTime start, LocalDateTime end) {
    Long count =
        msgLogRepository.selectCount(
            new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getStatus, status.name())
                .ge(MsgLog::getCreatedAt, start)
                .le(MsgLog::getCreatedAt, end));
    return count == null ? 0L : count;
  }

  /** 按状态 + 通道统计数量（带时间范围）。 */
  private long countByStatusAndChannel(
      MessageStatusEnum status, String channel, LocalDateTime start, LocalDateTime end) {
    Long count =
        msgLogRepository.selectCount(
            new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getStatus, status.name())
                .eq(MsgLog::getChannel, channel)
                .ge(MsgLog::getCreatedAt, start)
                .le(MsgLog::getCreatedAt, end));
    return count == null ? 0L : count;
  }

  /** 按回执状态统计数量（带时间范围）。 */
  private long countByReceiptStatus(
      ReceiptStatusEnum status, LocalDateTime start, LocalDateTime end) {
    Long count =
        msgLogRepository.selectCount(
            new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getReceiptStatus, status.name())
                .ge(MsgLog::getCreatedAt, start)
                .le(MsgLog::getCreatedAt, end));
    return count == null ? 0L : count;
  }

  @Override
  public FunnelStatsVO getFunnel(
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

    FunnelStatsVO vo = new FunnelStatsVO();
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
  public CostStatsVO getCostStats(LocalDateTime start, LocalDateTime end) {
    LocalDateTime[] range = normalizeRange(start, end);
    LocalDateTime actualStart = range[0];
    LocalDateTime actualEnd = range[1];

    MessageProperties.CostConfig costCfg = messageProperties.getCost();
    Map<String, BigDecimal> unitPrices =
        costCfg != null && costCfg.getUnitPrices() != null
            ? costCfg.getUnitPrices()
            : Collections.emptyMap();

    List<CostStatsVO.ChannelCost> channelCosts = new ArrayList<>();
    BigDecimal totalCost = BigDecimal.ZERO;

    for (Map.Entry<String, BigDecimal> entry : unitPrices.entrySet()) {
      String channel = entry.getKey();
      BigDecimal unitPrice = entry.getValue();
      // 统计该通道 SUCCESS 消息数
      LambdaQueryWrapper<MsgLog> w = new LambdaQueryWrapper<>();
      w.eq(MsgLog::getChannel, channel);
      w.eq(MsgLog::getStatus, MessageStatusEnum.SUCCESS.name());
      w.ge(MsgLog::getCreatedAt, actualStart);
      w.le(MsgLog::getCreatedAt, actualEnd);
      Long count = msgLogRepository.selectCount(w);
      long msgCount = count == null ? 0L : count;

      BigDecimal channelCost = unitPrice.multiply(BigDecimal.valueOf(msgCount));

      CostStatsVO.ChannelCost cc = new CostStatsVO.ChannelCost();
      cc.setChannel(channel);
      cc.setMessageCount(msgCount);
      cc.setUnitPrice(unitPrice);
      cc.setTotalCost(channelCost);
      channelCosts.add(cc);
      totalCost = totalCost.add(channelCost);
    }

    CostStatsVO vo = new CostStatsVO();
    vo.setTotalCost(totalCost);
    vo.setChannels(channelCosts);
    vo.setStart(actualStart.toString());
    vo.setEnd(actualEnd.toString());
    return vo;
  }

  /**
   * P2-2: 漏斗通用计数查询。
   *
   * <p>按 status（精确）或 receiptStatus（IN 集合）过滤,同时支持可选的 channel / templateCode 维度过滤。 status 与
   * receiptStatusList 互斥：status 非空时按 status 查,否则按 receiptStatusList 查。
   *
   * @param status 发送状态（非空时按此过滤）
   * @param receiptStatusList 回执状态集合（status 为空时按此 IN 过滤）
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
    LambdaQueryWrapper<MsgLog> w = new LambdaQueryWrapper<>();
    if (status != null) {
      w.eq(MsgLog::getStatus, status);
    } else if (receiptStatusList != null && !receiptStatusList.isEmpty()) {
      w.in(MsgLog::getReceiptStatus, receiptStatusList);
    }
    if (channel != null && !channel.isBlank()) {
      w.eq(MsgLog::getChannel, channel);
    }
    if (templateCode != null && !templateCode.isBlank()) {
      w.eq(MsgLog::getTemplateCode, templateCode);
    }
    w.ge(MsgLog::getCreatedAt, start);
    w.le(MsgLog::getCreatedAt, end);
    Long count = msgLogRepository.selectCount(w);
    return count == null ? 0L : count;
  }

  /** 规范化时间范围：start 为 null 时取 24h 前，end 为 null 时取当前时间。 */
  private LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
    LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
    LocalDateTime actualStart = start != null ? start : actualEnd.minusHours(24);
    return new LocalDateTime[] {actualStart, actualEnd};
  }

  /** 保留两位小数。 */
  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
