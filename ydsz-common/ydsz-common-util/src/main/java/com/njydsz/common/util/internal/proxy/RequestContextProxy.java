package com.njydsz.common.util.internal.proxy;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ydsz-common-core 模块中 {@code RequestContext} 的反射代理。
 *
 * <p>ydsz-common-util 作为 L1 工具层，禁止反向依赖 L2 的 ydsz-common-core。 本类通过反射桥接对 RequestContext
 * 的访问，保持工具层的纯净度。
 *
 * <p>反射 Method 句柄会被缓存，避免每次调用都进行类加载检查。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>当 ydsz-common-core 不在 classpath 时，所有方法返回安全默认值（null/空字符串/false）
 *   <li>不抛出任何反射相关异常，确保工具类在缺失 core 时的健壮性
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class RequestContextProxy {

  private static final Logger LOG = LoggerFactory.getLogger(RequestContextProxy.class);

  /** RequestContext 类全限定名 */
  private static final String REQUEST_CONTEXT_CLASS =
      "com.njydsz.common.core.context.RequestContext";

  /** 反射 Method 缓存 */
  private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

  /** RequestContext 是否可用的标记（null=未检查，TRUE=可用，FALSE=不可用） */
  private static final AtomicReference<Boolean> AVAILABLE = new AtomicReference<>();

  private RequestContextProxy() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /**
   * 检查 RequestContext 在 classpath 上是否可用。
   *
   * @return true 表示可用，false 表示不可用（util 模块被独立使用时）
   */
  public static boolean isAvailable() {
    Boolean result = available.get();
    if (result != null) {
      return result;
    }
    try {
      Class.forName(REQUEST_CONTEXT_CLASS);
      if (available.compareAndSet(null, Boolean.TRUE)) {
        return true;
      }
    } catch (ClassNotFoundException e) {
      available.compareAndSet(null, Boolean.FALSE);
      LOG.debug("ydsz-common-core 不在 classpath 中，RequestContext 功能将降级为无操作");
    }
    return available.get();
  }

  /**
   * 从上下文中获取指定键的值。
   *
   * <p>对应 {@code RequestContext.get(String key)}。
   *
   * @param key 上下文键名
   * @return 上下文值，不可用时返回 null
   */
  public static Object get(String key) {
    if (!isAvailable()) {
      return null;
    }
    try {
      Method method = getCachedMethod("get", String.class);
      return method.invoke(null, key);
    } catch (Exception e) {
      LOG.debug("调用 RequestContext.get({}) 失败: {}", key, e.getMessage());
      return null;
    }
  }

  /**
   * 获取链路追踪 ID。
   *
   * <p>对应 {@code RequestContext.getTraceId()}。
   *
   * @return 链路追踪 ID，不可用时返回 null
   */
  public static String getTraceId() {
    if (!isAvailable()) {
      return null;
    }
    try {
      Method method = getCachedMethod("getTraceId");
      return (String) method.invoke(null);
    } catch (Exception e) {
      LOG.debug("调用 RequestContext.getTraceId() 失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 设置链路追踪 ID。
   *
   * <p>对应 {@code RequestContext.setTraceId(String traceId)}。
   *
   * @param traceId 要设置的 Trace ID
   */
  public static void setTraceId(String traceId) {
    if (!isAvailable() || traceId == null || traceId.isEmpty()) {
      return;
    }
    try {
      Method method = getCachedMethod("setTraceId", String.class);
      method.invoke(null, traceId);
    } catch (Exception e) {
      LOG.debug("调用 RequestContext.setTraceId({}) 失败: {}", traceId, e.getMessage());
    }
  }

  /**
   * 移除上下文中的指定键。
   *
   * <p>对应 {@code RequestContext.remove(String key)}。
   *
   * @param key 上下文键名
   */
  public static void remove(String key) {
    if (!isAvailable() || key == null) {
      return;
    }
    try {
      Method method = getCachedMethod("remove", String.class);
      method.invoke(null, key);
    } catch (Exception e) {
      LOG.debug("调用 RequestContext.remove({}) 失败: {}", key, e.getMessage());
    }
  }

  /**
   * 获取请求 ID。
   *
   * <p>对应 {@code RequestContext.getRequestId()}。
   *
   * @return 请求 ID，不可用时返回 null
   */
  public static String getRequestId() {
    if (!isAvailable()) {
      return null;
    }
    try {
      Method method = getCachedMethod("getRequestId");
      return (String) method.invoke(null);
    } catch (Exception e) {
      LOG.debug("调用 RequestContext.getRequestId() 失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 获取缓存的反射 Method 句柄。
   *
   * <p>使用双重检查锁确保线程安全，首次调用后缓存结果避免重复反射查找。
   *
   * @param methodName 方法名
   * @param paramTypes 参数类型
   * @return Method 句柄；查找失败返回 null
   */
  private static Method getCachedMethod(String methodName, Class<?>... paramTypes) {
    String cacheKey = methodName + "_" + paramTypes.length;
    Method method = METHOD_CACHE.get(cacheKey);
    if (method != null) {
      return method;
    }
    try {
      Class<?> clazz = Class.forName(REQUEST_CONTEXT_CLASS);
      method = clazz.getMethod(methodName, paramTypes);
      method.setAccessible(true);
      METHOD_CACHE.put(cacheKey, method);
    } catch (NoSuchMethodException | ClassNotFoundException e) {
      LOG.debug("查找 RequestContext.{} 方法失败: {}", methodName, e.getMessage());
      return null;
    }
    return method;
  }

  /**
   * 清理所有缓存的反射 Method 句柄。
   *
   * <p>主要用于测试场景或热重载后的状态重置。
   */
  public static void clearCache() {
    METHOD_CACHE.clear();
    available.set(null);
  }
}
