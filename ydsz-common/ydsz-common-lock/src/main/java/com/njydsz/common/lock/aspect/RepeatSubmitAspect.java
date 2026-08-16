package com.njydsz.common.lock.aspect;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.annotation.RepeatSubmit;
import com.njydsz.common.lock.idempotent.RepeatSubmitTokenService;
import com.njydsz.common.lock.spi.CurrentUserIdResolver;
import com.njydsz.common.util.http.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StringUtils;

/**
 * 表单重复提交防护 AOP 切面
 *
 * <p>拦截标注 {@link RepeatSubmit} 注解的 Controller 方法，基于 Token 令牌模式 防止表单重复提交。
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>前端先调用 {@code GET /repeat-submit/token} 获取一次性 Token
 *   <li>前端提交表单时在请求头携带 {@code X-Repeat-Token}
 *   <li>切面从请求头提取 Token，调用 {@link RepeatSubmitTokenService#validateAndConsume(String, String)} 校验
 *   <li>校验通过则执行业务方法，失败则抛出 {@link BusinessException}
 * </ol>
 *
 * <p><b>与 {@link IdempotentAspect} 的区别：</b>
 *
 * <ul>
 *   <li>{@link IdempotentAspect}：基于请求参数摘要的服务端去重，适用于接口幂等性
 *   <li>本切面：基于前端 Token 的防重复提交，适用于表单提交场景
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RepeatSubmit
 * @see RepeatSubmitTokenService
 */
@Slf4j
@Aspect
public class RepeatSubmitAspect {

  private final RepeatSubmitTokenService tokenService;
  private final CurrentUserIdResolver userIdResolver;

  public RepeatSubmitAspect(
      RepeatSubmitTokenService tokenService, CurrentUserIdResolver userIdResolver) {
    this.tokenService = tokenService;
    this.userIdResolver = userIdResolver;
  }

  /**
   * 拦截 {@link RepeatSubmit} 注解方法，执行防重复提交校验
   *
   * @param joinPoint AOP 连接点
   * @param repeatSubmit 防重复提交注解
   * @return 目标方法返回值
   */
  @Around("@annotation(repeatSubmit)")
  public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) {
    HttpServletRequest request = getCurrentRequest();
    if (request == null) {
      log.warn("[ydsz-lock] [repeat-submit] 非 Web 环境，跳过多提交校验");
      return proceed(joinPoint);
    }

    String headerName = repeatSubmit.headerName();
    String token = request.getHeader(headerName);

    if (!StringUtils.hasText(token)) {
      log.warn("[ydsz-lock] [repeat-submit] 缺少防重复提交 Token | header={}", headerName);
      throw BusinessException.builder()
          .code(CoreExceptionCode.FAIL.getCode())
          .message("缺少防重复提交 Token，请先获取 Token")
          .build();
    }

    // 间隔窗口校验（用户维度 + 方法维度）：窗口内重复提交直接拒绝，不消费 Token
    String userId = userIdResolver.getCurrentUserId();
    String businessKey =
        joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName();
    if (!tokenService.acquireInterval(userId, businessKey, repeatSubmit.interval())) {
      log.warn(
          "[ydsz-lock] [repeat-submit] 间隔窗口内重复提交 | userId={}, businessKey={} | interval={}ms",
          userId,
          businessKey,
          repeatSubmit.interval());
      throw BusinessException.builder()
          .code(CoreExceptionCode.FAIL.getCode())
          .message(repeatSubmit.message())
          .build();
    }

    boolean valid = tokenService.validateAndConsume(userId, token);
    if (!valid) {
      log.warn("[ydsz-lock] [repeat-submit] Token 无效或已过期 | header={}, token={}", headerName, token);
      throw BusinessException.builder()
          .code(CoreExceptionCode.FAIL.getCode())
          .message(repeatSubmit.message())
          .build();
    }

    try {
      return joinPoint.proceed();
    } catch (RuntimeException | Error e) {
      log.debug("[ydsz-lock] [repeat-submit] 业务方法执行异常 | cause={}", e.getMessage());
      throw e;
    } catch (Throwable ex) {
      log.debug("[ydsz-lock] [repeat-submit] 检查型异常包装后抛出 | cause={}", ex.getMessage());
      throw wrapCheckedException(ex);
    }
  }

  /**
   * 执行目标方法并传播异常
   *
   * <p>切面不声明 {@code throws Throwable}（遵循编码规范）， 运行时异常与 Error 原样传播，检查型异常包装为业务异常。
   *
   * @param joinPoint 连接点
   * @return 目标方法返回值
   */
  private Object proceed(ProceedingJoinPoint joinPoint) {
    try {
      return joinPoint.proceed();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw wrapCheckedException(t);
    }
  }

  /**
   * 将检查型异常包装为业务异常
   *
   * @param cause 原始异常
   * @return 包装后的业务异常
   */
  private BusinessException wrapCheckedException(Throwable cause) {
    BusinessException wrapped = new BusinessException(CoreExceptionCode.FAIL, cause);
    wrapped.setMessage("接口执行异常: " + cause.getMessage());
    return wrapped;
  }

  /**
   * 获取当前 HTTP 请求
   *
   * <p>优先通过 {@link RequestContextUtils} 获取，兜底从 {@link RequestContext} 读取。
   *
   * @return HttpServletRequest，非 Web 环境返回 null
   */
  private HttpServletRequest getCurrentRequest() {
    HttpServletRequest request = RequestContextUtils.getRequest();
    if (request == null) {
      request = (HttpServletRequest) RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
    }
    return request;
  }
}
