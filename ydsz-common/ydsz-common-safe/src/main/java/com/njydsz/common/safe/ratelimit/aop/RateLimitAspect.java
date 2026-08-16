package com.njydsz.common.safe.ratelimit.aop;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.decorator.RateLimitResponseDecorator;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.util.http.RequestContextUtils;

/**
 * 限流 AOP 切面
 *
 * <p>拦截 {@link RateLimit} 注解，执行限流决策。 限流被拒绝时抛出 {@link BusinessException}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

  private final RateLimitManager rateLimitManager;

  /** 限流响应装饰器（用于添加 Retry-After/X-RateLimit-* 标准化头部） */
  private final RateLimitResponseDecorator responseDecorator = new RateLimitResponseDecorator();

  /** 方法签名缓存：避免重复解析 */
  private final ConcurrentHashMap<Method, RateLimitRule> ruleCache = new ConcurrentHashMap<>();

  /** 拦截 {@link RateLimit} 注解 */
  @Around("@annotation(com.njydsz.common.safe.ratelimit.annotation.RateLimit)")
  public Object aroundSentinel(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    RateLimit annotation = method.getAnnotation(RateLimit.class);
    if (annotation == null) {
      return pjp.proceed();
    }

    RateLimitRule rule = ruleCache.computeIfAbsent(method, m -> buildRule(annotation));
    RateLimitContext context = buildContext(pjp, annotation, rule);
    return executeWithLimit(pjp, context, rule, annotation.errorCode(), annotation.message());
  }

  /** 执行限流决策 */
  private Object executeWithLimit(
      ProceedingJoinPoint pjp,
      RateLimitContext context,
      RateLimitRule rule,
      String errorCode,
      String message)
      throws Throwable {
    RateLimitDecision decision = rateLimitManager.decide(context);
    if (decision.isBlocked()) {
      log.warn(
          "Rate limit blocked: resource={}, key={}, reason={}",
          decision.getResource(),
          context.getResource(),
          decision.getReason());
      // 添加标准化限流响应头（Retry-After / X-RateLimit-*）
      applyRateLimitHeaders(currentRequest(), decision);
      String code = (errorCode == null || errorCode.isEmpty()) ? "D02001" : errorCode;
      throw BusinessException.builder().code(code).key(message).build();
    }
    try {
      return pjp.proceed();
    } finally {
      // 并发数限流需要在 finally 中释放许可
      if (rule.getAlgorithm() == RateLimitAlgorithm.CONCURRENCY) {
        rateLimitManager
            .getRuleCache()
            .getLimiter(rule.getResource())
            .ifPresent(limiter -> limiter.release(context));
      }
    }
  }

  /**
   * 为限流拒绝响应添加标准化头部
   *
   * <p>从当前 HTTP 请求/响应中获取对象，调用 {@link RateLimitResponseDecorator} 设置头部。 头部设置失败不影响主流程（限流拒绝仍正常抛出异常）。
   *
   * @param request 当前 HTTP 请求（可为 null）
   * @param decision 限流决策
   */
  private void applyRateLimitHeaders(HttpServletRequest request, RateLimitDecision decision) {
    if (request == null) {
      return;
    }
    try {
      HttpServletResponse response = response(request);
      if (response != null && !response.isCommitted()) {
        responseDecorator.decorateBlockedResponse(request, response, decision);
      }
    } catch (Exception e) {
      log.debug("设置限流响应头失败: {}", e.getMessage());
    }
  }

  /**
   * 获取当前 HTTP 响应
   *
   * <p>通过 {@link org.springframework.web.context.request.RequestContextHolder} 获取 当前请求的
   * HttpServletResponse。
   *
   * @param request 当前 HTTP 请求（用于判空）
   * @return HttpServletResponse；不可用时返回 null
   */
  @SuppressWarnings("unchecked")
  private static HttpServletResponse response(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    try {
      org.springframework.web.context.request.RequestAttributes attrs =
          org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
      Object response =
          attrs.getAttribute(
              "jakarta.servlet.http.HttpServletResponse",
              org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
      return response instanceof HttpServletResponse httpResponse ? httpResponse : null;
    } catch (Exception e) {
      return null;
    }
  }

  private RateLimitRule buildRule(RateLimit annotation) {
    RateLimitRule rule =
        RateLimitRule.builder()
            .resource(annotation.resource())
            .algorithm(annotation.algorithm())
            .dimension(annotation.dimension())
            .mode(annotation.mode())
            .threshold(annotation.threshold())
            .window(Duration.ofMillis(annotation.windowMillis()))
            .burstCapacity(annotation.burstCapacity())
            .queueTimeout(Duration.ofMillis(annotation.queueTimeoutMillis()))
            .warmupPeriod(Duration.ofMillis(annotation.warmupMillis()))
            .errorCode(annotation.errorCode())
            .fallback(annotation.fallback())
            .enabled(true)
            .build();
    // 启动时校验规则合法性，提前暴露配置错误
    rule.validate();
    return rule;
  }

  private RateLimitContext buildContext(
      ProceedingJoinPoint pjp, RateLimit annotation, RateLimitRule rule) {
    Object[] args = pjp.getArgs();
    StringBuilder keyBuilder = new StringBuilder(rule.getResource());

    if (annotation.dimension() == RateLimitDimension.USER
        || annotation.dimension() == RateLimitDimension.HOT_USER) {
      // 从上下文中取 userId
      String userId = extractUserId(args);
      if (userId != null) {
        keyBuilder.append(":user:").append(userId);
      }
    } else if (annotation.dimension() == RateLimitDimension.IP) {
      String ip = extractIp();
      if (ip != null) {
        keyBuilder.append(":ip:").append(ip);
      }
    } else if (annotation.dimension() == RateLimitDimension.HOT_PARAM
        || annotation.dimension() == RateLimitDimension.HOT_GOODS) {
      int idx = annotation.keyParam();
      if (idx >= 0 && idx < args.length && args[idx] != null) {
        keyBuilder.append(":hot:").append(args[idx]);
      }
    }

    if (annotation.keyParam() >= 0
        && annotation.keyParam() < args.length
        && args[annotation.keyParam()] != null) {
      keyBuilder.append(":").append(args[annotation.keyParam()]);
    }
    if (annotation.keyParam2() >= 0
        && annotation.keyParam2() < args.length
        && args[annotation.keyParam2()] != null) {
      keyBuilder.append(":").append(args[annotation.keyParam2()]);
    }

    return RateLimitContext.builder()
        .resource(keyBuilder.toString())
        .args(args)
        .methodSignature(pjp.getSignature().toLongString())
        .build();
  }

  private String extractUserId(Object[] args) {
    if (args == null) return null;
    for (Object arg : args) {
      if (arg == null) continue;
      try {
        Method m = arg.getClass().getMethod("getUserId");
        Object val = m.invoke(arg);
        if (val != null) return val.toString();
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      try {
        Method m = arg.getClass().getMethod("getCurrentUserId");
        Object val = m.invoke(arg);
        if (val != null) return val.toString();
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
    }
    return null;
  }

  private String extractIp() {
    try {
      HttpServletRequest request = currentRequest();
      if (request == null) return null;
      return ClientIpResolver.getClientIp(request);
    } catch (Exception ex) {
      return null;
    }
  }

  /** 获取当前 HTTP 请求（优先 RequestContextUtils，兜底 RequestContext） */
  private static HttpServletRequest currentRequest() {
    try {
      HttpServletRequest request = RequestContextUtils.getRequest();
      if (request == null) {
        request = (HttpServletRequest) RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
      }
      return request;
    } catch (Exception ignored) {
      log.debug("Caught exception (ignored): {}", ignored.getMessage());
    }
    return null;
  }
}
