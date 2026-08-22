package com.njydsz.cronjob.server.core.logger;

import com.lmax.disruptor.EventFactory;

/**
 * P2-3: Disruptor 日志事件工厂。
 *
 * <p>预分配 ring buffer 中的所有事件对象，避免运行时 GC。 由 Disruptor 在初始化时调用，创建固定数量的事件实例。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DisruptorLogEventFactory implements EventFactory<DisruptorLogEvent> {

  @Override
  public DisruptorLogEvent newInstance() {
    return new DisruptorLogEvent();
  }
}
