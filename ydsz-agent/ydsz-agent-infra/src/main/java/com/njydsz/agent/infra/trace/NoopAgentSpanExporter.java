package com.njydsz.agent.infra.trace;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trace.AgentSpan;
import com.njydsz.agent.domain.trace.AgentSpanExporter;

/**
 * 空操作 Span 导出器（OpenTelemetry SDK 不在类路径时的默认实现）。
 *
 * <p>实现了"优雅降级"：当项目未引入 OTel  SDK 时，链路记录器仍然正常工作，
 * 仅不向外部系统传输 Span（Span 已写入 PG 表，可供本地审计）。
 *
 * <p>通过 {@code @ConditionalOnMissingBean} 注册，若 classpath 上存在 OTel SDK 且
 * AgentAutoConfiguration 成功创建了 {@code OtelAgentSpanExporter}，则本 Bean 不生效。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
public class NoopAgentSpanExporter implements AgentSpanExporter {

  /** 单例实例 */
  private static final NoopAgentSpanExporter INSTANCE = new NoopAgentSpanExporter();

  /**
   * 获取单例。
   *
   * @return 空操作导出器实例
   */
  public static NoopAgentSpanExporter getInstance() {
    return INSTANCE;
  }

  private NoopAgentSpanExporter() {}

  /**
   * 不执行任何操作（Span 已在 PG 中落库）。
   *
   * @param span 传入的 Span（被丢弃）
   */
  @Override
  public void export(AgentSpan span) {
    // Span 由 TraceRecorder 写入 PG 表，此处不对外传输
  }

  /**
   * 不执行任何操作。
   */
  @Override
  public void close() {
    // 无资源需要释放
  }
}
