package com.njydsz.common.safe.ratelimit.aop;

import java.lang.reflect.Method;
import java.math.BigDecimal;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.decorator.RateLimitResponseDecorator;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.net.ClientIpResolver;

/**
 * 限流 AOP 切面
 *
 * <p>拦截 {@link RateLimit} 注解，执行限流决策。 限流被拒绝时抛出 {@link BusinessException}。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /**
   * 拦截 {@link RateLimit} 注解执行限流。
   *
   * @param pjp 连接点
   * @return 目标方法执行结果
   * @throws Throwable 目标方法异常透传
   */
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
    return pjp.proceed();
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
  private static HttpServletResponse response(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    try {
      RequestAttributes attrs = RequestContextHolder.currentRequestAttributes();
      Object response =
          attrs.getAttribute(
              "jakarta.servlet.http.HttpServletResponse",
              RequestAttributes.SCOPE_REQUEST);
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
            .threshold(BigDecimal.valueOf(annotation.threshold()))
            .window(Duration.ofMillis(annotation.windowMillis()))
            .burstCapacity(annotation.burstCapacity())
            .queueTimeout(Duration.ofMillis(annotation.queueTimeoutMillis()))
            .warmupPeriod(Duration.ofMillis(annotation.warmupMillis()))
            .errorCode(annotation.errorCode())
            .fallback(annotation.fallback())
            .isEnabled(true)
            .build();
    // 启动时校验规则合法性，提前暴露配置错误
    rule.validate();
    return rule;
  }

  private RateLimitContext buildContext(
      ProceedingJoinPoint pjp, RateLimit annotation, RateLimitRule rule) {
    Object[] args = pjp.getArgs();
    StringBuilder keyBuilder = new StringBuilder(rule.getResource());

    // 优先使用自定义 keyExpression，支持 {paramName} / {index} 占位符
    String keyExpression = annotation.keyExpression();
    if (keyExpression != null && !keyExpression.isEmpty()) {
      String resolved = resolveKeyExpression(keyExpression, pjp);
      if (resolved != null && !resolved.isEmpty()) {
        keyBuilder.append(":").append(resolved);
      }
    } else if (annotation.dimension() == RateLimitDimension.USER
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
    if (args == null) {
      return null;
    }
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      try {
        Method m = arg.getClass().getMethod("getUserId");
        Object val = m.invoke(arg);
        if (val != null) {
          return val.toString();
        }
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      try {
        Method m = arg.getClass().getMethod("getCurrentUserId");
        Object val = m.invoke(arg);
        if (val != null) {
          return val.toString();
        }
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
    }
    return null;
  }

  private String extractIp() {
    try {
      HttpServletRequest request = currentRequest();
      if (request == null) {
        return null;
      }
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

  /**
   * 解析自定义 keyExpression 模板，将 {@code {paramName}} 或 {@code {index}} 占位符替换为实际参数值。
   *
   * <p>解析规则：
   *
   * <ul>
   *   <li>{@code {0}}、{@code {1}} — 数字索引占位符，替换为对应位置的参数值
   *   <li>{@code {paramName}} — 参数名占位符，通过方法参数名解析匹配（需要编译时保留参数名，即 {@code -parameters} 编译选项）
   * </ul>
   *
   * <p>解析失败的占位符保留原始文本（含花括号），确保限流 key 不意外合并导致误拦截。
   *
   * @param expression 模板表达式
   * @param pjp 当前连接点（用于获取参数名和参数值）
   * @return 解析后的 key 字符串
   */
  private String resolveKeyExpression(String expression, ProceedingJoinPoint pjp) {
    if (expression == null || expression.isEmpty()) {
      return "";
    }

    // 快速路径：不包含占位符，直接返回
    if (!expression.contains("{")) {
      return expression;
    }

    MethodSignature signature = (MethodSignature) pjp.getSignature();
    String[] paramNames = signature.getParameterNames();
    Object[] args = pjp.getArgs();

    StringBuilder result = new StringBuilder(expression.length());
    int fromIndex = 0;
    while (fromIndex < expression.length()) {
      int start = expression.indexOf('{', fromIndex);
      if (start == -1) {
        result.append(expression.substring(fromIndex));
        break;
      }
      int end = expression.indexOf('}', start + 1);
      if (end == -1) {
        result.append(expression.substring(fromIndex));
        break;
      }

      // 追加占位符之前的部分
      result.append(expression, fromIndex, start);

      String placeholder = expression.substring(start + 1, end);
      String value = resolvePlaceholder(placeholder, paramNames, args);
      result.append(value != null ? value : expression.substring(start, end + 1));

      fromIndex = end + 1;
    }

    return result.toString();
  }

  /**
   * 解析单个占位符，返回对应参数值；无法解析时返回 null。
   *
   * @param placeholder 占位符内容（不含花括号）
   * @param paramNames 方法参数名数组
   * @param args 方法参数值数组
   * @return 解析后的值；无法解析返回 null
   */
  private String resolvePlaceholder(String placeholder, String[] paramNames, Object[] args) {
    // 优先尝试按参数索引解析
    int index = tryParseInt(placeholder);
    if (index >= 0 && index < args.length && args[index] != null) {
      return args[index].toString();
    }

    // 按参数名解析（需要编译时 -parameters 保留参数名）
    if (paramNames != null && paramNames.length == args.length) {
      for (int i = 0; i < paramNames.length; i++) {
        if (placeholder.equals(paramNames[i]) && args[i] != null) {
          return args[i].toString();
        }
      }
    }

    return null;
  }

  /** 尝试将字符串解析为整数，解析失败返回 -1。 */
  private static int tryParseInt(String s) {
    if (s == null || s.isEmpty()) {
      return -1;
    }
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
