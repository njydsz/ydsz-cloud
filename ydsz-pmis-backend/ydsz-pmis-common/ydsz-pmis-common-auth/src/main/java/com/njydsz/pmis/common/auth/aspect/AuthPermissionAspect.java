package com.njydsz.pmis.common.auth.aspect;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.auth.annotation.AuthMenuPermission;
import com.njydsz.pmis.common.auth.service.RbacPermissionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 统一权限校验切面。
 *
 * <p>整合菜单权限和接口权限限于一个切面中，简化切面管理。
 * 根据注解类型自动选择对应的校验方法。
 *
 * <p><b>校验流程：</b>
 * <ol>
 *   <li>获取方法上的注解（优先）或类上的注解</li>
 *   <li>加载当前用户信息</li>
 *   <li>根据注解类型调用对应的 validate 方法</li>
 *   <li>权限校验通过则执行目标方法，否则抛出业务异常</li>
 * </ol>
 *
 * <p><b>切面顺序：</b>
 * <p>本切面 Order 为 10，在行级权限注入之前执行。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see AuthMenuPermission
 * @see AuthApiPermission
 * @see RbacPermissionEvaluator
 */
@Aspect
@Order(10)
public class AuthPermissionAspect {

    private final RbacPermissionEvaluator evaluator;

    public AuthPermissionAspect(RbacPermissionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Pointcut("@annotation(com.njydsz.pmis.common.auth.annotation.AuthMenuPermission) || @within(com.njydsz.pmis.common.auth.annotation.AuthMenuPermission)")
    public void menuPointCut() {
    }

    @Pointcut("@annotation(com.njydsz.pmis.common.auth.annotation.AuthApiPermission) || @within(com.njydsz.pmis.common.auth.annotation.AuthApiPermission)")
    public void apiPointCut() {
    }

    /**
     * 菜单/按钮权限切面环绕通知。
     *
     * <p>拦截标注了 {@link AuthMenuPermission} 的方法，在方法执行前校验用户是否拥有指定的菜单或按钮权限，
     * 校验失败时抛出 {@link com.njydsz.pmis.common.auth.exception.PermissionDeniedException}。
     *
     * @param joinPoint 切面连接点
     * @return 方法返回值
     * @throws Throwable 方法执行异常或权限校验异常
     */
    @Around("menuPointCut()")
    public Object doMenuAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        AuthMenuPermission classAnnotation = AnnotationUtils.findAnnotation(targetClass, AuthMenuPermission.class);
        AuthMenuPermission methodAnnotation = AnnotationUtils.findAnnotation(method, AuthMenuPermission.class);

        if (classAnnotation == null && methodAnnotation == null) {
            return joinPoint.proceed();
        }

        Map<String, Object> userInfo = evaluator.loadCurrentUserInfo();

        if (classAnnotation != null) {
            evaluator.validateMenu(userInfo, classAnnotation);
        }

        if (methodAnnotation != null) {
            evaluator.validateMenu(userInfo, methodAnnotation);
        }

        return joinPoint.proceed();
    }

    /**
     * 接口权限切面环绕通知。
     *
     * <p>拦截标注了 {@link AuthApiPermission} 的方法，在方法执行前校验用户是否拥有指定的接口权限，
     * 校验失败时抛出 {@link com.njydsz.pmis.common.auth.exception.PermissionDeniedException}。
     *
     * @param joinPoint 切面连接点
     * @return 方法返回值
     * @throws Throwable 方法执行异常或权限校验异常
     */
    @Around("apiPointCut()")
    public Object doApiAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        AuthApiPermission classAnnotation = AnnotationUtils.findAnnotation(targetClass, AuthApiPermission.class);
        AuthApiPermission methodAnnotation = AnnotationUtils.findAnnotation(method, AuthApiPermission.class);

        if (classAnnotation == null && methodAnnotation == null) {
            return joinPoint.proceed();
        }

        Map<String, Object> userInfo = evaluator.loadCurrentUserInfo();

        if (classAnnotation != null) {
            evaluator.validateApi(userInfo, classAnnotation);
        }

        if (methodAnnotation != null) {
            evaluator.validateApi(userInfo, methodAnnotation);
        }

        return joinPoint.proceed();
    }
}
