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
import com.njydsz.userinfo.server.config.ApiSignatureProperties;
import com.njydsz.userinfo.server.config.InternalCallProperties;
import com.njydsz.userinfo.web.annotation.RequireInternal;
import com.njydsz.userinfo.web.filter.ApiSignatureFilter;

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
 * <p><b>签名优先模式（P0-7）：</b>当 {@link ApiSignatureProperties#isEnabled()} 为 true 时，
 * 签名过滤器优先校验签名，通过则设置请求属性；本切面检测到该属性后直接放行，跳过 IP 标记头校验。
 * 签名未通过或未配置时，回退到原有的 IP 标记头校验逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RequireInternal 内部接口标记注解
 * @see InternalCallProperties 内部调用配置
 * @see ApiSignatureProperties 签名配置
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequireInternalAspect {

  private final InternalCallProperties properties;
  private final ApiSignatureProperties signatureProperties;

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

    // P0-7: 签名校验优先 — 若签名过滤器已通过校验（设置请求属性），直接放行
    if (isSignatureVerified(request)) {
      return;
    }

    // 回退到 IP 标记头校验
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

  /**
   * 判断签名过滤器是否已通过校验。
   *
   * <p>当 {@link ApiSignatureProperties#isEnabled()} 为 true 且签名过滤器成功验证签名后，
   * 会在请求属性中设置 {@code SIGNATURE_VERIFIED_ATTR} 为 {@link Boolean#TRUE}。
   * 本方法检查该属性，若存在且为 true 则视为签名校验已通过。
   *
   * @param request HTTP 请求
   * @return true 表示签名校验已通过（可跳过 IP 标记头校验）
   */
  private boolean isSignatureVerified(HttpServletRequest request) {
    if (!signatureProperties.isEnabled()) {
      return false;
    }
    Object attr = request.getAttribute(ApiSignatureFilter.SIGNATURE_VERIFIED_ATTR);
    return Boolean.TRUE.equals(attr);
  }
}
