package com.njydsz.common.safe.internal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.config.ApiSignatureProperties;
import com.njydsz.common.safe.filter.ApiSignatureFilter;

/**
 * 内部接口调用校验切面（公共版，P0-2 下沉）。
 *
 * <p>拦截标注了 {@link RequireInternal} 注解的 Controller 类/方法，在方法执行前校验内部调用
 * 凭据。作为网关路由之外的服务端最后一道防线：即使网关路由配置错误，缺少内部调用标记的
 * 外部请求也会在服务端被拒绝。
 *
 * <p><b>校验优先级</b>：
 *
 * <ol>
 *   <li>签名校验优先——{@code ydsz.security.internal-sign.enabled=true} 且
 *       {@link ApiSignatureFilter} 已验证通过（请求属性为 TRUE）时直接放行；</li>
 *   <li>未启用签名或签名未通过时，回退到内部调用标记头
 *       （{@code X-Internal-Call: true}）校验。</li>
 * </ol>
 *
 * <p><b>开关</b>：{@code ydsz.internal-call.enabled=true} 时校验生效，默认关闭。
 *
 * <p><b>下沉说明（26.11.06）</b>：原仅 userinfo 拥有此切面，其余 6 模块内部接口无服务端
 * 二次校验。下沉后各模块只需在内部 Controller 上加 {@code @RequireInternal} 并开启开关即可。
 *
 * @author ydsz-team
 * @since 26.11.06
 * @see RequireInternal 内部接口标记注解
 * @see InternalCallProperties 内部调用配置
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RequireInternalAspect {

  private final InternalCallProperties properties;
  private final ApiSignatureProperties signatureProperties;

  /** 切点：类级或方法级标注了 {@link RequireInternal} 的方法。 */
  @Pointcut("@annotation(com.njydsz.common.safe.internal.RequireInternal) "
      + "|| @within(com.njydsz.common.safe.internal.RequireInternal)")
  public void internalPointcut() {}

  /**
   * 前置通知：校验内部调用凭据。
   *
   * @param joinPoint 切点
   * @throws BusinessException 开关开启但请求缺少内部调用凭据时抛出
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
      log.warn("Internal API access denied (no request context): method={}",
          joinPoint.getSignature().toShortString());
      throw new BusinessException(SecurityExceptionCode.FORBIDDEN);
    }
    HttpServletRequest request = attributes.getRequest();

    // 签名校验优先 — 签名过滤器已通过则直接放行
    if (isSignatureVerified(request)) {
      return;
    }

    // 回退到内部调用标记头校验
    String flag = request.getHeader(properties.getHeaderName());
    if (!properties.getHeaderValue().equalsIgnoreCase(flag)) {
      log.warn(
          "Internal API access denied (missing {} header): method={}, uri={}",
          properties.getHeaderName(),
          joinPoint.getSignature().toShortString(),
          request.getRequestURI());
      throw new BusinessException(SecurityExceptionCode.FORBIDDEN);
    }
  }

  /**
   * 判断签名过滤器是否已通过校验。
   *
   * <p>当 {@link ApiSignatureProperties#isEnabled()} 为 true 且签名过滤器成功验证签名后，
   * 会在请求属性中设置 {@link ApiSignatureFilter#DEFAULT_SUCCESS_ATTRIBUTE} 为
   * {@link Boolean#TRUE}。本方法检查该属性，若存在且为 true 则视为签名校验已通过。
   *
   * @param request HTTP 请求
   * @return true 表示签名校验已通过（可跳过标记头校验）
   */
  private boolean isSignatureVerified(HttpServletRequest request) {
    if (!signatureProperties.isEnabled()) {
      return false;
    }
    Object attr = request.getAttribute(ApiSignatureFilter.DEFAULT_SUCCESS_ATTRIBUTE);
    return Boolean.TRUE.equals(attr);
  }
}
