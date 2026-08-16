package com.njydsz.common.safe.idempotent;

import com.njydsz.common.core.response.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 幂等性拦截器。
 *
 * <p>拦截标注了 {@link Idempotent} 注解的方法，确保同一业务键只执行一次。
 *
 * <p>工作流程：
 *
 * <ol>
 *   <li>检查 Handler 是否标注了 @Idempotent 注解
 *   <li>解析 SpEL 表达式获取幂等键
 *   <li>尝试从 Store 获取锁
 *   <li>成功 → 放行请求
 *   <li>失败 → 返回 429 Too Many Requests 或自定义错误码
 * </ol>
 *
 * <p><b>SpEL 支持说明：</b>
 *
 * <ul>
 *   <li>支持 {@code "#paramName"} 形式引用方法参数
 *   <li>支持 {@code "#paramName.field"} 形式引用参数的字段
 *   <li>空字符串 {@code "''"} 使用方法签名作为键
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IdempotentInterceptor implements HandlerInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotentInterceptor.class);

  /**
   * HTTP 429 Too Many Requests
   *
   * <p>Jakarta Servlet API 未提供 {@code SC_TOO_MANY_REQUESTS} 常量，直接使用标准状态码值。
   */
  private static final int HTTP_TOO_MANY_REQUESTS = 429;

  private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
  private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new DefaultParameterNameDiscoverer();

  private final IdempotentStore idempotentStore;
  private final ConcurrentHashMap<AnnotatedElementKey, Expression> expressionCache =
      new ConcurrentHashMap<>();

  public IdempotentInterceptor(IdempotentStore idempotentStore) {
    this.idempotentStore = idempotentStore;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    Idempotent idempotent = findIdempotentAnnotation(handlerMethod);
    if (idempotent == null) {
      return true;
    }

    String generatedKey = key(request, handlerMethod, idempotent);
    Duration expire = Duration.ofMillis(idempotent.timeUnit().toMillis(idempotent.expire()));

    if (idempotentStore.tryAcquire(generatedKey, expire)) {
      return true;
    }

    // 幂等键已存在，拒绝重复请求
    LOG.debug("幂等性校验拒绝重复请求 | key={} | uri={}", generatedKey, request.getRequestURI());
    rejectRequest(response, idempotent.message());
    return false;
  }

  /**
   * 查找方法上的 @Idempotent 注解（优先方法级，其次类级）。
   *
   * @param handlerMethod 处理方法
   * @return 幂等注解，未找到返回 null
   */
  private Idempotent findIdempotentAnnotation(HandlerMethod handlerMethod) {
    Method method = handlerMethod.getMethod();
    Idempotent idempotent = method.getAnnotation(Idempotent.class);
    if (idempotent != null) {
      return idempotent;
    }
    // 检查类级别注解
    return handlerMethod.getBeanType().getAnnotation(Idempotent.class);
  }

  /**
   * 构建幂等键。
   *
   * @param request HTTP 请求
   * @param handlerMethod 处理方法
   * @param idempotent 幂等注解
   * @return 生成的幂等键字符串
   */
  private String key(
      HttpServletRequest request, HandlerMethod handlerMethod, Idempotent idempotent) {
    String spelKey = idempotent.key();
    String resolvedKey;

    if (spelKey != null && !spelKey.isBlank()) {
      // 使用 SpEL 表达式解析
      try {
        resolvedKey = parseSpel(spelKey, handlerMethod, request);
      } catch (Exception e) {
        LOG.warn("SpEL 解析失败，使用方法签名作为幂等键 | spel={}", spelKey, e);
        resolvedKey = defaultKey(handlerMethod);
      }
    } else {
      resolvedKey = defaultKey(handlerMethod);
    }

    return "idem:" + resolvedKey;
  }

  /**
   * 默认幂等键：方法签名。
   *
   * @param handlerMethod 处理方法
   * @return 方法签名形式的幂等键
   */
  private String defaultKey(HandlerMethod handlerMethod) {
    return handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
  }

  /**
   * 解析 SpEL 表达式，从方法参数中提取值。
   *
   * <p>使用 Spring {@link MethodBasedEvaluationContext} 实现标准 SpEL 解析， 支持 {@code "#paramName"} 和
   * {@code "#paramName.field"} 形式。
   *
   * @param spel SpEL 表达式字符串
   * @param handlerMethod 处理方法
   * @param request HTTP 请求（用于获取参数值）
   * @return 解析后的键值
   */
  private String parseSpel(String spel, HandlerMethod handlerMethod, HttpServletRequest request) {
    if (!spel.startsWith("#")) {
      return spel;
    }

    Method method = handlerMethod.getMethod();
    Object[] args = resolveMethodArgs(request, handlerMethod);

    MethodBasedEvaluationContext evaluationContext =
        new MethodBasedEvaluationContext(null, method, args, PARAMETER_NAME_DISCOVERER);

    AnnotatedElementKey methodKey = new AnnotatedElementKey(method, handlerMethod.getBeanType());
    Expression expression =
        expressionCache.computeIfAbsent(methodKey, k -> EXPRESSION_PARSER.parseExpression(spel));

    Object value = expression.getValue(evaluationContext);
    return value != null ? value.toString() : spel;
  }

  /**
   * 解析方法参数值。
   *
   * <p>从请求中提取方法参数值，优先从请求属性获取，其次从请求参数获取。
   *
   * @param request HTTP 请求
   * @param handlerMethod 处理方法
   * @return 方法参数值数组
   */
  private Object[] resolveMethodArgs(HttpServletRequest request, HandlerMethod handlerMethod) {
    Method method = handlerMethod.getMethod();
    int paramCount = method.getParameterCount();
    Object[] args = new Object[paramCount];

    String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
    if (paramNames == null) {
      return args;
    }

    for (int i = 0; i < paramCount; i++) {
      String paramName = paramNames[i];
      // 优先从请求属性获取（@RequestBody 解析后的对象）
      Object value = request.getAttribute(paramName);
      if (value == null) {
        // 其次从请求参数获取
        value = request.getParameter(paramName);
      }
      args[i] = value;
    }

    return args;
  }

  /**
   * 拒绝重复请求。
   *
   * @param response HTTP 响应
   * @param message 错误提示信息
   * @throws IOException 如果写入响应失败
   */
  private void rejectRequest(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HTTP_TOO_MANY_REQUESTS);
    response.setContentType("application/json;charset=UTF-8");
    BaseResponse<?> body = BaseResponse.error("IDEMPOTENT_REJECT", message);
    response.getWriter().write(body.toString());
  }
}
