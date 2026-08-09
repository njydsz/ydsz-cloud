package com.njydsz.common.exception.custom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
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
 * <p><b>精简设计（v2.0）：</b>仅保留 3 个核心构造函数，
 * 其他参数化构造通过 {@link #builder()} 链式 Builder 完成，消除 15+ 构造函数爆炸问题。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 简单抛出
 * throw BusinessException.of(UnifiedExceptionCode.NOT_FOUND);
 *
 * // 带原始异常
 * throw new BusinessException(UnifiedExceptionCode.DATABASE_ERROR, cause);
 *
 * // 完整参数链式构建
 * throw BusinessException.builder()
 *     .code("USER_NOT_FOUND")
 *     .key("user.not.found")
 *     .httpStatus(404)
 *     .build();
 *
 * // 携带业务数据
 * throw BusinessException.of(UnifiedExceptionCode.PARAM_ERROR)
 *     .data("field", "username");
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

    // ==================== 核心构造函数（仅限 3 个） ====================

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
     * 使用异常码枚举和自定义消息构造业务异常（兼容构造器）。
     *
     * <p>保留给 {@code super(exceptionCode, message)} 形式的存量调用方
     * （如 {@code TenantIsolationException}），新代码请优先使用
     * {@link #BusinessException(ExceptionCode)} 或 {@link #builder()}。</p>
     *
     * @param exceptionCode 异常码枚举
     * @param message       自定义异常消息
     */
    public BusinessException(ExceptionCode exceptionCode, String message) {
        super(message);
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
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
    public static class BusinessExceptionBuilder extends YdszExceptionBuilder<BusinessException> {

        public BusinessExceptionBuilder() {
            this.code = DEFAULT_CODE;
            this.httpStatus = DEFAULT_HTTP_STATUS;
            this.level = DEFAULT_LEVEL;
            this.category = DEFAULT_CATEGORY;
        }

        /**
         * 便捷方法：设置 {@link ResultCode} 作为错误码
         */
        public BusinessExceptionBuilder resultCode(ResultCode resultCode) {
            if (resultCode != null) {
                this.code = resultCode.getCode();
                this.key = resultCode.getMessageKey();
                this.httpStatus = resultCode.getHttpStatusCode();
            }
            return this;
        }

        /**
         * 便捷方法：设置 {@link BaseResultCode} 作为错误码
         */
        public BusinessExceptionBuilder resultCode(BaseResultCode resultCode) {
            if (resultCode != null) {
                this.code = resultCode.getCode();
                this.key = resultCode.getMessageKey();
                this.httpStatus = resultCode.getHttpStatusCode();
            }
            return this;
        }

        /**
         * 便捷方法：设置国际化消息参数（变长参数版）
         *
         * <p>覆盖基类的 {@code params(Object[])} 以提供变长参数调用方式。
         */
        @Override
        public BusinessExceptionBuilder params(Object... params) {
            this.params = params;
            return this;
        }

        @Override
        protected BusinessException doBuild(String code, String key, Object[] params, int httpStatus,
                                            ExceptionLevel level, ExceptionCategory category,
                                            Throwable cause, Map<String, Object> extData, String message) {
            BusinessException exception = new BusinessException();
            exception.initFields(code, key, params);
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setExtData(extData);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }
    }
}
