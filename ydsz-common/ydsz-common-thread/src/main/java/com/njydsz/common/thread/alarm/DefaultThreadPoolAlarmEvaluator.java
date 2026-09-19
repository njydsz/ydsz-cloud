package com.njydsz.common.thread.alarm;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * 默认线程池告警评估器实现。
 *
 * <p>基于静态 {@link ThreadPoolRegistry} 周期性采集已注册平台线程池指标，按阈值判断并发出告警。
 *
 * <p>告警去重：在同一 suppress 窗口内同一线程池同一维度仅产生一条告警事件，避免告警风暴。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class DefaultThreadPoolAlarmEvaluator extends ThreadPoolAlarmEvaluator {

  /**
   * 构造默认告警评估器。
   *
   * @param alarmNotifier 告警通知器
   * @param scheduler 调度器
   * @param evalConfig 评估配置
   */
  public DefaultThreadPoolAlarmEvaluator(
      AlarmNotifier alarmNotifier,
      ScheduledExecutorService scheduler,
      AlarmEvalConfig evalConfig) {
    super(alarmNotifier, scheduler, evalConfig);
  }

  /**
   * 从 ThreadPoolRegistry 获取所有已注册的平台线程池。
   *
   * @return 线程池映射
   */
  @Override
  protected Map<String, ? extends Object> getMonitoredExecutors() {
    return ThreadPoolRegistry.getAll();
  }

  /**
   * 解包线程池执行器。
   *
   * <p>ThreadPoolRegistry 中存储的本身就是 ThreadPoolExecutor，直接返回即可。
   *
   * @param executor 线程池对象
   * @return 对应的 ThreadPoolExecutor
   */
  @Override
  protected ThreadPoolExecutor unwrapExecutor(Object executor) {
    if (executor instanceof ThreadPoolExecutor) {
      return (ThreadPoolExecutor) executor;
    }
    // 兼容 ThreadPoolTaskExecutor 类型（理论上不会出现在 Registry 中）
    if (executor instanceof ThreadPoolTaskExecutor) {
      return ((ThreadPoolTaskExecutor) executor).getThreadPoolExecutor();
    }
    return null;
  }
}
