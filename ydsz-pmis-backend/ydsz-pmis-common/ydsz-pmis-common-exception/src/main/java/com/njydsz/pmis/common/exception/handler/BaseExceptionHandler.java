package com.njydsz.pmis.common.exception.handler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.custom.AbstractYdszException;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;
import com.njydsz.pmis.common.exception.model.ProblemDetail;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常处理器抽象基类
 *
 * <p>提供通用的异常处理逻辑，子类只需实现特定的日志前缀和响应格式定制。
 * 支持国际化消息、异常链追踪、差异化环境处理（开发/生产）、
 * RFC 7807 ProblemDetail 输出格式切换、统一指标记录。
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>模板方法模式：子类通过重写抽象方法定制特定行为</li>
 *   <li>策略模式：不同子类实现不同的异常处理策略</li>
 * </ul>
 *
 * <p><b>响应格式：</b>
 * 通过 {@code ydsz.exception.response-format} 配置项切换：
 * <ul>
 *   <li>{@code base-response}（默认）— 返回 {@link BaseResponse} 格式</li>
 *   <li>{@code problem-detail} — 返回 RFC 7807 {@link ProblemDetail} 格式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see BusinessException
 * @see BaseResponse
 * @see ProblemDetail
 */
@Slf4j
public abstract class BaseExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @Value("${ydsz.exception.response-format:base-response}")
    private String responseFormat;

    @Value("${ydsz.exception.include-stack-trace:false}")
    private boolean includeStackTraceConfig;

    private ExceptionMetrics exceptionMetrics;

    /**
     * 获取日志前缀，由子类实现以定制不同端的日志前缀
     *
     * @return 日志前缀字符串
     */
    protected abstract String getLogPrefix();

    /**
     * 设置异常指标统计器（由 AutoConfiguration 注入）
     *
     * @param exceptionMetrics 异常指标统计器
     */
    protected void setExceptionMetrics(ExceptionMetrics exceptionMetrics) {
        this.exceptionMetrics = exceptionMetrics;
    }

    /**
     * 获取异常指标统计器
     *
     * @return 异常指标统计器；未注入时返回 null
     */
    protected ExceptionMetrics getExceptionMetrics() {
        return exceptionMetrics;
    }

    /**
     * 记录异常指标（统一入口，所有 handler 调用此方法）
     *
     * <p>如果异常指标统计器未注入或被禁用，此方法为空操作。
     *
     * @param throwable 异常对象
     */
    protected void recordMetrics(Throwable throwable) {
        if (exceptionMetrics != null) {
            exceptionMetrics.recordException(throwable);
        }
    }

    /**
     * 是否需要包含 ExceptionInfo 详细信息
     *
     * <p>开发/测试环境返回 true，生产环境返回 false。
     * 也可通过 {@code ydsz.exception.include-stack-trace} 配置强制开启。
     *
     * @return true-包含详细信息，false-不包含
     */
    protected boolean includeExceptionInfo() {
        return includeStackTraceConfig
                || "dev".equalsIgnoreCase(activeProfile)
                || "test".equalsIgnoreCase(activeProfile);
    }

    /**
     * 是否使用 ProblemDetail (RFC 7807) 响应格式
     *
     * @return true-使用 ProblemDetail 格式，false-使用 BaseResponse 格式
     */
    protected boolean useProblemDetail() {
        return "problem-detail".equalsIgnoreCase(responseFormat);
    }

    /**
     * 获取根本原因的消息
     */
    protected static String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    /**
     * 获取堆栈跟踪字符串
     */
    protected static String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return writer.toString();
    }

    /**
     * 构建异常信息对象
     *
     * @param throwable 异常对象
     * @param path      请求路径
     * @param traceId   追踪 ID（可为 null）
     * @return 异常信息对象
     */
    protected ExceptionInfo buildExceptionInfo(Throwable throwable, String path, String traceId) {
        ExceptionInfo info = new ExceptionInfo();
        info.setPath(path);
        if (traceId != null) {
            info.setTraceId(traceId);
        }
        info.setTimestamp(LocalDateTime.now());

        if (throwable instanceof AbstractYdszException) {
            AbstractYdszException ex = (AbstractYdszException) throwable;
            info.setCode(ex.getCode());
            info.setKey(ex.getKey());
            info.setMessage(ex.getMessage());
            info.setHttpStatus(ex.getHttpStatus());
            if (includeExceptionInfo()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("stackTrace", getStackTraceString(throwable));
                if (ex.getExtData() instanceof Map<?, ?> rawMap) {
                    rawMap.forEach((k, v) -> details.put(String.valueOf(k), v));
                }
                info.setDetails(details);
            }
        } else {
            info.setCode(UnifiedExceptionCode.INTERNAL_ERROR.getCode());
            info.setMessage(getRootCauseMessage(throwable));
            info.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            if (includeExceptionInfo()) {
                info.setDetails(Map.of("stackTrace", getStackTraceString(throwable)));
            }
        }

        return info;
    }

    /**
     * 构建 RFC 7807 ProblemDetail 对象
     *
     * @param throwable 异常对象
     * @param path      请求路径
     * @param traceId   追踪 ID（可为 null）
     * @return ProblemDetail 对象
     */
    protected ProblemDetail buildProblemDetail(Throwable throwable, String path, String traceId) {
        ProblemDetail.ProblemDetailBuilder builder = ProblemDetail.builder()
                .instance(path != null ? URI.create(path) : null)
                .traceId(traceId)
                .timestamp(Instant.now());

        if (throwable instanceof AbstractYdszException) {
            AbstractYdszException ex = (AbstractYdszException) throwable;
            builder.type(URI.create("https://pmis.njydsz.com/errors/" + ex.getCategory().name().toLowerCase()))
                    .title(ex.getClass().getSimpleName())
                    .status(ex.getHttpStatus())
                    .detail(ex.getMessage())
                    .errorCode(ex.getCode());
            if (ex.getExtData() instanceof Map<?, ?> rawMap) {
                Map<String, Object> extMap = new LinkedHashMap<>();
                rawMap.forEach((k, v) -> extMap.put(String.valueOf(k), v));
                builder.extensions(extMap);
            }
        } else {
            builder.type(URI.create("https://pmis.njydsz.com/errors/system"))
                    .title("System Error")
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .detail(getRootCauseMessage(throwable))
                    .errorCode(UnifiedExceptionCode.INTERNAL_ERROR.getCode());
        }

        return builder.build();
    }

    /**
     * 构建统一异常响应（根据配置自动选择 BaseResponse 或 ProblemDetail 格式）
     *
     * @param throwable 异常对象
     * @param path      请求路径
     * @param traceId   追踪 ID
     * @return 响应对象（BaseResponse 或 ProblemDetail）
     */
    protected Object buildResponse(Throwable throwable, String path, String traceId) {
        if (useProblemDetail()) {
            return buildProblemDetail(throwable, path, traceId);
        }
        ExceptionInfo info = buildExceptionInfo(throwable, path, traceId);
        if (throwable instanceof AbstractYdszException) {
            AbstractYdszException ex = (AbstractYdszException) throwable;
            return BaseResponse.error(ex.getCode(), ex.getMessage(),
                    includeExceptionInfo() ? info : null);
        }
        return BaseResponse.error(
                UnifiedExceptionCode.INTERNAL_ERROR.getCode(),
                info.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 构建 ResponseEntity（动态 HTTP 状态码）
     *
     * <p>从异常对象中提取 HTTP 状态码，设置到 ResponseEntity 中。
     * 解决 {@code @ResponseStatus} 只能设置固定状态码的问题。
     *
     * @param body      响应体
     * @param throwable 异常对象
     * @return ResponseEntity
     */
    protected ResponseEntity<Object> buildResponseEntity(Object body, Throwable throwable) {
        int httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (throwable instanceof AbstractYdszException) {
            httpStatus = ((AbstractYdszException) throwable).getHttpStatus();
        }
        return ResponseEntity.status(httpStatus).body(body);
    }

    /**
     * 构建校验错误响应
     *
     * @param errorCode  异常码
     * @param message    异常消息
     * @param httpStatus HTTP 状态码
     * @param path       请求路径
     * @param throwable  原始异常
     * @return 统一响应格式
     */
    protected BaseResponse<?> buildValidationErrorResponse(
            UnifiedExceptionCode errorCode, String message, int httpStatus,
            String path, Throwable throwable) {
        log.error("{}校验异常 | 路径: {} | 消息: {}", getLogPrefix(), path, message, throwable);
        recordMetrics(throwable);

        ExceptionInfo info = new ExceptionInfo(
                errorCode.getCode(),
                errorCode.getKey(),
                message,
                httpStatus
        );
        info.setPath(path);
        return BaseResponse.error(
                errorCode.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }
}
