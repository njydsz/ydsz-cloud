package com.njydsz.common.safe.filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 安全过滤器链构建器
 *
 * <p>集中编排安全过滤器链的注册顺序与条件，替代各 {@link FilterRegistrationBean} 分散注册的方式。 每个安全能力通过 {@link
 * FilterRegistrationDescriptor} 声明自身位置与启用条件， 由本构建器统一排序后输出为 Spring {@link FilterRegistrationBean}。
 *
 * <p><b>设计目标：</b>
 *
 * <ul>
 *   <li>单一入口管理过滤器顺序，避免散落在 {@code @Bean} 方法中难以全局调整
 *   <li>支持声明式条件注册（仅当某条件满足时才启用过滤器）
 *   <li>内置顺序冲突检测：相同 order 值时给出警告
 * </ul>
 *
 * <p><b>当前演进说明：</b> 本构建器为过滤器链统一编排提供基础抽象。当前实现以收集描述符 + 排序校验为主， 后续可演进为完全替代分散 {@link
 * FilterRegistrationBean} 的集中式注册。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SafeFilterChainBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(SafeFilterChainBuilder.class);

  private final List<FilterRegistrationDescriptor<?>> descriptors = new ArrayList<>(4);

  /**
   * 注册一个过滤器描述符
   *
   * @param descriptor 过滤器注册描述符
   * @return 当前构建器（链式调用）
   */
  public SafeFilterChainBuilder register(FilterRegistrationDescriptor<?> descriptor) {
    if (descriptor != null) {
      descriptors.add(descriptor);
    }
    return this;
  }

  /**
   * 构建并返回按 order 排序的过滤器描述符列表
   *
   * <p>排序后检测同 order 冲突并记录警告，便于排查顺序问题。
   *
   * @return 排序后的过滤器描述符列表
   */
  public List<FilterRegistrationDescriptor<?>> build() {
    List<FilterRegistrationDescriptor<?>> sorted = new ArrayList<>(descriptors);
    sorted.sort(Comparator.comparingInt(FilterRegistrationDescriptor::order));
    detectOrderConflict(sorted);
    return sorted;
  }

  /**
   * 获取当前已注册的过滤器数量
   *
   * @return 过滤器数量
   */
  public int size() {
    return descriptors.size();
  }

  /** 检测同 order 冲突并记录警告日志 */
  private void detectOrderConflict(List<FilterRegistrationDescriptor<?>> sorted) {
    for (int i = 1; i < sorted.size(); i++) {
      FilterRegistrationDescriptor<?> prev = sorted.get(i - 1);
      FilterRegistrationDescriptor<?> current = sorted.get(i);
      if (prev.order() == current.order()) {
        LOG.warn(
            "[SafeFilterChain] order conflict between '{}' and '{}' (both order={})",
            prev.name(), current.name(), current.order());
      }
    }
  }

  /**
   * 过滤器注册描述符（不可变记录）
   *
   * <p>声明一个安全过滤器的注册元数据，包括名称、顺序、URL 模式和启用条件。
   *
   * @param name 过滤器名称（唯一标识）
   * @param order 顺序（数值越小优先级越高）
   * @param urlPatterns URL 模式列表
   * @param enabled 启用条件供应器（返回 true 时注册）
   * @param supplier FilterRegistrationBean 供应器（仅在 enabled=true 时调用）
   * @param <T> 过滤器类型
   */
  public record FilterRegistrationDescriptor<T extends jakarta.servlet.Filter>(
      String name,
      int order,
      List<String> urlPatterns,
      Supplier<Boolean> enabled,
      Supplier<FilterRegistrationBean<T>> supplier) {

    /**
     * 创建描述符的便捷构造器。
     *
     * @param name 过滤器名称（唯一标识）
     * @param order 顺序（数值越小优先级越高）
     * @param urlPatterns URL 模式列表
     * @param enabled 启用条件供应器
     * @param supplier FilterRegistrationBean 供应器
     * @param <T> 过滤器类型
     * @return 新的描述符实例
     */
    public static <T extends jakarta.servlet.Filter> FilterRegistrationDescriptor<T> of(
        String name,
        int order,
        List<String> urlPatterns,
        Supplier<Boolean> enabled,
        Supplier<FilterRegistrationBean<T>> supplier) {
      return new FilterRegistrationDescriptor<>(name, order, urlPatterns, enabled, supplier);
    }
  }
}
