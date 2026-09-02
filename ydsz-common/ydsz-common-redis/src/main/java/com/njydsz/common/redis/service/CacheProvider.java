package com.njydsz.common.redis.service;

import java.util.List;
import java.util.function.Supplier;

/**
 * 缓存提供者接口
 *
 * <p>为注解缓存切面（{@code @YdszCacheable}、{@code @YdszCacheEvict}、{@code @YdszCachePut}）
 * 提供最小化的缓存操作契约，解耦切面与具体实现的强依赖。
 *
 * <p>设计目标：
 *
 * <ul>
 *   <li>仅包含缓存读写必需的方法，不包含 Hash/List/Set/ZSet/Stream 等专有操作
 *   <li>便于未来替换为多级缓存（Caffeine + Redis）实现，无需修改切面代码
 *   <li>便于单元测试中 Mock，无需加载完整的 RedisTemplate
 * </ul>
 *
 * <p><b>实现类：</b>
 *
 * <ul>
 *   <li>其他自定义实现（可后续扩展）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface CacheProvider {

  /**
   * 获取缓存值
   *
   * @param key 缓存键（不含前缀）
   * @return 值，不存在时返回 null
   */
  Object get(String key);

  /**
   * 获取缓存值（带类型转换）
   *
   * @param key 缓存键（不含前缀）
   * @param clazz 目标类型
   * @param <T> 值类型
   * @return 值，不存在或转换失败时返回 null
   */
  <T> T get(String key, Class<T> clazz);

  /**
   * 写入缓存（不带过期时间）
   *
   * @param key 缓存键（不含前缀）
   * @param value 值
   * @return true-写入成功
   */
  boolean set(String key, Object value);

  /**
   * 写入缓存（带过期时间）
   *
   * @param key 缓存键（不含前缀）
   * @param value 值
   * @param ttl 过期时间（秒）
   * @return true-写入成功
   */
  boolean set(String key, Object value, long ttl);

  /**
   * 删除缓存
   *
   * @param key 缓存键（不含前缀）
   * @return true-删除成功
   */
  boolean delete(String key);

  /**
   * 批量删除缓存
   *
   * @param keys 缓存键集合（不含前缀）
   */
  void delete(List<String> keys);

  /**
   * 执行 Lua 脚本
   *
   * @param script Lua 脚本内容
   * @param keys 键列表（不含前缀）
   * @param returnType 返回值类型
   * @param args 脚本参数
   * @param <T> 返回值类型
   * @return 脚本执行结果
   */
  <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args);

  /**
   * 获取值，若不存在则通过 supplier 获取并缓存（缓存穿透保护）
   *
   * @param key 缓存键（不含前缀）
   * @param expire 回填过期秒数
   * @param supplier 数据提供函数
   * @param clazz 值类型
   * @param <T> 值类型
   * @return 缓存值
   */
  <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz);
}
