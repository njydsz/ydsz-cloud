package com.njydsz.common.thread.alarm;

import java.util.List;

/**
 * 线程池告警通知 SPI。
 *
 * <p>业务方可通过实现本接口将告警事件推送到企微、钉钉、飞书、邮件等通道。
 * 默认实现 {@link DefaultAlarmNotifier} 仅打印 warn 日志，无外部依赖。
 *
 * <p>注册方式：在 Spring 容器中声明一个 {@code @Bean} 覆盖即可：
 *
 * <pre>{@code
 * @Bean
 * public AlarmNotifier wechatAlarmNotifier() {
 *     return new WechatAlarmNotifier();
 * }
 * }</pre>
 *
 * <p>告警去重由 {@link ThreadPoolAlarmEvaluator} 负责，实现类无需关心告警频率上限。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see DefaultAlarmNotifier
 * @see ThreadPoolAlarmEvaluator
 */
public interface AlarmNotifier {

  /**
   * 发送线程池告警通知。
   *
   * <p>实现类应捕获所有异常，确保告警发送失败不影响业务线程池运行。
   * 建议异步发送或设置短超时（≤ 3s），避免阻塞指标采集线程。
   *
   * @param event 告警事件，包含线程池名称、告警维度、当前值、阈值等信息；不会为 null
   */
  void notify(AlarmEvent event);

  /**
   * 批量发送线程池告警通知。
   *
   * <p>默认实现为逐条调用 {@link #notify(AlarmEvent)}。实现类可覆写此方法以支持批量推送优化。
   *
   * @param events 告警事件列表，不会为 null 但可能为空
   */
  default void notifyBatch(List<AlarmEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    for (AlarmEvent event : events) {
      notify(event);
    }
  }
}
