package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OperationLogAspect 操作日志切面单元测试
 */
@DisplayName("OperationLogAspect 操作日志切面测试")
class OperationLogAspectTest {

    private final OperationLogAspect aspect = new OperationLogAspect();

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("正常执行业务后应返回结果")
    void proceed_success() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("data");

        Object r = aspect.around(pjp, sampleAnnotation(true, false));
        assertThat(r).isEqualTo("data");
    }

    @Test
    @DisplayName("业务抛异常应透传")
    void proceed_throws() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        RuntimeException ex = new RuntimeException("biz error");
        when(pjp.proceed()).thenThrow(ex);

        assertThatThrownBy(() -> aspect.around(pjp, sampleAnnotation(true, false)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("biz error");
    }

    @Test
    @DisplayName("登录用户存在时应记录 userId / username")
    void withLoginUser() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(99L).username("u99").build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, sampleAnnotation(true, false));
    }

    @Test
    @DisplayName("请求上下文存在时记录 IP/UA")
    void withRequest() throws Throwable {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/x");
        when(req.getMethod()).thenReturn("POST");
        when(req.getHeader("User-Agent")).thenReturn("ua-test");
        when(req.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, sampleAnnotation(true, false));
    }

    @Test
    @DisplayName("saveParams=false 时不应读取参数")
    void skipParams() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenThrow(new RuntimeException("args read"));
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, sampleAnnotation(false, false));
    }

    private OperationLog sampleAnnotation(boolean saveParams, boolean saveResult) {
        return (OperationLog) java.lang.reflect.Proxy.newProxyInstance(
                OperationLog.class.getClassLoader(),
                new Class[]{OperationLog.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "module": return "用户管理";
                        case "action": return "创建用户";
                        case "bizType": return "USER";
                        case "saveParams": return saveParams;
                        case "saveResult": return saveResult;
                        case "excludeFields": return new String[]{"password"};
                        case "annotationType": return OperationLog.class;
                        default: return null;
                    }
                });
    }
}
