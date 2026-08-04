package com.njydsz.common.cache.metrics;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import com.njydsz.common.cache.api.Cache;

/**
 * 默认的 Cache ↔ HotKeyMetrics 桥接器。
 *
 * <p>在所有 Singleton Bean 初始化完成后，遍历 ApplicationContext 中的所有 {@link Cache} Bean，
 * 对每一个 Bean 尝试从 {@link HotKeyMetricsRegistry} 获取对应的{@code HotKeyTracker}；
 * 若不存在，则使用 Bean 名称作为 cacheName 新建一个。
 *
 * <p>本类作为通用组件只负责发现；真正的采集埋点由业务方手动通过{@code HotKeyTrackingCacheDecorator}
 * 或通过 BeanPostProcessor 包装完成 — 避免 Spring 上下文初始化时强包装代理影响 Bean 生命周期。
 *
 * <p>线程安全：{@link PostConstruct} 在容器刷新阶段只执行一次。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultHotKeyCacheBinder implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(DefaultHotKeyCacheBinder.class);

  private final HotKeyMetricsRegistry registry;
  private final ApplicationContext applicationContext;

  public DefaultHotKeyCacheBinder(HotKeyMetricsRegistry registry,
      ApplicationContext applicationContext) {
    this.registry = registry;
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterSingletonsInstantiated() {
    String[] cacheBeanNames = applicationContext.getBeanNamesForType(Cache.class);
    if (cacheBeanNames.length == 0) {
      log.info("HotKeyMetricsAutoConfiguration：容器中未发现 Cache Bean，跳过桥接");
      return;
    }
    log.info("HotKeyMetricsAutoConfiguration：发现 {} 个 Cache Bean，开始注册 HotKeyMetrics",
        cacheBeanNames.length);
    for (String beanName : cacheBeanNames) {
      try {
        registry.register(beanName);
      } catch (Exception e) {
        log.warn("HotKeyMetrics[{}] 注册失败", beanName, e);
      }
    }
  }
}
