package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 全局异常处理单元测试
 *
 * <p>覆盖业务异常、参数校验、缺失参数、方法不支持、404 与兜底异常的转换逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("GlobalExceptionHandler 全局异常测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        MDC.put("traceId", "test-trace-id");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("BizException 应被转换为 R 失败响应")
    void handleBizException() {
        BizException ex = new BizException(BizErrorCode.NOT_FOUND, "找不到资源");
        Result<Void> r = handler.handleBizException(ex, request);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
        assertThat(r.getMessage()).isEqualTo("找不到资源");
        assertThat(r.getTraceId()).isEqualTo("test-trace-id");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 应聚合字段错误")
    void handleValid() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "target");
        br.addError(new FieldError("target", "username", "用户名不能为空"));
        br.addError(new FieldError("target", "password", "密码长度至少 6 位"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, br);

        Result<Void> r = handler.handleValidException(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
        assertThat(r.getMessage()).contains("用户名不能为空").contains("密码长度至少 6 位");
    }

    @Test
    @DisplayName("BindException 应聚合字段错误")
    void handleBind() {
        BindException ex = mock(BindException.class);
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "t");
        br.addError(new FieldError("t", "f", "字段错误"));
        when(ex.getBindingResult()).thenReturn(br);

        Result<Void> r = handler.handleBindException(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
        assertThat(r.getMessage()).contains("字段错误");
    }

    @Test
    @DisplayName("ConstraintViolationException 应聚合校验消息")
    void handleConstraint() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        when(v.getMessage()).thenReturn("ID 必须大于 0");
        Set<ConstraintViolation<?>> set = new HashSet<>();
        set.add(v);
        ConstraintViolationException ex = new ConstraintViolationException(set);

        Result<Void> r = handler.handleConstraintViolation(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
        assertThat(r.getMessage()).contains("ID 必须大于 0");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException 应提示缺失参数")
    void handleMissing() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("id", "Long");
        Result<Void> r = handler.handleMissingParam(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.MISSING_PARAMETER.getCode());
        assertThat(r.getMessage()).contains("id");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException 应返回 BAD_REQUEST")
    void handleNotReadable() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null);
        Result<Void> r = handler.handleNotReadable(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException 应返回 METHOD_NOT_ALLOWED")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PUT");
        Result<Void> r = handler.handleMethodNotSupported(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    @DisplayName("NoHandlerFoundException 应返回 NOT_FOUND")
    void handleNotFound() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/x", new org.springframework.http.HttpHeaders());
        Result<Void> r = handler.handleNotFound(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("IllegalArgumentException 应被转换为 BAD_REQUEST")
    void handleIllegal() {
        IllegalArgumentException ex = new IllegalArgumentException("非法参数");
        Result<Void> r = handler.handleIllegalArgument(ex);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
        assertThat(r.getMessage()).isEqualTo("非法参数");
    }

    @Test
    @DisplayName("兜底 Exception 应返回 INTERNAL_ERROR")
    void handleGeneric() {
        Exception ex = new RuntimeException("boom");
        Result<Void> r = handler.handleException(ex, request);
        assertThat(r.getCode()).isEqualTo(BizErrorCode.INTERNAL_ERROR.getCode());
        assertThat(r.getTraceId()).isEqualTo("test-trace-id");
    }
}
