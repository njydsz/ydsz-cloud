package com.njydsz.message.server.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatsServiceImpl implements MessageStatsService {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

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

    // 单条 GROUP BY 拉取各状态计数，内存内取数，替代 5 次 COUNT
    Map<String, Long> statusCount = loadStatusCountMap(actualStart, actualEnd);
    long success = statusCount.getOrDefault(MessageStatusEnum.SUCCESS.name(), 0L);
    long failed = statusCount.getOrDefault(MessageStatusEnum.FAILED.name(), 0L);
    long retry = statusCount.getOrDefault(MessageStatusEnum.RETRY.name(), 0L);
    long dead = statusCount.getOrDefault(MessageStatusEnum.DEAD.name(), 0L);
    long recalled = statusCount.getOrDefault(MessageStatusEnum.RECALLED.name(), 0L);
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

    // 单条 GROUP BY 拉取全部 (channel, status) 计数，内存内聚合，替代 12 通道 × 4 状态 = 48 次 COUNT
    Map<String, Map<String, Long>> byChannel = new LinkedHashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row :
        msgLogRepository.countGroupByChannelAndStatus(actualStart, actualEnd)) {
      String channel = asString(row.get("channel"));
      String status = asString(row.get("status"));
      if (channel == null || status == null) {
        continue;
      }
      byChannel
          .computeIfAbsent(channel, k -> new HashMap<>(COLLECTION_CAPACITY))
          .merge(status, asLong(row.get("cnt")), Long::sum);
    }

    List<ChannelStatsDTO> result = new ArrayList<>(byChannel.size());
    for (Map.Entry<String, Map<String, Long>> entry : byChannel.entrySet()) {
      String channel = entry.getKey();
      Map<String, Long> statusMap = entry.getValue();
      long success = statusMap.getOrDefault(MessageStatusEnum.SUCCESS.name(), 0L);
      long failed = statusMap.getOrDefault(MessageStatusEnum.FAILED.name(), 0L);
      long retry = statusMap.getOrDefault(MessageStatusEnum.RETRY.name(), 0L);
      long dead = statusMap.getOrDefault(MessageStatusEnum.DEAD.name(), 0L);
      long total = success + failed + retry + dead;

      // 只输出有数据的通道（保持原有过滤语义）
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

    // 单条 GROUP BY 拉取回执状态计数，内存内取数，替代 6 次 COUNT
    Map<String, Long> receiptCount = loadReceiptCountMap(actualStart, actualEnd);
    // 回执分母 = 成功发送数（与回执状态同一张表，复用同一次扫描的状态维度聚合）
    Map<String, Long> statusCount = loadStatusCountMap(actualStart, actualEnd);
    long total = statusCount.getOrDefault(MessageStatusEnum.SUCCESS.name(), 0L);
    long delivered = receiptCount.getOrDefault(ReceiptStatusEnum.DELIVERED.name(), 0L);
    long read = receiptCount.getOrDefault(ReceiptStatusEnum.READ.name(), 0L);
    long clicked = receiptCount.getOrDefault(ReceiptStatusEnum.CLICKED.name(), 0L);
    long failed = receiptCount.getOrDefault(ReceiptStatusEnum.FAILED.name(), 0L);
    long timeout = receiptCount.getOrDefault(ReceiptStatusEnum.TIMEOUT.name(), 0L);
    long none = receiptCount.getOrDefault(ReceiptStatusEnum.NONE.name(), 0L);

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

    List<CostStatsDTO.ChannelCost> channelCosts = new ArrayList<>(COLLECTION_CAPACITY);
    BigDecimal totalCost = BigDecimal.ZERO;

    // 单条 GROUP BY 拉取各通道 SUCCESS 计数，替代逐通道 COUNT（最多 12 次）
    Map<String, Long> successByChannel = loadSuccessCountByChannel(actualStart, actualEnd);
    for (Map.Entry<String, BigDecimal> entry : unitPrices.entrySet()) {
      String channel = entry.getKey();
      BigDecimal unitPrice = entry.getValue();
      long msgCount = successByChannel.getOrDefault(channel, 0L);

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
    // receiptStatusList：单条 GROUP BY receipt_status 后内存内累加（原 N 次 COUNT 降为 1 次）
    if (receiptStatusList != null && !receiptStatusList.isEmpty()) {
      String keyword = (templateCode != null && !templateCode.isBlank()) ? templateCode : null;
      Map<String, Long> receiptCount =
          loadReceiptCountMapFiltered(channel, keyword, start, end);
      long total = 0L;
      for (String rs : receiptStatusList) {
        total += receiptCount.getOrDefault(rs, 0L);
      }
      return total;
    }
    return 0;
  }

  /**
   * 加载按发送状态分组的计数 Map（私有）。
   *
   * @param start 起始时间
   * @param end 结束时间
   * @return status → count
   */
  private Map<String, Long> loadStatusCountMap(LocalDateTime start, LocalDateTime end) {
    Map<String, Long> result = new HashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row : msgLogRepository.countGroupByStatus(start, end)) {
      String status = asString(row.get("status"));
      if (status != null) {
        result.merge(status, asLong(row.get("cnt")), Long::sum);
      }
    }
    return result;
  }

  /**
   * 加载按回执状态分组的计数 Map（私有）。
   *
   * @param start 起始时间
   * @param end 结束时间
   * @return receiptStatus → count
   */
  private Map<String, Long> loadReceiptCountMap(LocalDateTime start, LocalDateTime end) {
    Map<String, Long> result = new HashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row : msgLogRepository.countGroupByReceiptStatus(start, end)) {
      String receiptStatus = asString(row.get("receiptStatus"));
      if (receiptStatus != null) {
        result.merge(receiptStatus, asLong(row.get("cnt")), Long::sum);
      }
    }
    return result;
  }

  /**
   * 加载带通道 / 关键词过滤的回执状态分组计数 Map（私有）。
   */
  private Map<String, Long> loadReceiptCountMapFiltered(
      String channel, String keyword, LocalDateTime start, LocalDateTime end) {
    Map<String, Long> result = new HashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row :
        msgLogRepository.countGroupByReceiptStatusFiltered(channel, keyword, start, end)) {
      String receiptStatus = asString(row.get("receiptStatus"));
      if (receiptStatus != null) {
        result.merge(receiptStatus, asLong(row.get("cnt")), Long::sum);
      }
    }
    return result;
  }

  /**
   * 加载各通道 SUCCESS 计数 Map（私有）。
   */
  private Map<String, Long> loadSuccessCountByChannel(
      LocalDateTime start, LocalDateTime end) {
    Map<String, Long> result = new HashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row :
        msgLogRepository.countGroupByChannelAndStatus(start, end)) {
      String channel = asString(row.get("channel"));
      String status = asString(row.get("status"));
      if (channel != null && MessageStatusEnum.SUCCESS.name().equals(status)) {
        result.merge(channel, asLong(row.get("cnt")), Long::sum);
      }
    }
    return result;
  }

  /** Map取值转 String（私有）。 */
  private String asString(Object value) {
    return value != null ? value.toString() : null;
  }

  /** Map取值转 long（私有，兼容 Long/Integer/BigDecimal 等聚合返回类型）。 */
  private long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return value != null ? Long.parseLong(value.toString()) : 0L;
  }

  /**
   * 规范化时间范围：start 为 null 时取 24h 前，end 为 null 时取当前时间。
   *
   * @param start 统计起始时间（为空时默认取 end 前 24 小时）
   * @param end 统计结束时间（为空时默认取当前时间）
   * @return 包含 actualStart 和 actualEnd 的时间范围数组
   */
  private LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
    LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
    LocalDateTime actualStart = start != null ? start : actualEnd.minusHours(STATS_WINDOW_HOURS);
    return new LocalDateTime[] {actualStart, actualEnd};
  }

  /**
   * 保留两位小数。
   *
   * @param value 原始浮点值
   * @return 保留两位小数的结果
   */
  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
