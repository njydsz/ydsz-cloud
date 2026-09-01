package com.njydsz.common.util.internal.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceIdGeneratorProxy 降级路径测试。
 *
 * <p>测试 classpath 不含 ydsz-common-core（L1 工具层禁止反向依赖 L2），
 * 因此本测试验证的是降级实现的正确性：
 *
 * <ul>
 *   <li>内置 TraceId：32 位 hex 且带时间戳前缀（时间有序语义）
 *   <li>内置 SpanId：16 位 hex
 *   <li>verifyBinding 在 core 缺失时返回 true（正常独立使用场景）
 * </ul>
 *
 * <p>core 在 classpath 时的绑定路径由 UtilAutoConfiguration 启动自检验证，
 * 属集成测试范畴（需引入 ydsz-common-core 依赖）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class TraceIdGeneratorProxyTest {

  /** W3C TraceContext Trace ID 长度（hex 字符数） */
  private static final int TRACE_ID_LENGTH = 32;

  /** W3C TraceContext Span ID 长度（hex 字符数） */
  private static final int SPAN_ID_LENGTH = 16;

  /** 降级实现的时间戳前缀长度（12 hex = 48 bit 毫秒） */
  private static final int TIMESTAMP_PREFIX_LENGTH = 12;

  @Test
  @DisplayName("降级 TraceId：32 位 hex 且两次生成不重复")
  void fallbackTraceIdFormat() {
    String first = TraceIdGeneratorProxy.generateSortableTraceId();
    String second = TraceIdGeneratorProxy.generateSortableTraceId();

    assertThat(first).hasSize(TRACE_ID_LENGTH).matches("[0-9a-f]{32}");
    assertThat(second).hasSize(TRACE_ID_LENGTH).matches("[0-9a-f]{32}");
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("降级 TraceId：前 12 位 hex 为时间戳前缀，两次生成单调不减")
  void fallbackTraceIdTimeSortable() {
    String first = TraceIdGeneratorProxy.generateSortableTraceId();
    String second = TraceIdGeneratorProxy.generateSortableTraceId();

    long firstPrefix =
        Long.parseLong(first.substring(0, TIMESTAMP_PREFIX_LENGTH), 16);
    long secondPrefix =
        Long.parseLong(second.substring(0, TIMESTAMP_PREFIX_LENGTH), 16);

    assertThat(secondPrefix)
        .as("时间戳前缀应保持时间有序语义（单调不减）")
        .isGreaterThanOrEqualTo(firstPrefix);
  }

  @Test
  @DisplayName("降级 SpanId：16 位 hex")
  void fallbackSpanIdFormat() {
    assertThat(TraceIdGeneratorProxy.generateSpanId())
        .hasSize(SPAN_ID_LENGTH)
        .matches("[0-9a-f]{16}");
  }

  @Test
  @DisplayName("core 缺失时 verifyBinding 返回 true（正常独立使用，不告警）")
  void verifyBindingPassesWhenCoreAbsent() {
    assertThat(TraceIdGeneratorProxy.isAvailable()).isFalse();
    assertThat(TraceIdGeneratorProxy.verifyBinding()).isTrue();
  }
}
