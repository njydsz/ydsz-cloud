package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.server.config.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageStatsServiceImpl} 聚合查询测试（26.11.06 性能优化）。
 *
 * <p><b>背景</b>：历史实现按通道 × 状态循环 {@code COUNT(*)}（最多 48 次），
 * 现改为单条 {@code GROUP BY} 聚合 + 内存归并。本测试覆盖聚合结果的内存归并逻辑、
 * 空结果处理、缺失状态补零、比率计算。
 *
 * <p>采用纯 Mockito 单元测试，mock {@link MsgLogRepository} 返回聚合行。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class MessageStatsAggregationTest {

  private MsgLogRepository msgLogRepository;
  private MessageStatsServiceImpl service;

  @BeforeEach
  void setUp() {
    msgLogRepository = mock(MsgLogRepository.class);
    service = new MessageStatsServiceImpl(msgLogRepository, mock(MessageProperties.class));
  }

  @Test
  @DisplayName("getOverview：单条 GROUP BY 结果正确归并为总览")
  void shouldAggregateOverviewFromSingleGroupBy() {
    when(msgLogRepository.countGroupByStatus(any(), any()))
        .thenReturn(List.of(
            statusRow("SUCCESS", 80L),
            statusRow("FAILED", 10L),
            statusRow("RETRY", 5L),
            statusRow("DEAD", 3L),
            statusRow("RECALLED", 2L)));

    var result = service.getOverview(LocalDateTime.now().minusDays(7), LocalDateTime.now());

    assertThat(result.getTotal()).isEqualTo(100L);
    assertThat(result.getSuccess()).isEqualTo(80L);
    assertThat(result.getFailed()).isEqualTo(10L);
    assertThat(result.getRetry()).isEqualTo(5L);
    assertThat(result.getDead()).isEqualTo(3L);
    assertThat(result.getRecalled()).isEqualTo(2L);
    assertThat(result.getSuccessRate()).isEqualTo(80.0);
    assertThat(result.getDeadRate()).isEqualTo(3.0);
  }

  @Test
  @DisplayName("getOverview：空结果全部补零且比率不除零")
  void shouldHandleEmptyOverview() {
    when(msgLogRepository.countGroupByStatus(any(), any())).thenReturn(new ArrayList<>());

    var result = service.getOverview(LocalDateTime.now().minusDays(7), LocalDateTime.now());

    assertThat(result.getTotal()).isZero();
    assertThat(result.getSuccess()).isZero();
    assertThat(result.getSuccessRate()).isZero();
    assertThat(result.getDeadRate()).isZero();
  }

  @Test
  @DisplayName("getOverview：缺失状态补零（仅 SUCCESS 有数据）")
  void shouldFillMissingStatusWithZero() {
    when(msgLogRepository.countGroupByStatus(any(), any()))
        .thenReturn(List.of(statusRow("SUCCESS", 7L)));

    var result = service.getOverview(LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result.getTotal()).isEqualTo(7L);
    assertThat(result.getFailed()).isZero();
    assertThat(result.getRetry()).isZero();
    assertThat(result.getDead()).isZero();
    assertThat(result.getRecalled()).isZero();
  }

  @Test
  @DisplayName("getChannelStats：按 (channel,status) 聚合为通道维度")
  void shouldAggregateChannelStats() {
    when(msgLogRepository.countGroupByChannelAndStatus(any(), any()))
        .thenReturn(List.of(
            channelRow("SMS", "SUCCESS", 30L),
            channelRow("SMS", "FAILED", 2L),
            channelRow("EMAIL", "SUCCESS", 20L)));

    var result = service.getChannelStats(LocalDateTime.now().minusDays(7), LocalDateTime.now());

    assertThat(result).hasSize(2);
    var sms =
        result.stream().filter(c -> "SMS".equals(c.getChannel())).findFirst().orElseThrow();
    assertThat(sms.getSuccess()).isEqualTo(30L);
    assertThat(sms.getFailed()).isEqualTo(2L);
    assertThat(sms.getTotal()).isEqualTo(32L);
    var email =
        result.stream().filter(c -> "EMAIL".equals(c.getChannel())).findFirst().orElseThrow();
    assertThat(email.getSuccess()).isEqualTo(20L);
    assertThat(email.getFailed()).isZero();
  }

  @Test
  @DisplayName("getChannelStats：跳过 channel/status 为 null 的脏行")
  void shouldSkipDirtyRows() {
    when(msgLogRepository.countGroupByChannelAndStatus(any(), any()))
        .thenReturn(List.of(
            channelRow("SMS", "SUCCESS", 10L),
            channelRow(null, "SUCCESS", 99L),
            channelRow("SMS", null, 99L)));

    var result = service.getChannelStats(LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSuccess()).isEqualTo(10L);
  }

  @Test
  @DisplayName("getReceiptStats：回执状态聚合不抛异常")
  void shouldAggregateReceiptStats() {
    when(msgLogRepository.countGroupByReceiptStatus(any(), any()))
        .thenReturn(List.of(
            receiptRow("SUCCESS", 40L), receiptRow("FAILED", 5L)));

    var result = service.getReceiptStats(LocalDateTime.now().minusDays(7), LocalDateTime.now());

    assertThat(result).isNotNull();
  }

  /**
   * 构造状态计数行。
   *
   * @param status 状态名
   * @param cnt 计数
   * @return 聚合行 Map
   */
  private Map<String, Object> statusRow(String status, Object cnt) {
    Map<String, Object> map = new LinkedHashMap<>(2);
    map.put("status", status);
    map.put("cnt", cnt);
    return map;
  }

  /**
   * 构造通道 + 状态计数行。
   *
   * @param channel 通道名
   * @param status 状态名
   * @param cnt 计数
   * @return 聚合行 Map
   */
  private Map<String, Object> channelRow(String channel, String status, Object cnt) {
    Map<String, Object> map = new LinkedHashMap<>(4);
    map.put("channel", channel);
    map.put("status", status);
    map.put("cnt", cnt);
    return map;
  }

  /**
   * 构造回执状态计数行。
   *
   * @param receiptStatus 回执状态
   * @param cnt 计数
   * @return 聚合行 Map
   */
  private Map<String, Object> receiptRow(String receiptStatus, Object cnt) {
    Map<String, Object> map = new LinkedHashMap<>(2);
    map.put("receipt_status", receiptStatus);
    map.put("cnt", cnt);
    return map;
  }
}
