package com.njydsz.pmis.common.exception.handler;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.custom.ConcurrencyException;
import com.njydsz.pmis.common.exception.custom.DuplicateException;
import com.njydsz.pmis.common.exception.custom.ExternalException;
import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.exception.custom.RateLimitException;
import com.njydsz.pmis.common.exception.custom.RemiSecurityException;
import com.njydsz.pmis.common.exception.custom.RemiTimeoutException;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.exception.custom.ValidationException;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;
import com.njydsz.pmis.common.exception.observability.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring MVC 鍏ㄥ眬寮傚父澶勭悊鍣紙闈?Validation 閮ㄥ垎锛? *
 * <p>澶勭悊涓氬姟寮傚父銆佺郴缁熷紓甯搞€佸畨鍏ㄥ紓甯哥瓑閫氱敤寮傚父銆? * Validation 鐩稿叧寮傚父澶勭悊鐢?{@link ValidationExceptionHandler} 璐熻矗锛堜粎鍦?jakarta.validation 瀛樺湪鏃舵敞鍐岋級銆? *
 * <p><b>鑱岃矗鍒嗗眰锛?/b>
 * <ul>
 *   <li>鏈被锛氬鐞嗘鏋剁骇銆佷笟鍔＄骇銆佸畨鍏ㄧ骇寮傚父锛堟渶楂樹紭鍏堢骇锛?/li>
 *   <li>{@link ValidationExceptionHandler}锛氬鐞嗗弬鏁版牎楠屽紓甯?/li>
 * </ul>
 *
 * <p><b>瑁呴厤锛?/b>鏈被宸蹭笉鍐嶇洿鎺ユ爣娉?{@code @AutoConfiguration}锛? * 鏀圭敱 {@link MvcExceptionHandlerAutoConfiguration} 璐熻矗鏉′欢瑁呴厤涓?Bean 娉ㄥ叆銆? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see BaseExceptionHandler
 * @see ValidationExceptionHandler
 * @see MvcExceptionHandlerAutoConfiguration
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@Order(Ordered.HIGHEST_PRECEDENCE)
@org.springframework.web.bind.annotation.RestControllerAdvice
public class MvcExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;
    private final ExceptionMetrics exceptionMetrics;

    public MvcExceptionHandler(MessageSource messageSource,
                               ExceptionMetrics exceptionMetrics) {
        this.messageSource = messageSource;
        this.exceptionMetrics = exceptionMetrics;
    }

    /**
     * 璁板綍寮傚父鎸囨爣
     */
    private void recordExceptionMetrics(Throwable throwable) {
        if (exceptionMetrics != null) {
            exceptionMetrics.recordException(throwable);
        }
    }

    @Override
    protected String getLogPrefix() {
        return "銆愬叏灞€銆?;
    }

    /**
     * 浠?HttpServletRequest / MDC 鎻愬彇 traceId
     *
     * <p>浼樺厛绾э細MDC > Request Header
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null && request != null) {
            traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
            if (traceId == null) {
                traceId = request.getHeader(HeaderConstants.X_REQUEST_ID);
            }
        }
        return traceId;
    }

    // ============================ 寮傚父澶勭悊鏂规硶 ============================

    /**
     * 澶勭悊涓氬姟寮傚父
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        recordExceptionMetrics(e);
        log.warn("{}涓氬姟寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 澶勭悊骞跺彂鍐茬獊寮傚父
     */
    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleConcurrencyException(ConcurrencyException e, HttpServletRequest request) {
        log.warn("{}骞跺彂鍐茬獊寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 澶勭悊閲嶅鎻愪氦寮傚父
     */
    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleDuplicateException(DuplicateException e, HttpServletRequest request) {
        log.warn("{}閲嶅鎻愪氦寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 澶勭悊闄愭祦寮傚父
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public BaseResponse<?> handleRateLimitException(RateLimitException e, HttpServletRequest request) {
        log.warn("{}闄愭祦寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    // Validation 鐩稿叧寮傚父澶勭悊宸茬Щ鑷?ValidationExceptionHandler

    /**
     * 澶勭悊璇锋眰浣撹В鏋愬紓甯?     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("{}璇锋眰浣撹В鏋愬紓甯?| 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("invalid.request.format", null,
                "璇锋眰鏍煎紡閿欒", LocaleContextHolder.getLocale());

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getKey(),
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊缂哄皯璇锋眰鍙傛暟寮傚父
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = messageSource.getMessage("missing.request.parameter",
                new Object[]{e.getParameterName()}, "缂哄皯璇锋眰鍙傛暟", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 澶勭悊璇锋眰鍙傛暟绫诲瀷涓嶅尮閰嶅紓甯?     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Class<?> requiredType = e.getRequiredType();
        String message = messageSource.getMessage("type.mismatch",
                new Object[]{e.getName(), requiredType != null ? requiredType.getSimpleName() : "鏈煡"},
                "鍙傛暟绫诲瀷涓嶅尮閰?, LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 澶勭悊缂哄皯璇锋眰澶村紓甯?     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMissingRequestHeaderException(
            MissingRequestHeaderException e, HttpServletRequest request) {
        String message = messageSource.getMessage("missing.request.header",
                new Object[]{e.getHeaderName()}, "缂哄皯璇锋眰澶?, LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 澶勭悊璇锋眰鏂规硶涓嶆敮鎸佸紓甯?     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public BaseResponse<?> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String message = messageSource.getMessage("method.not.supported",
                new Object[]{e.getMethod()}, "涓嶆敮鎸佺殑璇锋眰鏂规硶", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.METHOD_NOT_ALLOWED.value(),
                request.getRequestURI(), e);
    }

    /**
     * 澶勭悊鏂囦欢涓婁紶澶у皬瓒呴檺寮傚父
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public BaseResponse<?> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        String message = messageSource.getMessage("file.size.exceeded.message", null,
                "涓婁紶鏂囦欢澶у皬瓒呭嚭闄愬埗", LocaleContextHolder.getLocale());
        log.error("{}鏂囦欢涓婁紶瓒呴檺 | 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), message, e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getKey(),
                message,
                HttpStatus.CONTENT_TOO_LARGE.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊 404 寮傚父
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public BaseResponse<?> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        String message = messageSource.getMessage("resource.not.found.detail",
                new Object[]{request.getRequestURI()}, "璧勬簮涓嶅瓨鍦?, LocaleContextHolder.getLocale());
        log.error("{}璧勬簮涓嶅瓨鍦?| 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), message, e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getCode(),
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getKey(),
                message,
                HttpStatus.NOT_FOUND.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊闈炴硶鍙傛暟寮傚父
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        log.error("{}闈炴硶鍙傛暟寮傚父 | 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getKey(),
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                e.getMessage(),
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊绯荤粺寮傚父
     */
    @ExceptionHandler(SysException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleSysException(SysException e, HttpServletRequest request) {
        log.error("{}绯荤粺寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊瀹夊叏寮傚父
     */
    @ExceptionHandler(RemiSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public BaseResponse<?> handleSecurityException(RemiSecurityException e, HttpServletRequest request) {
        log.warn("{}瀹夊叏寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊鏍￠獙寮傚父
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleValidationException(ValidationException e, HttpServletRequest request) {
        log.warn("{}鏍￠獙寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊瓒呮椂寮傚父
     */
    @ExceptionHandler(RemiTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public BaseResponse<?> handleTimeoutException(RemiTimeoutException e, HttpServletRequest request) {
        log.error("{}瓒呮椂寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊澶栭儴鏈嶅姟寮傚父
     */
    @ExceptionHandler(ExternalException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public BaseResponse<?> handleExternalException(ExternalException e, HttpServletRequest request) {
        log.error("{}澶栭儴鏈嶅姟寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊鍩虹璁炬柦寮傚父
     */
    @ExceptionHandler(InfrastructureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleInfrastructureException(InfrastructureException e, HttpServletRequest request) {
        log.error("{}鍩虹璁炬柦寮傚父 | 璺緞: {} | 閿欒鐮? {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        return BaseResponse.error(e.getCode(), e.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 澶勭悊闈炴硶鐘舵€佸紓甯?     *
     * <p>IllegalStateException 灞炰簬绯荤粺绾у紓甯革紙闈炰笟鍔″紓甯革級锛岀粺涓€杩斿洖 SYSTEM_ERROR锛?     * 閬垮厤鏆撮湶鍐呴儴鐘舵€佷俊鎭€備笟鍔″眰鐨?鐘舵€佹棤鏁?搴斾娇鐢?{@link BusinessException}銆?     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleIllegalStateException(
            IllegalStateException e, HttpServletRequest request) {
        log.error("{}闈炴硶鐘舵€佸紓甯?| 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "绯荤粺寮傚父锛岃鑱旂郴绠＄悊鍛?, LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊绌烘寚閽堝紓甯?     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleNullPointerException(
            NullPointerException e, HttpServletRequest request) {
        log.error("{}绌烘寚閽堝紓甯?| 璺緞: {} | 娑堟伅: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "绯荤粺寮傚父锛岃鑱旂郴绠＄悊鍛?, LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 澶勭悊鎵€鏈夋湭鎹曡幏鐨勫紓甯?     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleException(Exception e, HttpServletRequest request) {
        log.error("{}绯荤粺寮傚父 | 璺緞: {} | 绫诲瀷: {} | 娑堟伅: {}",
                getLogPrefix(), request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "绯荤粺寮傚父锛岃鑱旂郴绠＄悊鍛?, LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }
}
