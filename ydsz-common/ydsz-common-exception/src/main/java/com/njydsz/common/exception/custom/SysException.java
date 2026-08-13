package com.njydsz.common.exception.custom;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.code.CoreExceptionCode;
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
 * <p><b>精简设计（v2.0）：</b>仅保留 3 个核心构造函数，
 * 其他参数化构造通过 {@link #builder()} 链式 Builder 完成，消除 20+ 构造函数爆炸问题。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 简单抛出
 * throw SysException.of(CoreExceptionCode.DATABASE_ERROR);
 *
 * // 包装底层异常
 * throw new SysException(CoreExceptionCode.CACHE_ERROR, cause);
 *
 * // 完整参数链式构建
 * throw SysException.builder()
 *     .code("MQ_PUBLISH_FAILED")
 *     .key("mq.publish.failed")
 *     .level(ExceptionLevel.FATAL)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CoreExceptionCode
 * @see ExceptionCategory#SYSTEM
 */
@ToString(callSuper = true)
public class SysException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /** 默认 HTTP 状态码 */
    private static final int DEFAULT_HTTP_STATUS = HttpStatus.INTERNAL_SERVER_ERROR.value();
    /** 默认异常级别 */
    private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.ERROR;
    /** 默认异常分类 */
    private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.SYSTEM;
    /** 默认错误码 */
    private static final String DEFAULT_CODE = CoreExceptionCode.INTERNAL_ERROR.getCode();

    // ==================== 核心构造函数（仅限 3 个） ====================

    /**
     * 默认构造函数，初始化为 500 Internal Server Error / ERROR / SYSTEM
     */
    public SysException() {
        super();
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }

    /**
     * 使用异常码枚举构造系统异常
     *
     * @param exceptionCode 异常码枚举
     */
    public SysException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用异常码枚举和原始异常构造系统异常
     *
     * @param exceptionCode 异常码枚举
     * @param cause         原始异常
     */
    public SysException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    /**
     * 使用自定义消息构造系统异常（兼容构造器，v2.0 精简后由 Builder 替代）。
     *
     * <p>大量存量调用方仍使用消息字符串形式，保留此构造器避免批量迁移；
     * 新代码请优先使用 {@link #builder()} 或 {@link #SysException(ExceptionCode)}。</p>
     *
     * @param message 异常详细信息
     */
    public SysException(String message) {
        super(message);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }

    /**
     * 使用自定义消息和原始异常构造系统异常（兼容构造器，v2.0 精简后由 Builder 替代）。
     *
     * @param message 异常详细信息
     * @param cause   原始异常
     */
    public SysException(String message, Throwable cause) {
        super(message, cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }

    // ==================== 业务方法 ====================

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
        SysException exception = new SysException();
        exception.initFields(DEFAULT_CODE, key, new Object[]{});
        return exception;
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
        SysException exception = new SysException();
        exception.initFields(code, key, new Object[]{});
        return exception;
    }

    // ==================== Builder ====================

    /**
     * 系统异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class SysExceptionBuilder extends YdszExceptionBuilder<SysException> {

        public SysExceptionBuilder() {
            this.code = DEFAULT_CODE;
            this.httpStatus = DEFAULT_HTTP_STATUS;
            this.level = DEFAULT_LEVEL;
            this.category = DEFAULT_CATEGORY;
        }

        /**
         * 便捷方法：设置 {@link ExceptionCode} 作为错误码（推荐）。
         */
        public SysExceptionBuilder resultCode(ExceptionCode exceptionCode) {
            if (exceptionCode != null) {
                this.code = exceptionCode.getCode();
                this.key = exceptionCode.getKey();
                this.httpStatus = exceptionCode.getHttpStatus();
            }
            return this;
        }

        /**
         * 兼容路径：从 {@link ResultCode} 提取错误码。
         *
         * <p>ResultCode 不包含 HTTP 状态码，使用 {@link #DEFAULT_HTTP_STATUS} 作为默认。
         * 如需精确 HTTP 语义，请使用 {@link #resultCode(ExceptionCode)} 传入异常码。
         */
        public SysExceptionBuilder resultCode(ResultCode resultCode) {
            if (resultCode != null) {
                this.code = resultCode.getCode();
                this.key = resultCode.getKey();
                this.httpStatus = DEFAULT_HTTP_STATUS;
            }
            return this;
        }

        /**
         * 便捷方法：设置 {@link BaseResultCode} 作为错误码（兼容路径）。n         *
         * <p>BaseResultCode 不包含 HTTP 状态码，使用默认值。
         */
        public SysExceptionBuilder resultCode(BaseResultCode resultCode) {
            if (resultCode != null) {
                this.code = resultCode.getCode();
                this.key = resultCode.getKey();
                this.httpStatus = (resultCode == BaseResultCode.SUCCESS) ? 200 : DEFAULT_HTTP_STATUS;
            }
            return this;
        }

        /**
         * 便捷方法：设置国际化消息参数（变长参数版）。
         *
         * <p>覆盖基类的 {@code params(Object[])} 以提供变长参数调用方式，
         * 与 {@link com.njydsz.common.exception.custom.BusinessException.BusinessExceptionBuilder#params(Object...)} 对齐。
         */
        @Override
        public SysExceptionBuilder params(Object... params) {
            this.params = params;
            return this;
        }

        /**
         * 覆盖业务错误码（保持链式返回子类类型，便于后续继续调用变长 {@link #params(Object...)}）。
         */
        @Override
        public SysExceptionBuilder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * 设置国际化消息键（保持链式返回子类类型）。
         */
        @Override
        public SysExceptionBuilder key(String key) {
            this.key = key;
            return this;
        }

        @Override
        protected SysException doBuild(String code, String key, Object[] params, int httpStatus,
                                       ExceptionLevel level, ExceptionCategory category,
                                       Throwable cause, Map<String, Object> extData, String message) {
            SysException exception = new SysException();
            exception.initFields(code, key, params);
            if (cause != null) {
                exception.initCause(cause);
            }
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setExtData(extData);
            if (message != null) {
                exception.setMessage(message);
            }
            return exception;
        }
    }
}
