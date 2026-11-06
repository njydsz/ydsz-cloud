package com.njydsz.message.infra.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MsgLogRepositoryImpl} 聚合统计查询测试（26.11.06 性能优化）。
 *
 * <p><b>覆盖目标</b>：新增的 4 个 {@code GROUP BY} 聚合方法与 {@code markRecalledByBiz}
 * 的委托逻辑，以及 {@code safeGroupRows} 的空值防护分支。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class MsgLogRepositoryAggregationTest {

  private MsgLogMapper msgLogMapper;
  private MsgLogRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    msgLogMapper = mock(MsgLogMapper.class);
    repository = new MsgLogRepositoryImpl(msgLogMapper, mock(MessageConverter.class));
  }

  @Test
  @DisplayName("countGroupByChannelAndStatus：委托 mapper 并原样返回")
  void shouldDelegateChannelStatusAggregation() {
    List<Map<String, Object>> rows =
        List.of(Map.of("channel", "SMS", "status", "SUCCESS", "cnt", 10L));
    when(msgLogMapper.countGroupByChannelAndStatus(any(), any())).thenReturn(rows);

    List<Map<String, Object>> result =
        repository.countGroupByChannelAndStatus(
            LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).get("channel")).isEqualTo("SMS");
  }

  @Test
  @DisplayName("countGroupByStatus：委托 mapper")
  void shouldDelegateStatusAggregation() {
    when(msgLogMapper.countGroupByStatus(any(), any()))
        .thenReturn(List.of(Map.of("status", "SUCCESS", "cnt", 5L)));

    List<Map<String, Object>> result =
        repository.countGroupByStatus(LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).get("status")).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("countGroupByReceiptStatus：委托 mapper")
  void shouldDelegateReceiptStatusAggregation() {
    when(msgLogMapper.countGroupByReceiptStatus(any(), any()))
        .thenReturn(List.of(Map.of("receipt_status", "SUCCESS", "cnt", 3L)));

    List<Map<String, Object>> result =
        repository.countGroupByReceiptStatus(
            LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("countGroupByReceiptStatusFiltered：委托 mapper 并透传筛选参数")
  void shouldDelegateFilteredReceiptAggregation() {
    when(msgLogMapper.countGroupByReceiptStatusFiltered(anyString(), anyString(), any(), any()))
        .thenReturn(List.of(Map.of("cnt", 2L)));

    List<Map<String, Object>> result =
        repository.countGroupByReceiptStatusFiltered(
            "SMS", "keyword", LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("safeGroupRows：mapper 返回 null 时兜底为空列表")
  void shouldFallbackToEmptyListWhenMapperReturnsNull() {
    when(msgLogMapper.countGroupByStatus(any(), any())).thenReturn(null);

    List<Map<String, Object>> result =
        repository.countGroupByStatus(LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("safeGroupRows：mapper 返回空列表时原样返回")
  void shouldReturnEmptyListAsIs() {
    when(msgLogMapper.countGroupByStatus(any(), any())).thenReturn(Collections.emptyList());

    List<Map<String, Object>> result =
        repository.countGroupByStatus(LocalDateTime.now().minusDays(1), LocalDateTime.now());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("markRecalledByBiz：委托 mapper 并返回影响行数")
  void shouldDelegateMarkRecalledByBiz() {
    when(msgLogMapper.markRecalledByBiz(anyString(), anyString(), anyString(), any()))
        .thenReturn(7);

    int affected =
        repository.markRecalledByBiz(
            "ORDER", "BIZ-1", "RECALLED", LocalDateTime.now());

    assertThat(affected).isEqualTo(7);
  }

  @Test
  @DisplayName("markRecalledByBiz：无匹配记录时返回 0")
  void shouldReturnZeroWhenNoRowsAffected() {
    when(msgLogMapper.markRecalledByBiz(anyString(), anyString(), anyString(), any()))
        .thenReturn(0);

    int affected =
        repository.markRecalledByBiz(
            "ORDER", "BIZ-NOT-EXIST", "RECALLED", LocalDateTime.now());

    assertThat(affected).isZero();
  }
}
