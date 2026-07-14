package com.njydsz.pmis.common.exception.handler;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;

/**
 * Validation 相关异常处理器
 *
 * <p>仅在 jakarta.validation 存在时注册，处理 {@code @Valid}/{@code @Validated} 注解触发的校验异常。
 * 与 {@link MvcExceptionHandler} 配合使用，后者处理通用异常，本类专注于校验异常。
 *
 * <p><b>装配：</b>本类已不再直接标注 {@code @AutoConfiguration}，
 * 改由 {@link ValidationExceptionHandlerAutoConfiguration} 负责条件装配与 Bean 注入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see MvcExceptionHandler
 * @see ValidationExceptionHandlerAutoConfiguration
 */
@ConditionalOnClass(ConstraintViolationException.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
public class ValidationExceptionHandler {

    private final MessageSource messageSource;
    private final ExceptionMetrics exceptionMetrics;

    public ValidationExceptionHandler(MessageSource messageSource, ExceptionMetrics exceptionMetrics) {
        this.messageSource = messageSource;
        this.exceptionMetrics = exceptionMetrics;
    }

    /**
     * 记录异常指标
     */
    private void recordMetrics(Throwable throwable) {
        if (exceptionMetrics != null) {
            exceptionMetrics.recordException(throwable);
        }
    }

    /**
     * 提取约束违反异常的错误消息
     */
    private String extractConstraintViolationMessages(ConstraintViolationException e) {
        List<String> messages = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        return String.join(", ", messages);
    }

    /**
     * 提取绑定结果中的错误消息
     */
    private String extractBindingResultMessages(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
    }

    /**
     * 构建校验错误响应
     */
    private BaseResponse<?> buildValidationErrorResponse(String message, String path) {
        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getKey(),
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(path);
        return BaseResponse.error(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                message,
                info
        );
    }

    /**
     * 处理参数校验异常（简单参数 @Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleConstraintViolationException(ConstraintViolationException e) {
        recordMetrics(e);
        String message = extractConstraintViolationMessages(e);
        return buildValidationErrorResponse(message, "");
    }

    /**
     * 处理请求体参数校验异常（@Valid/@Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        recordMetrics(e);
        String message = extractBindingResultMessages(e.getBindingResult());
        return buildValidationErrorResponse(message, "");
    }

    /**
     * 处理表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleBindException(BindException e) {
        recordMetrics(e);
        String message = extractBindingResultMessages(e.getBindingResult());
        return buildValidationErrorResponse(message, "");
    }
}
