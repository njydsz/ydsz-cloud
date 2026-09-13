package com.njydsz.common.feign.fallback;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;

import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.exception.OpenFeignException;

/**
 * 通用 Feign 熔断降级工厂。
 *
 * <p>当 Resilience4j 熔断器打开或下游服务不可用时，本工厂创建的降级代理会将所有方法调用
 * 统一转换为抛出业务异常 {@link OpenFeignException}，避免返回 null/空对象导致调用方误判成功。
 *
 * <p>使用方式：
 *
 * <pre>
 * &#64;FeignClient(name = "ydsz-system", fallbackFactory = FeignCircuitBreakerFallbackFactory.class)
 * public interface SystemClient { ... }
 * </pre>
 *
 * <p>所有方法的降级行为一致：记录 WARN 日志 + 抛出 {@link OpenFeignException}（错误码 {@link
 * FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE}）。
 *
 * <p>对于需要差异化降级逻辑的服务，仍可使用自定义 FallbackFactory（如 {@link
 * com.njydsz.message.api.fallback.NotificationClientFallbackFactory}）。
 *
 * @param <T> Feign 客户端接口类型
 * @author ydsz-team
 * @since 26.09.01
 */
public class FeignCircuitBreakerFallbackFactory<T> implements FallbackFactory<T> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FeignCircuitBreakerFallbackFactory.class);

  private final Class<T> targetType;
  private final Throwable cause;

  /**
   * 构造熔断降级工厂。
   *
   * @param targetType Feign 客户端接口类型（用于创建动态代理）
   * @param cause 触发降级的原始异常
   */
  public FeignCircuitBreakerFallbackFactory(Class<T> targetType, Throwable cause) {
    this.targetType = targetType;
    this.cause = cause;
  }

  /**
   * 创建降级代理实例。
   *
   * <p>通过 JDK 动态代理生成接口实现，所有方法调用均抛出业务异常并记录 WARN 日志。
   *
   * @param cause 触发降级的原始异常（覆盖构造时传入的 cause，传递最新异常信息）
   * @return 降级代理实例
   */
  @Override
  @SuppressWarnings("unchecked")
  public T create(Throwable cause) {
    Throwable effectiveCause = cause != null ? cause : this.cause;
    LOGGER.warn(
        "[FeignCircuitBreaker] 触发熔断/降级: interface={}, cause={}",
        targetType.getName(),
        effectiveCause == null ? "unknown" : effectiveCause.getMessage());
    Class<?>[] interfaces = new Class<?>[] {targetType};
    return (T)
        Proxy.newProxyInstance(
            targetType.getClassLoader(),
            interfaces,
            new CircuitBreakerFallbackInvocationHandler(effectiveCause));
  }

  /** 熔断降级调用处理器：统一抛出业务异常。 */
  private static final class CircuitBreakerFallbackInvocationHandler implements InvocationHandler {

    private final Throwable cause;

    CircuitBreakerFallbackInvocationHandler(Throwable cause) {
      this.cause = cause;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      // Object 原生方法（toString/equals/hashCode）正常委托，避免调试信息被异常覆盖
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "toString" -> "CircuitBreakerFallbackProxy@" + Integer.toHexString(hashCode());
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == Arrays.stream(args).findFirst().orElse(null);
          default -> method.invoke(proxy, args);
        };
      }
      String message =
          String.format(
              "Feign 服务不可用: method=%s.%s, reason=%s",
              method.getDeclaringClass().getSimpleName(),
              method.getName(),
              cause == null ? "CircuitBreakerOpen" : cause.getMessage());
      throw new OpenFeignException(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, message, cause);
    }
  }
}
