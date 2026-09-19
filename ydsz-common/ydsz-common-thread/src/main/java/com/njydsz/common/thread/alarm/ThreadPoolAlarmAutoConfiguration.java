package com.njydsz.common.thread.alarm;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import com.njydsz.common.thread.config.ThreadPoolAutoConfiguration;

/**
 * 线程池告警系统自动配置。
 *
 * <p>启用方式：在 application.yml 中配置 {@code ydsz.thread.alarm.enabled=true}。
 *
 * <p>告警评估器周期性检查注册中心的所有线程池，当队列使用率、活跃度等指标超阈值时通过 {@link AlarmNotifier} SPI 发送通知。
 *
 * <p>自定义告警通道：声明一个 {@code AlarmNotifier} 类型的 Bean 覆盖默认实现即可对接企微/钉钉/邮件等通道。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see AlarmNotifier
 * @see ThreadPoolAlarmEvaluator
 */
// CHECKSTYLE.OFF: RegexpSinglelineJava — 自动配置类名长度豁免
@AutoConfiguration
// CHECKSTYLE.ON: RegexpSinglelineJava
@AutoConfigureAfter(ThreadPoolAutoConfiguration.class)
@EnableConfigurationProperties(ThreadPoolAlarmProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread.alarm", name = "enabled", havingValue = "true")
public class ThreadPoolAlarmAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolAlarmAutoConfiguration.class);

  /**
   * 注册默认告警通知器（仅打印日志）。
   *
   * <p>业务方可声明自定义 Bean 覆盖此实现。
   *
   * @return 默认告警通知器
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(AlarmNotifier.class)
  public AlarmNotifier alarmNotifier() {
    return new DefaultAlarmNotifier();
  }

  /**
   * 注册告警评估调度器。
   *
   * <p>使用单线程调度器周期性执行告警评估任务。
   *
   * @return 调度执行器
   */
  @Bean(destroyMethod = "shutdown")
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(name = "alarmEvalScheduler")
  public ScheduledExecutorService alarmEvalScheduler() {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ydsz-alarm-eval");
      t.setDaemon(true);
      return t;
    });
    return scheduler;
  }

  /**
   * 注册线程池告警评估器。
   *
   * <p>周期性采集 ThreadPoolRegistry 中所有已注册线程池指标，按阈值判断是否触发告警。
   *
   * @param alarmNotifier 告警通知器
   * @param scheduler 调度器
   * @param properties 告警配置属性
   * @return 告警评估器
   */
  @Bean(initMethod = "start", destroyMethod = "shutdown")
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(ThreadPoolAlarmEvaluator.class)
  public ThreadPoolAlarmEvaluator threadPoolAlarmEvaluator(
      AlarmNotifier alarmNotifier,
      ScheduledExecutorService alarmEvalScheduler,
      ThreadPoolAlarmProperties properties) {

    ThreadPoolAlarmEvaluator.AlarmEvalConfig config =
        ThreadPoolAlarmEvaluator.AlarmEvalConfig.builder()
            .evalIntervalMs(properties.getEvalIntervalMs())
            .alarmSuppressWindowMs(properties.getSuppressWindowMs())
            .queueUsageThreshold(properties.getQueueUsageThreshold())
            .activeNearMaxThreshold(properties.getActiveNearMaxThreshold())
            .build();

    DefaultThreadPoolAlarmEvaluator evaluator =
        new DefaultThreadPoolAlarmEvaluator(alarmNotifier, alarmEvalScheduler, config);

    LOG.info("[ydsz-thread] 告警系统已启用，评估间隔: {}ms", properties.getEvalIntervalMs());
    return evaluator;
  }
}
