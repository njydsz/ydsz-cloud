package com.njydsz.common.safe.permission;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.spring.SpELKeyUtils;

/**
 * 字段级权限 AOP 切面实现（骨架）。
 *
 * <p>拦截标注 {@link FieldPermission} 注解的 Controller 方法，通过 SpEL 表达式提取字段标识符，
 * 再与当前登录用户的字段权限集合进行比对，无权限时抛出 {@link FieldPermissionDeniedException}。
 *
 * <p><b>执行流程：</b>
 *
 * <ol>
 *   <li>解析 {@link FieldPermission#value()} SpEL 表达式，提取目标字段标识符
 *   <li>从当前线程安全上下文获取 userId / tenantId（通过 {@link RequestContext}）
 *   <li>通过 {@link FieldPermissionEvaluator} SPI 获取用户授权字段集合
 *   <li>超级管理员（{@link FieldPermissionEvaluator#isSuperAdmin}）直接放行
 *   <li>拒绝时抛出 {@link FieldPermissionDeniedException}（{@code C01065}，HTTP 403）
 * </ol>
 *
 * <p><b>扩展点：</b>
 *
 * <ul>
 *   <li>{@link FieldPermissionEvaluator} — 通过 Spring {@link ObjectProvider} 注入，由业务模块提供实现
 *   <li>{@link #isEnabled()} — 全局开关，默认 true
 * </ul>
 *
 * <p><b>架构约束：</b>
 *
 * <ul>
 *   <li>本模块位于 ydss-common-safe，不直接依赖 ydsz-common-auth（避免循环依赖）
 *   <li>通过 SPI 解耦，auth 模块或 web 组合层提供 {@code FieldPermissionEvaluator} 实现
 * </ul>
 *
 * @author ydsz
 * @since 26.10.14
 * @see FieldPermission
 * @see FieldPermissionEvaluator
 */
@Slf4j
@Aspect
@Order(20)
public class FieldPermissionAspectImpl {

  /** 方法注解缓存，避免每次请求都做反射查找 */
  private final ConcurrentHashMap<Method, FieldPermission> annotationCache =
      new ConcurrentHashMap<>(128);

  /** 字段权限求值器（可选，由业务模块提供实现） */
  private final ObjectProvider<FieldPermissionEvaluator> evaluatorProvider;

  /**
   * 构造字段权限切面。
   *
   * @param evaluatorProvider 字段权限求值器提供者（可为 null）
   */
  public FieldPermissionAspectImpl(ObjectProvider<FieldPermissionEvaluator> evaluatorProvider) {
    this.evaluatorProvider = evaluatorProvider;
  }

  /**
   * Pointcut：匹配标注或元标注了 {@link FieldPermission} 的方法或类。
   */
  @Pointcut(
      "@annotation(com.njydsz.common.safe.permission.FieldPermission)"
          + " || @within(com.njydsz.common.safe.permission.FieldPermission)")
  public void fieldPermissionPointcut() {}

  /**
   * 环绕通知：在目标方法执行前完成字段权限校验。
   *
   * @param joinPoint 切面连接点
   * @return 目标方法返回值
   * @throws Throwable 目标方法抛出的异常或权限校验异常
   */
  @Around("fieldPermissionPointcut()")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    if (!isEnabled()) {
      return joinPoint.proceed();
    }

    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    FieldPermission annotation = getAnnotation(method, joinPoint.getTarget().getClass());

    if (annotation == null) {
      return joinPoint.proceed();
    }

    String fieldExpression = annotation.value();
    Object[] args = joinPoint.getArgs();
    String fieldId = resolveFieldId(fieldExpression, method, args);

    if (fieldId == null || fieldId.isEmpty()) {
      log.warn(
          "[FieldPermission] SpEL 解析字段标识为空，跳过字段校验 expression={} method={}",
          fieldExpression,
          method.getName());
      return joinPoint.proceed();
    }

    // 获取当前用户上下文（通过 RequestContext / 请求上下文持有者）
    String userId = getCurrentUserId();
    String tenantId = getCurrentTenantId();

    if (userId == null || userId.isEmpty()) {
      log.warn(
          "[FieldPermission] 未登录用户访问字段权限控制接口，拒绝 method={} field={}",
          method.getName(),
          fieldId);
      throw new FieldPermissionDeniedException(
          "anonymous",
          null,
          Collections.singleton(fieldId),
          method.getDeclaringClass().getSimpleName() + "#" + method.getName());
    }

    // 获取 SPI evaluator
    FieldPermissionEvaluator evaluator = evaluatorProvider.getIfAvailable();
    if (evaluator == null) {
      log.warn(
          "[FieldPermission] 未配置 FieldPermissionEvaluator，跳过字段校验 userId={} field={}",
          userId,
          fieldId);
      return joinPoint.proceed();
    }

    // 超级管理员直接放行
    if (evaluator.isSuperAdmin(userId)) {
      log.debug(
          "[FieldPermission] 超级管理员跳过字段校验 userId={} field={}",
          userId, fieldId);
      return joinPoint.proceed();
    }

    // 查询用户被授权的字段权限集
    Set<String> grantedFields = evaluator.getAuthorizedFields(userId, tenantId);

    if (grantedFields != null && grantedFields.contains(fieldId)) {
      log.debug(
          "[FieldPermission] 字段权限校验通过 userId={} field={}",
          userId, fieldId);
      return joinPoint.proceed();
    }

    // 字段权限拒绝
    log.warn(
        "[FieldPermission] 字段权限拒绝 userId={} field={} method={}",
        userId, fieldId, method.getName());

    throw new FieldPermissionDeniedException(
        userId,
        grantedFields != null ? grantedFields : Collections.emptySet(),
        Collections.singleton(fieldId),
        method.getDeclaringClass().getSimpleName() + "#" + method.getName());
  }

  // ============================== 可覆写方法 ==============================

  /**
   * 解析 SpEL 表达式获取字段标识符。
   *
   * <p>覆写此方法可自定义字段标识的解析策略。
   *
   * @param expression SpEL 表达式
   * @param method 目标方法
   * @param args 方法参数
   * @return 字段标识符，解析失败时返回 null
   */
  protected String resolveFieldId(String expression, Method method, Object[] args) {
    try {
      return SpELKeyUtils.resolve(expression, method, args);
    } catch (Exception e) {
      log.warn(
          "[FieldPermission] SpEL 解析字段标识失败，跳过字段校验 expression={} cause={}",
          expression,
          e.getMessage());
      return null;
    }
  }

  /**
   * 字段级权限切面全局开关。
   *
   * @return true 表示启用字段权限校验
   */
  protected boolean isEnabled() {
    return true;
  }

  /**
   * 从安全上下文获取当前用户 ID。
   *
   * <p>默认从 {@link RequestContext} 获取（ydsz-common-core 提供），业务方可覆写此方法
   * 以适配不同的安全上下文策略。
   *
   * @return 当前用户 ID，未登录时返回 null
   */
  protected String getCurrentUserId() {
    Object userId = RequestContext.get("userId");
    return userId != null ? userId.toString() : null;
  }

  /**
   * 从安全上下文获取当前租户 ID。
   *
   * @return 当前租户 ID，未设置时返回 null
   */
  protected String getCurrentTenantId() {
    Object tenantId = RequestContext.get("tenantId");
    return tenantId != null ? tenantId.toString() : null;
  }

  // ============================== 私有方法 ==============================

  /**
   * 获取方法上的 {@link FieldPermission} 注解（优先），其次获取类级注解。
   *
   * @param method 目标方法
   * @param targetClass 目标类
   * @return 注解实例，未找到返回 null
   */
  private FieldPermission getAnnotation(Method method, Class<?> targetClass) {
    return annotationCache.computeIfAbsent(
        method,
        m -> {
          FieldPermission methodAnn = m.getAnnotation(FieldPermission.class);
          if (methodAnn != null) {
            return methodAnn;
          }
          return targetClass.getAnnotation(FieldPermission.class);
        });
  }
}
