package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PermissionAspect 权限切面单元测试
 */
@DisplayName("PermissionAspect 权限切面测试")
class PermissionAspectTest {

    private final PermissionAspect aspect = new PermissionAspect();

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("未登录访问 requireLogin=true 应抛 UNAUTHORIZED")
    void requireLogin_unauthorized() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        PrePermission ann = newSampleAnnotation(new String[]{"user:list"}, PrePermission.Mode.AND, true);

        assertThatThrownBy(() -> aspect.around(pjp, ann))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("未登录访问 requireLogin=false 不应校验权限")
    void noRequireLogin() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        PrePermission ann = newSampleAnnotation(new String[]{"user:list"}, PrePermission.Mode.AND, false);
        Object result = aspect.around(pjp, ann);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("AND 模式：所有权限都满足应放行")
    void andMode_pass() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:list", "user:create")).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        PrePermission ann = newSampleAnnotation(new String[]{"user:list", "user:create"}, PrePermission.Mode.AND, true);
        Object result = aspect.around(pjp, ann);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("AND 模式：缺少一个权限应拒绝")
    void andMode_forbidden() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:list")).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(mock(org.aspectj.lang.Signature.class));
        when(pjp.getSignature().toShortString()).thenReturn("Test.target()");

        PrePermission ann = newSampleAnnotation(new String[]{"user:list", "user:create"}, PrePermission.Mode.AND, true);
        assertThatThrownBy(() -> aspect.around(pjp, ann))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("OR 模式：拥有任一权限应放行")
    void orMode_pass() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:list")).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        PrePermission ann = newSampleAnnotation(new String[]{"user:list", "user:create"}, PrePermission.Mode.OR, true);
        Object result = aspect.around(pjp, ann);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("OR 模式：都不满足应拒绝")
    void orMode_forbidden() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of()).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(mock(org.aspectj.lang.Signature.class));
        when(pjp.getSignature().toShortString()).thenReturn("Test.target()");

        PrePermission ann = newSampleAnnotation(new String[]{"user:list", "user:create"}, PrePermission.Mode.OR, true);
        assertThatThrownBy(() -> aspect.around(pjp, ann))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("超管用户对任意权限都应放行")
    void superAdmin_alwaysPass() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("*:*:*")).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        PrePermission ann = newSampleAnnotation(new String[]{"any:perm"}, PrePermission.Mode.AND, true);
        Object result = aspect.around(pjp, ann);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("value() 为空时不应校验权限")
    void emptyValue() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().permissions(List.of()).build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        PrePermission ann = newSampleAnnotation(new String[]{}, PrePermission.Mode.AND, true);
        Object result = aspect.around(pjp, ann);
        assertThat(result).isEqualTo("ok");
    }

    private PrePermission newSampleAnnotation(String[] value, PrePermission.Mode mode, boolean requireLogin) {
        // 通过动态代理构造一个 PrePermission 注解
        return (PrePermission) Proxy.newProxyInstance(
                PrePermission.class.getClassLoader(),
                new Class[]{PrePermission.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("value".equals(name)) return value;
                    if ("mode".equals(name)) return mode;
                    if ("requireLogin".equals(name)) return requireLogin;
                    if ("annotationType".equals(name)) return PrePermission.class;
                    return null;
                });
    }

    private static class Proxy {
        static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces,
                                       java.lang.reflect.InvocationHandler h) {
            return java.lang.reflect.Proxy.newProxyInstance(loader, interfaces, h);
        }
    }
}
