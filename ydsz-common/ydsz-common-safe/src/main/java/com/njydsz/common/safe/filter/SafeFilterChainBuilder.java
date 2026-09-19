package com.njydsz.common.safe.filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 安全过滤器链构建器。
 *
 * <p>集中编排安全过滤器链的注册顺序与条件。每个安全能力通过 {@link FilterRegistrationDescriptor}
 * 声明自身位置与启用条件，由本构建器统一排序后输出为 Spring {@link FilterRegistrationBean}，
 * 替代在各 {@code @Configuration} 类中分散声明 {@code FilterRegistrationBean} @Bean 方法的方式。
 *
 * <p><b>标准过滤器链顺序（order 从小到大）：</b>
 *
 * <ol>
 *   <li>{@code SecurityHeaderFilter}（order=1）：安全响应头注入，最先执行确保所有响应携带安全头
 *   <li>{@code IpAccessFilter}（order=10）：黑名单 IP 拦截，尽早拒绝恶意来源
 *   <li>{@code CsrfFilter}（order=20）：CSRF Token 校验
 *   <li>{@code XssFilter}（order=30）：XSS 清洗
 *   <li>{@code RateLimitFilter}（order=40）：限流决策
 *   <li>{@code ApiSignatureFilter}（order=50）：API 签名校验（最外层业务安全）
 *   <li>{@code SafeRequestBodyCacheFilter}（order=60）：请求体缓存（最后缓存原始 body 供后续使用）
 * </ol>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * &#64;Bean
 * public List<FilterRegistrationBean<?>> safeFilterChain(SafeFilterChainBuilder builder) {
 *   return builder
 *     .register(FilterRegistrationDescriptor.of(
 *         "securityHeader", 1, List.of("/*"),
 *         () -> securityHeaderProps.isEnabled(),
 *         () -> new FilterRegistrationBean<>(new SecurityHeaderFilter(props))))
 *     .register(FilterRegistrationDescriptor.of(
 *         "ipAccess", 10, List.of("/*"),
 *         () -> ipAccessProps.isEnabled(),
 *         () -> new FilterRegistrationBean<>(new IpAccessFilter(service))))
 *     // ... 更多过滤器
 *     .toRegistrationBeans();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SafeFilterChainBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(SafeFilterChainBuilder.class);

  private final List<FilterRegistrationDescriptor<?>> descriptors = new ArrayList<>(4);

  /**
   * 注册一个过滤器描述符。
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
   * 构建并返回按 order 排序的 {@link FilterRegistrationBean} 列表。
   *
   * <p>排序后检测同 order 冲突并记录警告，便于排查顺序问题。仅 {@code enabled=true} 的描述符才会生成
   * {@link FilterRegistrationBean}。
   *
   * @return 排序后的过滤器注册 Bean 列表
   */
  public List<FilterRegistrationBean<?>> toRegistrationBeans() {
    List<FilterRegistrationDescriptor<?>> sorted = build();
    List<FilterRegistrationBean<?>> beans = new ArrayList<>(sorted.size());
    for (FilterRegistrationDescriptor<?> descriptor : sorted) {
      try {
        if (Boolean.TRUE.equals(descriptor.enabled().get())) {
          beans.add(descriptor.supplier().get());
        }
      } catch (Exception e) {
        LOG.warn(
            "[SafeFilterChain] failed to check/construct filter '{}': {}",
            descriptor.name(), e.getMessage());
      }
    }
    return beans;
  }

  /**
   * 构建并返回按 order 排序的过滤器描述符列表。
   *
   * <p>排序后检测同 order 冲突并记录警告，便于排查顺序问题。
   *
   * @return 排序后的过滤器描述符列表（包含已禁用描述符）
   */
  public List<FilterRegistrationDescriptor<?>> build() {
    List<FilterRegistrationDescriptor<?>> sorted = new ArrayList<>(descriptors);
    sorted.sort(Comparator.comparingInt(FilterRegistrationDescriptor::order));
    detectOrderConflict(sorted);
    return sorted;
  }

  /**
   * 获取当前已注册的过滤器数量。
   *
   * @return 过滤器数量
   */
  public int size() {
    return descriptors.size();
  }

  /** 检测同 order 冲突并记录警告日志。 */
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
   * 过滤器注册描述符（不可变记录）。
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
