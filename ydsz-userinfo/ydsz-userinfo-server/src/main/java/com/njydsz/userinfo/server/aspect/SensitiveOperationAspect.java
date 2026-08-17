package com.njydsz.userinfo.server.aspect;

import java.lang.reflect.Method;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.annotation.SensitiveOperation;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.SensitiveVerifyService;

/**
 * 敏感操作二次认证 AOP 切面。
 *
 * <p>拦截标注了 {@link SensitiveOperation} 注解的 Controller 方法，在执行前检查当前用户是否已通过二次认证。 未验证或验证已过期时抛出 {@link
 * UserInfoExceptionCode#SENSITIVE_VERIFY_REQUIRED} 异常，要求前端先调用 {@code /sensitive-verify} 接口。
 *
 * <p><b>切点表达式：</b>匹配所有标注 {@code @SensitiveOperation} 的方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SensitiveOperationAspect {

  private final SensitiveVerifyService sensitiveVerifyService;

  /** 切点：标注了 @SensitiveOperation 注解的方法 */
  @Pointcut("@annotation(com.njydsz.common.safe.annotation.SensitiveOperation)")
  public void sensitiveOperationPointcut() {}

  /**
   * 前置通知：在敏感操作执行前检查用户是否已通过二次认证。
   *
   * @param joinPoint 切点
   * @throws BusinessException 未通过二次认证时抛出
   */
  @Before("sensitiveOperationPointcut()")
  public void beforeSensitiveOperation(org.aspectj.lang.JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    SensitiveOperation annotation = method.getAnnotation(SensitiveOperation.class);
    String operationDesc = annotation.value().isEmpty() ? method.getName() : annotation.value();

    if (!sensitiveVerifyService.isVerified()) {
      log.warn("敏感操作被拒绝（未通过二次认证）: operation={}", operationDesc);
      throw new BusinessException(UserInfoExceptionCode.SENSITIVE_VERIFY_REQUIRED);
    }

    log.debug("敏感操作二次认证通过: operation={}", operationDesc);
  }
}
