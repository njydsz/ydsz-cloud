package com.njydsz.workflow.server.service.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.enums.FlowPerformType;

/**
 * 会签策略工厂。
 *
 * <p>封装或签/顺序会签/并行会签/FOREACH 并行等策略的创建与查找，
 *
 * <p>对外暴露 {@code getStrategy(String type)} 接口，根据节点配置返回对应策略实例。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountersignStrategyFactory {

  /** Spring 容器注入的所有会签策略 Bean 列表，启动时遍历注册到 {@link #registry} */
  private final List<CountersignStrategy> strategies;

  /** 策略注册表：performType -> strategy */
  private final Map<FlowPerformType, CountersignStrategy> registry =
      new EnumMap<>(FlowPerformType.class);

  /**
   * 启动时把容器内所有 {@link CountersignStrategy} 按 {@code supportedType} 注册到查找表。
   *
   * <p>采用「启动期一次性建表」而非每次查找时遍历，使 {@link #getStrategy(FlowPerformType)} 退化为 O(1) 的 {@link EnumMap}
   * 取值—— 该方法在每个会签节点的每次任务流转时都会被调用，属热点路径。
   *
   * <p><b>冲突处理：</b>同一 {@link FlowPerformType} 被多个策略声明时<b>后注册者覆盖先注册者</b>， 仅打印 warn
   * 而不启动失败。这样做是为了允许业务方通过自定义 Bean 覆写内置策略； 代价是重复注册属于配置错误时不会被及时暴露，需人工关注启动日志。
   *
   * <p>建表后会校验枚举完整性，缺失的类型只告警不报错，运行时由 {@link #getStrategy(FlowPerformType)} 回退到 {@code OR}（或签）策略。
   *
   * <p><b>线程安全：</b>{@link #registry} 仅在本方法内写入，之后全程只读， 因此无需加锁即可被多线程并发查找。
   */
  @PostConstruct
  public void init() {
    for (CountersignStrategy strategy : strategies) {
      FlowPerformType type = strategy.supportedType();
      CountersignStrategy prev = registry.put(type, strategy);
      if (prev != null) {
        log.warn(
            "[Flow] 会签策略重复注册: type={} new={} old={}",
            type,
            strategy.getClass().getSimpleName(),
            prev.getClass().getSimpleName());
      } else {
        log.info("[Flow] 会签策略已注册: type={} impl={}", type, strategy.getClass().getSimpleName());
      }
    }
    // 检查必备策略是否齐全
    for (FlowPerformType type : FlowPerformType.values()) {
      if (!registry.containsKey(type)) {
        log.warn("[Flow] 会签策略缺失: type={}（将回退到 OR）", type);
      }
    }
  }

  /**
   * 按 performType 获取策略；未注册时回退到 OR。
   *
   * @param performType 会签类型枚举
   * @return 对应的策略实例；未注册时回退到 OR 策略
   */
  public CountersignStrategy getStrategy(FlowPerformType performType) {
    if (performType == null) {
      return registry.get(FlowPerformType.OR);
    }
    CountersignStrategy strategy = registry.get(performType);
    if (strategy == null) {
      log.warn("[Flow] 会签策略未注册: type={}，回退到 OR", performType);
      return registry.get(FlowPerformType.OR);
    }
    return strategy;
  }
}
