package com.njydsz.common.core.context;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求级用户信息缓存持有者（与普通请求上下文分离）。
 *
 * <p>专用于在同一请求内避免重复远程调用（如 Redis / RPC 加载用户权限信息）。 与普通 {@link RequestContext} 的核心差异：
 *
 * <ul>
 *   <li><b>不跨线程传播</b>：子线程各自懒重建本地缓存，避免共享可变 Map 的并发风险， 也避免大对象被 TTL 无谓克隆放大拷贝成本</li>
 *   <li><b>独立生命周期</b>：由 {@link RequestContext#clear()} 统一清理，但不参与快照/恢复/桥接等通用上下文操作</li>
 * </ul>
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * Map<String, Object> cached = RequestCacheHolder.getCachedUserInfoMap();
 * if (cached != null && !cached.isEmpty()) {
 *     return cached;
 * }
 * Map<String, Object> loaded = loadFromRedis(token);
 * RequestCacheHolder.setCachedUserInfoMap(loaded);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RequestContext
 */
public final class RequestCacheHolder {

  /**
   * 请求级缓存存储（懒初始化）。
   *
   * <p>与普通上下文分离，不随 TransmittableThreadLocal 跨线程传播：
   * <ul>
   *   <li>子线程各自懒重建本地缓存，避免共享可变 Map 的并发写入风险</li>
   *   <li>避免大 Map 被 TTL copy() 无谓克隆，减少 GC 压力</li>
   * </ul>
   */
  private static final TransmittableThreadLocal<Map<String, Object>> CACHE_HOLDER =
      new TransmittableThreadLocal<Map<String, Object>>() {
        @Override
        protected Map<String, Object> initialValue() {
          return null; // 懒初始化
        }

        @Override
        public Map<String, Object> copy(Map<String, Object> parentValue) {
          // 不跨线程传播：返回 null，子线程按需重建本地缓存
          return null;
        }
      };

  private RequestCacheHolder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 在当前请求上下文中创建并注册一份用户信息缓存 Map。
   *
   * <p>由 RbacPermissionEvaluator 在首次加载后调用一次， 供同一请求内多次权限校验复用，避免反复 Redis 调用。
   *
   * @return 可变的缓存 Map（初始容量 8，适配典型用户信息字段数）
   * @since 26.09.01
   */
  public static Map<String, Object> createCachedUserInfoMap() {
    Map<String, Object> map = new LinkedHashMap<>(8);
    CACHE_HOLDER.set(map);
    return map;
  }

  /**
   * 设置请求级用户信息缓存 Map。
   *
   * <p>直接替换当前线程的缓存引用（不跨线程传播）。
   *
   * @param cache 缓存 Map（可为 null，等同于 {@link #clear()}）
   * @since 26.09.01
   */
  public static void setCachedUserInfoMap(Map<String, Object> cache) {
    if (cache == null) {
      CACHE_HOLDER.remove();
    } else {
      CACHE_HOLDER.set(cache);
    }
  }

  /**
   * 获取请求级用户信息缓存 Map。
   *
   * <p>该缓存存储于独立的 {@code CACHE_HOLDER}，不随 TTL 跨线程传播。 子线程如需缓存，请各自调用 {@link #createCachedUserInfoMap()} 懒初始化。
   *
   * @return 缓存 Map（可变），未创建返回 null
   * @since 26.09.01
   */
  public static Map<String, Object> getCachedUserInfoMap() {
    return CACHE_HOLDER.get();
  }

  /**
   * 清空当前线程的请求级缓存。
   *
   * <p>由 {@link RequestContext#clear()} 统一调用，一般不直接使用。
   *
   * @since 26.09.01
   */
  public static void clear() {
    CACHE_HOLDER.remove();
  }
}
