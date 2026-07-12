package com.njydsz.pmis.common.exception.handler;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.custom.AbstractRemiException;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常处理器抽象基类
 *
 * <p>提供通用的异常处理逻辑，子类只需实现特定的日志前缀和响应格式定制。
 * 支持国际化消息、异常链追踪、差异化环境处理（开发/生产）。
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>模板方法模式：子类通过重写抽象方法定制特定行为</li>
 *   <li>策略模式：不同子类实现不同的异常处理策略</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Slf4j
 * @RestControllerAdvice
 * public class AppExceptionHandler extends MvcExceptionHandler {
 *     @Override
 *     protected String getLogPrefix() {
 *         return "【App端】";
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see BusinessException
 * @see BaseResponse
 */
@Slf4j
public abstract class BaseExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * 获取日志前缀，由子类实现以定制不同端的日志前缀
     *
     * @return 日志前缀字符串
     */
    protected abstract String getLogPrefix();

    /**
     * 是否需要包含 ExceptionInfo 详细信息
     *
     * <p>开发/测试环境返回 true，生产环境返回 false
     *
     * @return true-包含详细信息，false-不包含
     */
    protected boolean includeExceptionInfo() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
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

        if (throwable instanceof AbstractRemiException) {
            AbstractRemiException ex = (AbstractRemiException) throwable;
            info.setCode(ex.getCode());
            info.setKey(ex.getKey());
            info.setMessage(ex.getMessage());
            info.setHttpStatus(ex.getHttpStatus());
            info.setDetails(includeExceptionInfo() ? Map.of("stackTrace", getStackTraceString(throwable)) : null);
        } else {
            info.setCode("500");
            info.setMessage(getRootCauseMessage(throwable));
            info.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            info.setDetails(includeExceptionInfo() ? Map.of("stackTrace", getStackTraceString(throwable)) : null);
        }

        return info;
    }

    /**
     * 构建统一异常响应
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
