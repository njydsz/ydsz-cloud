package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 系统异常类
 *
 * <p>用于封装基础设施故障类异常，如数据库连接失败、缓存服务异常、消息队列故障等。
 * 默认 HTTP 状态码为 500，异常分类为 SYSTEM。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new SysException(UnifiedExceptionCode.DATABASE_ERROR);
 * throw new SysException("database.error");
 * throw new SysException(UnifiedExceptionCode.CACHE_ERROR, cause);
 * throw SysException.of(UnifiedExceptionCode.INTERNAL_ERROR).cause(cause).build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#SYSTEM
 */
@ToString(callSuper = true)
public class SysException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数，初始化为 500 Internal Server Error / ERROR / SYSTEM
     */
    public SysException() {
        super();
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用国际化消息键构造系统异常
     *
     * @param key 国际化消息键
     */
    public SysException(String key) {
        super();
        init(UnifiedExceptionCode.INTERNAL_ERROR.getCode(), key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用异常码枚举构造系统异常
     *
     * @param exceptionCode 异常码枚举
     */
    public SysException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用国际化消息键和参数构造系统异常
     *
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public SysException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.INTERNAL_ERROR.getCode(), key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用异常码枚举和参数构造系统异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public SysException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用自定义错误码和消息键构造系统异常
     *
     * @param code 错误码字符串
     * @param key  国际化消息键
     */
    public SysException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用自定义错误码、消息键和参数构造系统异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public SysException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用原始异常构造系统异常
     *
     * @param cause 原始异常
     */
    public SysException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
        this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
    }

    /**
     * 使用自定义错误码和原始异常构造系统异常
     *
     * @param code  错误码字符串
     * @param cause 原始异常
     */
    public SysException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
        this.code = code;
    }

    /**
     * 使用异常码枚举和原始异常构造系统异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public SysException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用自定义错误码、消息键和原始异常构造系统异常
     *
     * @param code  错误码字符串
     * @param key   国际化消息键
     * @param cause 原始异常
     */
    public SysException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用自定义错误码、消息键、参数和原始异常构造系统异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     * @param cause  原始异常
     */
    public SysException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用 ResultCode 构造系统异常。
     *
     * <p>兼容业务模块中以 StandardResultCode 作为错误码的调用方式。
     *
     * @param resultCode 结果码
     */
    public SysException(ResultCode resultCode) {
        super();
        init(resultCode.getCode(), resultCode.getMessageKey(), new Object[]{},
             HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 使用 ResultCode 和自定义消息构造系统异常。
     *
     * <p>兼容业务模块中以 StandardResultCode 作为错误码的调用方式。
     *
     * @param resultCode 结果码
     * @param message    自定义消息
     */
    public SysException(ResultCode resultCode, String message) {
        super(message);
        init(resultCode.getCode(), resultCode.getMessageKey(), new Object[]{},
             HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
        setMessage(message);
    }

    /**
     * 使用 ResultCode、消息 key 和可变参数构造系统异常。
     *
     * <p>兼容业务模块中以 StandardResultCode 作为错误码、
     * 使用国际化消息 key 和参数的调用方式。
     *
     * @param resultCode 结果码
     * @param key        国际化消息 key
     * @param params     消息参数（可变参数）
     */
    public SysException(ResultCode resultCode, String key, Object... params) {
        super();
        init(resultCode.getCode(), key, params,
             HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.SYSTEM);
    }

    /**
     * 转换为可序列化的异常响应体，供全局异常处理器写回 HTTP 响应。
     *
     * <p>会触发国际化消息的懒加载解析，应在请求线程内调用以保证取到正确的 Locale。
     * 系统异常默认 HTTP 状态码为 500，属于基础设施故障，
     * 对外暴露时应避免把 {@code cause} 的原始堆栈信息带给客户端。
     *
     * @return 新建的异常信息对象，永不为 {@code null}
     */
    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    /**
     * 创建系统异常构建器。
     *
     * @return SysExceptionBuilder 实例
     */
    public static SysExceptionBuilder builder() {
        return new SysExceptionBuilder();
    }

    /**
     * 通过消息 key 创建系统异常。
     *
     * @param key 国际化消息 key（错误码自动取默认系统错误码）
     * @return SysException 实例
     */
    public static SysException of(String key) {
        return new SysException(key);
    }

    /**
     * 从预定义的异常码创建系统异常。
     *
     * @param exceptionCode 异常码（含 code、key、默认消息、HTTP 状态）
     * @return 携带该异常码默认配置的 SysException 实例
     */
    public static SysException of(ExceptionCode exceptionCode) {
        return new SysException(exceptionCode);
    }

    /**
     * 通过错误码与消息 key 创建系统异常。
     *
     * @param code 业务错误码
     * @param key  国际化消息 key
     * @return SysException 实例
     */
    public static SysException of(String code, String key) {
        return new SysException(code, key);
    }

    /**
     * 系统异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class SysExceptionBuilder extends YdszExceptionBuilder<SysException, SysExceptionBuilder> {

        @Override
        protected SysExceptionBuilder self() {
            return this;
        }

        public SysExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
            this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.SYSTEM;
        }

        @Override
        protected SysException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                       ExceptionLevel level, ExceptionCategory category,
                                       Throwable cause, String path, Object extData, String message) {
            SysException exception;
            if (cause != null) {
                exception = new SysException(code, key, params, cause);
            } else {
                exception = new SysException(code, key, params);
            }
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            return exception;
        }
    }
}
