package com.njydsz.userinfo.web.aspect;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.InternalCallProperties;
import com.njydsz.userinfo.web.annotation.RequireInternal;

/**
 * 内部接口调用校验切面（P0-6）。
 *
 * <p>拦截标注了 {@link RequireInternal} 注解的 Controller 类/方法，在方法执行前校验请求头
 * {@code X-Internal-Call: true}。作为网关白名单之外的服务端二次校验：
 * 即使网关路由配置错误，缺少内部调用标记的外部请求也会在服务端被拒绝。
 *
 * <p><b>开关控制：</b>通过 {@link InternalCallProperties#isEnabled()} 控制是否生效。
 * 默认关闭（渐进式启用），开启后所有内部端点强制要求标记头。
 *
 * @author ydsz-team
 * @since 2.21.0
 * @see RequireInternal 内部接口标记注解
 * @see InternalCallProperties 内部调用配置
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequireInternalAspect {

  private final InternalCallProperties properties;

  /** 切点：类级或方法级标注了 @RequireInternal 的方法 */
  @Pointcut("@annotation(com.njydsz.userinfo.web.annotation.RequireInternal) "
      + "|| @within(com.njydsz.userinfo.web.annotation.RequireInternal)")
  public void internalPointcut() {}

  /**
   * 前置通知：校验内部调用标记头。
   *
   * @param joinPoint 切点
   * @throws BusinessException 开关开启但请求缺少标记头时抛出
   */
  @Before("internalPointcut()")
  public void checkInternalCall(JoinPoint joinPoint) {
    // 开关未开启时跳过（兼容存量 Feign 客户端，渐进式启用）
    if (!properties.isEnabled()) {
      return;
    }
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      throw new BusinessException(UserInfoExceptionCode.INTERNAL_ACCESS_FORBIDDEN);
    }
    HttpServletRequest request = attributes.getRequest();
    String flag = request.getHeader(properties.getHeaderName());
    if (!"true".equalsIgnoreCase(flag)) {
      log.warn(
          "Internal API access denied (missing {} header): method={}, uri={}",
          properties.getHeaderName(),
          joinPoint.getSignature().toShortString(),
          request.getRequestURI());
      throw new BusinessException(UserInfoExceptionCode.INTERNAL_ACCESS_FORBIDDEN);
    }
  }
}
