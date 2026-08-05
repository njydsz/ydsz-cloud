package com.remisoft.common.exception.custom;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.code.ResultCode;
import com.remisoft.common.exception.code.UnifiedExceptionCode;
import com.remisoft.common.exception.core.ExceptionInfo;
import com.remisoft.common.exception.enums.ExceptionCategory;
import com.remisoft.common.exception.enums.ExceptionCode;
import com.remisoft.common.exception.enums.ExceptionLevel;

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
 * @author remi-team
 * @since 1.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#BUSINESS
 */
@ToString(callSuper = true)
public class BusinessException extends AbstractRemiException {

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
     * 从 v1.1.0 起，当传入的 ResultCode 同时实现 ExceptionCode 时，会优先走
     * {@link #BusinessException(ExceptionCode)} 构造函数以获得完整的 i18n 支持。
     * 消息回退为 {@link ResultCode#getMsg()}，避免缺少国际化消息时出现纯 key。
     *
     * <p><b>行为变更（v1.1.0）：</b>HTTP 状态码不再硬编码为 400，
     * 而是尊重 ResultCode 声明的 {@link ResultCode#getHttpStatusCode()} 值。
     *
     * @param resultCode 统一结果码
     */
    public BusinessException(ResultCode resultCode) {
        super();
        int httpStatus = resolveHttpStatus(resultCode);
        init(resultCode.getCode(), resultCode.getMsg(), new Object[]{}, httpStatus, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用统一结果码和自定义消息构造业务异常
     *
     * <p><b>行为变更（v1.1.0）：</b>HTTP 状态码不再硬编码为 400，
     * 而是尊重 ResultCode 声明的 {@link ResultCode#getHttpStatusCode()} 值。
     *
     * @param resultCode 统一结果码
     * @param message    自定义异常消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        int httpStatus = resolveHttpStatus(resultCode);
        initDefaults(httpStatus, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        initFields(resultCode.getCode(), resultCode.getMsg(), new Object[]{});
        this.message = message;
        this.messageResolved = true;
    }

    /**
     * 使用 {@link BaseResultCode} 构造业务异常。
     *
     * <p>remi-common-core 精简后 {@code BaseResultCode} 不再实现 {@link ResultCode} 接口，
     * 此重载用于兼容业务模块中以 BaseResultCode 作为错误码的调用方式。
     *
     * @param resultCode 基础结果码
     */
    public BusinessException(BaseResultCode resultCode) {
        super();
        init(resultCode.getCode(), resultCode.getMessageKey(), new Object[]{},
             resultCode.getHttpStatusCode(), DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 从 ResultCode 中解析 HTTP 状态码，优先使用显式声明的值，
     * 回退到默认 400。
     *
     * @param resultCode 结果码
     * @return HTTP 状态码
     */
    private static int resolveHttpStatus(ResultCode resultCode) {
        try {
            int status = resultCode.getHttpStatusCode();
            if (status > 0) {
                return status;
            }
        } catch (Exception ignored) {
            // 防御性编程：任何异常都回退到默认值
        }
        return DEFAULT_HTTP_STATUS;
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

    /**
     * 转换为可序列化的异常响应体，供全局异常处理器写回 HTTP 响应。
     *
     * <p>会触发国际化消息的懒加载解析，应在请求线程内调用以保证取到正确的 Locale。
     * 注意：通过 {@link #data(String, Object)} 附加的业务数据不会进入返回对象，
     * 需要透出时由调用方自行从 {@code getExtData()} 读取并填充 {@code details}。
     *
     * @return 新建的异常信息对象，永不为 {@code null}
     */
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

    /**
     * 从预定义的异常码创建业务异常。
     *
     * @param exceptionCode 异常码（含 code、key、默认消息、HTTP 状态）
     * @return 携带该异常码默认配置的 BusinessException 实例
     */
    public static BusinessException of(ExceptionCode exceptionCode) {
        return new BusinessException(exceptionCode);
    }

    // ==================== Builder ====================

    /**
     * 业务异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class BusinessExceptionBuilder extends RemiExceptionBuilder<BusinessException, BusinessExceptionBuilder> {

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
