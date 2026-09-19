package com.njydsz.common.cache.support;

/**
 * 缓存预热器 SPI — 在应用启动时主动加载热点数据到缓存。
 *
 * <p>对标 Caffeine 的 refreshAfterWrite 预热语义，提供显式的「预热」能力：
 *
 * <ul>
 *   <li>应用启动时预热配置、字典、热点规则等无需懒加载的数据
 *   <li>配合 Spring {@code @PostConstruct} 或 {@code ApplicationRunner} 使用
 *   <li>预热过程异步执行，不阻塞应用启动
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Component
 * public class DictCacheWarmer implements Warmer {
 *   @Override
 *   public void warm(Cache<String, Object> cache) {
 *     // 加载所有字典类型到缓存
 *     dictTypes.forEach(dt -> cache.put(dt.getCode(), dt));
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@FunctionalInterface
public interface Warmer {

  /**
   * 执行缓存预热。
   *
   * <p>实现此方法将热点数据加载到缓存中。已存在的 key 可以选择覆盖或跳过（由实现决定）。 预热不应抛出异常——异常应被内部捕获并记录，避免阻塞应用启动流程。
   *
   * @param cache 目标缓存实例（由调用方传入，非空）
   */
  void warm(com.njydsz.common.cache.api.Cache<String, Object> cache);
}
