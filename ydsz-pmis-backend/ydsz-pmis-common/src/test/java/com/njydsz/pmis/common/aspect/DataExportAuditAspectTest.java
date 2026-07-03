package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.DataExportAudit;
import com.njydsz.pmis.common.security.DataExportAuditEvent;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataExportAuditAspect 数据导出审计切面测试
 *
 * <p>覆盖集合/数字结果行数采集、IP 解析、异常吞掉与未登录兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DataExportAuditAspect 导出审计切面测试")
class DataExportAuditAspectTest {

    private ApplicationEventPublisher publisher;
    private DataExportAuditAspect aspect;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        aspect = new DataExportAuditAspect(publisher);
        SecurityContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("导出列表结果应正确采集行数")
    void collectionResult() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(List.of("a", "b", "c"));

        Object r = aspect.around(pjp, annotation("project", "export"));
        assertThat((List<?>) r).hasSize(3);

        ArgumentCaptor<DataExportAuditEvent> cap = ArgumentCaptor.forClass(DataExportAuditEvent.class);
        verify(publisher, times(1)).publishEvent(cap.capture());
        DataExportAuditEvent e = cap.getValue();
        assertThat(e.getUserId()).isEqualTo(1L);
        assertThat(e.getUsername()).isEqualTo("u");
        assertThat(e.getExportModule()).isEqualTo("project");
        assertThat(e.getExportAction()).isEqualTo("export");
        assertThat(e.getRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("数字结果作为行数")
    void numberResult() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(42);

        aspect.around(pjp, annotation("m", "a"));
        ArgumentCaptor<DataExportAuditEvent> cap = ArgumentCaptor.forClass(DataExportAuditEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getRowCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("请求 IP 应优先使用 X-Forwarded-For")
    void ipFromHeader() throws Throwable {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, annotation("m", "a"));
        ArgumentCaptor<DataExportAuditEvent> cap = ArgumentCaptor.forClass(DataExportAuditEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getClientIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("请求上下文为空时 IP 留空")
    void noRequest() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");
        aspect.around(pjp, annotation("m", "a"));
        ArgumentCaptor<DataExportAuditEvent> cap = ArgumentCaptor.forClass(DataExportAuditEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getClientIp()).isEqualTo("");
    }

    @Test
    @DisplayName("发布异常不影响主流程")
    void swallowException() throws Throwable {
        org.mockito.Mockito.doThrow(new RuntimeException("busy")).when(publisher).publishEvent(any());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");
        Object r = aspect.around(pjp, annotation("m", "a"));
        assertThat(r).isEqualTo("ok");
    }

    @Test
    @DisplayName("未登录时不报错，userId 为 null")
    void noLogin() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");
        aspect.around(pjp, annotation("m", "a"));
        ArgumentCaptor<DataExportAuditEvent> cap = ArgumentCaptor.forClass(DataExportAuditEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getUserId()).isNull();
    }

    private DataExportAudit annotation(String module, String action) {
        return (DataExportAudit) Proxy.newProxyInstance(
                DataExportAudit.class.getClassLoader(),
                new Class[]{DataExportAudit.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "module": return module;
                        case "action": return action;
                        case "bizType": return "TEST";
                        case "annotationType": return DataExportAudit.class;
                        default: return null;
                    }
                });
    }
}
