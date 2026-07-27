package com.njydsz.common.exception.custom;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

import com.njydsz.common.core.response.ResultCode;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 业务异常类
 *
 * <p>用于封装业务逻辑中的异常情况，支持国际化消息处理、异常分类、级别定义等功能。
 * 异常包含错误码、消息键、参数、HTTP状态码等完整上下文信息。
 *
 * <p><b>默认值：</b>
 * <ul>
 *   <li>HTTP 状态码：400 Bad Request</li>
 *   <li>异常级别：ERROR</li>
 *   <li>异常分类：BUSINESS</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new BusinessException(UnifiedExceptionCode.NOT_FOUND);
 * throw new BusinessException(UnifiedExceptionCode.NOT_FOUND).data("userId", 123);
 * throw BusinessException.builder()
 *     .code("USER_NOT_FOUND")
 *     .key("user.not.found")
 *     .httpStatus(404)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#BUSINESS
 */
@ToString(callSuper = true)
public class BusinessException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /** 默认 HTTP 状态码 */
    private static final int DEFAULT_HTTP_STATUS = HttpStatus.BAD_REQUEST.value();
    /** 默认异常级别 */
    private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.ERROR;
    /** 默认异常分类 */
    private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.BUSINESS;
    /** 默认错误码 */
    private static final String DEFAULT_CODE = UnifiedExceptionCode.FAIL.getCode();

    private transient ConcurrentHashMap<String, Object> dataMap;

    // ==================== 构造函数 ====================

    /**
     * 默认构造函数，初始化为 400 Bad Request / ERROR / BUSINESS
     */
    public BusinessException() {
        super();
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用异常码枚举构造业务异常
     *
     * @param exceptionCode 异常码枚举
     */
    public BusinessException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用异常码枚举和参数构造业务异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public BusinessException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用异常码枚举和自定义消息构造业务异常
     *
     * @param exceptionCode 异常码枚举
     * @param message       自定义异常消息
     */
    public BusinessException(ExceptionCode exceptionCode, String message) {
        super(message);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        initFields(exceptionCode.getCode(), exceptionCode.getKey(), new Object[]{});
        this.message = message;
        this.messageResolved = true;
    }

    /**
     * 使用统一结果码构造业务异常（兼容 {@link ResultCode} 体系）
     *
     * <p>用于业务模块尚未迁移到 {@link ExceptionCode}，但已实现 {@link ResultCode} 的场景。
     * 消息回退为 {@link ResultCode#getMsg()}，避免缺少国际化消息时出现纯 key。
     *
     * @param resultCode 统一结果码
     */
    public BusinessException(ResultCode resultCode) {
        super();
        init(resultCode.getCode(), resultCode.getMsg(), new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用统一结果码和自定义消息构造业务异常
     *
     * @param resultCode 统一结果码
     * @param message    自定义异常消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        initFields(resultCode.getCode(), resultCode.getMsg(), new Object[]{});
        this.message = message;
        this.messageResolved = true;
    }

    /**
     * 使用国际化消息键构造业务异常
     *
     * @param key 国际化消息键
     */
    public BusinessException(String key) {
        super();
        init(DEFAULT_CODE, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用国际化消息键和参数构造业务异常
     *
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public BusinessException(String key, Object[] params) {
        super();
        init(DEFAULT_CODE, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用自定义错误码和消息键构造业务异常
     *
     * @param code 错误码字符串
     * @param key  国际化消息键
     */
    public BusinessException(String code, String key) {
        super();
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用自定义错误码、消息键和参数构造业务异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     */
    public BusinessException(String code, String key, Object[] params) {
        super();
        init(code, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用原始异常构造业务异常
     *
     * @param cause 原始异常
     */
    public BusinessException(Throwable cause) {
        super(cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }

    /**
     * 使用自定义错误码和原始异常构造业务异常
     *
     * @param code  错误码字符串
     * @param cause 原始异常
     */
    public BusinessException(String code, Throwable cause) {
        super(cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = code;
    }

    /**
     * 使用异常码枚举和原始异常构造业务异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public BusinessException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用自定义错误码、消息键和原始异常构造业务异常
     *
     * @param code  错误码字符串
     * @param key   国际化消息键
     * @param cause 原始异常
     */
    public BusinessException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用自定义错误码、消息键、参数和原始异常构造业务异常
     *
     * @param code   错误码字符串
     * @param key    国际化消息键
     * @param params 消息参数
     * @param cause  原始异常
     */
    public BusinessException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    // ==================== 业务方法 ====================

    /**
     * 添加附加数据（链式调用）
     *
     * @param key   数据键
     * @param value 数据值
     * @return 当前异常对象
     */
    public BusinessException data(String key, Object value) {
        if (this.dataMap == null) {
            this.dataMap = new ConcurrentHashMap<>();
            this.extData = this.dataMap;
        }
        this.dataMap.put(key, value);
        return this;
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    /**
     * 获取业务异常构建器
     *
     * @return BusinessExceptionBuilder 实例
     */
    public static BusinessExceptionBuilder builder() {
        return new BusinessExceptionBuilder();
    }

    public static BusinessException of(ExceptionCode exceptionCode) {
        return new BusinessException(exceptionCode);
    }

    // ==================== Builder ====================

    /**
     * 业务异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class BusinessExceptionBuilder extends YdszExceptionBuilder<BusinessException, BusinessExceptionBuilder> {

        @Override
        protected BusinessExceptionBuilder self() {
            return this;
        }

        public BusinessExceptionBuilder() {
            super();
            this.code = DEFAULT_CODE;
            this.httpStatus = DEFAULT_HTTP_STATUS;
            this.level = DEFAULT_LEVEL;
            this.category = DEFAULT_CATEGORY;
        }

        @Override
        protected BusinessException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                            ExceptionLevel level, ExceptionCategory category,
                                            Throwable cause, String path, Object extData, String message) {
            BusinessException exception = new BusinessException();
            exception.initFields(code, key, params);
            exception.setSubCode(subCode);
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }
    }
}
