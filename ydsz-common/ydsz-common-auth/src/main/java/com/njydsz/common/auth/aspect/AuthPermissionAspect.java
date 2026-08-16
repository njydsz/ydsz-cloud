package com.njydsz.common.auth.aspect;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.annotation.AuthMenuPermission;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * 统一权限校验切面。
 *
 * <p>整合菜单权限和接口权限限于一个切面中，简化切面管理。 根据注解类型自动选择对应的校验方法。
 *
 * <p><b>校验流程：</b>
 *
 * <ol>
 *   <li>获取方法上的注解（优先）或类上的注解
 *   <li>加载当前用户信息
 *   <li>根据注解类型调用对应的 validate 方法
 *   <li>权限校验通过则执行目标方法，否则抛出业务异常
 * </ol>
 *
 * <p><b>切面顺序：</b>
 *
 * <p>本切面 Order 为 10，在行级权限注入之前执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuthMenuPermission
 * @see AuthApiPermission
 * @see RbacPermissionEvaluator
 */
@Aspect
@Order(10)
public class AuthPermissionAspect {

  private final RbacPermissionEvaluator evaluator;

  /** 缓存 Method -> [classAnnotation, methodAnnotation] 避免每次请求做反射查找 */
  private final ConcurrentHashMap<Method, CachedMenuAnnotation> menuAnnotationCache =
      new ConcurrentHashMap<>(256);

  private final ConcurrentHashMap<Method, CachedApiAnnotation> apiAnnotationCache =
      new ConcurrentHashMap<>(256);

  public AuthPermissionAspect(RbacPermissionEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * 菜单/按钮权限切点：匹配标注或元标注了 {@link AuthMenuPermission} 的方法或类。
   *
   * <p>作为 {@link #doMenuAround} 的引用锚点；命中后由该通知完成菜单/按钮权限校验。
   */
  @Pointcut(
      "@annotation(com.njydsz.common.auth.annotation.AuthMenuPermission) || @within(com.njydsz.common.auth.annotation.AuthMenuPermission)")
  public void menuPointCut() {}

  /**
   * 接口权限切点：匹配标注或元标注了 {@link AuthApiPermission} 的方法或类。
   *
   * <p>作为 {@link #doApiAround} 的引用锚点；命中后由该通知完成接口权限校验。
   */
  @Pointcut(
      "@annotation(com.njydsz.common.auth.annotation.AuthApiPermission) || @within(com.njydsz.common.auth.annotation.AuthApiPermission)")
  public void apiPointCut() {}

  /**
   * 菜单/按钮权限切面环绕通知。
   *
   * <p>拦截标注了 {@link AuthMenuPermission} 的方法，在方法执行前校验用户是否拥有指定的菜单或按钮权限， 校验失败时抛出 {@link
   * com.njydsz.common.auth.exception.PermissionDeniedException}。
   *
   * @param joinPoint 切面连接点
   * @return 方法返回值
   * @throws Throwable 方法执行异常或权限校验异常
   */
  @Around("menuPointCut()")
  public Object doMenuAround(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Class<?> targetClass = joinPoint.getTarget().getClass();

    CachedMenuAnnotation cached =
        menuAnnotationCache.computeIfAbsent(
            method,
            m -> {
              AuthMenuPermission classAnn =
                  AnnotationUtils.findAnnotation(targetClass, AuthMenuPermission.class);
              AuthMenuPermission methodAnn =
                  AnnotationUtils.findAnnotation(m, AuthMenuPermission.class);
              return new CachedMenuAnnotation(classAnn, methodAnn);
            });

    if (cached.classAnnotation == null && cached.methodAnnotation == null) {
      return joinPoint.proceed();
    }

    Map<String, Object> userInfo = evaluator.loadCurrentUserInfo();

    if (cached.classAnnotation != null) {
      evaluator.validateMenu(userInfo, cached.classAnnotation);
    }

    if (cached.methodAnnotation != null) {
      evaluator.validateMenu(userInfo, cached.methodAnnotation);
    }

    return joinPoint.proceed();
  }

  /**
   * 接口权限切面环绕通知。
   *
   * <p>拦截标注了 {@link AuthApiPermission} 的方法，在方法执行前校验用户是否拥有指定的接口权限， 校验失败时抛出 {@link
   * com.njydsz.common.auth.exception.PermissionDeniedException}。
   *
   * @param joinPoint 切面连接点
   * @return 方法返回值
   * @throws Throwable 方法执行异常或权限校验异常
   */
  @Around("apiPointCut()")
  public Object doApiAround(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Class<?> targetClass = joinPoint.getTarget().getClass();

    CachedApiAnnotation cached =
        apiAnnotationCache.computeIfAbsent(
            method,
            m -> {
              AuthApiPermission classAnn =
                  AnnotationUtils.findAnnotation(targetClass, AuthApiPermission.class);
              AuthApiPermission methodAnn =
                  AnnotationUtils.findAnnotation(m, AuthApiPermission.class);
              return new CachedApiAnnotation(classAnn, methodAnn);
            });

    if (cached.classAnnotation == null && cached.methodAnnotation == null) {
      return joinPoint.proceed();
    }

    Map<String, Object> userInfo = evaluator.loadCurrentUserInfo();

    if (cached.classAnnotation != null) {
      evaluator.validateApi(userInfo, cached.classAnnotation);
    }

    if (cached.methodAnnotation != null) {
      evaluator.validateApi(userInfo, cached.methodAnnotation);
    }

    return joinPoint.proceed();
  }

  /**
   * 菜单/按钮权限注解的缓存条目。
   *
   * <p>同时持有类级与方法级 {@link AuthMenuPermission}，避免每次请求重复反射解析注解； 两者可同时为 {@code null}，表示方法及其所在类上均未标注注解。
   */
  private record CachedMenuAnnotation(
      AuthMenuPermission classAnnotation, AuthMenuPermission methodAnnotation) {}

  /**
   * 接口权限注解的缓存条目。
   *
   * <p>同时持有类级与方法级 {@link AuthApiPermission}，避免每次请求重复反射解析注解； 两者可同时为 {@code null}，表示方法及其所在类上均未标注注解。
   */
  private record CachedApiAnnotation(
      AuthApiPermission classAnnotation, AuthApiPermission methodAnnotation) {}
}
