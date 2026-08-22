package com.njydsz.userinfo.server.aspect;

import java.lang.reflect.Method;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.annotation.SecondaryAuth;
import com.njydsz.common.safe.annotation.SensitiveLevel;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.SecondaryAuthService;

/**
 * 场景化二级认证 AOP 切面（P0-2 标准化）。
 *
 * <p>拦截标注了 {@link SecondaryAuth} 注解的 Controller 方法，在执行前检查当前用户是否在指定场景下已通过二级认证。
 * 未验证或验证已过期时抛出 {@link UserInfoExceptionCode#SECONDARY_AUTH_REQUIRED} 异常，
 * 要求前端先调用 {@code PostDO /api/v1/auth/secondary-auth} 接口完成场景化验证。
 *
 * <p><b>与 {@link SensitiveOperationAspect} 的区别：</b>
 *
 * <ul>
 *   <li>{@code SensitiveOperationAspect} — 全局单一验证标记，所有敏感操作共享</li>
 *   <li>{@code SecondaryAuthAspect} — 场景隔离验证，不同业务场景独立验证/独立过期</li>
 * </ul>
 *
 * <p><b>切点表达式：</b>匹配所有标注 {@code @SecondaryAuth} 的方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SecondaryAuthAspect {

  private final SecondaryAuthService secondaryAuthService;

  /** 切点：标注了 @SecondaryAuth 注解的方法 */
  @Pointcut("@annotation(com.njydsz.common.safe.annotation.SecondaryAuth)")
  public void secondaryAuthPointcut() {}

  /**
   * 前置通知：在目标方法执行前检查场景化二级认证状态。
   *
   * <p>根据 {@link SecondaryAuth#level()} 差异化处理时效：
   * CRITICAL 级别使用更短的验证窗口，降低极敏感操作风险。
   *
   * @param joinPoint 切点
   * @throws BusinessException 未通过二级认证时抛出
   */
  @Before("secondaryAuthPointcut()")
  public void beforeSecondaryAuth(JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    SecondaryAuth annotation = method.getAnnotation(SecondaryAuth.class);
    String scene = annotation.scene();
    String operationDesc = annotation.value().isEmpty() ? method.getName() : annotation.value();
    SensitiveLevel level = annotation.level();

    if (!secondaryAuthService.checkSafe(scene, level)) {
      log.warn("场景化二级认证未通过: operation={}, scene={}, level={}", operationDesc, scene, level);
      throw new BusinessException(UserInfoExceptionCode.SECONDARY_AUTH_REQUIRED);
    }

    log.info("场景化二级认证通过: operation={}, scene={}, level={}", operationDesc, scene, level);
  }

  /**
   * 计算实际生效的 TTL（CRITICAL 级别缩短为配置的 40%，最小 60 秒）。
   *
   * <p>供 AuthController 调用，确保写入 Redis 的 TTL 与切面检查逻辑一致。
   *
   * @param annotation 注解实例
   * @return 实际生效的 TTL
   */
  public static Duration resolveEffectiveTtl(SecondaryAuth annotation) {
    int configuredTtl = annotation.ttlSeconds();
    if (annotation.level() == SensitiveLevel.CRITICAL) {
      long criticalTtl = Math.round(configuredTtl * 0.4);
      return Duration.ofSeconds(Math.max(criticalTtl, 60));
    }
    return Duration.ofSeconds(configuredTtl);
  }
}
