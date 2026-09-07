package com.njydsz.agent.domain.trace;

/**
 * Agent Span 导出器接口。
 *
 * <p>定义 {@link AgentSpan} 的可观测性后端传输契约。实现类负责将 Span 转换为
 * 特定协议格式（OpenTelemetry OTLP gRPC/HTTP、日志、消息队列等）并推送至后端。
 *
 * <p>该接口作为 domain 层的网关（Gateway）抽象，解耦链路记录逻辑与具体可观测性后端的编译期依赖。
 * infra 层实现可能通过反射或 @ConditionalOnClass 按需加载 SDK。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface AgentSpanExporter {

  /**
   * 导出一条 Span 记录。
   *
   * <p>实现类应确保本方法快速返回（建议使用异步批量导出），不应阻塞调用线程。
   * 若导出失败，实现类应自行记录日志并可丢弃 Span（不抛出异常、不重试以避免反压）。
   *
   * @param span 待导出的 Span，不可为 {@code null}
   */
  void export(AgentSpan span);

  /**
   * 优雅关闭导出器。
   *
   * <p>触发缓冲区中剩余 Span 的刷出，释放底层连接池和线程资源。
   * 建议在 Spring 容器销毁时由 {@code @Bean(destroyMethod="close")} 或 {@code DisposableBean} 调用。
   */
  void close();
}
